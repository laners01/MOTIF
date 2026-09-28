"""Keep the continuous SVOR background and restore the original remaining person."""
import cv2,numpy as np,time
from source.util import removal_experiments as ex
from source.relighting import model_test as rel


def raw_background(background,record):
    """Recover the SVOR result from either a raw or preserved background."""
    if background is None:
        raise RuntimeError('[2-2] 배경 제거 셀을 먼저 실행하세요.')
    path=ex.local(background,exists=True);seen=set()
    while (path.parent/'preservation.json').is_file():
        if path in seen:raise ValueError('배경 보존 기록이 순환 참조합니다.')
        seen.add(path)
        saved=ex.read_json(path.parent/'preservation.json')
        path=ex.local(saved['inpaint'],exists=True)
    saved=ex.read_json(ex.local(path.parent/'experiment.json',exists=True))
    original=saved['experiment']
    if (saved['status']!='complete' or
        any(original.get(k)!=record.get(k) for k in ('source_sha256','selected_obj_id','work')) or
        ex.local(saved['result'])!=path):
        raise ValueError('현재 배경/제거 대상과 인페인팅 기록이 다릅니다. [2-2]를 다시 실행하세요.')
    return path


def combine(original,inpaint,mask,keep=None):
    # Keep the spatially continuous SVOR background. Cutting it along A's
    # silhouette reintroduces a visible low-frequency color boundary.
    if keep is None:return inpaint.copy()
    protected=keep>127
    if not protected.any():return inpaint.copy()
    # Original B is exact. Blend only a narrow exterior band into SVOR;
    # never bring original A pixels back through this band.
    distance=cv2.distanceTransform((~protected).astype(np.uint8),cv2.DIST_L2,3)
    radius=max(3,min(mask.shape)*.012)
    weight=np.clip(1-distance/radius,0,1)
    weight=weight*weight*(3-2*weight)
    weight[(mask>127)&~protected]=0
    out=(original*weight[:,:,None]+inpaint*(1-weight[:,:,None])).round().astype(np.uint8)
    out[protected]=original[protected]
    return out


def prepare(source,inpaint,masks,record,keep_masks=None):
    source=ex.local(source,exists=True);inpaint=ex.local(inpaint,exists=True)
    if ex.digest(source)!=record['source_sha256']:raise ValueError('원본 영상 변경')
    paths=ex.validate_masks(masks,record)
    keep=ex.validate_masks(keep_masks,record) if keep_masks is not None else None
    info,_=rel.video_info(inpaint)
    if info['frames']!=record['frames'] or info['size']!=record['size'] or abs(info['fps']-record['fps'])>1e-4:
        raise ValueError('인페인팅/원본 프레임 또는 해상도/FPS 불일치')
    work=ex.unique_dir('output/run_enhancement','preserved_background');out=work/'background.mp4'
    cap=cv2.VideoCapture(str(source));filled=cv2.VideoCapture(str(inpaint));started=time.perf_counter()
    def frames():
        try:
            for i,path in enumerate(paths):
                ok,a=cap.read();ok2,b=filled.read()
                if not ok or not ok2:raise ValueError('배경 프레임 디코딩 실패')
                yield combine(a,b,cv2.imread(str(path),0),None if keep is None else cv2.imread(str(keep[i]),0))
        finally:cap.release();filled.release()
    ex.encode_frames(frames(),out,record['fps'])
    ex.write_json(work/'preservation.json',dict(source=str(source.relative_to(ex.ROOT)),
        inpaint=str(inpaint.relative_to(ex.ROOT)),masks=str(ex.local(masks).relative_to(ex.ROOT)),
        keep_masks=None if keep_masks is None else str(ex.local(keep_masks).relative_to(ex.ROOT)),
        source_sha256=record['source_sha256'],frames=record['frames'],fps=record['fps'],
        seconds=time.perf_counter()-started,outside_mask='continuous SVOR background; original B restored before encoding',
        codec='H264 lossless YUV444; RGB conversion may round',
        method='continuous-svor-original-b-v2',
        color_alignment='no A-shaped color correction; feather only outside B'))
    return out
