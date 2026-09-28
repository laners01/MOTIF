"""Automatic time-varying placement from removed-person masks and foreground alpha."""
import cv2
import importlib
import numpy as np
from scipy.ndimage import median_filter, gaussian_filter1d
import time
from source.util import removal_experiments as ex
from source.relighting import model_test as rel


def silhouette(mask, *, include_width=False):
    """Estimate ground anchor and standing extent, ignoring disconnected mask noise."""
    binary=(mask>127).astype(np.uint8)
    count,labels,stats,_=cv2.connectedComponentsWithStats(binary,8)
    if count<=1:return None
    k=1+np.argmax(stats[1:,cv2.CC_STAT_AREA])
    x,y,w,h,area=stats[k]
    if area<64 or h<12:return None
    H,W=mask.shape
    # A clipped head/foot cannot provide a reliable height measurement.
    if y==0 or y+h>=H:return None
    ys,xs=np.where(labels==k)
    bottom=float(np.quantile(ys,.995))
    foot_band=ys>=bottom-max(2,h*.035)
    foot_x=float(np.median(xs[foot_band]))
    # Central upper silhouette reduces the influence of an arm raised to one side.
    center=float(np.median(xs[(ys>=y+h*.3)&(ys<=y+h*.7)]))
    central=abs(xs-center)<=max(2,w*.18)
    top=float(np.quantile(ys[central],.01)) if central.any() else float(y)
    height=bottom-top
    if height<12:return None
    if include_width:
        distance=cv2.distanceTransform((labels==k).astype(np.uint8),cv2.DIST_L2,3)
        rows=distance[y+round(h*.3):y+round(h*.65)]
        thickness=float(np.median(2*rows.max(axis=1)))
        return np.array([foot_x,bottom,height,max(1,thickness)])
    return np.array([foot_x,bottom,height])


def trajectory(bg,fg,fps):
    """Track feet; accept zoom only when height and torso thickness agree."""
    n=len(bg);times=np.arange(n)
    def fill(values):
        good=np.isfinite(values)
        if not good.any():raise ValueError('자동 배치에 필요한 유효 마스크가 없습니다.')
        return np.interp(times,times[good],values[good])
    def smooth(values,seconds):
        width=max(1,round(fps*.15))|1
        return gaussian_filter1d(median_filter(values,size=width,mode='nearest'),max(.5,fps*seconds),mode='nearest')
    def size_curve(data):
        good=np.isfinite(data).all(axis=1)
        height=float(np.quantile(data[good,2],.8))
        upright=good&(data[:,2]>=np.quantile(data[good,2],.65))
        thickness=float(np.median(data[upright,3]))
        relative_height=np.log(data[:,2]/height)
        relative_width=np.log(data[:,3]/thickness)
        # A crouch/arm gesture often changes height without a matching torso-size change.
        reliable=good&(abs(relative_height-relative_width)<.18)
        zoom=np.where(reliable,.5*(relative_height+relative_width),np.nan)
        if reliable.any():zoom=smooth(fill(zoom),.2)
        else:zoom=np.zeros(n)
        return height,zoom,reliable
    bh,bzoom,bgood=size_curve(bg);fh,fzoom,fgood=size_curve(fg)
    logscale=np.log(bh/fh)+bzoom-fzoom
    # Bound scale changes locally, forward and backward, to avoid sudden zoom pulses.
    limit=.35/fps
    for i in range(1,n):logscale[i]=np.clip(logscale[i],logscale[i-1]-limit,logscale[i-1]+limit)
    for i in range(n-2,-1,-1):logscale[i]=np.clip(logscale[i],logscale[i+1]-limit,logscale[i+1]+limit)
    scales=np.exp(logscale)
    bfeet=np.column_stack([fill(bg[:,k]) for k in (0,1)])
    ffeet=np.column_stack([fill(fg[:,k]) for k in (0,1)])
    raw=bfeet-scales[:,None]*ffeet
    offsets=np.column_stack([smooth(raw[:,k],.06) for k in (0,1)])
    frames=[dict(x=float(x),y=float(y),scale=float(scale)) for (x,y),scale in zip(offsets,scales)]
    residual=np.linalg.norm(offsets+scales[:,None]*ffeet-bfeet,axis=1)
    return frames,dict(background_height_px=bh,foreground_height_px=fh,
                       scale_min=float(scales.min()),scale_max=float(scales.max()),
                       median_foot_residual_px=float(np.median(residual)),
                       zoom_supported_frames=int((bgood&fgood).sum()))


