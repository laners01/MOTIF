"""Background-guided alternative backends; GPU models run in isolated workers."""
import os
import shutil
import subprocess
import sys
import time
import cv2
import numpy as np
from source.relighting import automatic as auto, model_test as rel
from source.relighting import quality
from source.util import removal_experiments as ex

SPECS = {
    'RelightVid': dict(vendor='vendor/RelightVid', env='relightvid', steps=20,
                      size=[512,512], chunk=16, overlap=4,
                      required=['inference.py', 'configs/inference_fbc.yaml',
                                'models/realistic-vision-v51/unet/diffusion_pytorch_model.safetensors',
                                'models/realistic-vision-v51/vae/config.json',
                                'models/realistic-vision-v51/vae/diffusion_pytorch_model.safetensors',
                                'models/realistic-vision-v51/text_encoder/config.json',
                                'models/realistic-vision-v51/text_encoder/model.safetensors',
                                'models/realistic-vision-v51/tokenizer/tokenizer_config.json',
                                'models/realistic-vision-v51/tokenizer/special_tokens_map.json',
                                'models/realistic-vision-v51/tokenizer/vocab.json',
                                'models/realistic-vision-v51/tokenizer/merges.txt',
                                'models/iclight_sd15_fbc.safetensors', 'models/relvid_mm_sd15_fbc.pth']),
    'LiveLight': dict(vendor='vendor/LiveLight', env='livelight', steps=4,
                     size=[512,512], chunk=64, overlap=16,
                     required=['src/livelight_wrapper_perframe_ref.py',
                               'configs/prompts/relight_perframe_ref.yaml',
                               'pretrained_weights/sd-image-variations-diffusers/model_index.json',
                               'pretrained_weights/sd-vae-ft-mse/config.json',
                               'pixel-perfect-depth/run_relight_batch.py',
                               'pixel-perfect-depth/checkpoints/depth_anything_v2_vitl.pth']),
    'LightXUni': dict(vendor='vendor/Light-X', env='lightxuni', steps=20,
                     size=[672,384], chunk=49, overlap=8,
                     required=['core/pipeline/pipeline_cogvideox_fun_inpaint.py']),
}


def runtime(model):
    # Validate the symlink target, but invoke the venv path to retain its packages.
    path = rel.ROOT / '.model-envs' / SPECS[model]['env'] / 'bin/python'
    ex.local(path)
    return path


def missing_backend(model):
    spec = SPECS[model]
    paths = [runtime(model)] + [ex.local(spec['vendor']) / p for p in spec['required']]
    if model == 'LightXUni':
        model_roots = [ex.local('models/lightxuni/Light-X-Uni'),
                       ex.local('models/lightxuni/CogVideoX-Fun-V1.1-5b-InP')]
        paths += [model_roots[0] / 'config.json', model_roots[1] / 'model_index.json']
    missing = [str(p.relative_to(rel.ROOT)) for p in paths if not ex.local(p).exists()]
    download_roots = ([ex.local('models/lightxuni/Light-X-Uni'),
                       ex.local('models/lightxuni/CogVideoX-Fun-V1.1-5b-InP')]
                      if model == 'LightXUni' else
                      [ex.local('vendor/LiveLight/pretrained_weights')]
                      if model == 'LiveLight' else [])
    if any(next(root.rglob('*.incomplete'), None) is not None for root in download_roots if root.exists()):
        missing.append('체크포인트 다운로드 미완료 (*.incomplete 존재)')
    if model == 'LiveLight':
        weights = ex.local('vendor/LiveLight/pretrained_weights/LiveLight')
        for name in ('reference_unet', 'denoising_unet', 'light_guider', 'temporal_module'):
            if len(list(weights.rglob(name+'*.pth'))) != 1:
                missing.append(str(weights.relative_to(rel.ROOT) / (name+'*.pth'))+' (정확히 1개 필요)')
    return missing


def require_backend(model):
    missing = missing_backend(model)
    if missing:
        raise FileNotFoundError(model+' 환경/가중치 준비 필요:\n'+'\n'.join(missing)+
                                '\n설정 안내: source/relighting/VARIANTS.md')


def windows(count, length, overlap):
    if count < 1 or not 0 <= overlap < length:
        raise ValueError('잘못된 프레임/청크 설정')
    start = 0
    while True:
        stop = min(count, start+length)
        yield start, stop
        if stop == count:
            break
        start += length-overlap


