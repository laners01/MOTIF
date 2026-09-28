"""Official MimicMotion inference, all source frames, explicit local checkpoints."""
import os,sys,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from source.util import removal_experiments as ex


def main(path):
    # The notebook's inline backend is unavailable in the standalone inference environment.
    os.environ['MPLBACKEND']='Agg'
    import numpy as np
    import torch
    from omegaconf import OmegaConf
    plan=ex.read_json(path);work=ex.local(plan['work'])
    vendor=ex.local('vendor/MimicMotion');os.chdir(vendor);sys.path.insert(0,str(vendor))
    from inference import preprocess,run_pipeline
    from mimicmotion.utils.loader import create_pipeline
    from mimicmotion.dwpose.dwpose_detector import dwpose_detector
    # CPU ONNX avoids runtime-specific CUDA/cuDNN provider mismatch. Generation uses the selected GPU.
    dwpose_detector.args=(str(ex.local(vendor/'models/DWPose/yolox_l.onnx')),
                         str(ex.local(vendor/'models/DWPose/dw-ll_ucoco_384.onnx')),'cpu')
    torch.set_default_dtype(torch.float16)
    cfg=OmegaConf.create(dict(base_model_path=str(ex.local('models/mimicmotion/svd')),
                             ckpt_path=str(ex.local(vendor/'models/MimicMotion_1-1.pth'))))
    # SVD 윈도우당 프레임 수 = MimicMotion VRAM 지배 변수. 24GB(L4) OOM 시 MIMIC_TILE로 낮춘다.
    tile=min(int(os.environ.get('MIMIC_TILE','72')),plan['frames']+1)
    task=OmegaConf.create(dict(seed=plan['seed'],num_frames=tile,frames_overlap=min(6,tile-1),noise_aug_strength=0.,
                              num_inference_steps=plan['steps'],guidance_scale=2.))
    with torch.inference_mode():
        poses,appearance=preprocess(str(work/'driver.mp4'),str(work/'reference.png'),resolution=576,sample_stride=1)
        if poses.shape[0]!=plan['frames']+1:raise ValueError('자세 추출 프레임 누락')
        # Diagnostic skeleton video, excluding the prepended appearance reference frame.
        pose_rgb=((poses[1:].float().cpu().numpy()+1)*127.5).clip(0,255).astype(np.uint8)
        ex.encode_frames((im.transpose(1,2,0)[...,::-1].copy() for im in pose_rgb),work/'poses.mp4',plan['fps'])
        pipeline=create_pipeline(cfg,torch.device('cuda'))
        frames=run_pipeline(pipeline,appearance,poses,torch.device('cuda'),task)
        if len(frames)!=plan['frames']:raise ValueError('춤 생성 프레임 누락')
        ex.encode_frames((im.permute(1,2,0).cpu().numpy()[...,::-1].copy() for im in frames),work/'generated.mp4',plan['fps'])


if __name__=='__main__':main(sys.argv[1])
