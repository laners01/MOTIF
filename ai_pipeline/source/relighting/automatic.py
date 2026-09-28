"""Automatic background-guided UniRelight search and foreground-only compositing.

The ranking is a conservative image-statistics heuristic, not physical lighting
reconstruction or a learned perceptual quality guarantee. No user tuning required.
"""
import hashlib
import time
import cv2
import numpy as np
from source.relighting import model_test as rel
from source.relighting.shadow_filter import linear, _SRGB
from source.util import removal_experiments as ex

POLICY = 'foreground-auto-v1'


def frame(path, index, gray=False):
    cap = cv2.VideoCapture(str(ex.local(path, exists=True)))
    try:
        cap.set(cv2.CAP_PROP_POS_FRAMES, index)
        ok, value = cap.read()
    finally:
        cap.release()
    if not ok:
        raise ValueError(f'프레임을 읽을 수 없습니다: {index}')
    return cv2.cvtColor(value, cv2.COLOR_BGR2GRAY) if gray else value


def mask_at(folder, index):
    image = cv2.imread(str(ex.local(ex.local(folder) / f'{index:06d}.png', exists=True)), 0)
    if image is None:
        raise ValueError('알파/추적 마스크 디코딩 실패')
    return image


def sequence_hash(folder, count):
    return hashlib.sha256(''.join(ex.digest(p) for p in ex.sequence(folder, count)).encode()).hexdigest()


def validate(plan):
    for key, digest_key in [('source','source_sha256'),('background','background_sha256')]:
        if ex.digest(ex.local(plan[key], exists=True)) != plan[digest_key]:
            raise ValueError('영상이 변경되었습니다. E-5a 자동 분석 셀부터 다시 실행하세요.')
    for key in ('alpha_dir', 'body_masks'):
        if sequence_hash(plan[key], plan['frames']) != plan[key+'_sha256']:
            raise ValueError('합성/기준 사람 마스크가 변경되었습니다. 자동 분석을 다시 실행하세요.')


def small(image):
    h,w = image.shape[:2]
    return cv2.resize(image, (max(1,round(w*min(1,256/max(h,w)))),
                              max(1,round(h*min(1,256/max(h,w))))), interpolation=cv2.INTER_AREA)


def descriptor(image, mask):
    valid = mask > 180
    if valid.sum() < 16:
        return None
    rgb = linear(image)[..., ::-1]
    avg = np.median(rgb[valid], axis=0).clip(.01,1)
    ys,xs = np.where(valid); mid=np.median(xs)
    lum = rgb @ np.array([.2126,.7152,.0722],np.float32)
    left=lum[valid & (np.indices(valid.shape)[1] <= mid)]
    right=lum[valid & (np.indices(valid.shape)[1] > mid)]
    direction = 0. if len(right)==0 else float(np.log((np.median(left)+.02)/(np.median(right)+.02)))
    return np.r_[np.log(avg), np.clip(direction,-1,1)]


def _neutral_tint(image, mask):
    """Return robust neutral chroma and confidence; no neutral evidence means no cue."""
    bgr=linear(small(image))
    valid=small(mask)>32
    luminance=bgr@np.array([.0722,.7152,.2126],np.float32)
    spread=(bgr.max(axis=2)-bgr.min(axis=2))/np.maximum(bgr.mean(axis=2),.03)
    valid &= (luminance>.04)&(luminance<.9)&(spread<.45)
    count=int(valid.sum())
    if count<32:
        return np.ones(3,np.float32),0.
    pixels=bgr[valid].clip(1e-3,None)
    normalized=pixels/np.exp(np.mean(np.log(pixels),axis=1,keepdims=True))
    value=np.median(normalized,axis=0).clip(.8,1.25)
    dispersion=float(np.median(np.abs(np.log(normalized)-np.log(value))))
    coverage=min(1.,count/128.)
    confidence=coverage*float(np.clip((.16-dispersion)/.12,0,1))
    return value.astype(np.float32),confidence


