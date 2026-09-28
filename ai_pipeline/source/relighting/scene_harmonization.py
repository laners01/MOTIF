"""Deterministic scene-to-scene photometric harmonization for isolated foregrounds."""
from pathlib import Path

import cv2
import numpy as np
from scipy.ndimage import gaussian_filter1d

from source.util import removal_experiments as ex

VERSION = 'scene-harmonization-v3'
_LUT = np.where((np.arange(256)/255) <= .04045,
                (np.arange(256)/255)/12.92,
                ((np.arange(256)/255+.055)/1.055)**2.4).astype(np.float32)
_SRGB = np.where(np.linspace(0,1,65536) <= .0031308,
                 12.92*np.linspace(0,1,65536),
                 1.055*np.linspace(0,1,65536)**(1/2.4)-.055)
_SRGB = np.clip(np.rint(_SRGB*255),0,255).astype(np.uint8)


def _read_video_frame(path,index):
    cap=cv2.VideoCapture(str(ex.local(path,exists=True)));cap.set(cv2.CAP_PROP_POS_FRAMES,int(index))
    ok,frame=cap.read();cap.release()
    if not ok:raise ValueError(f'장면 분석 프레임 누락: {path} frame {index}')
    return frame


def statistics(image,subject,exclude=None):
    """Robust local scene statistics outside a subject silhouette."""
    h,w=subject.shape;scale=min(1.,320/max(h,w))
    size=(max(1,round(w*scale)),max(1,round(h*scale)))
    im=cv2.resize(image,size,interpolation=cv2.INTER_AREA);mask=cv2.resize(subject,size,interpolation=cv2.INTER_NEAREST)>32
    blocked=None
    if exclude is not None:
        blocked=cv2.resize(exclude,size,interpolation=cv2.INTER_NEAREST)>32
        blocked=cv2.dilate(blocked.astype(np.uint8),np.ones((5,5),np.uint8)).astype(bool)
    rgb=_LUT[im];luma=rgb@np.array([.0722,.7152,.2126],np.float32)
    valid=None
    for fraction in (.08,.14,.22):
        radius=max(5,round(min(size)*fraction))|1
        ring=(cv2.dilate(mask.astype(np.uint8),np.ones((radius,radius),np.uint8))>0)&~mask
        if blocked is not None:ring &= ~blocked
        valid=ring&(luma>.025)&(luma<.95)
        if valid.sum()>=64:break
    if valid is None or valid.sum()<64:return None
    values=luma[valid];log=np.log2(values.clip(1e-4,None));median=float(np.median(values))
    spread=(rgb.max(2)-rgb.min(2))/np.maximum(rgb.mean(2),.03)
    neutral=valid&(spread<.45)
    exposure_confidence=min(1.,valid.sum()/512)
    if neutral.sum()<32:
        white=np.ones(3,np.float32);white_confidence=0.
    else:
        neutral_pixels=rgb[neutral].clip(1e-3,None)
        neutral_chroma=neutral_pixels/np.exp(np.mean(np.log(neutral_pixels),axis=1,keepdims=True))
        white=np.median(neutral_chroma,axis=0);dispersion=float(np.median(np.abs(np.log(neutral_chroma)-np.log(white))))
        white_confidence=min(1.,neutral.sum()/128)*float(np.clip((.16-dispersion)/.12,0,1))
    return dict(luma=median,log_iqr=float(np.percentile(log,75)-np.percentile(log,25)),
                white=white.astype(float).tolist(),confidence=exposure_confidence,
                white_confidence=white_confidence,valid=int(valid.sum()),neutral=int(neutral.sum()))


