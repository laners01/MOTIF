"""Shared implementation for run_pre/run_post. Models are loaded only in workers."""
import contextlib
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:sys.path.insert(0,str(ROOT))
import cv2
import numpy as np
from source.util import removal_experiments as ex
from source.relighting import model_test as rel

PYTHON = sys.executable
LIBRARY = 'output/background_precomputed'
MISSING = '준비된 값이 없습니다'
MODEL = 'rfdetr_s_sam2.1_large'
VERSION = 1


def relative(path):
    return str(ex.local(path).relative_to(ROOT))


def videos():
    return sorted(relative(p) for p in ex.local('video').rglob('*')
                  if p.is_file() and p.suffix.lower() in ('.mp4','.mov','.mkv','.webm'))


def default_gpu():
    try:
        count=len(subprocess.check_output(['nvidia-smi','-L'],text=True).strip().splitlines())
    except (OSError,subprocess.SubprocessError):count=0
    return 1 if count>=2 else 0


def fingerprint():
    """Changing algorithms or local model files makes old assets unavailable."""
    sources=[Path(__file__),ROOT/'models/registry.json',ROOT/'music_sync/core.py']
    for folder in ('source/enhancement','source/relighting','source/inpainting','source/service'):
        sources.extend(p for p in ex.local(folder).glob('*.py') if not p.name.startswith('test_'))
    sources.extend(ex.local('source/util')/p for p in ('pipeline.py','music_sync.py','removal_experiments.py'))
    sources.extend(ex.local(p) for p in (
        'vendor/RelightVid/configs/inference_fbc.yaml',
        'vendor/RelightVid/pipelines/diffusion.py','vendor/RelightVid/pipelines/relightvid.py',
        'vendor/Video-Depth-Anything/video_depth_anything/video_depth.py'))
    values={relative(p):ex.digest(p) for p in sorted(set(sources))}
    from source.enhancement import geometry_placement as gp, depth_occlusion as depth
    from source.relighting.variants import SPECS
    registry=ex.read_json('models/registry.json')
    weights=[row['checkpoint'] for row in registry if row['id']==MODEL]
    weights+=['models/rfdetr/rf-detr-small.pth',depth.CHECKPOINT,*gp.ASSETS,*gp.SUPPORT]
    weights+=['vendor/svor/'+p for p in ex.REQUIRED['svor']]
    weights+=['vendor/MimicMotion/models/DWPose/'+p for p in ('yolox_l.onnx','dw-ll_ucoco_384.onnx')]
    weights+=[SPECS['RelightVid']['vendor']+'/'+p for p in SPECS['RelightVid']['required']]
    for p in weights:
        path=ex.local(p)
        values[p]=[path.stat().st_size,path.stat().st_mtime_ns] if path.is_file() else None
    return hashlib.sha256(json.dumps([VERSION,values],sort_keys=True).encode()).hexdigest()


def prepared(background, *, remove_id=None, driver_id=None, deep=False):
    """Read-only availability check; never starts background inference."""
    source=ex.local(background)
    if not source.is_file():return None
    key=ex.digest(source)
    pointer=ex.local(LIBRARY)/key/'current.json'
    if not pointer.is_file():return None
    try:
        manifest=ex.read_json(pointer)
        path=ex.local(manifest['manifest'],exists=True)
        if ex.digest(path)!=manifest['sha256']:return None
        data=ex.read_json(path)
        if data['status']!='complete' or data['source_sha256']!=key:return None
        # 지문 게이트: 사전계산이 현재 코드·가중치와 동일 런타임에서 만들어졌는지 검증.
        # MOTIF_SKIP_FINGERPRINT=1이면 스킵(외부/타버전 패키지 소비 경로 테스트 전용, 기본 off).
        if data['implementation']!=fingerprint() and os.environ.get('MOTIF_SKIP_FINGERPRINT')!='1':return None
        if remove_id is not None and str(int(remove_id)) not in data['people']:return None
        if driver_id is not None:
            if str(int(driver_id)) not in data['people'] or int(driver_id)==int(remove_id):return None
        for name,receipt in data['files'].items():
            file=ex.local(name,exists=True);stat=file.stat()
            if [stat.st_size,stat.st_mtime_ns]!=receipt['stat']:return None
            if deep and ex.digest(file)!=receipt['sha256']:return None
        return data
    except (KeyError,ValueError,OSError,TypeError):return None


