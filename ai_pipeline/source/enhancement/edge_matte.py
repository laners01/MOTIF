"""Refine source RGBA edges before time warping; retain the raw masks for geometry."""
import hashlib,json
from functools import lru_cache
import cv2,numpy as np
from scipy.ndimage import distance_transform_edt
from source.util import removal_experiments as ex


def _gap_regions(frame):
    """Only consider short connections between an existing small part and the body."""
    mask=(frame[:,:,3]>127).astype(np.uint8)
    n,labels,stats,_=cv2.connectedComponentsWithStats(mask)
    if n<3:return []
    main=1+np.argmax(stats[1:,cv2.CC_STAT_AREA]);body=labels==main
    distance=distance_transform_edt(~body);limit=max(4,min(mask.shape)*.08)
    regions=[]
    for label in range(1,n):
        area=stats[label,cv2.CC_STAT_AREA]
        if label==main or not 8<=area<=stats[main,cv2.CC_STAT_AREA]*.025:continue
        part=labels==label;gap=distance[part].min()
        if gap>limit:continue
        width=min(limit/4,max(2,np.sqrt(area)*.5))
        corridor=(distance+distance_transform_edt(~part)<=gap+width)&(mask==0)
        regions.append((part,corridor))
    return regions


def _warp_support(frame,neighbor):
    """Warp an ORIGINAL neighbor mask; reject color changes and inconsistent motion."""
    if neighbor.shape!=frame.shape:raise ValueError('전경 크롭 크기 변경')
    h,w=frame.shape[:2];ratio=min(1.,640/max(h,w));size=(round(w*ratio),round(h*ratio))
    gray=cv2.resize(cv2.cvtColor(frame[:,:,:3],cv2.COLOR_BGR2GRAY),size)
    old=cv2.resize(cv2.cvtColor(neighbor[:,:,:3],cv2.COLOR_BGR2GRAY),size)
    args=(None,.5,4,21,5,7,1.5,0)
    back=cv2.calcOpticalFlowFarneback(gray,old,*args)
    forward=cv2.calcOpticalFlowFarneback(old,gray,*args)
    sy,sx=gray.shape;yy,xx=np.mgrid[:sy,:sx].astype(np.float32)
    xy=np.dstack([xx,yy])+back
    reverse=cv2.remap(forward,xy[:,:,0],xy[:,:,1],cv2.INTER_LINEAR)
    cycle=cv2.resize(np.linalg.norm(back+reverse,axis=2),(w,h))/ratio
    flow=cv2.resize(back,(w,h))*np.array([w/sx,h/sy],np.float32)
    y,x=np.mgrid[:h,:w].astype(np.float32);mx=x+flow[:,:,0];my=y+flow[:,:,1]
    alpha=cv2.remap(neighbor[:,:,3],mx,my,cv2.INTER_LINEAR).astype(np.float32)/255
    color=cv2.remap(neighbor[:,:,:3],mx,my,cv2.INTER_LINEAR)
    photo=np.mean(abs(color.astype(np.float32)-frame[:,:,:3]),axis=2)
    valid=(cycle<2.5)&(photo<28)&(mx>=0)&(mx<w-1)&(my>=0)&(my<h-1)
    return alpha*valid


def _repair_gaps(frame,regions,before,after):
    """Recover a connection only with agreement on BOTH sides, never copy old RGB."""
    if not regions or not before or not after:return frame,0
    support=np.minimum(np.maximum.reduce([_warp_support(frame,p) for p in before]),
                       np.maximum.reduce([_warp_support(frame,p) for p in after]))
    mask=frame[:,:,3]>127;out=frame.copy();added=np.zeros(mask.shape,bool)
    for part,corridor in regions:
        candidate=corridor&(support>.65)
        if not candidate.any():continue
        n,labels,stats,_=cv2.connectedComponentsWithStats((mask|candidate).astype(np.uint8))
        main=1+np.argmax(stats[1:,cv2.CC_STAT_AREA]);connected=labels==main
        if not connected[part].all():continue
        added|=candidate&connected
    out[:,:,3][added]=np.rint(support[added]*255).astype(np.uint8)
    return out,int(added.sum())