def letterbox(image, size):
    w,h = size
    ih,iw = image.shape[:2]
    scale = min(w/iw,h/ih)
    rw,rh = max(1,round(iw*scale)),max(1,round(ih*scale))
    x,y = (w-rw)//2,(h-rh)//2
    result = np.zeros((h,w)+image.shape[2:],np.uint8)
    interpolation=cv2.INTER_AREA if scale<1 else cv2.INTER_LANCZOS4
    result[y:y+rh,x:x+rw] = cv2.resize(image,(rw,rh),interpolation=interpolation)
    return result, [x,y,rw,rh]


def restore(image, rect, size):
    x,y,w,h = rect
    crop=image[y:y+h,x:x+w]
    interpolation=cv2.INTER_AREA if size[0]<w or size[1]<h else cv2.INTER_LANCZOS4
    return cv2.resize(crop,tuple(size),interpolation=interpolation)


def validate(plan):
    for key,digest_key in (('source','source_sha256'),('background','background_sha256')):
        if ex.digest(ex.local(plan[key],exists=True)) != plan[digest_key]:
            raise ValueError('relighting 입력 영상이 변경되었습니다. 준비 셀부터 다시 실행하세요.')
    if auto.sequence_hash(plan['alpha_dir'],plan['frames']) != plan['alpha_dir_sha256']:
        raise ValueError('객체 alpha가 변경되었습니다. 준비 셀부터 다시 실행하세요.')
    if auto.sequence_hash(plan['source_frames'],plan['frames']) != plan['source_frames_sha256']:
        raise ValueError('무손실 객체 RGB가 변경되었습니다. 객체 렌더부터 다시 실행하세요.')
    if plan['model'] != 'RelightVid':
        if auto.sequence_hash(plan['body_masks'],plan['frames']) != plan['body_masks_sha256']:
            raise ValueError('기준 사람 마스크가 변경되었습니다. 준비 셀부터 다시 실행하세요.')
    if plan['model'] == 'RelightVid' and 'conditioning_background' not in plan:
        raise ValueError('RelightVid 조명 조건 배경이 없습니다. 준비 셀부터 다시 실행하세요.')
    if 'conditioning_background' in plan:
        if ex.digest(ex.local(plan['conditioning_background'],exists=True)) != plan['conditioning_background_sha256']:
            raise ValueError('조명 조건 배경이 변경되었습니다. 준비 셀부터 다시 실행하세요.')


