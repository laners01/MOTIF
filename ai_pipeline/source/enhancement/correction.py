"""Conservative 2D dance alignment using existing foreground pixels and alpha."""
import os
import subprocess
import shutil
from source.enhancement.temporal import smooth_time, sample_pose, FrameSampler, premultiply, unpremultiply
from source.enhancement import temporal
import cv2
import numpy as np
from scipy.ndimage import gaussian_filter1d, median_filter, distance_transform_edt
from source.util import removal_experiments as ex
from source.relighting import model_test as rel

PYTHON='.model-envs/mimicmotion/bin/python'
MIRROR=np.array([0,1,5,6,7,2,3,4,11,12,13,8,9,10,15,14,17,16])
BONES=[(2,3),(3,4),(5,6),(6,7),(8,9),(9,10),(11,12),(12,13)]


def missing():
    paths=[PYTHON,'vendor/MimicMotion/mimicmotion/dwpose/wholebody.py']
    paths += ['vendor/MimicMotion/models/DWPose/'+p for p in ('yolox_l.onnx','dw-ll_ucoco_384.onnx')]
    return [p for p in paths if not ex.local(p).is_file()]


def prepare(foreground,driver,background,driver_id,foreground_video,*,foreground_times=None,render_foreground=None):
    if missing():raise FileNotFoundError('\n'.join(missing()))
    fg=ex.sequence(foreground);drive=ex.sequence(driver)
    info,_=rel.video_info(background);fginfo,_=rel.video_info(foreground_video)
    if len(drive)!=info['frames'] or len(fg)!=fginfo['frames']:
        raise ValueError('원본 영상과 추출 프레임 수가 다릅니다. 추적부터 다시 실행하세요.')
    work=ex.unique_dir('work/dance_enhancement','correction')
    plan=dict(info,mode='correction-v7',work=str(work.relative_to(ex.ROOT)),
              foreground=str(ex.local(foreground).relative_to(ex.ROOT)),
              driver=str(ex.local(driver).relative_to(ex.ROOT)),
              background=str(ex.local(background).relative_to(ex.ROOT)),
              foreground_video=str(ex.local(foreground_video).relative_to(ex.ROOT)),
              driver_id=int(driver_id),foreground_fps=fginfo['fps'])
    plan['input_hashes']={str(p.relative_to(ex.ROOT)):ex.digest(p) for p in fg+drive+[ex.local(background),ex.local(foreground_video)]}
    if foreground_times is not None:
        temporal.source_indices([0.],foreground_times)
        if len(foreground_times)!=len(fg):raise ValueError('전경 원본 시각과 추출 프레임 수 불일치')
        plan['foreground_times']=np.asarray(foreground_times,dtype=float).tolist()
    if render_foreground is not None:
        paths=ex.sequence(render_foreground,len(fg))
        plan['render_foreground']=str(ex.local(render_foreground).relative_to(ex.ROOT))
        plan['input_hashes'].update({str(p.relative_to(ex.ROOT)):ex.digest(p) for p in paths})
    ex.write_json(work/'plan.json',plan)
    return plan


def mirrored(p):
    p=p[:,MIRROR].copy();p[:,:,0]*=-1
    return p


def features(p):
    vectors=np.stack([p[:,b,:2]-p[:,a,:2] for a,b in BONES],1)
    lengths=np.linalg.norm(vectors,axis=2)
    valid=np.stack([(p[:,a,2]>.5)&(p[:,b,2]>.5) for a,b in BONES],1)&(lengths>1e-4)
    return vectors/np.maximum(lengths[:,:,None],1e-6),valid


