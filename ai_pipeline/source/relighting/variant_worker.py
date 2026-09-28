"""GPU entry point. Uses official model APIs, bypassing demo FPS/frame limits."""
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def local(value):
    # 가중치는 /mnt/model-weights로의 심링크(vendor/RelightVid/models 등)라 resolve하면 ROOT 밖으로 나간다.
    # lexical 정규화로 containment만 검사('..'·절대경로는 여전히 차단), 심링크는 따라가지 않는다.
    path = Path(os.path.normpath(ROOT/value))
    if not path.is_relative_to(ROOT):
        raise ValueError('프로젝트 밖 경로')
    return path


def main(plan_path):
    import cv2
    import numpy as np
    import torch
    from PIL import Image
    from source.relighting.variants import SPECS, windows, require_backend
    plan = json.loads(local(plan_path).read_text())
    model = plan['model']; spec = SPECS[model]
    require_backend(model)
    work = local(plan['work'])
    vendor = local(spec['vendor'])
    os.chdir(vendor)
    sys.path.insert(0,str(vendor))
    torch.manual_seed(plan['seed'])
    output = work/'reference';output.mkdir(exist_ok=True)
    # The model is loaded once per video; only bounded temporal windows enter it.
    if model == 'RelightVid':
        from omegaconf import OmegaConf
        # gcsfuse 랜덤 mmap이 SD unet(3.4G)+iclight(1.7G) safetensors 로딩을 ~16분으로 늘린다(SVOR와 동일 병목).
        # safetensors load_file(mmap)을 "순차 RAM read 후 bytes에서 load"로 갈아끼운다(순차 read는 gcsfuse가 빠름, FS 미사용).
        # 아래 import(load_utils→…→relightvid)가 `from safetensors.torch import load_file`로 이 패치본을 바인딩하도록
        # import보다 먼저 갈아끼운다. vae 등 from_pretrained 내부 load_file도 런타임에 패치본을 잡는다.
        import time as _time
        import safetensors.torch as _st
        def _seq_load_file(path, device="cpu"):
            _c0 = _time.perf_counter()
            with open(path, "rb") as _fh:
                data = _fh.read()
            print(f'[RelightVid 로딩] safetensors 순차 read {len(data)/2**30:.2f}GiB · {_time.perf_counter()-_c0:.1f}s',flush=True)
            sd = _st.load(data)
            return sd if device in (None, "cpu") else {k: v.to(device) for k, v in sd.items()}
        _st.load_file = _seq_load_file
        from utils.load_utils import init_create_model
        from pipelines.inference import RelightVidInference
        cfg = OmegaConf.load(vendor/'configs/inference_fbc.yaml')
        for key in ('sd_path','iclight_path','relvid_mm_path'):
            cfg.diffusion.params[key] = str(local(vendor/cfg.diffusion.params[key]))
        OmegaConf.save(cfg,work/'relightvid.yaml')
        network = init_create_model(str(work/'relightvid.yaml')).cuda().half().eval()
        pipe = RelightVidInference(network.unet,scheduler='ddpm',num_ddim_steps=plan['steps'])
        prepared=plan.get('prepared_background')
        bg_cache=text_cache=None
        if prepared:
            bg_cache=np.load(local(prepared['latents']),mmap_mode='r',allow_pickle=False)
            with np.load(local(prepared['text']),allow_pickle=False) as data:
                text_cache={key:torch.from_numpy(data[key].copy()).cuda().half() for key in ('conditional','unconditional')}
            if bg_cache.shape[1:]!=(4,64,64) or prepared['stop']>len(bg_cache):
                raise ValueError('준비된 RelightVid 배경 latent 크기 불일치')
        def predict(images,backgrounds,masks):
            fg = images*masks[...,None]+128*(1-masks[...,None])
            tensor = lambda x: torch.from_numpy(x).permute(0,3,1,2).unsqueeze(0).cuda().half()/127.5-1
            fg_latent = network.encode_image_to_latent(tensor(fg))
            bg_latent = (network.encode_image_to_latent(tensor(backgrounds)) if bg_cache is None else
                         torch.from_numpy(np.array(bg_cache[np.asarray(indices)+prepared['first']])).unsqueeze(0).cuda().half())
            latent = pipe(latent=torch.randn_like(fg_latent),
                          text_cond=network.encode_text(['natural lighting matching the background']).half() if text_cache is None else text_cache['conditional'],
                          text_uncond=network.encode_text(['']).half() if text_cache is None else text_cache['unconditional'],
                          img_cond=torch.cat((fg_latent,bg_latent),dim=2),text_cfg=7.5,img_cfg=1.2)['latent']
            decoded = network.decode_latent_to_image(latent)[0]
            return ((decoded.permute(0,2,3,1).float().cpu().numpy()+1)*127.5).clip(0,255).astype(np.uint8)
    elif model == 'LiveLight':
        from omegaconf import OmegaConf
        from src.livelight_wrapper_perframe_ref import RelightPerFrameRefLive
        cfg = OmegaConf.load(vendor/'configs/prompts/relight_perframe_ref.yaml')
        weights = vendor/'pretrained_weights/LiveLight'
        for field,name in [('reference_unet_weight_path','reference_unet'),('denoising_unet_path','denoising_unet'),
                           ('light_guider_path','light_guider'),('temporal_module_path','temporal_module')]:
            cfg[field] = str(local(next(weights.rglob(name+'*.pth'))))
        cfg.vae_model_path = str(local(vendor/'pretrained_weights/sd-vae-ft-mse'))
        cfg.pretrained_base_model_path = str(local(vendor/'pretrained_weights/sd-image-variations-diffusers'))
        cfg.image_encoder_path = str(local(Path(cfg.pretrained_base_model_path)/'image_encoder'))
        cfg.inference_config = str(local(vendor/cfg.inference_config))
        cfg.ppd_root = str(local(vendor/'pixel-perfect-depth'))
        local(Path(sys.executable))
        cfg.ppd_python = sys.executable
        cfg.cache_dir = str(work/'depth_cache')
        cfg.num_inference_steps = 4;cfg.seed = plan['seed']
        OmegaConf.save(cfg,work/'livelight.yaml')
        runner = RelightPerFrameRefLive(cfg)
        runner.ppd_python = Path(sys.executable)  # Official wrapper resolves away venv symlinks.
        def predict(images,backgrounds,masks):
            # Rewarm each overlapping window; avoid retaining decoded full-video frames.
            runner.reset()
            predictions,_ = runner.render_sequence([Image.fromarray(im) for im in images],overrides=plan['light'])
            return np.stack([np.asarray(im.convert('RGB')) for im in predictions])
    else:
        from transformers import T5EncoderModel
        from diffusers import DDIMScheduler
        from core.models.crosstransformer3d import CrossTransformer3DModel
        from core.models.autoencoder_magvit import AutoencoderKLCogVideoX
        from core.pipeline.pipeline_cogvideox_fun_inpaint import CogVideoXFunInpaintPipeline
        base = str(local('models/lightxuni/CogVideoX-Fun-V1.1-5b-InP'))
        dtype = torch.bfloat16
        pipe = CogVideoXFunInpaintPipeline.from_pretrained(
            base,transformer=CrossTransformer3DModel.from_pretrained(str(local('models/lightxuni/Light-X-Uni'))).to(dtype),
            vae=AutoencoderKLCogVideoX.from_pretrained(base,subfolder='vae').to(dtype),
            text_encoder=T5EncoderModel.from_pretrained(base,subfolder='text_encoder',torch_dtype=dtype),
            scheduler=DDIMScheduler.from_pretrained(base,subfolder='scheduler'),torch_dtype=dtype)
        pipe.enable_model_cpu_offload()
        def predict(images,backgrounds,masks):
            video = torch.from_numpy(images).permute(3,0,1,2).unsqueeze(0).cuda().float()/255
            # Official 'ref' conditioning: background image supplies illumination;
            # no camera warping, IC-Light generation, captioner or depth model needed.
            light = torch.from_numpy(backgrounds[len(backgrounds)//2]).permute(2,0,1).unsqueeze(0).unsqueeze(2).cuda().float()/255
            zero = torch.zeros_like(video[:,:1])
            result = pipe('A person, natural lighting, clear details, consistent motion.',
                          negative_prompt='distortion, flicker, blurry face, watermark',
                          num_frames=len(images),height=spec['size'][1],width=spec['size'][0],
                          generator=torch.Generator(device='cuda').manual_seed(plan['seed']),
                          guidance_scale=6.,num_inference_steps=plan['steps'],video=video,
                          mask_video=zero,reference=video[:,:,19:29],
                          light_video=light.repeat(1,1,len(images),1,1),relit_img=light,
                          relit_mask=torch.ones_like(zero)*255*.25).videos
            return (result[0].permute(1,2,3,0).float().cpu().numpy()*255).clip(0,255).astype(np.uint8)

    def read(folder,indices,gray=False):
        values = []
        for i in indices:
            path = local(work/folder/f'{i:06d}.png')
            image = cv2.imread(str(path),0 if gray else 1)
            if image is None: raise ValueError('입력 프레임 누락: '+str(path))
            values.append(image if gray else image[...,::-1])
        return np.stack(values)

    previous_stop = 0
    with torch.inference_mode():
        for start,stop in windows(plan['frames'],spec['chunk'],spec['overlap']):
            indices = list(range(start,stop))
            indices += [stop-1]*(spec['chunk']-len(indices))
            backgrounds=None if model=='RelightVid' and plan.get('prepared_background') else read('background',indices)
            predicted = predict(read('input',indices),backgrounds,read('alpha',indices,True)/255.)
            if predicted.shape != (len(indices),spec['size'][1],spec['size'][0],3):
                raise ValueError(f'모델 출력 크기/프레임 불일치: {predicted.shape}')
            overlap = max(0,previous_stop-start)
            for j,i in enumerate(range(start,stop)):
                path = local(output/f'{i:06d}.png')
                image = predicted[j][...,::-1]
                if j < overlap:
                    old = cv2.imread(str(path))
                    if old is None: raise ValueError('청크 겹침 프레임 누락')
                    weight = (j+1)/(overlap+1)
                    image = ((1-weight)*old+weight*image).round().astype(np.uint8)
                if not cv2.imwrite(str(path),image): raise OSError('모델 출력 저장 실패')
            previous_stop = stop
            print(f'{model}: {stop}/{plan["frames"]} frames',flush=True)


if __name__ == '__main__':
    main(sys.argv[1])
