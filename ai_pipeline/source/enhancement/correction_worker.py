"""CPU DWPose extraction and local pixel correction; no diffusion imports."""
import os
os.environ['MPLBACKEND']='Agg'
import sys
import time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from source.enhancement import correction as c
import cv2
import numpy as np


def extract(folder,model):
    poses=[]
    for i,p in enumerate(c.ex.sequence(folder)):
        im=cv2.imread(str(p),cv2.IMREAD_UNCHANGED)
        if im is None or im.ndim!=3 or im.shape[2]!=4:raise ValueError('RGBA 전경 프레임 필요')
        alpha=im[:,:,3:4]/255.
        bgr=(im[:,:,:3]*alpha+127*(1-alpha)).astype(np.uint8)
        # Match upstream get_video_pose's RGB (decord) input; coordinates remain in pixels.
        points,scores=model(cv2.cvtColor(bgr,cv2.COLOR_BGR2RGB))
        pose=np.zeros((18,3),np.float32)
        if len(points)==1:
            pose[:,:2]=points[0,:18];pose[:,2]=scores[0,:18]
        poses.append(pose)
        if i%30==0:print(f'자세 분석 {folder}: {i+1}',flush=True)
    return np.asarray(poses)


def main(path):
    plan=c.ex.read_json(c.ex.local(path,exists=True));work=c.ex.local(plan['work'])
    sys.path.insert(0,str(c.ex.local('vendor/MimicMotion')))
    from mimicmotion.dwpose.wholebody import Wholebody
    # DWPose는 onnxruntime이 직접 읽어 순차-read 패치 대상이 아니다(~330MB로 작음) — 로딩 경과만 계측.
    _c0=time.perf_counter()
    model=Wholebody(str(c.ex.local('vendor/MimicMotion/models/DWPose/yolox_l.onnx')),
                    str(c.ex.local('vendor/MimicMotion/models/DWPose/dw-ll_ucoco_384.onnx')),'cuda')
    print(f'[춤보정 로딩] DWPose ONNX(yolox_l+dw-ll) 세션 준비 · {time.perf_counter()-_c0:.1f}s',flush=True)
    poses=[]
    for name in ('foreground','driver'):
        p=work/(name+'_pose.npy')
        if p.exists():pose=np.load(p,allow_pickle=False)
        else:
            pose=extract(plan[name],model);np.save(p,pose)
        poses.append(pose)
    c.apply(plan,*poses)


if __name__=='__main__':main(sys.argv[1])
