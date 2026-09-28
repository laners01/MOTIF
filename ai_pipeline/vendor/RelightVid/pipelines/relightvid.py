'''
Use pretrained iclight model for illumination reference modification, and add temporal consistency layer
'''

import torch
from .diffusion import DDIMLDMTextTraining
from einops import rearrange

from modules.video_unet_temporal.resnet import InflatedConv3d
from safetensors.torch import load_file

import torch.nn.functional as F

from torch import nn
import cv2
import os
from torch.hub import download_url_to_file


class RelightVidConditioner(DDIMLDMTextTraining):
    @torch.no_grad()
    def encode_text(self, text):
        with torch.cuda.amp.autocast(dtype=torch.float16):
            encoded_text = super().encode_text(text)
        return encoded_text

    @torch.no_grad()
    def encode_image_to_latent(self, image):
        b, f, c, h, w = image.shape
        image = rearrange(image, 'b f c h w -> (b f) c h w')
        latent = super().encode_image_to_latent(image)
        latent = rearrange(latent, '(b f) c h w -> b f c h w', b=b)
        return latent

    @torch.no_grad()
    def decode_latent_to_image(self, latent):
        b, f, c, h, w = latent.shape
        latent = rearrange(latent, 'b f c h w -> (b f) c h w')

        image = []
        for latent_ in latent:
            image_ = super().decode_latent_to_image(latent_[None])
            image.append(image_.sample) 
        image = torch.cat(image, dim=0)
        image = rearrange(image, '(b f) c h w -> b f c h w', b=b)
        return image


class RelightVidBGConditioner(RelightVidConditioner):
    def initialize_unet(self, sd_path, ic_path, relvid_mm_path, ic_channels): 
        if relvid_mm_path is not None:
            print(f'INFO: initialize denoising UNet...')
            sd_init_weights, motion_module_init_weights, iclight_init_weights = f'{sd_path}/unet/diffusion_pytorch_model.safetensors', relvid_mm_path, ic_path
            # 상대경로 'models/unet'는 아래 HF 다운로드용 스테이징 디렉터리(sd_path='models'일 때만 의미).
            # 우리 런타임은 가중치를 절대경로 마운트로 제공 → 다운로드 스킵. CWD(vendor)가 읽기전용이면
            # 이 makedirs만 Errno 30으로 죽으므로, 다운로드가 실제로 필요할 때만 만들도록 가드한다.
            try:
                os.makedirs('models/unet', exist_ok=True)
            except OSError:
                pass

            if not os.path.exists(sd_init_weights):
                url = 'https://huggingface.co/stablediffusionapi/realistic-vision-v51/resolve/main/unet/diffusion_pytorch_model.safetensors'
                download_url_to_file(url=url, dst=sd_init_weights)
            if not os.path.exists(motion_module_init_weights):
                url = 'https://huggingface.co/aleafy/RelightVid/resolve/main/relvid_mm_sd15_fbc.pth'
                download_url_to_file(url=url, dst=motion_module_init_weights)
            if not os.path.exists(iclight_init_weights):
                url = 'https://huggingface.co/lllyasviel/ic-light/resolve/main/iclight_sd15_fbc.safetensors'
                download_url_to_file(url=url, dst=iclight_init_weights)

            sd = load_file(sd_init_weights) 

            if self.unet.use_motion_module:
                # load motion module
                motion_sd = torch.load(motion_module_init_weights, map_location='cpu')
                motion_sd = {k.replace("unet.", "", 1): v for k, v in motion_sd.items()}
                assert len(sd) + len(motion_sd) == len(self.unet.state_dict()), f'Improper state dict length, got {len(sd) + len(motion_sd)} expected {len(self.unet.state_dict())}' 
                sd.update(motion_sd)
                for k, v in self.unet.state_dict().items():
                    if 'pos_encoder.pe' in k: 
                        sd[k] = v 
            else:
                assert len(sd) == len(self.unet.state_dict())

            # extend unet to iclight base model
            self.unet.load_state_dict(sd) 
            unet = self.unet 
            with torch.no_grad():
                new_conv_in = InflatedConv3d(ic_channels, unet.conv_in.out_channels, unet.conv_in.kernel_size, unet.conv_in.stride, unet.conv_in.padding)
                new_conv_in.weight.zero_()
                new_conv_in.weight[:, :4, :, :].copy_(unet.conv_in.weight)
                new_conv_in.bias = unet.conv_in.bias
                unet.conv_in = new_conv_in

            sd_offset = load_file(iclight_init_weights)
            sd_origin = unet.state_dict()
            keys = sd_origin.keys()
            for k in sd_offset.keys():
                sd_origin[k] = sd_origin[k] + sd_offset[k]
            self.unet.load_state_dict(sd_origin, strict=True)
            del sd_offset, sd_origin, unet, keys

        else:
            with torch.no_grad():
                new_conv_in = InflatedConv3d(ic_channels, self.unet.conv_in.out_channels, self.unet.conv_in.kernel_size, self.unet.conv_in.stride, self.unet.conv_in.padding)
                self.unet.conv_in = new_conv_in

    def configure_optimizers(self): 
        import bitsandbytes as bnb
        motion_params = []
        for name, p in self.unet.named_parameters():
            if ('motion' in name): 
                motion_params.append(p)
            elif ('attentions' in name):
                motion_params.append(p)
        optimizer = bnb.optim.Adam8bit([
            {'params': motion_params, 'lr': self.optim_args['lr']},
        ], betas=(0.9, 0.999))
        return optimizer
