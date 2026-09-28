"""Original-video GVHMR geometry and joint scale/contact placement for run_depth."""
import hashlib,json,os,subprocess,time,importlib
import cv2,numpy as np
from scipy.optimize import lsq_linear
from scipy.sparse import coo_matrix
from source.util import removal_experiments as ex
from source.relighting import model_test as rel
from source.enhancement import anatomical

METHOD='gvhmr-planted-ground-v7'
GEOMETRY_VERSION='gvhmr-original-geometry-v1'
PYTHON='.model-envs/gvhmr/bin/python'
VENDOR='vendor/GVHMR'
ASSETS=[VENDOR+'/inputs/checkpoints/'+p for p in (
    'gvhmr/gvhmr_siga24_release.ckpt','hmr2/epoch=10-step=25000.ckpt',
    'vitpose/vitpose-h-multi-coco.pth','body_models/smplx/SMPLX_NEUTRAL.npz')]
SUPPORT=[VENDOR+'/hmr4d/utils/body_model/'+p for p in (
    'smplx2smpl_sparse.pt','smpl_coco17_J_regressor.pt','smplx_verts437.pt')]


def missing_backend():
    return [p for p in [PYTHON,VENDOR+'/hmr4d/configs/demo.yaml',*ASSETS,*SUPPORT]
            if not ex.local(p).is_file()]


def require_backend():
    missing=missing_backend()
    if missing:
        raise FileNotFoundError('GVHMR 3D 배치 환경/파일 준비 필요:\n'+'\n'.join(missing)+
            '\n준비 안내: source/enhancement/GEOMETRY_PLACEMENT.md')


def sample(values,indices):
    values=np.asarray(values);indices=np.asarray(indices,float)
    if (not np.isfinite(indices).all() or (indices<0).any() or
            (indices>len(values)-1+1e-6).any()):raise ValueError('기하 시간 경로가 원본 범위를 벗어났습니다.')
    lo=np.floor(indices).astype(int);hi=np.minimum(lo+1,len(values)-1)
    weight=(indices-lo).reshape((-1,)+(1,)*(values.ndim-1))
    return values[lo]*(1-weight)+values[hi]*weight


def validate_track(track,n):
    for name,shape in [('unit_scale',(n,)),('anchor',(n,2)),('feet',(n,2,2)),
                       ('contact',(n,2)),('confidence',(n,))]:
        if np.shape(track[name])!=shape or not np.isfinite(track[name]).all():
            raise ValueError('GVHMR 기하 배열 불일치: '+name)
    if (np.any(track['unit_scale']<=0) or not np.isfinite(track['height']) or track['height']<=0):
        raise ValueError('GVHMR 신체 크기/투영 배율이 유효하지 않습니다.')
    for name in ('confidence','contact'):
        if np.any(track[name]<0) or np.any(track[name]>1):raise ValueError('기하 신뢰도 범위 오류')


def stage_source(source,masks,info,work):
    """Decode in the notebook runtime; only model analysis is normalized to 30 FPS."""
    n=info['frames'];fps=info['fps']
    indices=np.minimum(np.rint(np.arange(int(np.ceil((n-1)*30/fps))+1)*fps/30).astype(int),n-1)
    boxes=[];observed=[]
    for path in masks:
        mask=cv2.imread(str(path),0)
        if mask is None or mask.shape!=tuple(info['size'][::-1]):raise ValueError('GVHMR 원본 마스크 크기 불일치')
        y,x=np.where(mask>127);good=len(x)>=64;observed.append(good)
        boxes.append([x.min(),y.min(),x.max()+1,y.max()+1] if good else [np.nan]*4)
    boxes=np.asarray(boxes,float)
    if not any(observed):raise ValueError('3D 분석할 인물 마스크가 없습니다.')
    for k in range(4):boxes[:,k]=anatomical.fill(boxes[:,k])
    def frames(isolate):
        cap=cv2.VideoCapture(str(source));i=0
        try:
            for frame_index in range(n):
                ok,frame=cap.read()
                if not ok:raise ValueError(f'GVHMR 원본 디코딩 누락: {frame_index}/{n}')
                if isolate:
                    alpha=(cv2.imread(str(masks[frame_index]),0)>127)[:,:,None]
                    frame=np.where(alpha,frame,127).astype(np.uint8)
                while i<len(indices) and indices[i]==frame_index:
                    yield frame;i+=1
        finally:cap.release()
    for isolate,name in [(False,'scene.mp4'),(True,'person.mp4')]:
        ex.encode_frames(frames(isolate),work/name,30.)
    np.savez_compressed(work/'observations.npz',boxes=boxes[indices],observed=np.array(observed)[indices])


