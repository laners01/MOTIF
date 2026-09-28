"""Conservative local occlusion evidence from original video and visible A/B masks.

This combines visible boundary ownership with an optional shared-floor ordering
hint; it does not estimate metric depth or synthesize hidden B texture.
Unknown overlap remains in the existing composite and is reported explicitly.
"""
import cv2
import numpy as np
import time
from source.util import removal_experiments as ex


def driver_masks(plan,record):
    folder=ex.local(plan.get('driver_masks',str(ex.local(plan['driver']).parent/'masks')),exists=True)
    meta=ex.read_json(folder.parent/'metadata.json')
    selected=meta.get('automatic_person_detection',{}).get('selected_obj_id')
    if ex.local(meta['source'])!=ex.local(record['source']) or selected!=plan['driver_id']:
        raise ValueError('B 추적 마스크의 원본/인물 ID가 다릅니다. D-1부터 다시 실행하세요.')
    if plan['driver_id']==record['selected_obj_id']:raise ValueError('A와 B는 다른 인물이어야 합니다.')
    if plan.get('driver_source_sha256',record['source_sha256'])!=record['source_sha256']:
        raise ValueError('B 추적 이후 원본 영상이 변경되었습니다.')
    ex.validate_masks(folder,record)
    return folder


def evidence(a,b,previous=None,following=None,depth_front=False):
    """Local/temporal cues, optionally extended by a sustained B-front floor hint."""
    a=a>127;b=(b>127)&~a
    h,w=a.shape;radius=max(2,round(min(h,w)*.015))
    kernel=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*radius+1,2*radius+1))
    def bridge(mask,other):
        closed=cv2.morphologyEx(mask.astype(np.uint8),cv2.MORPH_CLOSE,kernel)>0
        return closed&other&~mask
    b_front=bridge(a,b);a_front=bridge(b,a)
    temporal=[]
    for neighbor in (previous,following):
        if neighbor is not None:temporal.append((neighbor>127)&b)
    if len(temporal)==2:
        supported=temporal[0]&temporal[1]
        b_front |= supported
    # Spread only locally within visible B, keeping front/back cues separated.
    influence=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*radius+1,2*radius+1))
    front=cv2.dilate(b_front.astype(np.uint8),influence)>0
    opposing=cv2.dilate(a_front.astype(np.uint8),influence)>0
    if depth_front:front |= b
    front &= b&~opposing
    return front.astype(np.uint8)*255,dict(b_front_seed_pixels=int(b_front.sum()),
                                          a_front_seed_pixels=int(a_front.sum()))