def align(driver,foreground,fps,*,driver_features=None):
    """Banded DTW: at most 0.6 s local shift, monotone source time, global mirror."""
    n=len(driver)
    if n!=len(foreground) or n<1:raise ValueError('정규화된 자세 프레임 수 불일치')
    band=max(1,round(fps*.6));dv,dm=features(driver) if driver_features is None else driver_features
    results=[]
    for mirror in (False,True):
        fv,fm=features(mirrored(foreground) if mirror else foreground)
        costs={};back={}
        for i in range(n):
            for j in range(max(0,i-band),min(n,i+band+1)):
                valid=dm[i]&fm[j]
                # Missing/ambiguous poses favor preserving original time.
                cost=float(((dv[i]-fv[j])**2).sum(1)[valid].mean()) if valid.sum()>=4 else 2.
                cost+=.08*abs(i-j)/band
                options=[(costs.get((i-1,j-1),float('inf')),0),
                         (costs.get((i-1,j),float('inf'))+.08,1),
                         (costs.get((i,j-1),float('inf'))+.08,2)]
                prev,step=min(options)
                if i==j==0:prev=0
                costs[i,j]=cost+prev;back[i,j]=step
        i=j=n-1;path=[]
        while i>=0 and j>=0:
            path.append((i,j));step=back[i,j]
            if step==0:i-=1;j-=1
            elif step==1:i-=1
            else:j-=1
        sums=np.zeros(n);counts=np.zeros(n)
        for i,j in path:sums[i]+=j;counts[i]+=1
        mapping=sums/counts
        results.append((costs[n-1,n-1]/len(path),mirror,mapping))
    # Require a margin to switch laterality, preserving original choice in ties.
    best=results[1] if results[1][0]<results[0][0]-.03 else results[0]
    path_cost,mirror,mapping=best
    mapping=smooth_time(mapping,fps)
    reference=mirrored(foreground) if mirror else foreground
    fv,fm=features(reference)
    warped,wm=features(sample_pose(reference,mapping))
    # Judge the feasible, speed-limited result against the original on the SAME
    # joints. DTW path length and search penalties are not output pose quality.
    valid=dm&fm&wm
    coverage=float((valid.sum(1)>=4).mean())
    baseline=float(np.mean([((dv[i]-fv[i])**2).sum(1)[valid[i]].mean() for i in range(n) if valid[i].sum()>=4])) if coverage else 2.
    score=float(np.mean([((dv[i]-warped[i])**2).sum(1)[valid[i]].mean() for i in range(n) if valid[i].sum()>=4])) if coverage else 2.
    accepted=coverage>=.6 and score<baseline*.95 and score<.65
    if not accepted:mapping=np.arange(n,dtype=float)
    return mapping,dict(mirror=bool(mirror),timing_applied=bool(accepted),pose_coverage=coverage,
                       alignment_cost=float(score if accepted else baseline),candidate_cost=score,
                       path_cost=float(path_cost),baseline_cost=baseline,
                       max_shift_seconds=float(np.max(abs(mapping-np.arange(n)))/fps))