def environment(gpu,work):
    env=os.environ.copy()
    env.update(CUDA_VISIBLE_DEVICES=str(int(gpu)),CUDA_DEVICE_ORDER='PCI_BUS_ID',
        PYTHONPATH=str(ROOT),PYTHONDONTWRITEBYTECODE='1',MPLBACKEND='Agg',
        HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',WANDB_MODE='disabled',PYTHONUNBUFFERED='1')
    for key in ('XDG_CACHE_HOME','XDG_CONFIG_HOME','HF_HOME','TORCH_HOME','MPLCONFIGDIR',
                'NUMBA_CACHE_DIR','TRITON_CACHE_DIR','TORCH_EXTENSIONS_DIR','CUDA_CACHE_PATH',
                'RF_HOME','INFERENCE_HOME','HF_HUB_CACHE','YOLO_CONFIG_DIR'):
        folder=ex.local('.cache/prepost')/key.lower();folder.mkdir(parents=True,exist_ok=True);env[key]=str(folder)
    env['TMPDIR']=str(ex.local(work))
    weight=ex.local('models/rfdetr/rf-detr-small.pth',exists=True)
    cache=ex.local(env['RF_HOME'])/weight.name
    if not cache.exists():cache.symlink_to(weight)
    ex.local(cache,exists=True)
    return env


def launch(mode,config,*,python=PYTHON):
    work=ex.local(config['work'],exists=True);cfg=work/(mode+'_input.json')
    ex.write_json(cfg,config)
    script=Path(__file__) if mode in ('pre','post') else ROOT/'source/service'/f'{mode}_worker.py'
    if python!=PYTHON:ex.local(python,exists=True)
    command=[str(ROOT/python) if python!=PYTHON else PYTHON,str(script),mode,str(cfg)]
    log_path=work/(mode+'.log')
    print('실행 로그:',log_path,flush=True)
    own_group=mode in ('pre','post')
    with log_path.open('w') as log:
        proc=subprocess.Popen(command,cwd=ROOT,env=environment(config['gpu'],work),
            stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1,start_new_session=own_group)
        try:
            for line in proc.stdout:
                log.write(line);log.flush()
                if not line.startswith(('\r','  ')):print(line.rstrip(),flush=True)
            if proc.wait():raise RuntimeError(f'{mode} 처리 실패: {log_path}\n'+log_path.read_text(errors='replace')[-5000:])
        except BaseException:
            if proc.poll() is None:
                if own_group:os.killpg(proc.pid,signal.SIGTERM)
                else:proc.terminate()
                proc.wait()
            raise
        finally:proc.stdout.close()


def run_pre(background,*,gpu=1,output_dir=None):
    destination=ex.local(output_dir) if output_dir is not None else None
    ready=prepared(background)
    if ready and (destination is None or ex.local(ready['directory']).is_relative_to(destination)):
        print('이미 준비되어 있습니다:',ready['manifest']);return ready
    work=ex.unique_dir('work/run_pre','session')
    config=dict(background=relative(background),gpu=int(gpu),work=relative(work))
    if destination is not None:config['output_dir']=relative(destination)
    launch('pre',config)
    ready=prepared(background)
    if ready is None:raise RuntimeError('준비 결과 검증 실패: '+str(work/'pre.log'))
    return ready


def driver_for(asset,remove_id):
    remaining=sorted(int(key) for key in asset['people'] if int(key)!=int(remove_id))
    if not remaining:raise ValueError('남아 있는 기준 춤 인물이 없습니다. 두 명 이상인 배경을 선택하세요.')
    return remaining[0]


def run_post(background,foreground,*,remove_id,driver_id=None,foreground_id=None,correction_enabled=True,gpu=1):
    started=time.perf_counter()
    ready=prepared(background,remove_id=remove_id,driver_id=driver_id)
    if ready is None:print(MISSING);return None
    if driver_id is None:driver_id=driver_for(ready,remove_id)
    work=ex.unique_dir('work/run_post','session');out=ex.unique_dir('output/run_post','session')
    config=dict(background=relative(background),foreground=relative(foreground),manifest=ready['manifest'],
                remove_id=int(remove_id),driver_id=int(driver_id),correction_enabled=bool(correction_enabled),
                gpu=int(gpu),work=relative(work),output=relative(out))
    if foreground_id is not None:config["foreground_id"]=int(foreground_id)
    launch('post',config)
    result=ex.read_json(out/'result.json')
    result['total_seconds']=time.perf_counter()-started
    result['timer_scope']='run_post 호출 시작부터 검증·worker 시작·모델 로딩·최종 영상 저장/검사까지; 파일 업로드·선택·플레이어 표시는 제외'
    ex.write_json(out/'result.json',result)
    print(f"전체 처리 시간: {result['total_seconds']:.3f}초 ({result['total_seconds']/60:.2f}분)")
    return result


@contextlib.contextmanager
def stage(timings,name):
    print('\n처리:',name,flush=True);started=time.perf_counter()
    try:yield
    finally:
        timings[name]=time.perf_counter()-started
        print(f'{name}: {timings[name]:.3f}초',flush=True)


def link(source,destination):
    source=ex.local(source,exists=True);destination=ex.local(destination)
    destination.parent.mkdir(parents=True,exist_ok=True)
    try:os.link(source,destination)
    except OSError:shutil.copyfile(source,destination)


def sequence_view(source,destination,first,stop):
    paths=ex.sequence(source)
    if not 0<=first<stop<=len(paths):raise ValueError('준비된 프레임 구간이 올바르지 않습니다.')
    destination=ex.local(destination);destination.mkdir(parents=True,exist_ok=False)
    for i,path in enumerate(paths[first:stop]):link(path,destination/f'{i:06d}{path.suffix}')
    return destination


def video_frames(source):
    cap=cv2.VideoCapture(str(ex.local(source,exists=True)))
    try:
        while True:
            ok,frame=cap.read()
            if not ok:break
            yield frame
    finally:cap.release()


def decode(source,folder):
    folder=ex.local(folder);folder.mkdir(parents=True,exist_ok=False)
    count=0
    for count,frame in enumerate(video_frames(source),1):
        if not cv2.imwrite(str(folder/f'{count-1:06d}.png'),frame,[cv2.IMWRITE_PNG_COMPRESSION,1]):raise IOError('프레임 저장 실패')
    return count


def arrays(path):
    with np.load(ex.local(path),allow_pickle=False) as data:return {k:data[k].copy() for k in data.files}


def slice_arrays(source,destination,first,stop):
    data=arrays(source)
    np.savez_compressed(ex.local(destination),**{k:v[first:stop] if v.ndim else v for k,v in data.items()})


def precompute(cfg):
    from scipy.ndimage import binary_dilation
    from music_sync import core
    from source.util import pipeline,music_sync
    from source.enhancement import correction as dance,geometry_placement as gp,anatomical,foot_contact,scene_motion,depth_occlusion as depth,preservation
    from source.relighting import variants,composite as cp
    variants.require_backend('RelightVid');gp.require_backend()
    missing=dance.missing()+depth.missing_backend()
    missing+=next(row['missing'] for row in ex.backend_status() if row['model']=='svor')
    if missing:raise FileNotFoundError('\n'.join(missing))
    source=ex.local(cfg['background'],exists=True);key=ex.digest(source)
    destination=ex.local(cfg['output_dir']) if cfg.get('output_dir') else ex.local(LIBRARY)/key
    folder=ex.unique_dir(destination,'asset');work=ex.local(cfg['work']);timings={}
    asset=dict(version=VERSION,status='building',source=relative(source),source_sha256=key,
               implementation=fingerprint(),directory=relative(folder),manifest=relative(folder/'manifest.json'),people={})
    ex.write_json(folder/'manifest.json',asset)
    with stage(timings,'배경 기준 영상·시간·음악'):
        original_info,_=rel.video_info(source)
        master=folder/'original.mp4'
        stream=video_frames(source)
        try:ex.encode_frames(stream,master,original_info['fps'],audio_source=source)
        finally:stream.close()
        info,_=rel.video_info(master)
        if info['frames']!=original_info['frames'] or abs(info['fps']-original_info['fps'])>1e-4:
            raise ValueError('기준 영상 프레임/FPS 보존 실패')
        frame_dir=folder/'frames'
        if decode(master,frame_dir)!=info['frames']:raise ValueError('기준 영상 실제 프레임 수 불일치')
        np.save(folder/'times.npy',np.arange(info['frames'])/info['fps'])
        np.save(folder/'source_times.npy',music_sync.frame_times(source))
        record=dict(info,source=relative(master),source_sha256=ex.digest(master),original_fps=info['fps'],frame_dir=relative(frame_dir),work=relative(work))
        asset.update(record=record,times=relative(folder/'times.npy'),music=None)
        try:
            audio,duration=core.read_video(master)
            np.savez_compressed(folder/'music.npz',audio=audio,features=core.features(audio),duration=duration)
            asset['music']=relative(folder/'music.npz')
        except core.MatchError as error:asset['music_unavailable']=str(error)
    with stage(timings,'배경 모든 인물 탐색·추적'):
        pipeline.WORK_DIR=work/'tracking'
        print(pipeline.load_video(relative(master),MODEL),flush=True)
        ids=list(pipeline.SESSION.object_ids)
        if not ids:raise ValueError('배경에서 추적할 사람을 찾지 못했습니다.')
        if pipeline.SESSION.detector_preview is not None:
            cv2.imwrite(str(folder/'people.jpg'),cv2.cvtColor(pipeline.SESSION.detector_preview,cv2.COLOR_RGB2BGR))
            asset['preview']=relative(folder/'people.jpg')
        for person_id in ids:
            person=folder/'people'/str(person_id);person.mkdir(parents=True)
            pipeline.OUTPUT_DIR=person/'tracking'
            print(pipeline.select_object(person_id),flush=True)
            pipeline.track_video()
            tracking=pipeline.SESSION.result_dir
            crop,bbox,_=pipeline.export_isolated_crop(margin=.12)
            sequence_view(tracking/'masks',person/'masks',0,info['frames'])
            sequence_view(crop,person/'crop',0,info['frames'])
            link(tracking/'metadata.json',person/'metadata.json')
            masks=person/'removal_masks';masks.mkdir()
            for i,path in enumerate(ex.sequence(person/'masks')):
                mask=binary_dilation(cv2.imread(str(path),0)>127,iterations=8).astype(np.uint8)*255
                if not cv2.imwrite(str(masks/f'{i:06d}.png'),mask):raise IOError('제거 마스크 저장 실패')
            ex.write_json(masks/'mask_manifest.json',ex.mask_manifest(record,selected_obj_id=int(person_id),dilation_iterations=8,dilation_structure='scipy default cross'))
            asset['people'][str(person_id)]=dict(id=int(person_id),directory=relative(person),masks=relative(person/'masks'),
                crop=relative(person/'crop'),bbox=bbox,removal_masks=relative(masks))
        pipeline.release_predictor()
    with stage(timings,'배경 관절·기준 춤'):
        launch('pose',dict(gpu=cfg['gpu'],work=relative(work),frames=relative(frame_dir),people=list(asset['people'].values())),python=dance.PYTHON)
        for person in asset['people'].values():
            directory=ex.local(person['directory'])
            for name in ('driver_pose','pose'):person[name]=relative(directory/(name+'.npy'))
            vectors,valid=dance.features(np.load(ex.local(person['driver_pose']),allow_pickle=False))
            np.savez_compressed(directory/'features.npz',vectors=vectors,valid=valid)
            person['features']=relative(directory/'features.npz')
    with stage(timings,'배경 카메라·장면 깊이'):
        camera,camera_path=scene_motion.track(master,[p['masks'] for p in asset['people'].values()])
        link(camera_path,folder/'camera.npz');asset['camera']=relative(folder/'camera.npz')
        z,zpath=depth.scene_depth(record,cfg['gpu'])
        link(zpath,folder/'inverse_depth.npy');asset['depth']=relative(folder/'inverse_depth.npy')
        asset['depth_sha256']=ex.digest(folder/'inverse_depth.npy')
        spans=np.array([max(float(np.percentile(im[::8,::8],95)-np.percentile(im[::8,::8],5)),1e-6) for im in z])
    for person_id,person in asset['people'].items():
        directory=ex.local(person['directory']);masks=ex.sequence(person['masks'],info['frames'])
        poses=np.load(ex.local(person['pose']),allow_pickle=False)
        with stage(timings,f'인물 {person_id} 기하·접지·부위 깊이'):
            geometry,path=gp._track(master,person['masks'],cfg['gpu'])
            link(path,directory/'geometry.npz');person['geometry']=relative(directory/'geometry.npz')
            feet,valid=foot_contact.observe(masks,poses,geometry)
            gy,sy=foot_contact.ground(feet,geometry,info['fps'],camera=camera)
            gx,sx=foot_contact.ground(foot_contact.ankle_reference(feet,poses,geometry),geometry,info['fps'],camera=camera,axis=0)
            np.savez_compressed(directory/'reference.npz',feet=feet,feet_valid=valid,ground_y=gy,support_y=sy,ground_x=gx,support_x=sx)
            person['reference']=relative(directory/'reference.npz')
            values,noise=depth.part_depths(z,masks,depth.stabilize_poses(poses,info['fps']))
            values=depth.smooth_parts(values,info['fps']);noise=depth.smooth_parts(noise,info['fps'])
            if not np.isfinite(values).any():raise ValueError(f'인물 {person_id}의 신체 깊이 분석 실패')
            np.savez_compressed(directory/'parts.npz',values=values,noise=noise,spans=spans)
            person['parts']=relative(directory/'parts.npz')
        with stage(timings,f'인물 {person_id} SVOR·남은 인물 화질 복원'):
            removal_work=ex.unique_dir(work,'removal')
            removal=dict(record,work=relative(removal_work),selected_obj_id=int(person_id))
            raw=ex.run_backend('svor',removal,person['removal_masks'],label='run_pre_svor',steps=3,seed=43,
                               memory_mode='model_full_load',chunk_frames=65,chunk_overlap=16)  # ponytail: RTX PRO 6000 96GB 전제로 offload 제거(속도↑). OOM 시 model_cpu_offload로 복귀
            keep=directory/'keep_masks';keep.mkdir()
            others=[ex.sequence(p['masks']) for key,p in asset['people'].items() if key!=person_id]
            for i in range(info['frames']):
                mask=np.zeros(info['size'][::-1],np.uint8)
                for paths in others:mask|=cv2.imread(str(paths[i]),0)
                cv2.imwrite(str(keep/f'{i:06d}.png'),mask)
            ready=preservation.prepare(master,raw,person['removal_masks'],record,keep_masks=keep)
            link(ready,directory/'background.mp4');person['background']=relative(directory/'background.mp4')
            link(raw.parent/'experiment.json',directory/'svor_experiment.json')
            person['svor_record']=relative(directory/'svor_experiment.json')
            bgframes=directory/'background_frames';decode(ready,bgframes)
            person['background_frames']=relative(bgframes)
            condition=directory/'condition';condition.mkdir();grain=[]
            for i,path in enumerate(ex.sequence(bgframes,info['frames'])):
                image=cv2.imread(str(path));grain.append(cp._grain_sigma(image))
                small,rect=variants.letterbox(image,variants.SPECS['RelightVid']['size'])
                if not cv2.imwrite(str(condition/f'{i:06d}.png'),small):raise IOError('조명 조건 저장 실패')
            np.save(directory/'grain.npy',np.array(grain,np.float32))
            person.update(condition_frames=relative(condition),grain=relative(directory/'grain.npy'),rect=rect,
                          latents=relative(directory/'background_latents.npy'))
    with stage(timings,'RelightVid 배경 VAE·고정 문장'):
        asset['text']=relative(folder/'text.npz')
        launch('light',dict(gpu=cfg['gpu'],work=relative(work),people=list(asset['people'].values()),
                            text=asset['text']),python='.model-envs/relightvid/bin/python')
    asset['timings']=timings
    files={}
    for path in sorted(folder.rglob('*')):
        if path.is_file() and path.name!='manifest.json':
            files[relative(path)]=dict(stat=[path.stat().st_size,path.stat().st_mtime_ns],sha256=ex.digest(path))
    asset.update(status='complete',files=files)
    if asset['implementation']!=fingerprint():raise RuntimeError('준비 중 코드/모델이 바뀌었습니다. 다시 실행하세요.')
    ex.write_json(folder/'manifest.json',asset)
    pointer=ex.local(LIBRARY)/key/'current.json';pointer.parent.mkdir(parents=True,exist_ok=True)
    temporary=folder/'current.pending.json'
    ex.write_json(temporary,dict(manifest=asset['manifest'],sha256=ex.digest(folder/'manifest.json')))
    temporary.replace(pointer)
    if pointer.parent!=folder.parent:
        ex.write_json(folder.parent/'current.json',ex.read_json(pointer))
    print('배경 준비 완료:',asset['manifest'],flush=True)


def match_segment(asset,foreground):
    """Snap once to the canonical background grid and preserve the audio offset."""
    from music_sync import core
    record=asset['record'];fps=record['fps'];n=record['frames']
    try:
        if asset['music'] is None:raise core.MatchError(asset.get('music_unavailable','배경 음악 없음'))
        saved=arrays(asset['music']);audio,duration=core.read_video(ex.local(foreground))
        offset,score=core.find_offset(audio,saved['audio'],background_features=saved['features'])
        first=max(0,math.ceil(max(0.,-offset)*fps-1e-8))
        foreground_start=first/fps+offset
        length=math.floor(min(duration-foreground_start,float(saved['duration'])-first/fps)*fps+1e-7)
        stop=min(n,first+length)
        if stop-first<fps:raise core.MatchError('음악 공통 구간이 1초보다 짧습니다.')
        return dict(status='matched',first=first,stop=stop,foreground_start=max(0.,foreground_start),
                    background_start=first/fps,offset_seconds=offset,score=score)
    except core.MatchError as error:
        return dict(status='unchanged',first=0,stop=n,foreground_start=0.,background_start=0.,reason=str(error))


def segment_audio(source,output,start,duration):
    from music_sync import core
    command=[core.FFMPEG,'-nostdin','-hide_banner','-loglevel','error','-n','-ss',f'{start:.9f}',
             '-i',str(ex.local(source)), '-t',f'{duration:.9f}','-map','0:a:0?', '-vn','-c:a','aac',str(output)]
    # A silent original is valid. Probe the stream before requesting an audio-only output.
    from source.util.removal_experiments import has_audio
    if not has_audio(source):return None
    result=subprocess.run(command,capture_output=True,text=True)
    if result.returncode:raise RuntimeError(result.stderr[-4000:])
    return output


def materialize_segment(asset,match,remove_id,driver_id,out):
    """All views share one integer frame interval; no background model is called."""
    first,stop=match['first'],match['stop'];count=stop-first
    person=asset['people'][str(remove_id)];driver=asset['people'][str(driver_id)]
    source_record=asset['record'];fps=source_record['fps']
    folder=out/'background';folder.mkdir()
    original=folder/'original.mp4';background=folder/'background.mp4'
    audio=segment_audio(source_record['source'],folder/'audio.m4a',first/fps,count/fps)
    for frames,output in ((source_record['frame_dir'],original),(person['background_frames'],background)):
        paths=ex.sequence(frames,source_record['frames'])[first:stop]
        ex.encode_frames((cv2.imread(str(p)) for p in paths),output,fps,audio_source=audio)
        info,_=rel.video_info(output)
        if info['frames']!=count or info['size']!=source_record['size'] or abs(info['fps']-fps)>1e-4:
            raise ValueError('준비된 배경 구간 프레임/FPS 불일치')
    original_sha=ex.digest(original)
    body=sequence_view(person['masks'],folder/'removed'/'masks',first,stop)
    common=sequence_view(person['removal_masks'],folder/'removal_masks',first,stop)
    b_masks=sequence_view(driver['masks'],folder/'driver'/'masks',first,stop)
    b_crop=sequence_view(driver['crop'],folder/'driver'/'crop',first,stop)
    ex.write_json(b_masks.parent/'metadata.json',dict(source=relative(original),frames=count,fps=fps,
        automatic_person_detection=dict(selected_obj_id=driver_id)))
    prepared_data=dict(first=first,stop=stop,depth=asset['depth'],depth_sha256=asset['depth_sha256'])
    np.save(folder/'pose.npy',np.load(ex.local(person['pose']),allow_pickle=False)[first:stop])
    prepared_data['pose']=relative(folder/'pose.npy')
    for key,source in (('geometry',person['geometry']),('reference',person['reference']),
                       ('parts',person['parts']),('camera',asset['camera'])):
        file=folder/(key+'.npz');slice_arrays(source,file,first,stop);prepared_data[key]=relative(file)
    np.save(folder/'driver_pose.npy',np.load(ex.local(driver['driver_pose']),allow_pickle=False)[first:stop])
    slice_arrays(driver['features'],folder/'driver_features.npz',first,stop)
    record=dict(source_record,source=relative(original),source_sha256=original_sha,frames=count,
                selected_obj_id=remove_id,work=relative(folder),prepared_background=prepared_data)
    # No mutable tracking-session frame directory is needed for the prepared path.
    record.pop('frame_dir',None)
    lighting={key:person[key] for key in ('condition_frames','latents','grain')}
    lighting.update(first=first,stop=stop,text=asset['text'])
    ex.write_json(folder/'segment.json',dict(template=asset['manifest'],match=match,record=record,
        frame_indices=list(range(first,stop)),driver_id=driver_id))
    return record,original,background,body,common,b_masks,b_crop,lighting


def postprocess(cfg):
    started=time.perf_counter();started_wall=time.time();timings={};out=ex.local(cfg['output']);work=ex.local(cfg['work'])
    # This guard runs again in the child, before importing any tracking/model code.
    with stage(timings,'준비 자료 검증'):
        asset=prepared(cfg['background'],remove_id=cfg['remove_id'],driver_id=cfg['driver_id'])
        if asset is None or asset['manifest']!=cfg['manifest']:raise FileNotFoundError(MISSING)
    from source.util import pipeline,music_sync as music
    from source.enhancement import correction as dance,geometry_placement as placement,depth_occlusion as depth,edge_matte
    from source.relighting import variants,composite as cp
    placement.require_backend();variants.require_backend('RelightVid')
    if dance.missing():raise FileNotFoundError('\n'.join(dance.missing()))
    with stage(timings,'음악 매칭·준비 배경 구간 구성'):
        match=match_segment(asset,cfg['foreground'])
        record,bg_source,bg_video,body,common,b_masks,b_crop,lighting=materialize_segment(
            asset,match,cfg['remove_id'],cfg['driver_id'],out)
        fg_source=out/'foreground.mp4'
        native=music._trim_native(ex.local(cfg['foreground']),fg_source,match['foreground_start'],record['frames']/record['fps'])
        ex.write_json(out/'music_sync.json',dict(match,foreground=relative(fg_source),background=relative(bg_source),foreground_timing=native))
        if match['status']=='unchanged':print('음악 매칭 생략:',match['reason'],flush=True)
    with stage(timings,'유저 인물 검출·추적·추출'):
        pipeline.WORK_DIR=work/'tracking';pipeline.OUTPUT_DIR=out/'foreground_tracking'
        print(pipeline.load_video(relative(fg_source),MODEL),flush=True)
        ids=pipeline.SESSION.object_ids
        if not ids:raise ValueError('유저 영상에서 사람을 찾지 못했습니다.')
        requested=cfg.get('foreground_id')
        if requested is not None and int(requested) not in ids:
            raise ValueError(f'요청한 유저 인물 ID {requested}를 찾지 못했습니다. 선택 가능 ID: {ids}')
        # 여러 명이 검출되면 최고 신뢰도(ids[0]=id1)를 자동 선택한다(MOTIF 경로·문서와 동일 정책).
        cfg['foreground_id']=int(ids[0] if requested is None else requested)
        cfg['foreground_selection']='automatic' if requested is None else 'request'
        if len(ids)>1 and requested is None:
            print(f'유저 영상에서 {len(ids)}명 검출 · 최고 신뢰도 ID {cfg["foreground_id"]} 자동 선택',flush=True)
        print('유저 인물 선택: ID',cfg['foreground_id'],flush=True)
        pipeline.select_object(cfg['foreground_id']);pipeline.track_video()
        fg_original,_,_=pipeline.export_isolated_crop(margin=.06)
        pipeline.release_predictor()
    with stage(timings,'유저 경계 보정'):
        matte=edge_matte.prepare(fg_original,frame_times=native['times'])
    with stage(timings,'유저 관절·춤 보정'):
        plan=dance.prepare(fg_original,b_crop,bg_source,cfg['driver_id'],fg_source,
                           foreground_times=native['times'],render_foreground=matte)
        plan.update(driver_masks=relative(b_masks),driver_source_sha256=record['source_sha256'],
                    driver_features=relative(bg_source.parent/'driver_features.npz'))
        link(bg_source.parent/'driver_pose.npy',ex.local(plan['work'])/'driver_pose.npy')
        for path in (ex.local(plan['work'])/'driver_pose.npy',ex.local(plan['driver_features'])):
            plan['input_hashes'][relative(path)]=ex.digest(path)
        ex.write_json(ex.local(plan['work'])/'plan.json',plan)
        corrected=dance.run(plan)
        foreground=corrected if cfg.get('correction_enabled',True) else ex.local(plan['baseline'],exists=True)
    with stage(timings,'유저 기하·최종 위치·접지'):
        placed=placement.estimate(foreground,bg_video,body,record,plan=plan,gpu=cfg['gpu'],preserve_foreground_height=True)
        ex.write_json(out/'placement.json',placed)
    with stage(timings,'C 부위 대응·깊이 가림'):
        occlusion=depth.prepare(plan,body,record,placement=placed,gpu=cfg['gpu'])
    with stage(timings,'유저 객체 RGB·알파 렌더'):
        object_dir=out/'object';object_dir.mkdir()
        object_video=cp.render_object_video(bg_video,foreground,placed['transform'],object_dir/'object.mp4',
            foreground_fps=plan['fps'],foreground_video=fg_source,source_masks=fg_original.parent/'masks',
            destination_exclusion_masks=common,fg_shortfall='disappear',dof_match=False,motion_blur=False,
            edge_decontam=False,scene_harmonization=False)
    with stage(timings,'RelightVid 유저 입력 준비'):
        lighting_plan=variants.prepare(object_video,bg_source,body,record,'RelightVid',
            conditioning_background=bg_video,prepared_background=lighting)
    with stage(timings,'RelightVid 20 step·품질 평가'):
        lighting_plan=variants.choose(lighting_plan,cfg['gpu'])
    with stage(timings,'조명·그림자·가림·오디오·최종 저장'):
        final=variants.finish(lighting_plan,gpu=cfg['gpu'],output_path=out/'final.mp4',compose=True,
            contact_shadow=True,cast_shadow=True,light_angle=35.,occlusion_dir=occlusion)
        info,_=rel.video_info(final)
        if info['frames']!=record['frames'] or info['size']!=record['size'] or abs(info['fps']-record['fps'])>1e-4:
            raise ValueError('최종 영상 프레임/FPS/해상도 불일치')
    result=dict(status='complete',final_video=relative(final),template=asset['manifest'],
        inputs=cfg,timings_seconds=timings,worker_seconds=time.perf_counter()-started,video=info,
        music_sync=relative(out/'music_sync.json'),dance_plan=relative(ex.local(plan['work'])/'plan.json'),
        placement=relative(out/'placement.json'),occlusion=relative(occlusion),relight_plan=relative(ex.local(lighting_plan['work'])/'plan.json'),
        background_inference='disabled: masks/poses/GVHMR/camera/depth/part-depth/ground/relight-background-VAE reused',
        foreground_cache_reused={key:ex.local(placed[key]).stat().st_mtime<started_wall
                                 for key in ('geometry_foreground','camera_foreground')},
        shadows=dict(contact=True,cast=True,light_angle=35.),comparison_preview=False)
    ex.write_json(out/'result.json',result)
    print('최종 영상:',final,flush=True)


if __name__=='__main__':
    mode,config=sys.argv[1:]
    {'pre':precompute,'post':postprocess}[mode](ex.read_json(config))
