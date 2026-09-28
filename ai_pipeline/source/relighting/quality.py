"""Conservative same-subject illumination transfer, not a physical lighting metric."""
import cv2
import numpy as np
from source.relighting import automatic as a
from source.util import removal_experiments as ex

POLICY = 'same-subject-harmonization-v9'


def gray(image):
    return cv2.cvtColor(image,cv2.COLOR_BGR2GRAY).astype(np.float32)/255


def structure(first,second,valid):
    """Gradient cosine ignores uniform exposure scaling, unlike raw Laplacian error."""
    gradients = []
    for image in (first,second):
        im = gray(image)
        gradients.append(np.stack([cv2.Sobel(im,cv2.CV_32F,1,0),
                                   cv2.Sobel(im,cv2.CV_32F,0,1)],axis=-1)[valid].ravel())
    x,y = gradients
    norm = float(np.linalg.norm(x)*np.linalg.norm(y))
    return float(np.clip(np.dot(x,y)/norm,0,1)) if norm>1e-8 else 0.


def frame_file(folder,index):
    image=cv2.imread(str(ex.local(folder,exists=True)/f'{index:06d}.png'))
    if image is None: raise ValueError(f'무손실 프레임 누락: {folder} frame {index}')
    return image


def evaluate(plan,folder):
    # Distributed samples; no other person's RGB is used as a target.
    count=plan['probe_frames'];start=plan['probe_start']
    indices=sorted({min(count-1,int(i)+j) for i in np.linspace(0,max(0,count-2),min(16,count)) for j in (0,1)})
    samples=[]
    for i in indices:
        original=a.small(frame_file(plan['source_frames'],start+i))
        alpha=a.small(a.mask_at(plan['alpha_dir'],start+i))
        valid=cv2.erode((alpha>240).astype(np.uint8),np.ones((3,3),np.uint8))>0
        if valid.sum()<16:continue
        reference=a.small(frame_file(ex.local(folder)/'relit_frames',start+i))
        confidence=structure(original,reference,valid)
        gain=a.directional_gain_map(original,reference,alpha)
        samples.append((i,original,alpha,valid,confidence,gain))
    if not samples:raise ValueError('조명 평가에 필요한 전경 내부 영역이 없습니다.')
    strengths=(0.,.25,.5,.75);rows=[];local_costs={}
    for strength in strengths:
        values=[];flicker=[];last=None;local=[]
        for i,original,alpha,valid,confidence,gain in samples:
            trust=float(np.clip((confidence-.25)/.5,0,1))
            adjusted=a.apply_gain(original,alpha,gain,strength,reference_confidence=trust)
            target=np.log(1+trust*(gain-1))
            correction=np.log((a.linear(adjusted)+.015)/(a.linear(original)+.015))
            fidelity=float(np.mean(np.abs(correction[valid]-target[valid])))
            detail=1-structure(original,adjusted,valid)
            new_clip=((adjusted<3)|(adjusted>252))&~((original<3)|(original>252))
            clipping=float(np.mean(new_clip[valid]))
            unreliable=(1-trust)*float(np.mean(np.abs(correction[valid])))
            components=np.array([fidelity,.25*detail,5*clipping,unreliable],np.float64)
            values.append(components);local.append(float(components.sum()))
            if last is not None and i==last[0]+1:
                current_gray=cv2.cvtColor(original,cv2.COLOR_BGR2GRAY)
                prior_gray=cv2.cvtColor(last[1],cv2.COLOR_BGR2GRAY)
                flow=cv2.calcOpticalFlowFarneback(current_gray,prior_gray,None,.5,3,15,3,5,1.2,0)
                yy,xx=np.indices(valid.shape,dtype=np.float32);mx,my=xx+flow[...,0],yy+flow[...,1]
                prior=cv2.remap(last[2],mx,my,cv2.INTER_LINEAR)
                prior_mask=cv2.remap(last[3].astype(np.uint8),mx,my,cv2.INTER_NEAREST)>0
                overlap=valid&prior_mask&(mx>=0)&(mx<valid.shape[1]-1)&(my>=0)&(my<valid.shape[0]-1)
                if overlap.any():flicker.append(float(np.mean(np.abs(correction[overlap]-prior[overlap]))))
            last=(i,original,correction,valid)
        mean=np.mean(values,axis=0)
        components=dict(zip(('illumination_residual','normalized_detail','new_clipping','unreliable_reference'),map(float,mean)))
        components['motion_aligned_flicker']=float(np.mean(flicker or [0]))
        rows.append(dict(candidate=0 if strength else None,strength=strength,score=sum(components.values()),components=components))
        local_costs[strength]=local
    best=min(rows,key=lambda row:row['score']);improvement=rows[0]['score']-best['score']
    if improvement<max(1e-4,.01*rows[0]['score']):best=rows[0]
    scene_confidence=float(plan.get('scene_harmonization_confidence',0.))
    keyframes=[]
    if best['strength']:
        # Dynamic programming keeps local adaptation but penalizes rapid strength changes.
        costs=np.array([local_costs[value] for value in strengths]).T
        dp=np.empty_like(costs);back=np.zeros(costs.shape,dtype=np.int16);dp[0]=costs[0]
        for t in range(1,len(samples)):
            seconds=max((samples[t][0]-samples[t-1][0])/plan['fps'],.25)
            transition=.015/seconds*((np.subtract.outer(strengths,strengths)/.25)**2)
            options=dp[t-1][:,None]+transition
            back[t]=np.argmin(options,axis=0);dp[t]=costs[t]+options[back[t],np.arange(len(strengths))]
        state=int(np.argmin(dp[-1]));path=[]
        for t in range(len(samples)-1,-1,-1):
            path.append(strengths[state]);state=int(back[t,state]) if t else state
        path=path[::-1]
        keyframes=[{'frame':int(sample[0]+start),'strength':float(value)} for sample,value in zip(samples,path)]
        selected={**best,'mode':'model','strength':float(np.median(path))}
        reason='시간 연속성 제약으로 구간별 RelightVid 조명 변화량 채택'
    elif scene_confidence>0:
        selected={**best,'candidate':'scene_harmonization','mode':'base_harmonization_only','strength':0.}
        reason='RelightVid 방향성 residual 거부 · deterministic scene harmonization만 유지'
    else:
        selected={**best,'mode':'original','strength':0.}
        reason='RelightVid 방향성 residual 거부 · scene 분석 근거도 없어 원본 유지'
    return dict(policy=POLICY,selected=selected,strength_keyframes=keyframes,evaluations=rows,
                baseline_score=rows[0]['score'],evaluation_frames=[sample[0]+start for sample in samples],
                reference_structure_confidence=float(np.mean([sample[4] for sample in samples])),
                fallback=dict(applied=selected['mode']=='base_harmonization_only',
                              confidence=scene_confidence,median_gain_delta=0.,
                              type='deterministic_scene_harmonization_only'),reason=reason,
                metric='confidence-weighted illumination transfer + adaptive protection + temporal strength regularization; heuristic')