def prepare(plan,a_masks,record,placement=None):
    source=ex.local(record['source'],exists=True)
    if ex.digest(source)!=record['source_sha256']:raise ValueError('원본 영상이 변경되었습니다.')
    a_paths=ex.validate_masks(a_masks,record)
    b_folder=driver_masks(plan,record);b_paths=ex.validate_masks(b_folder,record)
    inputs=dict(version=2,source_sha256=record['source_sha256'],frames=record['frames'],
                size=record['size'],fps=record['fps'],a_id=record['selected_obj_id'],b_id=plan['driver_id'],
                a_hashes=[ex.digest(p) for p in a_paths],b_hashes=[ex.digest(p) for p in b_paths])
    depth_front=np.zeros(record['frames'],bool)
    if placement is not None and placement.get('pose_file'):
        from scipy.ndimage import median_filter
        from source.relighting.composite import union_bbox
        afile=ex.local(placement['pose_file'],exists=True)
        bfile=ex.local(plan['work'])/'driver_pose.npy'
        if bfile.is_file():
            ap=np.load(afile)['background'];bp=np.load(bfile).copy()
            x0,y0,_,_=union_bbox(b_folder,record['frames'],margin=.12)
            bp[:,:,:2]+=[x0,y0]
            inputs['pose_hashes']=[ex.digest(afile),ex.digest(bfile)]
            scores=[]
            for a,b in zip(ap,bp):
                aa=[a[j,1] for j in (10,13) if a[j,2]>.6 and 0<a[j,1]<record['size'][1]-2]
                bb=[b[j,1] for j in (10,13) if b[j,2]>.6 and 0<b[j,1]<record['size'][1]-2]
                size=max(80,float(abs(a[1,1]-np.mean(a[[8,11],1])))*2.5)
                scores.append((max(bb)-max(aa))/size if aa and bb else 0.)
            # A shared-floor depth hint is only used with a sustained, clear separation.
            filtered=median_filter(np.asarray(scores),size=max(3,round(record['fps']*.2))|1,mode='nearest')
            depth_front=filtered>.10
    parent=ex.local(plan['work'],exists=True);cache=parent/'occlusion_cache.json'
    if cache.exists():
        old=ex.read_json(cache)
        if old['inputs']==inputs:
            folder=ex.local(old['folder'],exists=True)
            paths=ex.sequence(folder/'front_masks',record['frames'])
            saved=ex.read_json(folder/'occlusion.json')
            if [ex.digest(p) for p in paths]==saved['front_hashes']:return folder
    work=ex.unique_dir(parent,'occlusion');out=work/'front_masks';out.mkdir()
    width,height=record['size'];scale=min(1,512/max(width,height))
    size=(max(1,round(width*scale)),max(1,round(height*scale)))
    cap=cv2.VideoCapture(str(source));started=time.perf_counter()
    def read(i):
        ok,frame=cap.read()
        if not ok:raise ValueError(f'원본 프레임 읽기 실패: {i}')
        gray=cv2.cvtColor(cv2.resize(frame,size),cv2.COLOR_BGR2GRAY)
        a=cv2.resize(cv2.imread(str(a_paths[i]),0),size,interpolation=cv2.INTER_NEAREST)
        b=cv2.resize(cv2.imread(str(b_paths[i]),0),size,interpolation=cv2.INTER_NEAREST)
        return gray,a,b
    def transported(current,neighbor):
        if neighbor is None:return None
        flow=cv2.calcOpticalFlowFarneback(current[0],neighbor[0],None,.5,3,21,3,5,1.2,0)
        yy,xx=np.mgrid[:size[1],:size[0]].astype(np.float32)
        warped=cv2.remap(neighbor[1],xx+flow[:,:,0],yy+flow[:,:,1],cv2.INTER_NEAREST)
        # Reject transport where the brightness match is unreliable.
        other=cv2.remap(neighbor[0],xx+flow[:,:,0],yy+flow[:,:,1],cv2.INTER_LINEAR)
        warped[np.abs(other.astype(float)-current[0])>35]=0
        return warped
    diagnostics=[];previous=None;smoothed=None
    try:
        current=read(0);following=read(1) if record['frames']>1 else None
        for i in range(record['frames']):
            mask,stats=evidence(current[1],current[2],transported(current,previous),transported(current,following),bool(depth_front[i]))
            # Current geometry gates temporal persistence so old B pixels cannot trail behind.
            value=mask.astype(np.float32)/255
            smoothed=value if smoothed is None else .75*value+.25*smoothed
            value=(smoothed>=.6).astype(np.uint8)*255
            full=cv2.resize(value,(width,height),interpolation=cv2.INTER_NEAREST)
            a=cv2.imread(str(a_paths[i]),0);b=cv2.imread(str(b_paths[i]),0)
            full[(b<=127)|(a>127)]=0
            if not cv2.imwrite(str(out/f'{i:06d}.png'),full):raise IOError('가림 마스크 저장 실패')
            stats.update(frame=i,b_front_pixels=int((full>0).sum()),shared_floor_b_front=bool(depth_front[i]))
            diagnostics.append(stats)
            previous,current=current,following
            following=read(i+2) if i+2<record['frames'] else None
    finally:cap.release()
    manifest=dict(inputs=inputs,source=str(source.relative_to(ex.ROOT)),front_masks=str(out.relative_to(ex.ROOT)),
                  frames=record['frames'],size=record['size'],fps=record['fps'],seconds=time.perf_counter()-started,
                  method='local-boundaries-and-supported-ground-order-v2',diagnostics=diagnostics,
                  active_frames=sum(d['b_front_pixels']>0 for d in diagnostics),
                  front_hashes=[ex.digest(p) for p in ex.sequence(out,record['frames'])])
    ex.write_json(work/'occlusion.json',manifest)
    ex.write_json(cache,dict(inputs=inputs,folder=str(work.relative_to(ex.ROOT))))
    print('가림 분석:',manifest['active_frames'],'/',record['frames'],'프레임 ·',round(manifest['seconds'],2),'초')
    return work


def restore(composite,original,alpha,front,background):
    """Restore B only where it occludes C; effective C alpha excludes those pixels."""
    f=front.astype(np.float32)[:,:,None]/255
    a=alpha.astype(np.float32)[:,:,None]/255
    restored=original*a+background*(1-a)
    out=(composite*(1-f)+restored*f).round().clip(0,255).astype(np.uint8)
    effective=(alpha.astype(np.float32)*(1-front.astype(np.float32)/255)).round().astype(np.uint8)
    return out,effective


class Renderer:
    def __init__(self,folder,frames,size,fps):
        self.record=ex.read_json(ex.local(folder,exists=True)/'occlusion.json')
        source=ex.local(self.record['source'],exists=True)
        if ex.digest(source)!=self.record['inputs']['source_sha256']:raise ValueError('가림 원본이 변경되었습니다.')
        if frames!=self.record['frames'] or list(size)!=self.record['size'] or abs(fps-self.record['fps'])>1e-4:
            raise ValueError('가림 마스크와 합성 영상의 프레임/해상도/FPS 불일치')
        self.paths=ex.sequence(self.record['front_masks'],frames)
        if [ex.digest(p) for p in self.paths]!=self.record['front_hashes']:raise ValueError('가림 마스크가 변경되었습니다.')
        self.cap=cv2.VideoCapture(str(source));self.hidden_pixels=0;self.index=0

    def apply(self,composite,alpha,index,background):
        if index!=self.index:raise ValueError('가림 프레임 순서 불일치')
        ok,original=self.cap.read();self.index+=1
        if not ok:raise ValueError('가림 원본 디코딩 실패')
        front=cv2.imread(str(self.paths[index]),0)
        self.hidden_pixels+=int(((front>0)&(alpha>0)).sum())
        return restore(composite,original,alpha,front,background)

    def close(self):self.cap.release()
