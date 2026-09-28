"""CPU camera motion from scene features outside the tracked people."""
import hashlib,json,time
import cv2,numpy as np
from source.util import removal_experiments as ex
from source.relighting import model_test as rel


def track(source,mask_folders,*,region=None):
    source=ex.local(source,exists=True);info,_=rel.video_info(source)
    masks=[ex.sequence(folder,info['frames']) for folder in mask_folders]
    inputs=dict(source=ex.digest(source),masks=[[ex.digest(p) for p in paths] for paths in masks],
        implementation=ex.digest(__file__),region=region)
    key=hashlib.sha256(json.dumps(inputs,sort_keys=True).encode()).hexdigest()
    work=ex.local('work/scene_motion')/key;work.mkdir(parents=True,exist_ok=True)
    output=work/'camera.npz';receipt=work/'camera.json'
    if output.is_file() and receipt.is_file() and ex.digest(output)==ex.read_json(receipt)['sha256']:
        with np.load(output,allow_pickle=False) as data:return {k:data[k].copy() for k in data.files},output
    started=time.perf_counter();width,height=info['size'];ratio=min(1.,480/max(width,height))
    size=(round(width*ratio),round(height*ratio));resize=np.diag([width/size[0],height/size[1],1.])
    matrices=[np.eye(3)];valid=[True];previous=None;previous_mask=None
    cap=cv2.VideoCapture(str(source))
    try:
        for i in range(info['frames']):
            ok,image=cap.read()
            if not ok:raise ValueError(f'카메라 이동 분석 원본 프레임 누락: {i}')
            gray=cv2.cvtColor(cv2.resize(image,size),cv2.COLOR_BGR2GRAY)
            person=np.zeros(size[::-1],np.uint8)
            for paths in masks:
                person|=cv2.resize(cv2.imread(str(paths[i]),0),size,interpolation=cv2.INTER_NEAREST)
            visible=255-cv2.dilate(person,np.ones((15,15),np.uint8))
            if region is not None:
                x0,y0,x1,y1=region
                if not (0<=x0<x1<=width and 0<=y0<y1<=height):raise ValueError('카메라 분석 영역이 영상 밖입니다.')
                roi=np.zeros_like(visible)
                roi[round(y0*size[1]/height):round(y1*size[1]/height),
                    round(x0*size[0]/width):round(x1*size[0]/width)]=255
                visible&=roi
            if previous is not None:
                affine=None;points=cv2.goodFeaturesToTrack(previous,300,.01,7,mask=previous_mask)
                if points is not None and len(points)>=12:
                    following,status,_=cv2.calcOpticalFlowPyrLK(previous,gray,points,None,winSize=(21,21),maxLevel=3)
                    backward,back_status,_=cv2.calcOpticalFlowPyrLK(gray,previous,following,None,winSize=(21,21),maxLevel=3)
                    good=(status.ravel()>0)&(back_status.ravel()>0)&(np.linalg.norm(backward-points,axis=2).ravel()<1.)
                    xy=np.rint(following[:,0]).astype(int)
                    inside=(xy[:,0]>=0)&(xy[:,0]<size[0])&(xy[:,1]>=0)&(xy[:,1]<size[1])
                    selected=np.flatnonzero(inside);good[~inside]=False
                    good[selected]&=visible[xy[selected,1],xy[selected,0]]>200
                    if good.sum()>=12:
                        affine,inliers=cv2.estimateAffinePartial2D(points[good],following[good],method=cv2.RANSAC,
                            ransacReprojThreshold=1.,maxIters=1000,confidence=.99)
                        if affine is not None:
                            zoom=np.linalg.norm(affine[0,:2]);rotation=abs(np.arctan2(affine[1,0],affine[0,0]))
                            if inliers.sum()<10 or inliers.mean()<.6 or not .94<zoom<1.06 or rotation>.04:affine=None
                valid.append(affine is not None)
                step=np.eye(3)
                if affine is not None:step[:2]=affine
                matrices.append(resize@step@np.linalg.inv(resize)@matrices[-1])
            previous=gray;previous_mask=visible
    finally:cap.release()
    result=dict(matrix=np.array(matrices),valid=np.array(valid))
    np.savez_compressed(output,**result)
    ex.write_json(receipt,dict(inputs=inputs,sha256=ex.digest(output),seconds=time.perf_counter()-started,
        valid_frames=int(np.sum(valid)),frames=info['frames']))
    return result,output