def _track(source,masks,gpu):
    source=ex.local(source,exists=True);info,_=rel.video_info(source)
    masks=ex.sequence(masks,info['frames'])
    require_backend()
    inputs=dict(version=GEOMETRY_VERSION,stage_version=1,source=ex.digest(source),masks=[ex.digest(p) for p in masks],
        info=info,worker=ex.digest('source/enhancement/geometry_worker.py'),
        assets=[(p,ex.local(p).stat().st_size,ex.local(p).stat().st_mtime_ns)
                                          for p in ASSETS+SUPPORT])
    inputs=json.loads(json.dumps(inputs))
    key=hashlib.sha256(json.dumps(inputs,sort_keys=True).encode()).hexdigest()
    work=ex.local('work/gvhmr')/key;work.mkdir(parents=True,exist_ok=True)
    output=work/'geometry.npz';receipt=work/'geometry.json'
    if not (output.is_file() and receipt.is_file() and ex.read_json(receipt)['sha256']==ex.digest(output)):
        # Placement-only changes must not rerun the original-video GPU analysis.
        # v1 receipts used an entire-adapter hash; their staging format is version 1.
        for candidate in ex.local('work/gvhmr').glob('*/geometry.json'):
            saved=ex.read_json(candidate);previous=saved['inputs']
            if not all(previous.get(k,1 if k=='stage_version' else None)==v for k,v in inputs.items()):continue
            cached=ex.local(candidate.parent/'geometry.npz')
            if not cached.is_file() or saved['sha256']!=ex.digest(cached):continue
            with np.load(cached,allow_pickle=False) as data:track={k:data[k].copy() for k in data.files}
            validate_track(track,info['frames'])
            return track,cached
        pointer=work/'stage.json';saved=ex.read_json(pointer) if pointer.is_file() else None
        stage=ex.local(saved['directory']) if saved else None
        names=('scene.mp4','person.mp4','observations.npz')
        if stage is None or any(not (stage/name).is_file() or ex.digest(stage/name)!=saved['hashes'][name] for name in names):
            stage=ex.unique_dir(work,'inputs');stage_source(source,masks,info,stage)
            ex.write_json(pointer,dict(directory=str(stage.relative_to(ex.ROOT)),
                                      hashes={name:ex.digest(stage/name) for name in names}))
        config=dict(source=str(source.relative_to(ex.ROOT)),masks=str(masks[0].parent.relative_to(ex.ROOT)),
                    work=str(work.relative_to(ex.ROOT)),stage=str(stage.relative_to(ex.ROOT)),**info)
        ex.write_json(work/'input.json',config)
        env=os.environ.copy();env.update(CUDA_VISIBLE_DEVICES=str(gpu),CUDA_DEVICE_ORDER='PCI_BUS_ID',
            PYTHONDONTWRITEBYTECODE='1',MPLBACKEND='Agg',PYTHONPATH=str(ex.ROOT),
            HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1')
        for key in ('TMPDIR','MPLCONFIGDIR','XDG_CACHE_HOME','XDG_CONFIG_HOME','YOLO_CONFIG_DIR','HF_HOME','TORCH_HOME',
                    'CUDA_CACHE_PATH','NUMBA_CACHE_DIR','TORCH_EXTENSIONS_DIR'):
            path=ex.local('.cache/gvhmr')/key.lower();path.mkdir(parents=True,exist_ok=True);env[key]=str(path)
        print('GVHMR 원본 기하 분석:',source.name,'· 로그:',work/'inference.log',flush=True)
        started=time.perf_counter()
        with (work/'inference.log').open('w') as log:
            # require_backend() validates the resolved path; execute via the venv symlink.
            # 자식 출력을 파일+부모 stdout(Cloud Run 로그) 양쪽에 흘린다(RelightVid tee와 동일). 기존엔 파일만 남아 로딩·진행이 무음이었다.
            proc=subprocess.Popen([str(ex.ROOT/PYTHON),str(ex.local('source/enhancement/geometry_worker.py')),
                str(work/'input.json')],cwd=ex.local(VENDOR),env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            for chunk in iter(proc.stdout.readline,b""):
                line=chunk.decode(errors='replace');log.write(line);print(line,end='',flush=True)
            returncode=proc.wait()
        if returncode:raise RuntimeError((work/'inference.log').read_text(errors='replace')[-6000:])
        with np.load(output,allow_pickle=False) as data:validate_track(data,info['frames'])
        ex.write_json(receipt,dict(inputs=inputs,sha256=ex.digest(output),seconds=time.perf_counter()-started))
    with np.load(output,allow_pickle=False) as data:track={k:data[k].copy() for k in data.files}
    validate_track(track,info['frames'])
    return track,output


def foreground_origin(folder,masks):
    """Recover the fixed crop origin from matching SAM masks, without a margin guess."""
    paths=ex.sequence(folder);masks=ex.sequence(masks,len(paths));offsets=[]
    for fp,mp in zip(paths,masks):
        rgba=cv2.imread(str(fp),-1);mask=cv2.imread(str(mp),0)
        fy,fx=np.where(rgba[:,:,3]>127);by,bx=np.where(mask>127)
        if len(fx) and len(bx):offsets.append([bx.min()-fx.min(),by.min()-fy.min()])
    if not offsets or not np.all(np.array(offsets)==offsets[0]):
        raise ValueError('원본 전경 크롭과 SAM 마스크의 고정 좌표가 일치하지 않습니다.')
    return np.array(offsets[0],float)


def solve(background,foreground,fps,*,mirror=False,preserve_foreground_height=False):
    """Joint least squares in normalized screen coordinates, with a strong 3D size prior."""
    n=len(background['unit_scale'])
    if n<1 or not np.isfinite(fps) or fps<=0:raise ValueError('유효한 프레임/FPS 필요')
    validate_track(background,n);validate_track(foreground,n)
    confidence=np.minimum(background['confidence'],foreground['confidence'])
    valid=confidence>.35
    if not valid.any():raise ValueError('신뢰할 수 있는 원본 3D 기하가 없습니다. 자동 확대를 적용할 수 없습니다.')
    # Preserve C's estimated stature at A's depth; legacy callers match A's height.
    height_factor=1. if preserve_foreground_height else background['height']/foreground['height']
    raw=height_factor*background['unit_scale']/foreground['unit_scale']
    logscale=anatomical.fill(np.where(valid,np.log(raw),np.nan))
    scale=np.exp(anatomical.fit_curve(logscale,fps,weights=confidence*valid,seconds=.12))
    bg=np.array(background['anchor'],float);fg=np.array(foreground['anchor'],float)
    for k in range(2):
        bg[:,k]=anatomical.fit_curve(anatomical.fill(np.where(valid,bg[:,k],np.nan)),fps,weights=confidence*valid,seconds=.12)
        fg[:,k]=anatomical.fit_curve(anatomical.fill(np.where(valid,fg[:,k],np.nan)),fps,weights=confidence*valid,seconds=.12)
    unit=float(np.median(background['height']*background['unit_scale'][valid]))
    root=(bg-scale[:,None]*fg)/unit
    # Unknowns per frame: fractional correction to geometry scale, x/unit, y/unit.
    rr=[];cc=[];vv=[];rhs=[]
    def add(items,target,weight):
        row=len(rhs);factor=np.sqrt(weight)
        for column,value in items:rr.append(row);cc.append(column);vv.append(value*factor)
        rhs.append(target*factor)
    for i in range(n):
        add([(3*i,1.)],1.,400.)
        for k in range(2):
            add([(3*i,scale[i]*fg[i,k]/unit),(3*i+k+1,1.)],bg[i,k]/unit,4.)
    active=0
    for i in range(1,n):
        for foot in range(2):
            other=1-foot if mirror else foot
            contact=min(foreground['contact'][i-1,foot],foreground['contact'][i,foot])
            if contact<.75 or not (valid[i-1] and valid[i]):continue
            ref=min(background['contact'][i-1,other],background['contact'][i,other])
            # Ground anchor motion carries camera/stage travel when A uses another foot.
            delta=(background['feet'][i,other]-background['feet'][i-1,other]
                   if ref>=.75 else bg[i]-bg[i-1])
            for k in range(2):
                add([(3*i,scale[i]*foreground['feet'][i,foot,k]/unit),
                     (3*(i-1),-scale[i-1]*foreground['feet'][i-1,foot,k]/unit),
                     (3*i+k+1,1.),(3*(i-1)+k+1,-1.)],delta[k]/unit,120.*contact)
            active+=1
    # Smooth the correction, not the real scale/motion predicted by geometry.
    for i in range(2,n):
        for k in range(3):
            target=0. if k==0 else root[i,k-1]-2*root[i-1,k-1]+root[i-2,k-1]
            add([(3*i+k,1.),(3*(i-1)+k,-2.),(3*(i-2)+k,1.)],target,(fps*.12)**4)
    matrix=coo_matrix((vv,(rr,cc)),shape=(len(rhs),3*n)).tocsr()
    low=np.full(3*n,-np.inf);high=-low;low[::3]=.99;high[::3]=1.01
    fit=lsq_linear(matrix,np.array(rhs),bounds=(low,high),tol=1e-9,lsmr_tol=1e-10,max_iter=200)
    if not fit.success:raise RuntimeError('3D 크기·접지 공동 배치 계산 실패: '+fit.message)
    values=fit.x.reshape(n,3);scales=scale*values[:,0]
    frames=[dict(x=float(x*unit),y=float(y*unit),scale=float(s)) for s,(_,x,y) in zip(scales,values)]
    return frames,dict(scale_min=float(scales.min()),scale_max=float(scales.max()),
        preserve_foreground_height=bool(preserve_foreground_height),
        estimated_height_ratio=float(foreground['height']/background['height']),
        geometry_valid_frames=int(valid.sum()),contact_constraints=active,
        geometry_scale_adjustment_max_percent=float(np.max(abs(values[:,0]-1))*100),
        background_height=float(background['height']),foreground_height=float(foreground['height']))


def estimate(foreground,background,body_masks,record,*,plan,gpu,preserve_foreground_height=False):
    started=time.perf_counter();require_backend()
    foreground=ex.local(foreground,exists=True);background=ex.local(background,exists=True)
    if ex.local(plan['background'])!=ex.local(record['source']) or ex.digest(record['source'])!=record['source_sha256']:
        raise ValueError('3D 배치의 원본 배경/마스크가 바뀌었습니다.')
    info,_=rel.video_info(background)
    if any(info[k]!=record[k] for k in ('frames','size')) or abs(info['fps']-record['fps'])>1e-4:
        raise ValueError('배경 영상/기하 프레임 또는 FPS 불일치')
    fg_paths=ex.sequence(foreground,record['frames']);bg_paths=ex.validate_masks(body_masks,record)
    kind=next((key for key in ('corrected','baseline') if key in plan and foreground==ex.local(plan[key])),None)
    if kind is None:raise ValueError('현재 춤 계획의 corrected/baseline 전경이 필요합니다.')
    original=ex.local(plan['foreground'],exists=True);masks=original.parent/'masks'
    for path in [ex.local(plan['foreground_video']),*ex.sequence(original)]:
        key=str(path.relative_to(ex.ROOT))
        if plan['input_hashes'].get(key)!=ex.digest(path):raise ValueError('원본 전경이 변경됐습니다. D-2부터 실행하세요.')
    prepared=record.get('prepared_background')
    if prepared:
        bfile=ex.local(prepared['geometry'],exists=True)
        with np.load(bfile,allow_pickle=False) as data:bg={k:data[k].copy() for k in data.files}
        validate_track(bg,record['frames'])
    else:bg,bfile=_track(record['source'],body_masks,gpu)
    fg,ffile=_track(plan['foreground_video'],masks,gpu)
    with np.load(ex.local(plan['work'])/'alignment.npz',allow_pickle=False) as align:
        mapping=align['source_frames' if kind=='corrected' else 'baseline_frames'].copy()
    if mapping.shape!=(record['frames'],) or np.any(np.diff(mapping)<-1e-6):raise ValueError('기하/춤 보정 시간 경로 불일치')
    fg={key:(value if np.ndim(value)==0 else sample(value,mapping)) for key,value in fg.items()}
    origin=foreground_origin(original,masks)
    fg['anchor']-=origin;fg['feet']-=origin
    # Observed joints locate rendered soles and local occlusion, never global scale.
    ap,cp,posefile=anatomical.poses(foreground,body_masks,record)
    mirror=anatomical.pose_mirror(ap,cp)
    frames,stats=solve(bg,fg,record['fps'],mirror=mirror,preserve_foreground_height=preserve_foreground_height)
    from source.enhancement import foot_contact,scene_motion
    from source.enhancement.occlusion import driver_masks
    importlib.reload(foot_contact)
    importlib.reload(scene_motion)
    reference=None
    if prepared:
        with np.load(ex.local(prepared['reference']),allow_pickle=False) as data:
            reference={k:data[k].copy() for k in data.files}
        bf,bv=reference['feet'],reference['feet_valid']
    else:bf,bv=foot_contact.observe(bg_paths,ap,bg)
    ff,fv=foot_contact.observe(fg_paths,cp,fg,alpha=True)
    if prepared:
        bcfile=ex.local(prepared['camera'],exists=True)
        with np.load(bcfile,allow_pickle=False) as data:bc={k:data[k].copy() for k in data.files}
    else:bc,bcfile=scene_motion.track(record['source'],[body_masks,driver_masks(plan,record)])
    # Separately detected people above C's entire crop can belong to an inset
    # demonstration with its own camera. Exclude that band from C's camera fit.
    metadata=original.parent/'metadata.json'
    people=(ex.read_json(metadata).get('automatic_person_detection',{}).get('people',[])
            if metadata.is_file() else [])
    region=None
    if any(person['box'][3]<origin[1] for person in people):
        fg_info,_=rel.video_info(ex.local(plan['foreground_video']))
        region=[0,int(origin[1]),fg_info['size'][0],fg_info['size'][1]]
    fc,fcfile=scene_motion.track(plan['foreground_video'],[masks],region=region)
    fc=dict(matrix=sample(fc['matrix'],mapping),valid=sample(fc['valid'],mapping)>.999)
    frames,stats['contact']=foot_contact.refine(frames,bg,fg,bf,ff,bv,fv,record['fps'],mirror=mirror,
        background_camera=bc,foreground_camera=fc,foreground_origin=origin,foreground_poses=cp,
        background_poses=ap,ground_horizontal=True,background_reference=reference)
    i=len(frames)//2
    transform=dict(frames[i],start=0,trajectory=frames,quality_strength=0.,extra_blur=0.,preserve_detail=True,subpixel=True)
    return dict(method=METHOD,transform=transform,preview_index=i,seconds=time.perf_counter()-started,**stats,
        pose_file=str(posefile.relative_to(ex.ROOT)),foreground=str(foreground.relative_to(ex.ROOT)),
        background=str(background.relative_to(ex.ROOT)),body_masks=str(ex.local(body_masks).relative_to(ex.ROOT)),
        geometry_background=str(bfile.relative_to(ex.ROOT)),geometry_foreground=str(ffile.relative_to(ex.ROOT)),
        camera_background=str(bcfile.relative_to(ex.ROOT)),camera_foreground=str(fcfile.relative_to(ex.ROOT)),
        source_frames=mapping.tolist(),foreground_origin=origin.tolist(),placement_mirror=mirror,
        removed_obj_id=record['selected_obj_id'])
