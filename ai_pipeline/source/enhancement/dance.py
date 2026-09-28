"""Motion transfer preparation and process isolation for run_enhancement."""
import os
import subprocess
import time
import cv2
import numpy as np
from source.util import removal_experiments as ex
from source.relighting import model_test as rel, composite as cp

VENDOR='vendor/MimicMotion'
PYTHON='.model-envs/mimicmotion/bin/python'


def missing():
    paths=[PYTHON,VENDOR+'/inference.py',VENDOR+'/models/MimicMotion_1-1.pth',
           VENDOR+'/models/DWPose/yolox_l.onnx',VENDOR+'/models/DWPose/dw-ll_ucoco_384.onnx']
    paths += ['models/mimicmotion/svd/'+p for p in ('unet/config.json','vae/config.json','vae/diffusion_pytorch_model.fp16.safetensors',
             'image_encoder/config.json','image_encoder/model.fp16.safetensors','scheduler/scheduler_config.json','feature_extractor/preprocessor_config.json')]
    return [p for p in paths if not ex.local(p).is_file()]


def portrait(bgra):
    h,w=bgra.shape[:2];ratio=min(512/w,896/h)
    size=max(1,round(w*ratio)),max(1,round(h*ratio))
    # Mask before resize: original background must not enter the appearance reference.
    alpha=bgra[...,3:4].astype(np.float32)/255
    rgb=(bgra[...,:3]*alpha+127*(1-alpha)).round().astype(np.uint8)
    im=cv2.resize(rgb,size,interpolation=cv2.INTER_AREA if ratio<1 else cv2.INTER_CUBIC)
    canvas=np.full((1024,576,3),127,np.uint8)
    x,y=(576-size[0])//2,(1024-size[1])//2
    canvas[y:y+size[1],x:x+size[0]]=im
    return canvas


def prepare(foreground,driver,background,driver_id):
    absent=missing()
    if absent:raise FileNotFoundError('MimicMotion 필수 파일 누락:\n'+'\n'.join(absent))
    fg=ex.sequence(foreground);drive=ex.sequence(driver)
    info,_=rel.video_info(background)
    if len(drive)!=info['frames']:raise ValueError('기준 춤과 배경 프레임 수 불일치')
    work=ex.unique_dir('work/dance_enhancement','session')
    # Choose a large, sharp, visible reference among distributed source frames.
    candidates=[]
    for i in np.unique(np.linspace(0,len(fg)-1,min(24,len(fg)),dtype=int)):
        im=cv2.imread(str(fg[i]),-1)
        if im is None or im.shape[2]!=4:raise ValueError('전경 RGBA 프레임 필요')
        valid=im[...,3]>240
        if valid.sum()<64:continue
        lap=cv2.Laplacian(cv2.cvtColor(im[...,:3],cv2.COLOR_BGR2GRAY),cv2.CV_32F)
        score=float(np.mean(lap[valid]**2))*np.sqrt(valid.sum())
        candidates.append((score,int(i)))
    if not candidates:raise ValueError('전경 인물 참고 프레임을 찾을 수 없습니다.')
    index=max(candidates)[1]
    cv2.imwrite(str(work/'reference.png'),portrait(cv2.imread(str(fg[index]),-1)))
    def frames():
        for p in drive:
            im=cv2.imread(str(p),-1)
            if im is None or im.shape[2]!=4:raise ValueError('기준 인물 RGBA 프레임 필요')
            yield portrait(im)
    # Only container metadata is 24: prevent upstream pose extractor auto-skipping high-FPS frames.
    # Every background frame remains present; generated output is encoded at the real source FPS.
    ex.encode_frames(frames(),work/'driver.mp4',24)
    plan=dict(info,work=str(work.relative_to(ex.ROOT)),background=str(ex.local(background).relative_to(ex.ROOT)),
              background_sha256=ex.digest(background),foreground=str(ex.local(foreground).relative_to(ex.ROOT)),
              driver=str(ex.local(driver).relative_to(ex.ROOT)),driver_id=int(driver_id),reference_index=index,
              steps=25,seed=43,reference_sha256=ex.digest(work/'reference.png'),driver_sha256=ex.digest(work/'driver.mp4'))
    ex.write_json(work/'plan.json',plan)
    return plan


