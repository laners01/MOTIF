"""Pose-based body-center placement, support-foot tracking and stable size ratios."""
import os,subprocess,time
import cv2,numpy as np
from scipy.ndimage import median_filter,gaussian_filter1d,percentile_filter
from scipy.sparse import diags, eye
from scipy.sparse.linalg import spsolve
from source.util import removal_experiments as ex


def background_frames(record):
    """Decode once in the notebook runtime; workers consume the same verified PNGs."""
    source=ex.local(record['source'],exists=True)
    if ex.digest(source)!=record['source_sha256']:raise ValueError('배경 원본이 변경되었습니다.')
    key=record['source_sha256']
    # The digest is checked above, so it cannot introduce an arbitrary cache path.
    work=ex.local('work/dance_enhancement/background_frames')/key
    work.mkdir(parents=True,exist_ok=True);pointer=work/'frames.json'
    expected=dict(source_sha256=key,frames=record['frames'],size=record['size'],decoder=cv2.__version__)
    if pointer.is_file():
        saved=ex.read_json(pointer)
        if saved['inputs']==expected:
            paths=ex.sequence(saved['folder'],record['frames'])
            if saved['hashes']==[ex.digest(p) for p in paths]:return ex.local(saved['folder'])
    folder=ex.unique_dir(work,'decoded');cap=cv2.VideoCapture(str(source));count=0
    try:
        while True:
            ok,frame=cap.read()
            if not ok:break
            if [frame.shape[1],frame.shape[0]]!=record['size']:raise ValueError('배경 프레임 해상도 불일치')
            if not cv2.imwrite(str(folder/f'{count:06d}.png'),frame):raise IOError('배경 프레임 저장 실패')
            count+=1
    finally:cap.release()
    if count!=record['frames']:
        raise ValueError(f"배경 실제 디코딩 프레임 수 불일치: 예상 {record['frames']}, 읽음 {count} · {record['source']}")
    paths=ex.sequence(folder,count)
    ex.write_json(pointer,dict(inputs=expected,folder=str(folder.relative_to(ex.ROOT)),hashes=[ex.digest(p) for p in paths]))
    return folder