def _scene_ratio(original, background, alpha):
    """Weak colour-temperature cue with explicit evidence confidence."""
    a=small(alpha)
    subject=a>32
    radius=max(5,round(min(a.shape)*.06))|1
    outer=cv2.dilate(subject.astype(np.uint8),np.ones((radius,radius),np.uint8))>0
    ring=(outer&~subject).astype(np.uint8)*255
    if np.count_nonzero(ring)<32:
        ring=np.where(subject,0,255).astype(np.uint8)
    source_tint,source_confidence=_neutral_tint(original,a)
    target_tint,target_confidence=_neutral_tint(background,ring)
    confidence=float(min(source_confidence,target_confidence))
    ratio=(target_tint/np.maximum(source_tint,1e-3)).clip(.85,1.18)
    return ratio,confidence


def gain_map(original, reference, alpha, background=None):
    a=small(alpha).astype(np.float32)/255
    source=linear(small(original));target=linear(small(reference))
    weighted_source=source*a[...,None];weighted_target=target*a[...,None]
    weight=cv2.GaussianBlur(a,(0,0),10)[...,None]
    base=cv2.GaussianBlur(weighted_source,(0,0),10)/np.maximum(weight,1e-5)
    changed=cv2.GaussianBlur(weighted_target,(0,0),10)/np.maximum(weight,1e-5)
    luma=np.array([.0722,.7152,.2126],np.float32)
    illumination=((changed@luma+.015)/(base@luma+.015)).clip(.75,1.35)[...,None]
    channel_ratio=(changed+.015)/(base+.015)
    tint=(channel_ratio/illumination).clip(.8,1.25)
    if background is not None:
        scene,scene_confidence=_scene_ratio(original,background,alpha)
        scene_weight=.25*scene_confidence
        tint=((1-scene_weight)*tint+scene_weight*scene.reshape(1,1,3)).clip(.8,1.25)
    fine_weight=cv2.GaussianBlur(a,(0,0),3)[...,None]
    fine_base=cv2.GaussianBlur(weighted_source,(0,0),3)/np.maximum(fine_weight,1e-5)
    fine_changed=cv2.GaussianBlur(weighted_target,(0,0),3)/np.maximum(fine_weight,1e-5)
    fine_luma=(fine_changed@luma+.015)/(fine_base@luma+.015)
    direction=(fine_luma/np.maximum(illumination[...,0],1e-3)).clip(.85,1.15)[...,None]
    gain=(illumination*tint*direction).clip(.65,1.55)
    gain=np.where(weight>.08,gain,1).astype(np.float32)
    return gain

def directional_gain_map(original, reference, alpha):
    """Keep spatial RelightVid residual while removing its global exposure and tint."""
    gain=gain_map(original,reference,alpha,background=None)
    valid=small(alpha)>180
    if valid.sum()<16:
        return np.ones_like(gain)
    global_gain=np.exp(np.median(np.log(gain[valid].clip(.65,1.55)),axis=0))
    residual=(gain/np.maximum(global_gain.reshape(1,1,3),1e-3)).clip(.75,1.35)
    return np.where(valid[...,None],residual,1).astype(np.float32)


def stabilize_gain(gain, previous, previous_frame, current_frame, *, history=.35):
    """Blend history after backward-flow alignment into current frame coordinates."""
    if previous is None or previous_frame is None:
        return gain
    prior=small(previous_frame)
    current=small(current_frame)
    prior_gray=cv2.cvtColor(prior,cv2.COLOR_BGR2GRAY)
    current_gray=cv2.cvtColor(current,cv2.COLOR_BGR2GRAY)
    flow=cv2.calcOpticalFlowFarneback(
        current_gray,prior_gray,None,.5,3,15,3,5,1.2,0)
    yy,xx=np.indices(current_gray.shape,dtype=np.float32)
    aligned=cv2.remap(previous,xx+flow[...,0],yy+flow[...,1],cv2.INTER_LINEAR,
                      borderMode=cv2.BORDER_CONSTANT,borderValue=(1,1,1))
    active=np.any(np.abs(gain-1)>.005,axis=2)
    if active.sum()<32:
        return gain
    unaligned_error=float(np.mean(np.abs(previous[active]-gain[active])))
    aligned_error=float(np.mean(np.abs(aligned[active]-gain[active])))
    # Reject uncertain flow instead of dragging stale illumination across the subject.
    if aligned_error>=.95*unaligned_error or aligned_error>.08:
        return gain
    return history*aligned+(1-history)*gain