def stable_targets(source,driver,fps):
    """Small limb rotations with short-gap support and gradual confidence withdrawal."""
    source=source.copy();driver=driver.copy();limit=max(1,round(fps*.2))
    for poses in (source,driver):
        valid=(poses[:,:,2]>.6)&np.isfinite(poses).all(2)
        poses[~valid,2]=0
        torso=np.linalg.norm(poses[:,1,:2]-poses[:,[8,11],:2].mean(1),axis=1)
        body=torso[valid[:,[1,8,11]].all(1)&np.isfinite(torso)&(torso>8)]
        unit=float(np.median(body)) if len(body) else 40.
        for joint in range(18):
            good=np.flatnonzero(valid[:,joint])
            for left,right in zip(good[:-1],good[1:]):
                travel=np.linalg.norm(poses[right,joint,:2]-poses[left,joint,:2])
                if 1<right-left<=limit+1 and travel<=max(8,2*unit*(right-left)/fps):
                    poses[left+1:right,joint,:2]=np.linspace(poses[left,joint,:2],poses[right,joint,:2],right-left+1)[1:-1]
                    poses[left+1:right,joint,2]=max(.61,.9*min(poses[left,joint,2],poses[right,joint,2]))
    out=source[:,:,:2].copy();angles=[]
    view_ok=np.ones(len(source),bool)
    shoulders=[]
    for poses in (source,driver):
        shoulder=poses[:,5,:2]-poses[:,2,:2];shoulders.append(shoulder)
        torso=poses[:,1,:2]-poses[:,[8,11],:2].mean(1)
        known=(poses[:,[1,2,5,8,11],2]>.6).all(1)
        view_ok &= ~known | (np.linalg.norm(shoulder,axis=1)>.2*np.linalg.norm(torso,axis=1))
    known=(source[:,[2,5],2]>.6).all(1)&(driver[:,[2,5],2]>.6).all(1)
    view_ok &= ~known | ((shoulders[0]*shoulders[1]).sum(1)>0)
    for a,b in BONES:
        v=source[:,b,:2]-source[:,a,:2];u=driver[:,b,:2]-driver[:,a,:2]
        confidence=np.minimum.reduce([source[:,a,2],source[:,b,2],driver[:,a,2],driver[:,b,2]])
        valid=view_ok&(confidence>.6)&np.isfinite(v).all(1)&np.isfinite(u).all(1)
        valid &= (np.linalg.norm(v,axis=1)>8)&(np.linalg.norm(u,axis=1)>8)
        delta=np.arctan2(u[:,1],u[:,0])-np.arctan2(v[:,1],v[:,0]);delta=(delta+np.pi)%(2*np.pi)-np.pi
        # Large view/pose differences are outside a local 2D correction's scope.
        valid &= abs(delta)<np.deg2rad(35)
        reliability=np.clip((confidence-.6)/.25,0,1)*np.clip((35-np.rad2deg(abs(delta)))/15,0,1)
        taper=np.ones(len(valid)) if valid.all() else np.clip((distance_transform_edt(valid)-1)/max(1,fps*.15),0,1)
        theta=np.where(valid,np.clip(delta*.35,-np.deg2rad(8),np.deg2rad(8))*reliability,0.)
        theta=gaussian_filter1d(median_filter(theta,size=3,mode='nearest'),max(.5,fps*.06),mode='nearest')
        theta*=taper;theta[~valid]=0;angles.append(theta)
        c,s=np.cos(theta),np.sin(theta)
        rotated=np.column_stack([c*v[:,0]-s*v[:,1],s*v[:,0]+c*v[:,1]])
        out[:,b]=out[:,a]+rotated
    out=np.where(np.isfinite(out),out,source[:,:,:2])
    return source,out,np.array(angles).T


