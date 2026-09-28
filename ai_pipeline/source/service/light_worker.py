"""Offline deterministic RelightVid background encodings, using its existing loader."""
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'vendor/RelightVid')]
import cv2
import numpy as np
import torch
from source.util import removal_experiments as ex


def main(path):
    from omegaconf import OmegaConf
    # gcsfuse 랜덤 mmap이 SD unet(3.4G)+iclight(1.7G) safetensors 로딩을 ~16분으로 늘린다(compose RelightVid와 동일 병목).
    # safetensors load_file(mmap)을 "순차 RAM read 후 bytes에서 load"로 갈아끼운다(순차 read는 gcsfuse가 빠름).
    # 아래 import(load_utils→…→relightvid)가 이 패치본을 바인딩하도록 import보다 먼저 갈아끼운다.
    import time as _time
    import safetensors.torch as _st
    def _seq_load_file(p, device="cpu"):
        _c0=_time.perf_counter()
        with open(p,"rb") as _fh:data=_fh.read()
        print(f'[light 로딩] safetensors 순차 read {len(data)/2**30:.2f}GiB · {_time.perf_counter()-_c0:.1f}s',flush=True)
        sd=_st.load(data)
        return sd if device in (None,"cpu") else {k:v.to(device) for k,v in sd.items()}
    _st.load_file=_seq_load_file
    from utils.load_utils import init_create_model
    cfg=ex.read_json(path);vendor=ex.local('vendor/RelightVid')
    config=OmegaConf.load(vendor/'configs/inference_fbc.yaml')
    for key in ('sd_path','iclight_path','relvid_mm_path'):
        config.diffusion.params[key]=str(ex.local(vendor/config.diffusion.params[key]))
    model_config=ex.local(cfg['work'])/'relightvid_pre.yaml';OmegaConf.save(config,model_config)
    network=init_create_model(str(model_config)).cuda().half().eval()
    with torch.inference_mode():
        np.savez_compressed(ex.local(cfg['text']),
            conditional=network.encode_text(['natural lighting matching the background']).half().cpu().numpy(),
            unconditional=network.encode_text(['']).half().cpu().numpy())
        for person in cfg['people']:
            frames=ex.sequence(person['condition_frames'])
            result=np.lib.format.open_memmap(ex.local(person['latents']),mode='w+',dtype=np.float16,shape=(len(frames),4,64,64))
            for start in range(0,len(frames),16):
                images=np.stack([cv2.imread(str(p))[...,::-1] for p in frames[start:start+16]])
                tensor=torch.from_numpy(images.copy()).permute(0,3,1,2).unsqueeze(0).cuda().half()/127.5-1
                result[start:start+len(images)]=network.encode_image_to_latent(tensor)[0].half().cpu().numpy()
                print('배경 VAE:',person['id'],min(len(frames),start+16),'/',len(frames),flush=True)
            result.flush();del result


if __name__=='__main__':main(sys.argv[-1])