def _thin_support(mask):
    """Protect original bridges and attached strands, not every small contour bump."""
    opened=cv2.morphologyEx(mask,cv2.MORPH_OPEN,np.ones((5,5),np.uint8))
    _,thick=cv2.connectedComponents(opened)
    n,labels,stats,_=cv2.connectedComponentsWithStats(mask-opened)
    keep=np.zeros(mask.shape,bool)
    for label in range(1,n):
        _,_,w,h,area=stats[label]
        if area<2:continue
        part=labels==label
        touch=np.unique(thick[cv2.dilate(part.astype(np.uint8),np.ones((3,3),np.uint8))>0])
        contacts=np.count_nonzero(touch)
        bridge=contacts>=2
        strand=False
        if contacts and area>=6 and max(w,h)>=6:
            y,x=np.nonzero(part);variance=np.linalg.eigvalsh(np.cov(np.array([x,y])))
            strand=variance[1]>=4*max(variance[0],.25)
        if bridge or strand:keep|=part
    return keep


def refine(frame,previous=None):
    """Mask-bounded alpha and motion-validated history in a one-pixel edge band."""
    if frame.ndim!=3 or frame.shape[2]!=4:raise ValueError('원본 RGB가 포함된 RGBA 프레임 필요')
    raw=frame[:,:,3].astype(np.float32)/255;mask=(raw>.5).astype(np.uint8)
    base=cv2.GaussianBlur(raw,(3,3),.7)
    core=cv2.erode(mask,np.ones((3,3),np.uint8))>0
    thin=_thin_support(mask);floor=np.where(thin,.85*raw,0)
    # Outside pixels only receive the adjacent mask's antialiasing footprint.
    # Their RGB cannot grow alpha or create detached foreground-colored specks.
    ceiling=np.where(mask>0,1.,np.minimum(base,.35))
    alpha=np.minimum(np.maximum(base,floor),ceiling).astype(np.float32)
    alpha[core]=raw[core]
    out=frame.copy();seeds=core|thin
    if seeds.any():
        _,nearest=distance_transform_edt(~seeds,return_indices=True)
        edge=(alpha>0)&~core&~thin
        out[:,:,:3][edge]=frame[:,:,:3][tuple(nearest)][edge]
    gray=cv2.cvtColor(frame[:,:,:3],cv2.COLOR_BGR2GRAY)
    h,w=gray.shape;ratio=min(1.,384/max(h,w))
    small=cv2.resize(gray,(max(1,round(w*ratio)),max(1,round(h*ratio))),interpolation=cv2.INTER_AREA)
    supported=0
    if previous is not None:
        old_gray,old_alpha=previous
        if old_gray.shape!=small.shape or old_alpha.shape!=alpha.shape:raise ValueError('전경 크롭 크기 변경')
        args=(None,.5,3,21,3,5,1.2,0)
        back=cv2.calcOpticalFlowFarneback(small,old_gray,*args)
        forward=cv2.calcOpticalFlowFarneback(old_gray,small,*args)
        sy,sx=small.shape
        yy,xx=np.mgrid[:sy,:sx].astype(np.float32)
        xy=np.dstack([xx,yy])+back
        reverse=cv2.remap(forward,xy[:,:,0],xy[:,:,1],cv2.INTER_LINEAR)
        photo=abs(small.astype(float)-cv2.remap(old_gray,xy[:,:,0],xy[:,:,1],cv2.INTER_LINEAR).astype(float))
        confidence=np.clip(1-np.linalg.norm(back+reverse,axis=2)/1.5,0,1)*np.clip(1-photo/20,0,1)
        confidence*=(xy[:,:,0]>=0)&(xy[:,:,0]<=sx-1)&(xy[:,:,1]>=0)&(xy[:,:,1]<=sy-1)
        flow=cv2.resize(back,(w,h))*np.array([w/sx,h/sy],np.float32)
        y,x=np.mgrid[:h,:w].astype(np.float32)
        history=cv2.remap(old_alpha,x+flow[:,:,0],y+flow[:,:,1],cv2.INTER_LINEAR)
        # Current-mask bounds below prevent trails even when past alpha differs.
        edge=(alpha>0)&~core&~thin
        weight=.3*cv2.resize(confidence.astype(np.float32),(w,h))*edge
        alpha+=weight*(history-alpha)
        supported=int((weight>.05).sum())
    alpha=np.minimum(np.maximum(alpha,floor),ceiling).astype(np.float32)
    alpha[core]=raw[core]
    out[:,:,3]=(alpha*255).round().clip(0,255).astype(np.uint8)
    return out,(small,alpha),supported