def transform(source,target):
    exposure_confidence=float(min(source['confidence'],target['confidence']))
    white_confidence=float(min(source.get('white_confidence',source['confidence']),
                               target.get('white_confidence',target['confidence'])))
    raw_exposure=np.log2((target['luma']+.01)/(source['luma']+.01))
    exposure=float(np.clip(raw_exposure,-.35,.35))*exposure_confidence
    sw=np.asarray(source['white']);tw=np.asarray(target['white'])
    white=np.clip(tw/np.maximum(sw,1e-3),.92,1.08)
    white=np.exp(white_confidence*np.log(white));white/=np.exp(np.mean(np.log(white)))
    white=np.clip(white,.92,1.08)
    gamma=(float(np.clip(target['log_iqr']/source['log_iqr'],.95,1.05))
           if min(source['log_iqr'],target['log_iqr'])>=.15 else 1.)
    gamma=1+exposure_confidence*(gamma-1)
    # Scene rings contain different objects, so their histogram, palette and
    # saturation are not transferable evidence. Keep those transforms neutral.
    return dict(exposure=exposure,white=white.astype(float).tolist(),look=[1.,1.,1.],
                saturation=1.,tone=[[.05,.05],[.18,.18],[.7,.7]],gamma=gamma,
                confidence=max(exposure_confidence,white_confidence),
                exposure_confidence=exposure_confidence,white_confidence=white_confidence)


def smooth(records,count,fps):
    keys=('exposure','gamma','saturation','confidence');known=np.array([row['frame'] for row in records],float);x=np.arange(count)
    result={key:np.interp(x,known,[row[key] for row in records]) for key in keys}
    result['white']=np.stack([np.interp(x,known,[row['white'][channel] for row in records]) for channel in range(3)],1)
    result['look']=np.stack([np.interp(x,known,[row['look'][channel] for row in records]) for channel in range(3)],1)
    result['tone']=np.stack([np.stack([np.interp(x,known,[row['tone'][point][axis] for row in records])
                                      for axis in range(2)],1) for point in range(3)],1)
    sigma=max(.5,fps*.25)
    for key in keys:result[key]=gaussian_filter1d(result[key],sigma,mode='nearest')
    result['white']=gaussian_filter1d(result['white'],sigma,axis=0,mode='nearest')
    result['look']=gaussian_filter1d(result['look'],sigma,axis=0,mode='nearest')
    result['tone']=gaussian_filter1d(result['tone'],sigma,axis=0,mode='nearest')
    # Prefer the stable video-level grade; retain 25% of slow scene changes.
    for key in ('exposure','gamma'):
        result[key]=.25*result[key]+.75*np.median(result[key])
    result['white']=.25*result['white']+.75*np.median(result['white'],axis=0)
    result['exposure']=np.clip(result['exposure'],-.35,.35);result['gamma']=np.clip(result['gamma'],.95,1.05)
    result['white']=np.clip(result['white'],.92,1.08)
    return result


def apply(image,record):
    """Apply exposure, chromatic adaptation and pivoted midtone contrast in linear light."""
    alpha=image[...,3].copy();linear=_LUT[image[...,:3]]
    white=np.asarray(record['white'],np.float32).reshape(1,1,3)
    look=np.asarray(record.get('look',[1.,1.,1.]),np.float32).reshape(1,1,3)
    corrected=linear*white*look*(2**float(record['exposure']))
    luma=corrected@np.array([.0722,.7152,.2126],np.float32)
    tone=np.asarray(record.get('tone',[[.05,.05],[.18,.18],[.7,.7]]),np.float32)
    mapped=np.interp(luma,np.r_[0.,tone[:,0],1.],np.r_[0.,tone[:,1],1.])
    pivot=.18;mapped*=np.power(np.maximum(mapped,1e-5)/pivot,float(record['gamma'])-1)
    corrected*=np.divide(mapped,luma,out=np.ones_like(luma),where=luma>1e-5)[...,None]
    saturation=float(record.get('saturation',1.))
    corrected=mapped[...,None]*np.power(np.maximum(corrected,1e-5)/np.maximum(mapped[...,None],1e-5),saturation)
    # Reduce only newly introduced clipping/crushed blacks. Existing source
    # clipping is not counted against the correction.
    valid=alpha>32
    if valid.any():
        original_luma=linear@np.array([.0722,.7152,.2126],np.float32)
        original_clip=np.max(linear,axis=2)>=.98
        original_black=original_luma<=.01
        for strength in (1.,.75,.5,.25,0.):
            candidate=linear+strength*(corrected-linear)
            candidate_luma=candidate@np.array([.0722,.7152,.2126],np.float32)
            new_clip=np.mean((np.max(candidate,axis=2)>=.98)[valid]&~original_clip[valid])
            new_black=np.mean((candidate_luma<=.01)[valid]&~original_black[valid])
            if new_clip<=.005 and new_black<=.005:
                corrected=candidate
                break
    encoded=_SRGB[(np.clip(corrected,0,1)*65535).round().astype(np.uint16)]
    return np.dstack([encoded,alpha])