def warp_map(shape,pose,target):
    """Local rigid limb maps; no changing whole-body triangulation or global rejection."""
    h,w=shape[:2]
    yy,xx=np.mgrid[:h,:w].astype(np.float32)
    identity=np.dstack([xx,yy])
    valid=(pose[:,2]>.6)&np.isfinite(pose).all(1)&np.isfinite(target).all(1)
    shift=np.where(valid[:,None],target-pose[:,:2],0.)
    limit=min(h,w)*.015;norm=np.linalg.norm(shift,axis=1)
    target=pose[:,:2]+shift*np.minimum(1,limit/np.maximum(norm,1e-6))[:,None]
    displacement=np.zeros_like(identity);weights=np.zeros((h,w),np.float32)
    for a,b in BONES:
        if not (valid[a] and valid[b]) or max(norm[a],norm[b])<.05:continue
        src=pose[[a,b],:2];dst=target[[a,b]];v=src[1]-src[0];u=dst[1]-dst[0]
        length=np.linalg.norm(u)
        if min(length,np.linalg.norm(v))<8:continue
        theta=np.arctan2(v[1],v[0])-np.arctan2(u[1],u[0]);c,s=np.cos(theta),np.sin(theta)
        radius=max(limit*4,min(np.linalg.norm(v)*.28,min(h,w)*.09))
        lo=np.maximum(np.floor(dst.min(0)-radius).astype(int),0);hi=np.minimum(np.ceil(dst.max(0)+radius).astype(int)+1,[w,h])
        x0,y0=lo;x1,y1=hi
        if x1<=x0 or y1<=y0:continue
        grid=identity[y0:y1,x0:x1];offset=grid-dst[0]
        t=np.clip((offset@u)/length**2,0,1);distance=np.linalg.norm(offset-t[:,:,None]*u,axis=2)/radius
        weight=np.maximum(0,1-distance**2)**2
        mapped=np.stack([c*offset[:,:,0]-s*offset[:,:,1],s*offset[:,:,0]+c*offset[:,:,1]],axis=2)+src[0]
        displacement[y0:y1,x0:x1]+=(mapped-grid)*weight[:,:,None]
        weights[y0:y1,x0:x1]+=weight
    displacement/=np.maximum(1,weights)[:,:,None]
    # Keep the head and torso interior unchanged, even when an arm crosses them.
    protected=np.zeros((h,w),np.uint8)
    if valid[[2,5,8,11]].all():
        torso=pose[[2,5,11,8],:2];middle=torso.mean(0)
        cv2.fillConvexPoly(protected,cv2.convexHull(np.rint(middle+(torso-middle)*.85).astype(np.int32)),255)
    if valid[0] and valid[1]:
        r=max(4,int(np.linalg.norm(pose[0,:2]-pose[1,:2])*.8))
        cv2.circle(protected,tuple(np.rint(pose[0,:2]).astype(int)),r,255,-1)
    outside=cv2.distanceTransform(255-protected,cv2.DIST_L2,3)
    displacement*=np.minimum(1,outside/max(2,limit*4))[:,:,None]
    magnitude=np.linalg.norm(displacement,axis=2)
    displacement*=np.minimum(1,limit/np.maximum(magnitude,1e-6))[:,:,None]
    return identity+displacement


def warp(bgra,pose,target):
    xy=warp_map(bgra.shape,pose,target)
    return unpremultiply(cv2.remap(premultiply(bgra),xy[:,:,0],xy[:,:,1],cv2.INTER_LINEAR,
                                  borderMode=cv2.BORDER_CONSTANT))


def _render_frames(render,work,indices,base,selected,sampled_pose,offsets,n,*,cv_threads=None):
    """apply() 프레임 루프 본문 그대로 — 연속 구간 indices만 렌더. 출력은 프레임 i의 순수 함수(분배와 무관하게 바이트 동일)."""
    if cv_threads is not None:cv2.setNumThreads(cv_threads)   # 병렬 워커: OpenCV 내부 스레드 과다구독 방지
    baseline_sampler=FrameSampler(render);corrected_sampler=FrameSampler(render)
    for k,i in enumerate(indices):
        baseline=baseline_sampler.sample(base[k])
        if not cv2.imwrite(str(work/'baseline'/f'{i:06d}.png'),baseline):raise IOError('원본 FPS 변환 저장 실패')
        shape=baseline.shape
        joint_map=warp_map(shape,sampled_pose[k],sampled_pose[k,:,:2]+offsets[k])
        result=corrected_sampler.sample(selected[k],coordinate_map=joint_map)
        if not cv2.imwrite(str(work/'corrected'/f'{i:06d}.png'),result):raise IOError('보정 프레임 저장 실패')
        if cv_threads is None and i%30==0:print(f'프레임 보정 {i+1}/{n}',flush=True)
    return len(indices)


