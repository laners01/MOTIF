import torch
import numpy as np
from typing import Optional, Union, Tuple, List, Callable, Dict
from tqdm import tqdm
import torch
from diffusers import DDPMScheduler, DDIMScheduler, PNDMScheduler
import torch.nn.functional as nnf
import numpy as np
from einops import rearrange
from functools import partial

def rescale_noise_cfg(noise_cfg, noise_pred_text, guidance_rescale=0.0):
    """
    Rescale `noise_cfg` according to `guidance_rescale`. Based on findings of [Common Diffusion Noise Schedules and
    Sample Steps are Flawed](https://arxiv.org/pdf/2305.08891.pdf). See Section 3.4
    """
    std_text = noise_pred_text.std(dim=list(range(1, noise_pred_text.ndim)), keepdim=True)
    std_cfg = noise_cfg.std(dim=list(range(1, noise_cfg.ndim)), keepdim=True)
    # rescale the results from guidance (fixes overexposure)
    noise_pred_rescaled = noise_cfg * (std_text / std_cfg)
    # mix with the original results from guidance by factor guidance_rescale to avoid "plain looking" images
    noise_cfg = guidance_rescale * noise_pred_rescaled + (1 - guidance_rescale) * noise_cfg
    return noise_cfg
        
class Inference():
    def __init__(
        self, 
        unet,
        scheduler='ddim',
        beta_start=0.00085, beta_end=0.012, beta_schedule="scaled_linear",
        num_ddim_steps=20, guidance_scale=5,
    ):
        self.unet = unet
        if scheduler == 'ddim':
            scheduler_cls = DDIMScheduler
            scheduler_kwargs = {'set_alpha_to_one': False, 'steps_offset': 1, 'clip_sample': False}
        elif scheduler == 'ddpm':
            scheduler_cls = DDPMScheduler
            scheduler_kwargs = {'clip_sample': False}
        else:
            raise NotImplementedError()
        self.scheduler = scheduler_cls(
            beta_start = beta_start,
            beta_end = beta_end,
            beta_schedule = beta_schedule,
            **scheduler_kwargs
        )
        self.scheduler.set_timesteps(num_ddim_steps)
        self.num_ddim_steps = num_ddim_steps
        self.guidance_scale = guidance_scale

    @torch.no_grad()
    def __call__(
        self,
        latent: torch.Tensor,
        context: torch.Tensor,
        uncond_context: torch.Tensor=None,
        start_time: int = 0,
        null_embedding: List[torch.Tensor]=None,
        context_kwargs={},
        model_kwargs={},
    ):
        all_latent = []
        all_pred = [] # x0_hat
        do_classifier_free_guidance = self.guidance_scale > 1 and ((uncond_context is not None) or (null_embedding is not None))
        for i, t in enumerate(tqdm(self.scheduler.timesteps[start_time:])):
            t = int(t)
            if do_classifier_free_guidance:
                latent_input = torch.cat([latent, latent], dim=0)
                if null_embedding is not None:
                    context_input = torch.cat([null_embedding[i], context], dim=0)
                else:
                    context_input = torch.cat([uncond_context, context], dim=0)
            else:
                latent_input = latent
                context_input = context
            noise_pred = self.unet(
                latent_input,
                torch.full((len(latent_input),), t, device=latent_input.device, dtype=torch.long), 
                context={ 'text': context_input, **context_kwargs},
                **model_kwargs
            )

            if do_classifier_free_guidance:
                noise_pred_uncond, noise_pred_cond = noise_pred.chunk(2, dim=0)
                noise_pred = noise_pred_uncond + self.guidance_scale * (noise_pred_cond - noise_pred_uncond)

            pred_samples = self.scheduler.step(noise_pred, t, latent)
            latent = pred_samples.prev_sample
            pred = pred_samples.pred_original_sample
            all_latent.append(latent.detach())
            all_pred.append(pred.detach())

        return {
            'latent': latent,
            'all_latent': all_latent,
            'all_pred': all_pred
        }


class RelightVidInference(Inference):
    def zeros(self, x):
        return torch.zeros_like(x)
    @torch.no_grad()
    def __call__(
        self,
        latent: torch.Tensor,
        text_cond: torch.Tensor,
        text_uncond: torch.Tensor,
        img_cond: torch.Tensor,
        text_cfg = 7.5,
        img_cfg = 1.2,
        start_time: int = 0,
        guidance_rescale: float = 0.0,
    ):
        '''
                latent1 | latent2 | latent3
        text       x         x         v
        img        x         v         v
        '''

        for i, t in enumerate(tqdm(self.scheduler.timesteps[start_time:])):
            t = int(t)
            latent1 = torch.cat([latent, self.zeros(img_cond)], dim=2)
            latent2 = torch.cat([latent, img_cond], dim=2)
            latent3 = latent2.clone()
            latent_input = torch.cat([latent1, latent2, latent3], dim=0)
            context_input = torch.cat([text_uncond, text_uncond, text_cond], dim=0)

            latent_input = rearrange(latent_input, 'b f c h w -> b c f h w')
            noise_pred = self.unet(
                latent_input,
                torch.full((len(latent_input),), t, device=latent_input.device, dtype=torch.long), 
                encoder_hidden_states=context_input,
            ).sample
            noise_pred = rearrange(noise_pred, 'b c f h w -> b f c h w')


            noise_pred1, noise_pred2, noise_pred3 = noise_pred.chunk(3, dim=0)
            noise_pred = (
                noise_pred1 + 
                img_cfg * (noise_pred2 - noise_pred1) +
                text_cfg * (noise_pred3 - noise_pred2)
            )

            if guidance_rescale > 0:
                noise_pred = rescale_noise_cfg(noise_pred, noise_pred1, guidance_rescale=guidance_rescale)

            pred_samples = self.scheduler.step(noise_pred, t, latent)
            latent = pred_samples.prev_sample
            pred = pred_samples.pred_original_sample

            del noise_pred, noise_pred1, noise_pred2, noise_pred3, pred_samples
            del latent_input, context_input
            torch.cuda.empty_cache()


        return {
            'latent': latent,
        }

