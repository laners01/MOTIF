"""Depth-only B/C ordering: original-scene inverse depth transferred by body part."""
import hashlib,json,os,subprocess,sys,time
import cv2,numpy as np
from scipy.ndimage import median_filter
from source.util import removal_experiments as ex
from source.enhancement.occlusion import driver_masks
from source.enhancement import anatomical
from source.relighting import composite as cp

PYTHON=sys.executable
VENDOR='vendor/Video-Depth-Anything'
CHECKPOINT=VENDOR+'/checkpoints/video_depth_anything_vitl.pth'
PARTS=((0,1),(1,18),(2,3),(3,4),(5,6),(6,7),(8,9),(9,10),(11,12),(12,13))
MIRROR=np.array([0,1,4,5,2,3,8,9,6,7])


def missing_backend():
    return [p for p in (CHECKPOINT,VENDOR+'/video_depth_anything/video_depth.py',
        '.model-envs/video-depth-deps/easydict/__init__.py') if not ex.local(p).is_file()]


def signature(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True).encode()).hexdigest()


def scene_depth(record,gpu):
    missing=missing_backend()
    if missing:raise FileNotFoundError('Video Depth Anything Large 준비 필요:\n'+'\n'.join(missing))
    source=ex.local(record['source'],exists=True)
    if ex.digest(source)!=record['source_sha256']:raise ValueError('깊이 분석 원본 영상이 변경되었습니다.')
    inputs=dict(source_sha256=record['source_sha256'],frames=record['frames'],fps=record['fps'],
        size=record['size'],checkpoint=ex.digest(ex.local(CHECKPOINT)),
        worker=ex.digest(ex.local('source/enhancement/depth_worker.py')),input_size=518)
    work=ex.local('work/video_depth_anything')/signature(inputs);work.mkdir(parents=True,exist_ok=True)
    output=work/'inverse_depth.npy'
    if not output.is_file() or not (work/'result.json').is_file():
        cfg=dict(inputs,source=record['source'],work=str(work.relative_to(ex.ROOT)),checkpoint=CHECKPOINT)
        ex.write_json(work/'input.json',cfg)
        env=os.environ.copy();env.update(CUDA_VISIBLE_DEVICES=str(gpu),CUDA_DEVICE_ORDER='PCI_BUS_ID',
            MPLBACKEND='Agg',PYTHONDONTWRITEBYTECODE='1',PYTHONPATH=str(ex.ROOT),
            HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1')
        for key in ('TMPDIR','MPLCONFIGDIR','XDG_CACHE_HOME','TORCH_HOME','CUDA_CACHE_PATH'):
            folder=ex.local('.cache/video_depth_anything')/key.lower();folder.mkdir(parents=True,exist_ok=True);env[key]=str(folder)
        print('Video Depth Anything Large · 원본 A/B 깊이 추론:',work/'inference.log',flush=True)
        started=time.perf_counter()
        with (work/'inference.log').open('w') as log:
            # 자식 출력을 파일+부모 stdout(Cloud Run 로그) 양쪽에 흘린다(RelightVid tee와 동일). 기존엔 파일만 남아 로딩·진행이 무음이었다.
            proc=subprocess.Popen([PYTHON,str(ex.local('source/enhancement/depth_worker.py')),str(work/'input.json')],
                cwd=ex.ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            for chunk in iter(proc.stdout.readline,b""):
                line=chunk.decode(errors='replace');log.write(line);print(line,end='',flush=True)
            returncode=proc.wait()
        if returncode:raise RuntimeError((work/'inference.log').read_text(errors='replace')[-6000:])
        ex.write_json(work/'timing.json',dict(seconds=time.perf_counter()-started,inputs=inputs))
    depth=np.load(output,mmap_mode='r',allow_pickle=False)
    if depth.shape!=(record['frames'],record['size'][1],record['size'][0]):raise ValueError('깊이 결과 프레임/해상도 불일치')
    return depth,output


def stabilize_poses(poses,fps):
    """Bridge short joint dropouts; retain recent joints with body translation only briefly."""
    result=np.array(poses,dtype=float,copy=True);limit=max(1,round(fps*.2))
    valid=(result[:,:,2]>=.55)&np.isfinite(result[:,:,:2]).all(2)
    result[~valid,2]=0
    for joint in range(result.shape[1]):
        good=np.flatnonzero(valid[:,joint])
        for left,right in zip(good[:-1],good[1:]):
            if 1<right-left<=limit+1:
                result[left+1:right,joint,:2]=np.linspace(result[left,joint,:2],result[right,joint,:2],right-left+1)[1:-1]
                result[left+1:right,joint,2]=.55
        # No long-gap interpolation or indefinite reuse of an invented joint.
        observed=result[:,joint,2]>=.55
        last=-1
        for i in range(len(result)):
            if observed[i]:last=i
            elif last>=0 and i-last<=limit:
                anchors=[j for j in (1,8,11) if valid[i,j] and valid[last,j]]
                shift=np.median(poses[i,anchors,:2]-poses[last,anchors,:2],axis=0) if anchors else 0
                result[i,joint,:2]=result[last,joint,:2]+shift;result[i,joint,2]=.55
    return result


def segments(pose):
    hips=(pose[8]+pose[11])/2;hips[2]=min(pose[8,2],pose[11,2])
    return np.vstack([pose,hips])


def part_costs(points,pose):
    """Anatomical capsules leave distant pixels unknown instead of assigning any surviving limb."""
    p=segments(pose);cost=np.full((len(points),len(PARTS)),np.inf)
    sizes=[np.linalg.norm(p[a,:2]-p[b,:2])*factor for a,b,factor in
           ((1,18,1.),(2,5,1.5),(8,9,1.),(11,12,1.)) if min(p[a,2],p[b,2])>=.55]
    if not sizes:return cost
    unit=max(8.,float(np.median(sizes)))
    # Shins include the shoe beyond the ankle; a narrow bone-only tube clips the toes.
    widths=(.5,.65,.35,.35,.35,.35,.4,.55,.4,.55)
    for i,(a,b) in enumerate(PARTS):
        if min(p[a,2],p[b,2])<.55:continue
        v=p[b,:2]-p[a,:2];length=float(v@v)
        if length<16:continue
        t=np.clip((points-p[a,:2])@v/length,0,1)
        d=((points-(p[a,:2]+t[:,None]*v))**2).sum(1)
        score=d/max(3.,unit*widths[i])**2
        cost[score<=1,i]=score[score<=1]
    return cost


def nearest_parts(points,pose):
    cost=part_costs(points,pose);labels=cost.argmin(1)
    labels[~np.isfinite(cost).any(1)]=-1
    return labels


class PartTracker:
    """Keep ambiguous labels in a moving limb's coordinates, never at fixed screen pixels."""
    def __init__(self,shape):
        self.labels=np.full(shape,-1,np.int8);self.pose=None

    def assign(self,points,pose):
        cost=part_costs(points,pose);p=segments(pose);h,w=self.labels.shape
        if self.pose is not None:
            old=segments(self.pose)
            for part,(a,b) in enumerate(PARTS):
                selected=np.flatnonzero(np.isfinite(cost[:,part]))
                if not len(selected) or min(old[a,2],old[b,2])<.55:continue
                v=p[b,:2]-p[a,:2];ov=old[b,:2]-old[a,:2]
                if ov@ov<16:continue
                delta=points[selected]-p[a,:2];normal=np.array([-v[1],v[0]])
                previous=old[a,:2]+(delta@v/(v@v))[:,None]*ov+(delta@normal/(v@v))[:,None]*np.array([-ov[1],ov[0]])
                xy=np.rint(previous).astype(int);inside=(xy[:,0]>=0)&(xy[:,0]<w)&(xy[:,1]>=0)&(xy[:,1]<h)
                rows=selected[inside];xy=xy[inside]
                # A modest tie preference; a clearly closer moving limb can still take over.
                retained=rows[self.labels[xy[:,1],xy[:,0]]==part]
                cost[retained,part]-=.15
        labels=cost.argmin(1);labels[~np.isfinite(cost).any(1)]=-1
        self.labels.fill(-1)
        self.labels[points[:,1],points[:,0]]=labels
        self.pose=pose.copy()
        return labels


def part_depths(depth,masks,poses):
    values=np.full((len(masks),len(PARTS)),np.nan);noise=values.copy()
    tracker=PartTracker(depth.shape[1:])
    for i,path in enumerate(masks):
        mask=cv2.erode(cv2.imread(str(path),0),np.ones((3,3),np.uint8))>127
        y,x=np.where(mask);labels=tracker.assign(np.column_stack([x,y]),poses[i]);z=depth[i,y,x]
        for part in range(len(PARTS)):
            good=z[(labels==part)&np.isfinite(z)]
            if len(good)<16:continue
            values[i,part]=np.median(good);noise[i,part]=np.median(abs(good-values[i,part]))
    return values,noise


def smooth_parts(values,fps):
    result=values.copy();max_gap=max(1,round(fps*.2))
    for part in range(values.shape[1]):
        col=values[:,part];valid=np.flatnonzero(np.isfinite(col))
        for left,right in zip(valid[:-1],valid[1:]):
            if right-left<=max_gap+1:result[left:right+1,part]=np.linspace(col[left],col[right],right-left+1)
        # Smooth only observations with local support; long occlusions stay unknown.
        good=np.flatnonzero(np.isfinite(result[:,part]))
        if len(good):
            filled=np.interp(np.arange(len(col)),good,result[good,part])
            result[good,part]=median_filter(filled,size=3,mode='nearest')[good]
    return result


class Ordering:
    """Depth hysteresis by body part, independent of fixed image coordinates."""
    def __init__(self,fps):
        n=len(PARTS);self.state=np.zeros(n,int);self.pending=np.zeros(n,int)
        self.count=np.zeros(n,int);self.age=np.zeros(n,int);self.hold=max(1,round(fps*.2))
        self.history=np.full((n,3),np.nan)

    def update(self,part,delta,margin):
        row=self.history[part];row[:-1]=row[1:];row[-1]=delta/max(margin,1e-6)
        # Normalize by local depth uncertainty before rejecting isolated one-frame reversals.
        supported=row[np.isfinite(row)]
        filtered=float(np.median(supported)) if np.isfinite(delta) and len(supported) else np.nan
        evidence=0 if not np.isfinite(filtered) or abs(filtered)<=1 else (1 if filtered>0 else -1)
        if not evidence:
            self.age[part]+=1;self.count[part]=0;self.pending[part]=0
            if self.age[part]>self.hold:self.state[part]=0
        else:
            self.age[part]=0
            self.count[part]=self.count[part]+1 if evidence==self.pending[part] else 1
            self.pending[part]=evidence
            if self.state[part]==0 or self.count[part]>=2:self.state[part]=evidence
        return self.state[part]


def soften_front(front,overlap):
    """Antialias only the one-pixel boundary; never restore B outside today's B/C overlap."""
    soft=cv2.GaussianBlur(front,(3,3),.65)
    return np.where(overlap,soft,0).astype(np.uint8)


def prepare(plan,a_masks,record,placement,*,gpu,foreground=None):
    started=time.perf_counter();a_paths=ex.validate_masks(a_masks,record)
    b_folder=driver_masks(plan,record);b_paths=ex.validate_masks(b_folder,record)
    foreground=ex.local(foreground or placement['foreground'],exists=True)
    fg_paths=ex.sequence(foreground,record['frames'])
    if foreground==ex.local(placement['foreground']):
        posefile=ex.local(placement['pose_file'],exists=True);poses=np.load(posefile,allow_pickle=False)
        ap,cp_poses=poses['background'],poses['foreground']
    else:
        ap,cp_poses,posefile=anatomical.poses(foreground,a_masks,record,
            cache_file=foreground.parent/'depth_baseline_pose_cache.json')
    prepared=record.get('prepared_background')
    if prepared:
        depthfile=ex.local(prepared['depth'],exists=True)
        depth=np.load(depthfile,mmap_mode='r',allow_pickle=False)[prepared['first']:prepared['stop']]
        if depth.shape!=(record['frames'],record['size'][1],record['size'][0]):raise ValueError('준비된 깊이 프레임 불일치')
    else:depth,depthfile=scene_depth(record,gpu)
    inputs=dict(version=2,source_sha256=record['source_sha256'],frames=record['frames'],size=record['size'],fps=record['fps'],
        a_hashes=[ex.digest(p) for p in a_paths],b_hashes=[ex.digest(p) for p in b_paths],
        fg_hashes=[ex.digest(p) for p in fg_paths],pose_hash=ex.digest(posefile),
        depth_hash=prepared['depth_sha256'] if prepared else ex.digest(depthfile),transform=placement['transform'],mirror=bool(plan.get('mirror',False)),
        compositor=ex.digest(ex.local('source/relighting/composite.py')),
        implementation=ex.digest(ex.local('source/enhancement/depth_occlusion.py')))
    work=ex.local('work/depth_occlusion')/signature(inputs);out=work/'front_masks'
    if (work/'occlusion.json').is_file():
        saved=ex.read_json(work/'occlusion.json');files=ex.sequence(out,record['frames'])
        if saved['front_hashes']==[ex.digest(p) for p in files]:return work
    out.mkdir(parents=True,exist_ok=True)
    cp_poses=stabilize_poses(cp_poses,record['fps'])
    if prepared:
        with np.load(ex.local(prepared['parts']),allow_pickle=False) as data:
            values,noise,spans=data['values'],data['noise'],data['spans']
    else:
        ap=stabilize_poses(ap,record['fps'])
        values,noise=part_depths(depth,a_paths,ap);values=smooth_parts(values,record['fps'])
        noise=smooth_parts(noise,record['fps'])
    if not np.isfinite(values).any():raise ValueError('A의 신뢰할 수 있는 신체 부위 깊이가 없습니다.')
    mapping=MIRROR if inputs['mirror'] else np.arange(len(PARTS))
    order=Ordering(record['fps']);diagnostics=[];height,width=depth.shape[1:]
    tracker=PartTracker((height,width))
    print('A 깊이 → C 신체 부위 대응 · 깊이 기반 가림 계산',flush=True)
    for i,fp in enumerate(fg_paths):
        fg=cv2.imread(str(fp),-1);t=cp.frame_transform(placement['transform'],i)
        alpha=cp.placed_alpha(fg,(height,width),t)
        b=cv2.imread(str(b_paths[i]),0)>127;overlap=b&(alpha>0)
        y,x=np.where(overlap);pose=cp_poses[i].copy()
        pose[:,:2]=cp.placed_points(pose[:,:2],t)
        fy,fx=np.where(alpha>0)
        tracker.assign(np.column_stack([fx,fy]),pose)
        labels=tracker.labels[y,x]
        z=depth[i,y,x];front=np.zeros((height,width),np.uint8);unknown=int((labels<0).sum())
        if prepared:span=float(spans[i])
        else:
            scene=depth[i,::8,::8];span=max(float(np.percentile(scene,95)-np.percentile(scene,5)),1e-6)
        for part in range(len(PARTS)):
            selected=labels==part;a_part=mapping[part];value=values[i,a_part]
            delta=float(np.median(z[selected])-value) if selected.sum()>=4 and np.isfinite(value) else np.nan
            spread=noise[i,a_part];margin=max(.015*span,.5*spread if np.isfinite(spread) else .015*span)
            state=order.update(part,delta,margin)
            if state==1:front[y[selected],x[selected]]=255
            elif state==0:unknown+=int(selected.sum())
        hard_pixels=int((front>0).sum());front=soften_front(front,overlap)
        if not cv2.imwrite(str(out/f'{i:06d}.png'),front):raise IOError('깊이 가림 마스크 저장 실패')
        diagnostics.append(dict(frame=i,overlap_pixels=int(overlap.sum()),b_front_pixels=int((front>0).sum()),
            hard_front_pixels=hard_pixels,edge_pixels=int(((front>0)&(front<255)).sum()),
            unknown_pixels=unknown,part_order=order.state.tolist(),
            part_pixels=[int((labels==p).sum()) for p in range(len(PARTS))]))
    manifest=dict(inputs=inputs,source=record['source'],front_masks=str(out.relative_to(ex.ROOT)),
        frames=record['frames'],size=record['size'],fps=record['fps'],seconds=time.perf_counter()-started,
        method='video-depth-anything-large-body-part-transfer-v2',depth_file=str(depthfile.relative_to(ex.ROOT)),
        foreground=str(foreground.relative_to(ex.ROOT)),diagnostics=diagnostics,
        active_frames=sum(d['b_front_pixels']>0 for d in diagnostics),
        front_hashes=[ex.digest(p) for p in ex.sequence(out,record['frames'])])
    ex.write_json(work/'occlusion.json',manifest)
    return work