def _render_all(render,work,base,selected,sampled_pose,offsets,n):
    """프레임 루프를 프로세스 병렬로 분배한다. Farneback(temporal.FrameSampler)이 단일 스레드라 21 vCPU 중 ~20개가 놀던 구간.

    MOTIF_CORRECTION_WORKERS=1 이면 기존 직렬 경로 그대로(롤백·A/B). 연속 청크라 FrameSampler의 인접쌍 캐시도 유지된다.
    """
    base=np.asarray(base);selected=np.asarray(selected)
    workers=int(os.environ.get('MOTIF_CORRECTION_WORKERS') or 0) or max(1,min(18,(os.cpu_count() or 2)-1))
    if workers<=1 or n<2*workers:
        _render_frames(render,work,list(range(n)),base,selected,sampled_pose,offsets,n);return
    import multiprocessing as mp
    from concurrent.futures import ProcessPoolExecutor,as_completed
    for key in ('OMP_NUM_THREADS','OPENBLAS_NUM_THREADS','MKL_NUM_THREADS'):os.environ.setdefault(key,'1')  # 자식 BLAS 과다구독 방지
    chunks=[chunk.tolist() for chunk in np.array_split(np.arange(n),workers)]
    done=0
    # spawn: 부모(correction_worker)가 ORT CUDA 세션을 들고 있어 fork는 데드락 위험.
    with ProcessPoolExecutor(max_workers=workers,mp_context=mp.get_context('spawn')) as pool:
        futures=[pool.submit(_render_frames,render,work,chunk,base[chunk],selected[chunk],sampled_pose[chunk],offsets[chunk],n,cv_threads=1)
                 for chunk in chunks]
        for future in as_completed(futures):
            done+=future.result();print(f'프레임 보정 {done}/{n}',flush=True)


def apply(plan,fgpose,driverpose):
    work=ex.local(plan['work']);fg=ex.sequence(plan['foreground']);n=plan['frames']
    native=plan.get('foreground_times')
    if native is not None and len(native)!=len(fg):raise ValueError('전경 원본 시각과 프레임 수 불일치')
    def indices(output_frames):
        if native is not None:return temporal.source_indices(np.asarray(output_frames)/plan['fps'],native)
        return np.minimum(np.asarray(output_frames)*plan['foreground_fps']/plan['fps'],len(fg)-1)
    # Fractional source times retain sub-frame timing, including FPS conversion.
    base=indices(np.arange(n))
    # Evaluate the same fractional source times that the renderer will use.
    evaluation_pose=sample_pose(fgpose,base)
    cached_features=None
    if plan.get('driver_features'):
        with np.load(ex.local(plan['driver_features']),allow_pickle=False) as data:
            cached_features=(data['vectors'],data['valid'])
    mapping,stats=align(driverpose,evaluation_pose,plan['fps'],driver_features=cached_features)
    selected=indices(mapping)
    sampled_pose=sample_pose(fgpose,selected)
    desired=mirrored(driverpose) if stats['mirror'] else driverpose.copy()
    sampled_pose,target,angles=stable_targets(sampled_pose,desired,plan['fps'])
    offsets=target-sampled_pose[:,:,:2]
    if stats['pose_coverage']<.6 or stats['alignment_cost']>=.65:offsets[:]=0;angles[:]=0
    for name in ('baseline','corrected'):(work/name).mkdir(exist_ok=True)
    render=ex.sequence(plan['render_foreground'],len(fg)) if 'render_foreground' in plan else fg
    _render_all(render,work,base,selected,sampled_pose,offsets,n)
    speeds=np.diff(mapping)
    stats.update(temporal_version=7 if native is not None else 6,joint_version=6,
                 interpolation='native-timestamps-single-remap-rgba' if native is not None else 'nearest-source-joint-preserving-single-remap-rgba',
                 joint_max_degrees=float(np.rad2deg(abs(angles)).max(initial=0)),
                 min_speed=float(speeds.min()) if len(speeds) else 1.,
                 max_speed=float(speeds.max()) if len(speeds) else 1.,
                 interpolated_frames=int(np.sum(abs(selected-np.rint(selected))>1e-6)),
                 held_tail_frames=int(np.sum(mapping/plan['fps']>native[-1]+1e-6)) if native is not None
                                  else int(np.sum(mapping*plan['foreground_fps']/plan['fps']>len(fg)-1)))
    np.savez_compressed(work/'alignment.npz',source_frames=selected,baseline_frames=base,offsets=offsets)
    plan.update(stats,corrected=str((work/'corrected').relative_to(ex.ROOT)),baseline=str((work/'baseline').relative_to(ex.ROOT)))
    ex.write_json(work/'plan.json',plan)
    return work/'corrected'