def build(foreground_video,source_masks,background_video,foreground_frames,transform_record,
          foreground_fps,background_fps,count,shortfall,destination_exclusion=None):
    """Build output-frame transforms at one-second keyframes."""
    from source.relighting import composite as cp
    foreground_video=ex.local(foreground_video,exists=True);source_masks=ex.local(source_masks,exists=True)
    background_video=ex.local(background_video,exists=True)
    ex.sequence(source_masks,len(foreground_frames))
    cap=cv2.VideoCapture(str(foreground_video));source_count=int(cap.get(cv2.CAP_PROP_FRAME_COUNT));source_rate=cap.get(cv2.CAP_PROP_FPS);cap.release()
    if source_count!=len(foreground_frames) or abs(source_rate-foreground_fps)>1e-4:
        raise ValueError('전경 원본·crop의 프레임 수 또는 FPS가 다릅니다.')
    exclusion=ex.local(destination_exclusion) if destination_exclusion is not None else None
    sample_indices=sorted(set(range(0,count,max(1,round(background_fps))))|{count-1});records=[];diagnostics=[]
    for index in sample_indices:
        time=cp.foreground_frame_time(index,int(transform_record.get('start',0)),foreground_fps,background_fps,len(foreground_frames),shortfall)
        if time is None:continue
        source_index=min(len(foreground_frames)-1,int(round(time)))
        source=_read_video_frame(foreground_video,source_index)
        source_mask=cv2.imread(str(source_masks/f'{source_index:06d}.png'),cv2.IMREAD_GRAYSCALE)
        background=_read_video_frame(background_video,index)
        crop=cv2.imread(str(foreground_frames[source_index]),cv2.IMREAD_UNCHANGED)
        if source_mask is None or crop is None:continue
        current=cp.frame_transform(transform_record,index)
        placed=cp._affine_foreground(crop,background.shape,{**current,'subpixel':True})[...,3]
        blocked=None if exclusion is None else cv2.imread(str(exclusion/f'{index:06d}.png'),cv2.IMREAD_GRAYSCALE)
        source_stats=statistics(source,source_mask);target_stats=statistics(background,placed,blocked)
        if source_stats is None or target_stats is None:continue
        row={'frame':index,**transform(source_stats,target_stats)};records.append(row)
        diagnostics.append(dict(frame=index,source=source_stats,target=target_stats,transform=row))
    if not records:
        records=[dict(frame=0,exposure=0.,white=[1.,1.,1.],look=[1.,1.,1.],saturation=1.,
                      tone=[[.05,.05],[.18,.18],[.7,.7]],gamma=1.,confidence=0.)]
    curve=smooth(records,count,background_fps)
    return curve,dict(version=VERSION,keyframes=records,diagnostics=diagnostics,
                      foreground_video=str(foreground_video.relative_to(ex.ROOT)),
                      source_masks=str(source_masks.relative_to(ex.ROOT)),
                      destination_exclusion=str(exclusion.relative_to(ex.ROOT)) if exclusion else None)


def at(curve,index):
    return dict(exposure=float(curve['exposure'][index]),white=curve['white'][index].astype(float).tolist(),
                look=curve['look'][index].astype(float).tolist(),saturation=float(curve['saturation'][index]),
                tone=curve['tone'][index].astype(float).tolist(),gamma=float(curve['gamma'][index]),
                confidence=float(curve['confidence'][index]))