def bounds(mask):
    count,labels,stats,_=cv2.connectedComponentsWithStats((mask>127).astype(np.uint8),8)
    if count<=1:return np.full(4,np.nan)
    k=1+np.argmax(stats[1:,cv2.CC_STAT_AREA])
    x,y,w,h,area=stats[k]
    if area<64:return np.full(4,np.nan)
    return np.array([x,y,x+w-1,y+h-1],float)


def foot_soles(mask,pose):
    """Observed shoe boundary near each ankle; ambiguous/cropped feet stay unknown."""
    result=np.full(2,np.nan);box=bounds(mask)
    if not np.isfinite(box).all():return result
    h,w=mask.shape;radius=max(4.,(box[3]-box[1])*.065);feet=pose[[10,13]]
    for j,foot in enumerate(feet):
        x,y,score=foot
        if score<=.6 or not np.isfinite(foot).all() or not (0<=x<w and 0<=y<h):continue
        other=feet[1-j]
        if other[2]>.6 and np.linalg.norm(foot[:2]-other[:2])<radius*1.5:continue
        x0=max(0,int(x-radius));x1=min(w,int(x+radius)+1)
        y0=max(0,int(y-radius*.25));y1=min(h,int(y+radius*1.4)+1)
        ys,xs=np.where(mask[y0:y1,x0:x1]>200)
        if len(ys)<8:continue
        sole=float(np.quantile(ys,.99)+y0)
        # Never identify the image edge or an arbitrary search-window edge as the sole.
        if sole>=h-2 or sole>=y1-2 or sole<y-2:continue
        result[j]=sole
    return result


def estimate(foreground,background,body_masks,record):
    from source.enhancement import anatomical
    # Older open notebook cells reload only placement; refresh its helper as well.
    importlib.reload(anatomical)
    started=time.perf_counter();foreground=ex.local(foreground,exists=True)
    background=ex.local(background,exists=True)
    if ex.digest(ex.local(record['source'],exists=True))!=record['source_sha256']:
        raise ValueError('배경 원본이 변경되었습니다.')
    masks=ex.validate_masks(body_masks,record);fg=ex.sequence(foreground,record['frames'])
    info,_=rel.video_info(background)
    if info['frames']!=record['frames'] or info['size']!=record['size']:
        raise ValueError('배경/마스크 프레임 수 또는 크기 불일치')
    bgpose,fgpose,pose_file=anatomical.poses(foreground,body_masks,record)
    bb=[];fb=[];bs=[];fs=[]
    for i,(mp,fp) in enumerate(zip(masks,fg)):
        mask=cv2.imread(str(mp),0);bb.append(bounds(mask));bs.append(foot_soles(mask,bgpose[i]))
        im=cv2.imread(str(fp),-1);fb.append(bounds(im[:,:,3]));fs.append(foot_soles(im[:,:,3],fgpose[i]))
    bb=np.array(bb);fb=np.array(fb)
    frames,stats=anatomical.solve(bgpose,fgpose,bb,fb,(record['size'][1],record['size'][0]),record['fps'],
        foreground_shape=im.shape[:2],background_soles=np.array(bs),foreground_soles=np.array(fs))
    preview_index=len(frames)//2
    transform=dict(frames[preview_index],start=0,trajectory=frames,quality_strength=0.,extra_blur=0.,preserve_detail=True)
    return dict(method='anatomical-paired-contact-v6',transform=transform,preview_index=preview_index,
        seconds=time.perf_counter()-started,**stats,pose_file=str(pose_file.relative_to(ex.ROOT)),
        sampled_frames=len(frames),valid_frames=int((np.isfinite(bb).all(1)&np.isfinite(fb).all(1)).sum()),
        foreground=str(foreground.relative_to(ex.ROOT)),background=str(background.relative_to(ex.ROOT)),
        body_masks=str(ex.local(body_masks).relative_to(ex.ROOT)),removed_obj_id=record['selected_obj_id'])
