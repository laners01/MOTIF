"""Extract A/C anatomical anchors on CPU in the existing DWPose environment."""
import os,sys
os.environ['MPLBACKEND']='Agg'
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
import cv2,numpy as np
from source.util import removal_experiments as ex


def detect(image,alpha,model):
    h,w=alpha.shape;ys,xs=np.where(alpha>127)
    pose=np.zeros((18,3),np.float32)
    if len(xs)<64:return pose
    margin=max(12,round((ys.max()-ys.min())*.12))
    x0=max(0,int(xs.min())-margin);x1=min(w,int(xs.max())+margin+1)
    y0=max(0,int(ys.min())-margin);y1=min(h,int(ys.max())+margin+1)
    a=alpha[y0:y1,x0:x1,None]/255.
    crop=(image[y0:y1,x0:x1]*a+127*(1-a)).astype(np.uint8)
    points,scores=model(cv2.cvtColor(crop,cv2.COLOR_BGR2RGB))
    if len(points)!=1:return pose
    pose[:,:2]=points[0,:18]+[x0,y0];pose[:,2]=scores[0,:18]
    return pose


def main(path):
    cfg=ex.read_json(ex.local(path,exists=True));work=ex.local(cfg['work'])
    fg=ex.sequence(cfg['foreground']);masks=ex.sequence(cfg['a_masks'],len(fg))
    # OpenCV/FFmpeg versions disagree on the tail of some MP4 streams. Use the
    # parent runtime's checked frames, with exactly the mask/foreground indexing.
    cached=cfg.get('prepared_background_pose')
    background=None if cached else ex.sequence(cfg['background_frames'],len(fg))
    sys.path.insert(0,str(ex.local('vendor/MimicMotion')))
    from mimicmotion.dwpose.wholebody import Wholebody
    model=Wholebody(str(ex.local('vendor/MimicMotion/models/DWPose/yolox_l.onnx')),
                    str(ex.local('vendor/MimicMotion/models/DWPose/dw-ll_ucoco_384.onnx')),'cuda')
    bgposes=np.load(ex.local(cached),allow_pickle=False) if cached else []
    if cached and bgposes.shape!=(len(fg),18,3):raise ValueError('준비된 배경 관절 프레임 불일치')
    fgposes=[]
    for i,(fp,mp) in enumerate(zip(fg,masks)):
        im=cv2.imread(str(fp),-1)
        if im is None:raise ValueError(f'배치 입력 이미지 읽기 실패: 프레임 {i}')
        if not cached:
            bg=cv2.imread(str(background[i]));mask=cv2.imread(str(mp),0)
            if bg is None or mask is None or bg.shape[:2]!=mask.shape:raise ValueError('배경/마스크 읽기 실패')
            bgposes.append(detect(bg,mask,model))
        fgposes.append(detect(im[:,:,:3],im[:,:,3],model))
        if i%30==0:print('배치 관절 분석',i+1,'/',len(fg),flush=True)
    np.savez_compressed(work/'poses.npz',background=np.asarray(bgposes),foreground=np.asarray(fgposes))
    print('배치 관절 저장 완료',flush=True)


if __name__=='__main__':main(sys.argv[1])