def run(plan,gpu):
    work=ex.local(plan['work'],exists=True)
    for p,key in [(ex.local(plan['background']),'background_sha256'),(work/'reference.png','reference_sha256'),(work/'driver.mp4','driver_sha256')]:
        if ex.digest(p)!=plan[key]:raise ValueError('입력이 바뀌었습니다. 춤 입력 준비부터 다시 실행하세요.')
    out=work/'generated.mp4'
    if out.is_file():
        info,_=rel.video_info(out)
        if info['frames']==plan['frames'] and abs(info['fps']-plan['fps'])<1e-4:return out
        raise ValueError('기존 생성 영상 프레임/FPS 불일치')
    env=os.environ.copy()
    for key in ('HF_HOME','TORCH_HOME','XDG_CACHE_HOME','MPLCONFIGDIR','TRITON_CACHE_DIR','NUMBA_CACHE_DIR','TORCH_EXTENSIONS_DIR','CUDA_CACHE_PATH'):
        path=ex.local('.cache/dance_enhancement/'+key.lower());path.mkdir(parents=True,exist_ok=True);env[key]=str(path)
    env.update(PYTHONDONTWRITEBYTECODE='1',HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',CUDA_VISIBLE_DEVICES=str(int(gpu)),
               CUDA_DEVICE_ORDER='PCI_BUS_ID',PYTHONPATH=str(ex.ROOT),TMPDIR=str(work),MPLBACKEND='Agg')
    ex.local(PYTHON,exists=True)  # validate target; preserve venv executable path
    command=[str(ex.ROOT/PYTHON),str(ex.local('source/enhancement/worker.py')),str(work/'plan.json')]
    print('춤 생성 시작 · 로그:',work/'inference.log',flush=True)
    started=time.perf_counter()
    with (work/'inference.log').open('w') as log:
        result=subprocess.run(command,cwd=ex.local(VENDOR),env=env,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise RuntimeError((work/'inference.log').read_text(errors='replace')[-7000:])
    info,_=rel.video_info(out)
    if info['frames']!=plan['frames'] or abs(info['fps']-plan['fps'])>1e-4:raise ValueError('춤 생성 출력 프레임/FPS 불일치')
    plan.update(generation_seconds=time.perf_counter()-started,generated=str(out.relative_to(ex.ROOT)))
    ex.write_json(work/'plan.json',plan)
    return out


def isolate_generated(video,plan,progress=None):
    """Independent tracker accepts generated work/ video without altering the BG session."""
    from source.util import pipeline
    work=ex.unique_dir('work/dance_enhancement','segmentation')
    paths,fps,w,h=pipeline.extract_frames(ex.local(video,exists=True),work/'frames')
    exp=dict(source=str(ex.local(video).relative_to(ex.ROOT)),source_sha256=ex.digest(video),frames=len(paths),fps=fps,
             size=[w,h],work=str(work.relative_to(ex.ROOT)),frame_dir=str((work/'frames').relative_to(ex.ROOT)))
    tracker=ex.new_tracker(exp)
    try:
        candidates=[];index=0
        for i in np.unique(np.linspace(0,len(paths)-1,min(5,len(paths)),dtype=int)):
            candidates=ex.detect_people(exp,int(i))
            if len(candidates)==1:index=int(i);break
        if len(candidates)!=1:raise ValueError('생성 영상에서 한 명의 전경을 확정하지 못했습니다. 생성 결과를 확인하세요.')
        ex.initialize_tracker(tracker,exp)
        ex.add_boxes(tracker,index,candidates,1)
        tracker.OUTPUT_DIR=ex.unique_dir('output/dance_enhancement','tracking')
        tracker.track_video(progress=progress)
        crop,bbox=cp.export_isolated_crop(paths,tracker.SESSION.result_dir/'masks',tracker.SESSION.result_dir,
                                          fps=fps,progress=progress)
    finally:tracker.release_predictor()
    return crop