def run(plan,gpu=0):
    caller_plan=plan
    if plan.get('mode') not in ('correction-v1','correction-v2','correction-v3','correction-v4','correction-v5','correction-v6','correction-v7'):raise RuntimeError('이전 생성용 입력입니다. 변경된 D-2부터 다시 실행하세요.')
    work=ex.local(plan['work'],exists=True)
    for p,digest in plan['input_hashes'].items():
        if ex.digest(ex.local(p,exists=True))!=digest:raise ValueError('입력이 변경되었습니다. D-2부터 실행하세요.')
    if plan['mode']!='correction-v7':
        old_work=work;work=ex.unique_dir('work/dance_enhancement','correction')
        plan=dict(plan,mode='correction-v7',work=str(work.relative_to(ex.ROOT)))
        for key in ('corrected','baseline'):plan.pop(key,None)
        for name in ('foreground_pose.npy','driver_pose.npy'):
            if (old_work/name).is_file():shutil.copyfile(old_work/name,work/name)
        ex.write_json(work/'plan.json',plan)
    env=os.environ.copy()
    env.update(MPLBACKEND='Agg',PYTHONDONTWRITEBYTECODE='1',PYTHONPATH=str(ex.ROOT),  # DWPose GPU 사용: CUDA_VISIBLE_DEVICES='' 제거
               HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',TMPDIR=str(work))
    for key in ('MPLCONFIGDIR','XDG_CACHE_HOME','HF_HOME','TORCH_HOME'):
        p=ex.local('.cache/dance_enhancement/'+key.lower());p.mkdir(parents=True,exist_ok=True);env[key]=str(p)
    ex.local(PYTHON,exists=True)
    with (work/'correction.log').open('w') as log:
        print('관절 분석·춤 보정 중 · 로그:',work/'correction.log',flush=True)
        # 자식 출력을 파일+부모 stdout(Cloud Run 로그) 양쪽에 흘린다(RelightVid tee와 동일). 기존엔 파일만 남아 로딩·진행이 무음이었다.
        proc=subprocess.Popen([str(ex.ROOT/PYTHON),str(ex.local('source/enhancement/correction_worker.py')),str(work/'plan.json')],cwd=ex.ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        for chunk in iter(proc.stdout.readline,b""):
            line=chunk.decode(errors='replace');log.write(line);print(line,end='',flush=True)
        returncode=proc.wait()
    if returncode:raise RuntimeError((work/'correction.log').read_text(errors='replace')[-7000:])
    ex.sequence(work/'corrected',plan['frames'])
    caller_plan.update(ex.read_json(work/'plan.json'))
    return work/'corrected'


def side_by_side(before,after,output,*,audio_source=None):
    before=ex.local(before,exists=True);after=ex.local(after,exists=True);output=ex.local(output)
    a,_=rel.video_info(before);b,_=rel.video_info(after)
    if a['frames']!=b['frames'] or abs(a['fps']-b['fps'])>1e-4 or a['size']!=b['size']:
        raise ValueError('비교 영상의 프레임 수/FPS/크기가 다릅니다.')
    def frames():
        left=cv2.VideoCapture(str(before));right=cv2.VideoCapture(str(after))
        try:
            for _ in range(a['frames']):
                ok1,im1=left.read();ok2,im2=right.read()
                if not ok1 or not ok2:raise ValueError('비교 영상 디코딩 실패')
                yield np.concatenate([im1,im2],axis=1)
        finally:left.release();right.release()
    ex.encode_frames(frames(),output,a['fps'],audio_source=after if audio_source is None else audio_source)
    return output