def prepare(composite, background, body_masks, mask_record, model, *, conditioning_background=None,
            prepared_background=None):
    require_backend(model)
    source = ex.local(composite,exists=True)
    background = ex.local(background,exists=True)
    if model == 'RelightVid' and conditioning_background is None:
        raise ValueError('RelightVid에는 conditioning_background=BG_VIDEO가 필요합니다. 새 E-5a 셀을 실행하세요.')
    condition = ex.local(conditioning_background,exists=True) if conditioning_background is not None else background
    info,_ = rel.video_info(source)
    condition_info,_ = rel.video_info(condition)
    if (info['frames'] != condition_info['frames'] or info['size'] != condition_info['size']
            or abs(info['fps']-condition_info['fps']) > 1e-4):
        raise ValueError('조명 조건 배경의 프레임·FPS·해상도가 합성 영상과 다릅니다.')
    bg_info,_ = rel.video_info(background)
    if (info['frames'] != bg_info['frames'] or info['size'] != bg_info['size']
            or abs(info['fps']-bg_info['fps']) > 1e-4):
        raise ValueError('합성/배경 영상의 프레임·FPS·해상도가 다릅니다.')
    if ex.local(mask_record['source']) != background or ex.digest(background) != mask_record['source_sha256']:
        raise ValueError('배경 추적 기록이 다릅니다.')
    if model != 'RelightVid':
        ex.validate_masks(body_masks,mask_record)
    composition = ex.read_json(source.with_suffix('.composition.json'))
    if ex.digest(source) != composition['source_sha256']:
        raise ValueError('합성 결과가 바뀌었습니다. E-4부터 다시 실행하세요.')
    if not composition.get('rgb_dir'):
        raise ValueError('무손실 객체 RGB 기록이 없습니다. 객체 렌더부터 다시 실행하세요.')
    source_frames=ex.local(composition['rgb_dir'],exists=True)
    source_frames_sha256=auto.sequence_hash(source_frames,info['frames'])
    if source_frames_sha256 != composition.get('rgb_sha256'):
        raise ValueError('무손실 객체 RGB가 변경되었습니다. 객체 렌더부터 다시 실행하세요.')
    scene_record=composition.get('scene_harmonization',{})
    scene_keyframes=scene_record.get('keyframes',[])
    scene_confidence=float(np.mean([row.get('confidence',0.) for row in scene_keyframes])) if scene_keyframes else 0.
    work = ex.unique_dir('work/relighting_variants/'+model,'session')
    plan = dict(info, model=model, source=str(source.relative_to(rel.ROOT)),
                source_sha256=ex.digest(source),source_frames=str(source_frames.relative_to(rel.ROOT)),
                source_frames_sha256=source_frames_sha256,
                background=str(background.relative_to(rel.ROOT)),
                background_sha256=ex.digest(background), alpha_dir=composition['alpha_dir'],
                work=str(work.relative_to(rel.ROOT)), steps=SPECS[model]['steps'], seed=43,
                candidates=[{'condition':'background'}],probe_start=0,probe_frames=info['frames'],
                status='prepared',scene_harmonization=scene_record,
                scene_harmonization_confidence=scene_confidence)
    plan['conditioning_background'] = str(condition.relative_to(rel.ROOT))
    plan['conditioning_background_sha256'] = ex.digest(condition)
    plan['alpha_dir_sha256'] = auto.sequence_hash(plan['alpha_dir'],info['frames'])
    if prepared_background:
        if model != 'RelightVid':raise ValueError('준비된 조명 조건은 RelightVid 전용입니다.')
        plan['prepared_background']=prepared_background
        if prepared_background['stop']-prepared_background['first']!=info['frames']:
            raise ValueError('준비된 조명 조건 프레임 불일치')
    if model != 'RelightVid':
        plan['body_masks'] = str(ex.local(body_masks).relative_to(rel.ROOT))
        plan['body_masks_sha256'] = auto.sequence_hash(plan['body_masks'],info['frames'])
    if model == 'RelightVid':
        plan['light'] = dict(light_u=.5,light_v=.25,light_z_rel=1.,light_intensity=1.,
                             light_r=1.,light_g=1.,light_b=1.)
        plan['analysis'] = 'RelightVid uses the removed-background video directly; no other-person RGB analysis'
    else:
        observations = []
        for i in np.unique(np.linspace(0,info['frames']-1,min(9,info['frames']),dtype=int)):
            original = auto.descriptor(auto.small(auto.frame(source,int(i))),auto.small(auto.mask_at(plan['alpha_dir'],int(i))))
            target = auto.descriptor(auto.small(auto.frame(background,int(i))),auto.small(auto.mask_at(body_masks,int(i))))
            if original is not None and target is not None:
                observations.append((original,target))
        if not observations:
            raise ValueError('조명 분석에 필요한 전경/배경 사람 마스크가 없습니다.')
        _original,target = np.median(np.array(observations),axis=0)
        plan['light'] = dict(light_u=float(np.clip(.5-target[3]*.25,.15,.85)),
                             light_v=.25,light_z_rel=1.,light_intensity=1.,
                             light_r=1.,light_g=1.,light_b=1.)
        plan['analysis'] = 'neutral light with background-person left/right contrast; RGB albedo ignored'
    for folder in ('input','background','alpha'):
        (work/folder).mkdir()
    condition_cap=None if prepared_background else cv2.VideoCapture(str(condition))
    try:
        for i in range(info['frames']):
            source_image=cv2.imread(str(source_frames/f'{i:06d}.png'))
            if source_image is None:raise ValueError('입력 프레임 누락')
            if prepared_background:
                import shutil
                cached=ex.local(prepared_background['condition_frames'])/f'{i+prepared_background["first"]:06d}.png'
                destination=work/'background'/f'{i:06d}.png'
                try:os.link(ex.local(cached,exists=True),destination)
                except OSError:shutil.copyfile(cached,destination)
                images=(('input',source_image),)
            else:
                ok,condition_image=condition_cap.read()
                if not ok:raise ValueError('입력 프레임 누락')
                images=(('input',source_image),('background',condition_image))
            for folder,image in images:
                image,rect = letterbox(image,SPECS[model]['size'])
                if not cv2.imwrite(str(work/folder/f'{i:06d}.png'),image,[cv2.IMWRITE_PNG_COMPRESSION,1]):
                    raise OSError('프레임 저장 실패')
            original_alpha = auto.mask_at(plan['alpha_dir'],i)
            if original_alpha.shape != tuple(reversed(info['size'])):
                raise ValueError('합성 알파 해상도 불일치. E-4부터 다시 실행하세요.')
            alpha,_ = letterbox(original_alpha,SPECS[model]['size'])
            if not cv2.imwrite(str(work/'alpha'/f'{i:06d}.png'),alpha):
                raise OSError('알파 저장 실패')
    finally:
        if condition_cap is not None:condition_cap.release()
    plan['rect'] = rect
    ex.write_json(work/'plan.json',plan)
    return plan