def apply_gain(original, alpha, gain, strength, *, reference_confidence=0.):
    if strength==0 or not np.any(alpha):
        return original.copy()
    gain=cv2.resize(gain,(original.shape[1],original.shape[0]))
    hsv=cv2.cvtColor(original,cv2.COLOR_BGR2HSV)
    confidence=float(np.clip(reference_confidence,0,1))
    protection=np.ones(alpha.shape,np.float32)
    dark=hsv[...,2]<35;saturated=hsv[...,1]>190
    # Even a trusted generative reference does not fully override material protection.
    protection[dark]=np.minimum(protection[dark],.6+.3*confidence)
    protection[saturated]=np.minimum(protection[saturated],.8+.1*confidence)
    effective=(strength*protection)[...,None]
    changed=linear(original)*(1+effective*(gain-1))
    adjusted=_SRGB[(changed.clip(0,1)*65535).round().astype(np.uint16)]
    # Keep straight foreground RGB corrected through soft matte edges. Alpha is
    # consumed exactly once later by compose_frame().
    out=original.copy()
    out[alpha>0]=adjusted[alpha>0]
    return out


def prepare(composite, background, body_masks, mask_record):
    source=ex.local(composite,exists=True);background=ex.local(background,exists=True)
    info,_=rel.video_info(source);bg_info,_=rel.video_info(background)
    if (info['frames']!=bg_info['frames'] or info['size']!=bg_info['size']
            or abs(info['fps']-bg_info['fps'])>1e-4):
        raise ValueError('합성 영상과 원본 배경의 프레임·FPS·해상도가 다릅니다.')
    if ex.digest(background)!=mask_record['source_sha256'] or ex.local(mask_record['source'])!=background:
        raise ValueError('기준 배경 사람의 추적 기록이 다릅니다.')
    composition=ex.read_json(ex.local(source.with_suffix('.composition.json'),exists=True))
    source_hash=ex.digest(source)
    if source_hash!=composition['source_sha256']:
        raise ValueError('합성 영상이 바뀌었습니다. E-4 합성을 다시 실행하세요.')
    alpha_dir=ex.local(composition['alpha_dir'],exists=True)
    ex.validate_masks(body_masks,mask_record)
    manifest={**info,'source':str(source.relative_to(rel.ROOT)),'source_sha256':source_hash,
        'background':str(background.relative_to(rel.ROOT)),'background_sha256':ex.digest(background),
        'alpha_dir':str(alpha_dir.relative_to(rel.ROOT)),'body_masks':str(ex.local(body_masks).relative_to(rel.ROOT)),
        'policy':POLICY,'steps':20,'seed':43}
    for name in ('alpha_dir','body_masks'):
        manifest[name+'_sha256']=sequence_hash(manifest[name],info['frames'])
    # Invalidate cached choices if the implementation or model file changes.
    model=ex.local(f'{rel.VENDOR}/checkpoints/UniRelight/model.pt',exists=True)
    manifest['model_stamp']=[model.stat().st_size,model.stat().st_mtime_ns]
    manifest['code_sha256']=ex.digest(__file__)
    import json
    key=hashlib.sha256(json.dumps(manifest,sort_keys=True).encode()).hexdigest()
    cache=ex.local(f'work/auto_relighting/{key}');cache.mkdir(parents=True,exist_ok=True)
    path=cache/'plan.json'
    if path.exists():
        saved=ex.read_json(path)
        if ex.local(saved['probe']).is_file():
            print('자동 분석 기록 재사용:',path)
            return saved
    indices=np.unique(np.linspace(0,info['frames']-1,min(9,info['frames']),dtype=int))
    observations=[];visible=[]
    for i in indices:
        alpha=mask_at(alpha_dir,int(i));body=mask_at(body_masks,int(i))
        if alpha.shape != tuple(reversed(info['size'])):
            raise ValueError('합성 알파 해상도 불일치')
        a=descriptor(small(frame(source,int(i))),small(alpha))
        b=descriptor(small(frame(background,int(i))),small(body))
        if a is not None and b is not None:
            observations.append((a,b));visible.append(int(i))
    if not observations:
        raise ValueError('전경과 기준 배경 사람이 함께 보이는 평가 프레임이 없습니다.')
    src=np.median([p[0] for p in observations],axis=0)
    target=np.median([p[1] for p in observations],axis=0)
    exposure=float(np.clip(np.mean(target[:3]-src[:3])/np.log(2),-.75,.75))
    yaw=float(np.clip(-target[3]*90,-90,90))
    tint=np.exp(target[:3]-np.mean(target[:3])).clip(.85,1.15).tolist()
    candidates=[{'environment':'automatic_studio','yaw':float(yaw+d),'exposure':float(exposure+ev),
                 'tint':tint} for d,ev in [(0,0),(-60,-.35),(60,.35)]]
    center=visible[len(visible)//2]
    start=max(0,min(center-28,info['frames']-57));end=min(info['frames'],start+57)
    probe=cache/'probe.mp4'
    def frames():
        cap=cv2.VideoCapture(str(source));cap.set(cv2.CAP_PROP_POS_FRAMES,start)
        try:
            for _ in range(start,end):
                ok,image=cap.read()
                if not ok:raise ValueError('후보 평가 구간 디코딩 실패')
                yield image
        finally:cap.release()
    if not probe.exists():ex.encode_frames(frames(),probe,info['fps'])
    plan={**manifest,'work':str(cache.relative_to(rel.ROOT)),'probe':str(probe.relative_to(rel.ROOT)),
          'probe_start':start,'probe_frames':end-start,'candidates':candidates,'status':'prepared',
          'analysis_frames':visible,'analysis':'masked RGB brightness/chroma and left-right contrast heuristic'}
    ex.write_json(path,plan)
    return plan


def metrics(original, adjusted, reference, alpha, target):
    description=descriptor(adjusted,alpha)
    if description is None:return None
    mismatch=float(np.mean(np.abs(description[:3]-target[:3]))+.2*abs(description[3]-target[3]))
    valid=alpha>180
    gray=lambda im:cv2.cvtColor(im,cv2.COLOR_BGR2GRAY).astype(np.float32)/255
    before=cv2.Laplacian(gray(original),cv2.CV_32F)
    after=cv2.Laplacian(gray(adjusted),cv2.CV_32F)
    detail=float(np.mean(np.abs(after[valid]-before[valid])))
    artifact=float(np.mean(np.abs(cv2.Laplacian(gray(reference),cv2.CV_32F)[valid]-before[valid])))
    clipping=float(np.mean((adjusted[valid]<3)|(adjusted[valid]>252)))
    return mismatch+2*detail+.1*artifact+.5*clipping


def evaluate(plan, probe_result):
    start=plan['probe_start'];count=plan['probe_frames']
    # Adjacent pairs at the beginning/middle/end make the flicker term meaningful.
    indices=sorted({min(count-1,i+j) for i in (0,count//2,max(0,count-2)) for j in (0,1)})
    samples=[]
    for i in indices:
        original=small(frame(plan['source'],start+i));alpha=small(mask_at(plan['alpha_dir'],start+i))
        target=descriptor(small(frame(plan['background'],start+i)),small(mask_at(plan['body_masks'],start+i)))
        if target is not None and descriptor(original,alpha) is not None:
            samples.append((i,original,alpha,target))
    if not samples:raise ValueError('후보 구간에서 평가할 전경/배경 사람이 없습니다.')
    baseline=np.mean([metrics(o,o,o,a,t) for _,o,a,t in samples])
    rows=[{'candidate':None,'strength':0.,'score':float(baseline)}]
    for candidate in range(len(plan['candidates'])):
        references={i:small(frame(ex.local(probe_result)/f'relit_{candidate:04d}.mp4',i)) for i,*_ in samples}
        for strength in (.25,.5,.75):
            values=[];flicker=[];last=None
            for i,original,alpha,target in samples:
                ref=references[i];gain=gain_map(original,ref,alpha)
                adjusted=apply_gain(original,alpha,gain,strength)
                value=metrics(original,adjusted,ref,alpha,target)
                values.append(value)
                if last is not None and i==last[0]+1:
                    overlap=(alpha>180)&(last[2]>180)
                    if overlap.any():flicker.append(float(np.mean(np.abs(gain[overlap]-last[1][overlap])))*strength)
                last=(i,gain,alpha)
            score=float(np.mean(values)+.5*np.mean(flicker or [0])+.02*strength)
            rows.append({'candidate':candidate,'strength':strength,'score':score})
    best=min(rows,key=lambda row:row['score'])
    # Small improvements are too uncertain to justify altering the person's appearance.
    if best['score']>baseline*.97:best=rows[0]
    return {'selected':best,'evaluations':rows,'baseline_score':float(baseline),
            'metric':'background masked brightness/chroma/direction + detail + clipping + flicker; heuristic'}


def choose(plan, gpu):
    validate(plan);path=ex.local(plan['work'])/'plan.json'
    current=ex.read_json(path)
    if current.get('status') in ('selected','complete'):
        return current
    started=time.perf_counter()
    lights=[(rel.edit_hdr(rel.studio_map(),c['yaw'],c['exposure'],c['tint']),c) for c in plan['candidates']]
    probe=current.get('probe_result')
    if probe is None or not all(ex.local(probe).joinpath(f'relit_{i:04d}.mp4').is_file() for i in range(len(lights))):
        probe=rel.run(plan['probe'],lights[0][0],{'automatic_probe':True},steps=20,seed=43,gpu=gpu,candidate_lights=lights)
        current['probe_result']=str(ex.local(probe).relative_to(rel.ROOT));ex.write_json(path,current)
    choice=evaluate(plan,probe)
    current.update(choice=choice,status='selected',selection_seconds=round(time.perf_counter()-started,3))
    ex.write_json(path,current)
    return current


def finish(plan,gpu):
    validate(plan);path=ex.local(plan['work'])/'plan.json';plan=ex.read_json(path)
    if plan.get('status')=='complete' and ex.local(plan['final_video']).is_file():
        if plan.get('audio_source')!=plan['background']:
            out=ex.unique_dir('output/auto_relighting','result')
            video=ex.add_audio(plan['final_video'],plan['background'],out/'relit.mp4')
            plan.update(final_video=str(video.relative_to(rel.ROOT)),audio=ex.has_audio(video),audio_source=plan['background'])
            ex.write_json(out/'experiment.json',plan);ex.write_json(path,plan)
        print('자동 조명 최종 결과 재사용:',plan['final_video']);return ex.local(plan['final_video'])
    if plan.get('status') not in ('selected','complete'):raise ValueError('자동 후보 평가 셀을 먼저 실행하세요.')
    selected=plan['choice']['selected'];strength=selected['strength'];reference=None
    if strength:
        c=plan['candidates'][selected['candidate']]
        cached=plan.get('full_reference')
        if cached is None or not ex.local(cached).joinpath('relit.mp4').is_file():
            hdr=rel.edit_hdr(rel.studio_map(),c['yaw'],c['exposure'],c['tint'])
            cached=rel.run(plan['source'],hdr,c,steps=20,seed=43,gpu=gpu)
            plan['full_reference']=str(ex.local(cached).relative_to(rel.ROOT));ex.write_json(path,plan)
        reference=ex.local(cached)/'relit.mp4'
    out=ex.unique_dir('output/auto_relighting','result');started=time.perf_counter()
    def frames():
        cap=cv2.VideoCapture(str(ex.local(plan['source'])))
        ref=cv2.VideoCapture(str(reference)) if reference is not None else None
        previous_gain=previous_frame=None
        try:
            for i in range(plan['frames']):
                ok,original=cap.read()
                if not ok:raise ValueError('합성 영상 프레임 누락')
                alpha=mask_at(plan['alpha_dir'],i)
                if ref is None:yield original;continue
                ok,changed=ref.read()
                if not ok:raise ValueError('조명 참고 프레임 누락')
                gain=gain_map(original,changed,alpha)
                if np.any(alpha):
                    gain=stabilize_gain(gain,previous_gain,previous_frame,original)
                yield apply_gain(original,alpha,gain,strength)
                if np.any(alpha):
                    previous_gain,previous_frame=gain,original
                else:
                    previous_gain=previous_frame=None
        finally:
            cap.release()
            if ref is not None:ref.release()
    stream=frames()
    try:ex.encode_frames(stream,out/'relit.mp4',plan['fps'],audio_source=plan['background'])
    finally:stream.close()
    info,_=rel.video_info(out/'relit.mp4')
    if info['frames']!=plan['frames'] or info['size']!=plan['size'] or abs(info['fps']-plan['fps'])>1e-4:
        raise ValueError('자동 조명 결과 프레임·FPS·해상도 보존 실패')
    plan.update(status='complete',final_video=str((out/'relit.mp4').relative_to(rel.ROOT)),
                export_seconds=round(time.perf_counter()-started,3),audio=ex.has_audio(out/'relit.mp4'),audio_source=plan['background'])
    ex.write_json(out/'experiment.json',plan);ex.write_json(path,plan)
    return out/'relit.mp4'