def poses(foreground,masks,record,*,cache_file=None):
    paths=ex.sequence(foreground,record['frames']);mp=ex.validate_masks(masks,record)
    signature=dict(source_sha256=record['source_sha256'],fg=[ex.digest(p) for p in paths],masks=[ex.digest(p) for p in mp])
    prepared=record.get('prepared_background')
    if prepared:signature['prepared_pose']=ex.digest(prepared['pose'])
    parent=ex.local(foreground).parent
    pointer=ex.local(cache_file) if cache_file is not None else ex.local(foreground)/'placement_pose_cache.json'
    # Before/after have different source times and must keep independent poses.
    candidates=[pointer] if cache_file is not None else [pointer,parent/'placement_pose_cache.json',parent/'depth_baseline_pose_cache.json']
    for candidate in candidates:
        if not candidate.exists():continue
        cache=ex.read_json(candidate)
        if cache['signature']==signature:
            file=ex.local(cache['file'],exists=True);data=np.load(file,allow_pickle=False)
            return data['background'],data['foreground'],file
    frames=None if prepared else background_frames(record)
    work=ex.unique_dir(parent,'placement_poses')
    config=dict(work=str(work.relative_to(ex.ROOT)),source=record['source'],
                background_frames=str(frames.relative_to(ex.ROOT)) if frames is not None else None,
                foreground=str(ex.local(foreground).relative_to(ex.ROOT)),a_masks=str(ex.local(masks).relative_to(ex.ROOT)))
    if prepared:config['prepared_background_pose']=prepared['pose']
    ex.write_json(work/'input.json',config)
    env=os.environ.copy();env.update(MPLBACKEND='Agg',PYTHONDONTWRITEBYTECODE='1',  # DWPose GPU 사용: CUDA_VISIBLE_DEVICES='' 제거
        MPLCONFIGDIR=str(ex.local('.cache/dance_enhancement/mplconfigdir')),TMPDIR=str(work),PYTHONPATH=str(ex.ROOT))
    print('A·C 골반/좌우 발목 추출 중:',work/'inference.log',flush=True)
    with (work/'inference.log').open('w') as log:
        result=subprocess.run([str(ex.ROOT/'.model-envs/mimicmotion/bin/python'),str(ex.local('source/enhancement/placement_worker.py')),str(work/'input.json')],env=env,cwd=ex.ROOT,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise RuntimeError((work/'inference.log').read_text()[-5000:])
    file=work/'poses.npz';ex.write_json(pointer,dict(signature=signature,file=str(file.relative_to(ex.ROOT))))
    data=np.load(file,allow_pickle=False);return data['background'],data['foreground'],file


def fill(values,fallback=0.):
    values=np.asarray(values,dtype=float);good=np.isfinite(values)
    return np.interp(np.arange(len(values)),np.flatnonzero(good),values[good]) if good.any() else np.full(len(values),fallback)


def smooth(values,fps,seconds=.12):
    width=max(1,round(fps*.13))|1
    return gaussian_filter1d(median_filter(values,size=width,mode='nearest'),max(.5,fps*seconds),mode='nearest')


def center(pose,bounds=None,fps=30.):
    hips=np.where((pose[:,8,2]>.45)&(pose[:,11,2]>.45),pose[:,[8,11],0].mean(1),np.nan)
    shoulders=np.where((pose[:,2,2]>.45)&(pose[:,5,2]>.45),pose[:,[2,5],0].mean(1),np.nan)
    if bounds is None:return np.where(np.isfinite(hips),hips,shoulders)
    # Changing from hips to shoulders must not relocate a leaning person's root.
    shoulders+=smooth(fill(hips-shoulders),fps,.3)
    root=np.where(np.isfinite(hips),hips,shoulders)
    silhouette=(bounds[:,0]+bounds[:,2])/2
    silhouette+=smooth(fill(root-silhouette),fps,.3)
    return median_filter(fill(np.where(np.isfinite(root),root,silhouette)),size=3,mode='nearest')


def stable_scales(bg,fg,bounds_bg,bounds_fg,fps):
    # Compare corresponding poses at the actual rendered times. Independent
    # per-person zoom filters turn dance posture differences into scale drift.
    def lengths(pose):
        result=np.full((len(pose),3),np.nan)
        good=(pose[:,[1,8,11],2]>.5).all(1)
        result[good,0]=np.linalg.norm(pose[good,1,:2]-pose[good][:,[8,11],:2].mean(1),axis=1)
        for k,(hip,knee,ankle) in enumerate(((8,9,10),(11,12,13)),1):
            good=(pose[:,[hip,knee,ankle],2]>.5).all(1)
            result[good,k]=(np.linalg.norm(pose[good,hip,:2]-pose[good,knee,:2],axis=1)
                           +np.linalg.norm(pose[good,knee,:2]-pose[good,ankle,:2],axis=1))
        result[(result<8)|~np.isfinite(result)]=np.nan
        return result
    b=lengths(bg);f=lengths(fg)
    mirror=pose_mirror(bg,fg)
    if mirror:f=f[:,[0,2,1]]
    reference=lambda data:np.array([np.quantile(v[np.isfinite(v)],.8) if np.isfinite(v).any() else np.nan for v in data.T])
    br,fr=reference(b),reference(f);good=np.isfinite(br)&np.isfinite(fr)
    if good.any():
        weights=np.array([1.,.5,.5])*good
        base=float(np.log(np.nansum(br*weights)/np.nansum(fr*weights)))
    else:
        bh=bounds_bg[:,3]-bounds_bg[:,1];fh=bounds_fg[:,3]-bounds_fg[:,1]
        bh=bh[np.isfinite(bh)&(bh>0)];fh=fh[np.isfinite(fh)&(fh>0)]
        if not len(bh) or not len(fh):raise ValueError('자동 배치에 필요한 인물 크기 관측이 없습니다.')
        base=float(np.log(np.quantile(bh,.8)/np.quantile(fh,.8)))
    relative=np.log(b/f)-np.log(br/fr)
    target=np.full(len(bg),np.nan)
    for i,row in enumerate(relative):
        if not np.isfinite(row[0]):continue
        legs=row[1:];valid=np.isfinite(legs)&(abs(legs-row[0])<.14)
        # Two foreshortened legs cannot outvote an unchanged torso.
        if valid.any():target[i]=np.median(np.r_[row[0],legs[valid]])
    valid=np.isfinite(target)
    zoom=fit_curve(fill(target),fps,weights=valid.astype(float)) if valid.any() else np.zeros(len(bg))
    scale=np.exp(base+zoom)
    return scale,dict(background_zoom_frames=int(valid.sum()),foreground_zoom_frames=int(valid.sum()),
                      anatomical_scale_frames=int(valid.sum()),placement_mirror=bool(mirror))


def fit_curve(values,fps,*,weights=None,seconds=.16):
    """Offline curvature fit: suppress jitter without delaying a real size change."""
    values=np.asarray(values,float);n=len(values)
    if n<3:return values.copy()
    w=np.ones(n) if weights is None else np.asarray(weights,float)
    second=diags([np.ones(n-2),-2*np.ones(n-2),np.ones(n-2)],[0,1,2],shape=(n-2,n)).tocsr()
    penalty=(fps*seconds)**4*(second.T@second)
    # A tiny level prior makes long missing intervals well-defined.
    return spsolve(diags(w+1e-6)+penalty,(w+1e-6)*values)


def pose_mirror(bg,fg):
    """Match anatomical sides for placement; do not flip foreground pixels."""
    bones=((2,3),(3,4),(5,6),(6,7),(8,9),(9,10),(11,12),(12,13))
    order=np.array([0,1,5,6,7,2,3,4,11,12,13,8,9,10,15,14,17,16])
    costs=[]
    for mirror in (False,True):
        p=fg[:,order].copy() if mirror else fg.copy()
        if mirror:p[:,:,0]*=-1
        errors=[]
        for a,b in bones:
            u=bg[:,b,:2]-bg[:,a,:2];v=p[:,b,:2]-p[:,a,:2]
            good=(np.minimum.reduce([bg[:,a,2],bg[:,b,2],p[:,a,2],p[:,b,2]])>.6)
            un=np.linalg.norm(u,axis=1);vn=np.linalg.norm(v,axis=1);good&=(un>8)&(vn>8)
            errors.extend(np.sum((u[good]/un[good,None]-v[good]/vn[good,None])**2,axis=1))
        costs.append(float(np.mean(errors)) if errors else 2.)
    return costs[1]<costs[0]-.03


def contact_x(bg,fg,scale,bounds_bg,bounds_fg,bs,fs,fps,mirror):
    """Follow stage travel while preserving planted-foot motion, rather than hip sway."""
    bx=center(bg,bounds_bg,fps);fx=center(fg,bounds_fg,fps);n=len(bg)
    root=fit_curve(bx-scale*fx,fps,seconds=.25)
    if n<3:return root,0
    difference=diags([-np.ones(n-1),np.ones(n-1)],[0,1],shape=(n-1,n)).tocsr()
    weight=np.zeros(n-1);velocity=np.zeros(n-1)
    stage=np.diff(fit_curve(bx,fps,seconds=.35))
    for ankle in (10,13):
        other=(23-ankle) if mirror else ankle
        planted=(fs==ankle)&(fg[:,ankle,2]>.6)
        active=planted[1:]&planted[:-1]
        reference=(bs==other)&(bg[:,other,2]>.6)
        observed=np.where(reference[1:]&reference[:-1],np.diff(bg[:,other,0]),stage)
        target=observed-np.diff(scale*fg[:,ankle,0])
        weight+=active;velocity+=np.where(active,target,0.)
    # Position is a soft stage anchor; support-foot velocities take precedence.
    second=difference[1:]-difference[:-1]
    matrix=eye(n)+80*(difference.T@diags(weight)@difference)+(fps*.06)**4*(second.T@second)
    x=spsolve(matrix,root+80*difference.T@velocity)
    return x,int((weight>0).sum())


def ground_track(pose,bounds,shape,fps,soles=None):
    """Estimate contact from low, slow feet; bridge missing contacts instead of following kicks."""
    h,w=shape;n=len(pose);feet=pose[:,[10,13],:]
    stature=fill(bounds[:,3]-bounds[:,1]);shoe=np.maximum(2,stature*.018)
    sole=feet[:,:,1]+shoe[:,None]
    if soles is not None:
        # Estimate footwear offset from actual alpha boundaries, including thick soles.
        for j in range(2):
            offset=soles[:,j]-feet[:,j,1];good=np.isfinite(offset)
            if good.sum()>=max(1,min(3,n)):
                measured=smooth(fill(offset,float(np.median(shoe))),fps,.04)
                sole[:,j]=feet[:,j,1]+measured
    valid=(feet[:,:,2]>.45)&np.isfinite(feet).all(2)
    valid &= (feet[:,:,0]>=2)&(feet[:,:,0]<w-2)&(feet[:,:,1]>=0)&(feet[:,:,1]<h-2)
    low=np.max(np.where(valid,sole,-np.inf),axis=1);low[~valid.any(1)]=np.nan
    if not np.isfinite(low).any():
        # With no usable ankles, retain a stable silhouette-derived level, never a per-frame kick.
        floor=np.full(n,float(np.nanquantile(bounds[:,3],.85)))
        return floor,np.full(n,-1,int)
    window=max(3,round(fps*1.0))|1
    # Remove gradual stage/camera travel before finding the contact envelope.
    # Otherwise an approaching/receding floor is mistaken for a lifted foot.
    trend=fit_curve(fill(low),fps,seconds=.5)
    envelope=trend+percentile_filter(fill(low)-trend,85,size=window,mode='nearest')
    speed=np.zeros((n,2))
    for j in range(2):
        xy=np.column_stack([median_filter(fill(np.where(valid[:,j],feet[:,j,k],np.nan)),size=3,mode='nearest') for k in (0,1)])
        if n>1:speed[:,j]=np.linalg.norm(np.gradient(xy,axis=0),axis=1)*fps
    near=np.abs(sole-envelope[:,None])<=np.maximum(4,stature*.035)[:,None]
    contacts=valid&near&(speed<=np.maximum(30,stature*1.2)[:,None])
    # A single noisy detection cannot establish a new support foot.
    if n>2:
        neighbors=np.pad(contacts.astype(int),((1,1),(0,0)),mode='edge')
        contacts &= neighbors[:-2]+neighbors[1:-1]+neighbors[2:]>=2
    ids=np.full(n,-1,int);observed=np.full(n,np.nan);last=-1
    for i in range(n):
        candidates=np.flatnonzero(contacts[i])
        if not len(candidates):continue
        lower=int(candidates[np.argmax(sole[i,candidates])])
        if last in candidates and sole[i,lower]-sole[i,last]<max(4,stature[i]*.02):lower=last
        ids[i]=(10,13)[lower];observed[i]=sole[i,lower];last=lower
    # The inferred ground remains through lifted/occluded feet. Foreground jump height
    # stays in the RGBA pose; it is not cancelled by following its airborne ankle.
    floor=smooth(fill(observed),fps,.06) if np.isfinite(observed).any() else smooth(envelope,fps,.15)
    return floor,ids


def solve(bg,fg,bounds_bg,bounds_fg,shape,fps,*,foreground_shape=None,background_soles=None,foreground_soles=None):
    n=len(bg)
    if n<1 or fps<=0:raise ValueError('유효한 프레임과 FPS가 필요합니다.')
    if not np.isfinite(bounds_bg).all(1).any() or not np.isfinite(bounds_fg).all(1).any():
        raise ValueError('배경 또는 전경 인물 마스크에 유효한 프레임이 없습니다.')
    scale,size_stats=stable_scales(bg,fg,bounds_bg,bounds_fg,fps)
    bx=center(bg,bounds_bg,fps);fx=center(fg,bounds_fg,fps)
    if foreground_shape is None:
        foreground_shape=(int(np.nanmax(bounds_fg[:,3]))+20,int(np.nanmax(bounds_fg[:,2]))+20)
    by,bs=ground_track(bg,bounds_bg,shape,fps,background_soles)
    fy,fs=ground_track(fg,bounds_fg,foreground_shape,fps,foreground_soles)
    x,locked=contact_x(bg,fg,scale,bounds_bg,bounds_fg,bs,fs,fps,size_stats['placement_mirror'])
    y=by-scale*fy
    vertical_anchor='ground'
    if not (bs>=0).any() or not (fs>=0).any():
        # A permanently cropped lower body has no observed floor. Align a shared
        # anatomical anchor instead of mistaking the bottom of the crop for feet.
        for joints,name in (((8,11),'hips'),((1,),'neck')):
            bgood=(bg[:,joints,2]>.6).all(1);fgood=(fg[:,joints,2]>.6).all(1)
            if bgood.mean()<.1 or fgood.mean()<.1:continue
            ba=fill(np.where(bgood,bg[:,joints,1].mean(1),np.nan))
            fa=fill(np.where(fgood,fg[:,joints,1].mean(1),np.nan))
            y=smooth(ba,fps,.06)-scale*smooth(fa,fps,.04);vertical_anchor=name
            break
    # Keep the body-center trajectory. Off-screen hands/feet are clipped by the
    # compositor; they must never push the entire person sideways or upwards.
    frames=[dict(x=float(xx),y=float(yy),scale=float(ss)) for xx,yy,ss in zip(x,y,scale)]
    return frames,dict(scale_min=float(scale.min()),scale_max=float(scale.max()),
        background_support_ankle=bs.tolist(),foreground_support_ankle=fs.tolist(),
        body_center_median_error_px=float(np.median(abs(x+scale*fill(fx)-fill(bx)))),
        visibility_guard_frames=0,vertical_anchor=vertical_anchor,contact_locked_intervals=locked,**size_stats,
        background_ground_y=by.tolist(),foreground_ground_y=fy.tolist(),
        background_contact_frames=int((bs>=0).sum()),foreground_contact_frames=int((fs>=0).sum()))