def choose(plan,gpu):
    validate(plan)
    require_backend(plan['model'])
    work = ex.local(plan['work'])
    current = ex.read_json(work/'plan.json')
    if current.get('status') in ('selected','complete') and current.get('choice',{}).get('policy') == quality.POLICY:
        return current
    relit_frames=work/'relit_frames'
    try: cached_reference=bool(ex.sequence(relit_frames,plan['frames']))
    except (FileNotFoundError,ValueError): cached_reference=False
    if cached_reference:
        current.update(choice=quality.evaluate(current,work),status='selected')
        current.pop('final_video',None)
        ex.write_json(work/'plan.json',current)
        print('기존 무손실 모델 참고 프레임을 새 기준으로 재평가했습니다. GPU 추론 생략.')
        return current
    env = os.environ.copy()
    env.update(CUDA_VISIBLE_DEVICES=str(int(gpu)),CUDA_DEVICE_ORDER='PCI_BUS_ID',
               PYTHONDONTWRITEBYTECODE='1',HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',WANDB_MODE='disabled')
    for key in ('HF_HOME','XDG_CACHE_HOME','TORCH_HOME','MPLCONFIGDIR','TRITON_CACHE_DIR',
                'TORCH_EXTENSIONS_DIR','CUDA_CACHE_PATH','NUMBA_CACHE_DIR'):
        path = ex.local('.cache/relighting_variants/'+plan['model']+'/'+key.lower())
        path.mkdir(parents=True,exist_ok=True)
        env[key] = str(path)
    env['TMPDIR'] = str(work)
    env['PYTHONPATH'] = str(rel.ROOT)
    cmd = [str(runtime(plan['model'])),str(ex.local('source/relighting/variant_worker.py')),
           str(work/'plan.json')]
    started = time.perf_counter()
    log_path = work/'inference.log'
    print('모델 추론 시작 · 로그:',log_path,flush=True)
    # 서브프로세스 출력을 파일과 부모 stdout(=Cloud Run 로그) 양쪽에 흘린다(SVOR tee와 동일).
    # 기존엔 파일에만 남아 RelightVid 로딩/스텝 진행이 Cloud Run에서 완전 무음이었다.
    with log_path.open('w') as log:
        proc = subprocess.Popen(cmd,cwd=ex.local(SPECS[plan['model']]['vendor']),env=env,
                                stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        for chunk in iter(proc.stdout.readline,b""):
            line = chunk.decode(errors='replace')
            log.write(line);sys.stdout.write(line);sys.stdout.flush()
        returncode = proc.wait()
    if returncode:
        tail = log_path.read_text(errors='replace')[-6000:]
        raise RuntimeError(f"{plan['model']} 추론 실패 · {log_path}\n{tail}")
    references = ex.sequence(work/'reference',plan['frames'])
    relit_frames.mkdir(exist_ok=True)
    for index,path in enumerate(references):
        image=cv2.imread(str(path))
        if image is None: raise ValueError('모델 출력 프레임 디코딩 실패')
        restored=restore(image,plan['rect'],plan['size'])
        if not cv2.imwrite(str(relit_frames/f'{index:06d}.png'),restored,[cv2.IMWRITE_PNG_COMPRESSION,1]):
            raise OSError('무손실 모델 참고 프레임 저장 실패')
    current.update(choice=quality.evaluate(plan,work),status='selected',
                   inference_seconds=round(time.perf_counter()-started,3))
    ex.write_json(work/'plan.json',current)
    return current


def finish_configuration(*,compose,contact_shadow,cast_shadow,light_angle):
    from source.relighting import composite as cp
    if not isinstance(compose,bool) or not isinstance(contact_shadow,bool) or not isinstance(cast_shadow,bool):
        raise TypeError('합성/그림자 설정은 bool이어야 합니다.')
    if not np.isfinite(light_angle) or not -180 <= light_angle <= 180:
        raise ValueError('광원 각도는 -180~180도여야 합니다.')
    return dict(compose=compose,contact_shadow=contact_shadow,cast_shadow=cast_shadow,
                light_angle=float(light_angle),encoding='h264-crf14-slow-yuv444p',
                automatic_sha256=ex.digest(auto.__file__),
                composite_sha256=ex.digest(cp.__file__),variants_sha256=ex.digest(__file__))


def compose_frame(adjusted,bg,alpha,index,*,contact_shadow=True,cast_shadow=True,light_angle=35.,grain_sigma=None):
    from source.relighting import composite as cp
    if adjusted.shape != bg.shape or alpha.shape != bg.shape[:2]:
        raise ValueError('최종 합성 프레임/alpha 해상도 불일치')
    shadow_bg=bg
    ys,xs=np.where(alpha>32)
    if xs.size and (contact_shadow or cast_shadow):
        x0,x1,y0,y1=xs.min(),xs.max()+1,ys.min(),ys.max()+1
        contact,cast=cp.shadow_matte(alpha[y0:y1,x0:x1],int(x0),int(y0),bg.shape,
                                     angle=float(light_angle))
        shade=(.42*contact if contact_shadow else 0)+(.23*cast if cast_shadow else 0)
        shadow_bg=np.clip(bg.astype(np.float32)*(1-np.clip(shade,0,.65))[...,None],0,255).astype(np.uint8)
    weight=alpha.astype(np.float32)[...,None]/255
    result=(adjusted.astype(np.float32)*weight+shadow_bg*(1-weight)).round().clip(0,255).astype(np.uint8)
    if np.any(alpha):
        noise=np.random.default_rng(index).normal(0,cp._grain_sigma(bg) if grain_sigma is None else grain_sigma,alpha.shape)
        result=np.clip(result.astype(np.float32)+noise[...,None]*weight,0,255).astype(np.uint8)
    result[alpha==0]=shadow_bg[alpha==0]
    return result



def strength_at(choice,index):
    keyframes=choice.get('strength_keyframes') or []
    if not keyframes:
        return float(choice['selected']['strength'])
    frames=np.array([row['frame'] for row in keyframes],np.float64)
    values=np.array([row['strength'] for row in keyframes],np.float64)
    return float(np.interp(index,frames,values))

def finish(plan,gpu=None,output_path=None,*,compose=False,contact_shadow=True,cast_shadow=True,light_angle=35.,
           occlusion_dir=None):
    validate(plan)
    work = ex.local(plan['work'])
    plan = ex.read_json(work/'plan.json')
    finish_config=finish_configuration(compose=compose,contact_shadow=contact_shadow,
                                        cast_shadow=cast_shadow,light_angle=light_angle)
    occlusion = ex.local(occlusion_dir, exists=True) if occlusion_dir is not None else None
    finish_config['occlusion_dir'] = (str(occlusion.relative_to(rel.ROOT))
                                      if occlusion is not None else None)
    if occlusion is not None:
        record = ex.local(occlusion / 'occlusion.json', exists=True)
        finish_config['occlusion_sha256'] = ex.digest(record)
    if plan.get('choice',{}).get('policy') != quality.POLICY:
        raise ValueError('평가 기준이 변경되었습니다. E-5b부터 다시 실행하세요 (기존 추론 재사용).')
    requested_output = ex.local(output_path) if output_path is not None else None
    if (plan['status']=='complete' and plan.get('finish_config')==finish_config
            and ex.local(plan['final_video']).is_file()
            and (requested_output is None or ex.local(plan['final_video']) == requested_output)):
        return ex.local(plan['final_video'])
    if plan['status']=='complete':
        plan['status']='selected'
    if plan['status']!='selected': raise ValueError('E-5b를 먼저 실행하세요.')
    selected=plan['choice']['selected'];mode=selected.get('mode','model' if selected['strength'] else 'original')
    fallback_confidences=[]
    if requested_output is None:
        out = ex.unique_dir('output/run_'+plan['model'],'relighting')
        final_video = out/'relit.mp4'
    else:
        requested_output.parent.mkdir(parents=True,exist_ok=True)
        final_video = requested_output
    encoded_video = final_video
    if requested_output is not None:
        encoded_video = work/f'final_{time.time_ns()}.mp4'
    def frames():
        source_frames=ex.local(plan['source_frames'],exists=True)
        relit_frames=ex.local(work/'relit_frames',exists=True)
        background = cv2.VideoCapture(str(ex.local(plan['conditioning_background'])))
        previous_gain = previous_frame = None
        prepared=plan.get('prepared_background')
        grain=(np.load(ex.local(prepared['grain']),allow_pickle=False)[prepared['first']:prepared['stop']]
               if prepared else None)
        occluder = None
        if occlusion is not None:
            if not compose:
                raise ValueError('depth 가림은 최종 배경 합성과 함께 사용해야 합니다.')
            from source.enhancement.occlusion import Renderer
            occluder = Renderer(occlusion, plan['frames'], plan['size'], plan['fps'])
        try:
            for i in range(plan['frames']):
                original=cv2.imread(str(source_frames/f'{i:06d}.png'))
                changed=cv2.imread(str(relit_frames/f'{i:06d}.png'))
                bg_ok,bg = background.read()
                if original is None or changed is None or not bg_ok: raise ValueError('최종 보정 프레임 누락')
                alpha = auto.mask_at(plan['alpha_dir'],i)
                strength=strength_at(plan['choice'],i)
                if mode=='base_harmonization_only':
                    gain=np.ones(auto.small(alpha).shape+(3,),np.float32);confidence=0.
                    fallback_confidences.append(plan.get('scene_harmonization_confidence',0.))
                else:
                    gain=auto.directional_gain_map(original,changed,alpha)
                    small_original=auto.small(original);small_changed=auto.small(changed)
                    valid=cv2.erode((auto.small(alpha)>240).astype(np.uint8),np.ones((3,3),np.uint8))>0
                    structure=quality.structure(small_original,small_changed,valid) if valid.sum()>=16 else 0.
                    confidence=float(np.clip((structure-.25)/.5,0,1))
                if np.any(alpha):
                    gain=auto.stabilize_gain(gain,previous_gain,previous_frame,original)
                adjusted=auto.apply_gain(original,alpha,gain,strength,reference_confidence=confidence)
                if compose:
                    adjusted=compose_frame(adjusted,bg,alpha,i,contact_shadow=contact_shadow,
                                           cast_shadow=cast_shadow,light_angle=light_angle,
                                           grain_sigma=None if grain is None else float(grain[i]))
                    if occluder is not None:
                        adjusted, _ = occluder.apply(adjusted, alpha, i, bg)
                yield adjusted
                if np.any(alpha):
                    previous_gain,previous_frame = gain,original
                else:
                    previous_gain = previous_frame = None
            if background.read()[0]:
                raise ValueError('기록보다 많은 배경 프레임이 있습니다.')
        finally:
            background.release()
            if occluder is not None:
                occluder.close()
    stream = frames()
    audio_source=ex.local(plan['background']) if compose else None
    try: ex.encode_frames(stream,encoded_video,plan['fps'],audio_source=audio_source,final=True)
    finally: stream.close()
    info,_ = rel.video_info(encoded_video)
    if info['frames']!=plan['frames'] or info['size']!=plan['size'] or abs(info['fps']-plan['fps'])>1e-4:
        raise ValueError('출력 프레임·FPS·해상도 보존 실패')
    if requested_output is not None:
        # work→output은 별도 볼륨(별도 FS)이라 os.replace(rename)가 EXDEV(Errno 18)로 실패한다.
        # shutil.move는 크로스디바이스면 copy+delete로 폴백한다.
        shutil.move(str(encoded_video), str(final_video))
    plan.update(status='complete',audio=bool(compose),
                audio_source=str(audio_source.relative_to(rel.ROOT)) if audio_source else None,
                final_video=str(final_video.relative_to(rel.ROOT)),
                final_stage=('relight_then_depth_composite' if compose and occlusion is not None else
                             'relight_then_composite' if compose else 'foreground_only_relight'),
                finish_config=finish_config,
                fallback_runtime=dict(applied=mode=='base_harmonization_only',
                                      mean_confidence=float(np.mean(fallback_confidences)) if fallback_confidences else 0.))
    ex.write_json(work/'plan.json',plan)
    if requested_output is None:
        ex.write_json(out/'experiment.json',plan)
    return final_video