def prepare(foreground,*,frame_times,progress=None):
    """Cache rendered mattes separately; never modify original SAM masks or crop bounds."""
    foreground=ex.local(foreground,exists=True);paths=ex.sequence(foreground)
    times=np.asarray(frame_times,dtype=float)
    if times.shape!=(len(paths),) or not np.isfinite(times).all() or np.any(np.diff(times)<=0):
        raise ValueError('경계 보완 입력의 프레임 시각 불일치')
    inputs=dict(version=3,source=str(foreground.relative_to(ex.ROOT)),
                hashes=[ex.digest(p) for p in paths],times=times.tolist(),
                implementation=ex.digest(__file__))
    key=hashlib.sha256(json.dumps(inputs,sort_keys=True).encode()).hexdigest()
    pointer=foreground/'edge_matte_cache.json'
    if pointer.is_file():
        saved=ex.read_json(pointer)
        if saved['signature']==key:
            output=ex.local(saved['output']);files=sorted(output.glob('*.png'))
            if len(files)==len(paths) and saved['hashes']==[ex.digest(p) for p in files]:
                if progress:progress(1.,'경계 보완 캐시 재사용')
                return output
    @lru_cache(maxsize=8)
    def read(index):
        raw=cv2.imread(str(paths[index]),cv2.IMREAD_UNCHANGED)
        if raw is None or raw.ndim!=3 or raw.shape[2]!=4:raise ValueError('원본 RGBA 읽기 실패: '+str(paths[index]))
        return raw
    output=ex.unique_dir(foreground.parent,'edge_matte');previous=None;count=0
    repaired_frames=0;repaired_pixels=0;unresolved_frames=0
    for i in range(len(paths)):
        raw=read(i);regions=_gap_regions(raw)
        if regions:
            # Up to three raw frames on each side, within 0.12 s, no timestamp gaps.
            def neighbors(step):
                result=[]
                for offset in range(1,4):
                    j=i+step*offset;last=j-step
                    if not 0<=j<len(paths) or abs(times[j]-times[i])>.12 or abs(times[j]-times[last])>.06:break
                    result.append(read(j))
                return result
            raw,repaired=_repair_gaps(raw,regions,neighbors(-1),neighbors(1))
            repaired_frames+=int(repaired>0);repaired_pixels+=repaired
            unresolved_frames+=int(bool(_gap_regions(raw)))
        # No history across a large timestamp gap; both alpha and RGB use this frame.
        if i and times[i]-times[i-1]>.15:previous=None
        result,previous,supported=refine(raw,previous);count+=supported
        if not cv2.imwrite(str(output/f'{i:06d}.png'),result):raise IOError('경계 보완 RGBA 저장 실패')
        if progress:progress((i+1)/len(paths),f'원본 경계 알파 보완 {i+1}/{len(paths)}')
    ex.write_json(output/'edge_matte.json',dict(inputs=inputs,temporal_edge_pixels=count,
        method='mask-bounded-1px-alpha-bridge-strand-protection',interior_rgb='unchanged',
        gap_repair=dict(frames=repaired_frames,pixels=repaired_pixels,unresolved_frames=unresolved_frames),
        raw_foreground=str(foreground.relative_to(ex.ROOT))))
    ex.write_json(pointer,dict(signature=key,output=str(output.relative_to(ex.ROOT)),
        hashes=[ex.digest(p) for p in ex.sequence(output,len(paths))]))
    return output
