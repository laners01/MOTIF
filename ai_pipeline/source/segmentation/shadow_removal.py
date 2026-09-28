"""VISD or brightness shadow proposals, followed by SAM2 mask tracking."""
import cv2
import numpy as np
from source.util import removal_experiments as ex


def propose_shadow(rgb, body, others, *, darkness=0.78, reach=1.5, min_area=40, polygons=()):
    if not 0 < darkness < 1 or reach <= 0:
        raise ValueError("darkness는 0~1 사이, reach는 양수여야 합니다.")
    body = np.asarray(body, bool)
    others = np.asarray(others, bool)
    h, w = body.shape
    ys, xs = np.where(body)
    if not len(xs):
        raise ValueError("선택한 사람의 SAM2 마스크가 비어 있습니다.")
    bw, bh = xs.max()-xs.min()+1, ys.max()-ys.min()+1
    x0, x1 = max(0, int(xs.min()-reach*bw)), min(w, int(xs.max()+reach*bw)+1)
    y0, y1 = max(0, int(ys.max()-0.3*bh)), min(h, int(ys.max()+0.5*bh)+1)
    roi = np.zeros((h, w), bool); roi[y0:y1, x0:x1] = True
    available = roi & ~body & ~others
    luminance = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB)[..., 0].astype(float)
    reference = float(np.percentile(luminance[available], 75)) if available.any() else 0
    dark = ((luminance < reference*darkness) & available).astype(np.uint8)
    dark = cv2.morphologyEx(dark, cv2.MORPH_CLOSE, np.ones((3, 3), np.uint8))
    dark[~available] = 0
    feet = body.copy(); feet[:max(0, int(ys.max()-0.08*bh))] = False
    radius = max(3, int(bw*0.3))
    near_feet = cv2.dilate(feet.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (radius*2+1, radius*2+1))) > 0
    count, labels, stats, _ = cv2.connectedComponentsWithStats(dark, 8)
    shadow = np.zeros_like(body)
    rejected = 0
    for label in range(1, count):
        region = labels == label
        if stats[label, cv2.CC_STAT_AREA] < min_area or not (region & near_feet).any():
            continue
        # Large connected dark floors should not silently become removal masks.
        if stats[label, cv2.CC_STAT_AREA] > min(body.sum()*2, available.sum()*0.65):
            rejected += 1
            continue
        shadow |= region
    for polygon in polygons:
        pts = np.asarray(polygon, dtype=float)
        if pts.ndim != 2 or pts.shape[1] != 2 or len(pts) < 3 or not np.isfinite(pts).all():
            raise ValueError("다각형은 [[x,y], ...] 형태의 점 3개 이상이어야 합니다.")
        if (pts < 0).any() or (pts[:, 0] >= w).any() or (pts[:, 1] >= h).any():
            raise ValueError("다각형 좌표가 영상 범위를 벗어납니다.")
        manual = np.zeros((h, w), np.uint8)
        cv2.fillPoly(manual, [np.rint(pts).astype(np.int32)], 1)
        shadow |= manual.astype(bool)
    shadow &= ~body & ~others
    return shadow, {"method": "foot_connected_dark_region", "darkness": darkness,
        "reach": reach, "reference_luminance": reference, "rejected_large_regions": rejected,
        "polygons": list(polygons), "roi": [x0, y0, x1, y1]}


def prepare(tracker, target_id, *, method="heuristic", **options):
    import torch
    s = tracker.SESSION
    if s.predictor is None or s.inference_state is None:
        raise RuntimeError("사람 검출 셀부터 다시 실행하세요.")
    if target_id not in [d['obj_id'] for d in s.detections]:
        raise ValueError("검출된 사람의 ID를 선택하세요.")
    if method not in {"heuristic", "visd"}:
        raise ValueError(f"알 수 없는 그림자 추출 방식: {method}")
    if method == "visd":
        from source.segmentation import visd_adapter
        visd_adapter.require_assets()
    frame_index = s.prompt_frame
    with torch.inference_mode(), tracker.autocast_ctx():
        s.predictor.reset_state(s.inference_state)
        for detection in s.detections:
            _, ids, logits = s.predictor.add_new_points_or_box(inference_state=s.inference_state,
                frame_idx=frame_index, obj_id=detection['obj_id'], box=np.asarray(detection['box'], np.float32))
        ids = list(ids)
        masks = (logits[:, 0] > 0).detach().cpu().numpy()
        body = masks[ids.index(target_id)]
        others = np.any(masks[[i for i, ident in enumerate(ids) if ident != target_id]], axis=0) if len(ids)>1 else np.zeros_like(body)
        rgb = cv2.cvtColor(cv2.imread(str(ex.local(s.frame_paths[frame_index], exists=True))), cv2.COLOR_BGR2RGB)
        if method == "visd":
            other_masks = [masks[i] for i, ident in enumerate(ids) if ident != target_id]
            shadow, settings = visd_adapter.predict(s.frame_paths[frame_index], body, other_masks,
                                                    tracker.WORK_DIR, **options)
        else:
            shadow, settings = propose_shadow(rgb, body, others, **options)
        shadow_id = max(ids)+1
        if shadow.any():
            _, ids, logits = s.predictor.add_new_mask(inference_state=s.inference_state,
                frame_idx=frame_index, obj_id=shadow_id, mask=shadow)
            refined = (logits[list(ids).index(shadow_id), 0] > 0).detach().cpu().numpy() & ~body & ~others
        else:
            refined = shadow.copy()
    s.selected_obj_id = target_id
    return {"target_id":target_id, "shadow_id":shadow_id, "frame_index":frame_index,
        "source":str(s.source), "state_id":id(s.inference_state), "rgb":rgb, "body":body,
        "proposal":shadow, "refined":refined, "settings":settings}


def preview(data):
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(1, 3, figsize=(15, 7))
    for ax, mask, title in zip(axes, [data['body'], data['proposal'], data['refined']],
                             ['Person', 'VISD associated shadow' if data['settings']['method'] == 'visd_instance_association' else 'Shadow candidate (heuristic)', 'SAM2 shadow prompt result']):
        rgb = data['rgb'].copy()
        rgb[mask] = (rgb[mask]*0.4+np.array([255, 60, 30])*0.6).astype(np.uint8)
        ax.imshow(rgb); ax.set_title(title); ax.axis('off')
    plt.show()
    print('그림자 후보 픽셀:', int(data['proposal'].sum()), '/ SAM2 그림자 픽셀:', int(data['refined'].sum()))
    if not data['proposal'].any():
        print('그림자 후보가 없습니다. 추출 방식과 기준 프레임을 확인하세요.')


def union_mask(body, shadow, others, dilation):
    from scipy.ndimage import binary_dilation
    union = np.asarray(body, bool) | np.asarray(shadow, bool)
    if dilation:
        union = binary_dilation(union, iterations=dilation)
    return union & ~np.asarray(others, bool)


def track(tracker, data, exp, *, dilation=8, progress=None):
    import torch
    s = tracker.SESSION
    if s.predictor is None or id(s.inference_state) != data['state_id'] or str(s.source) != data['source']:
        raise RuntimeError("그림자 후보 생성 셀부터 다시 실행하세요.")
    if not data['proposal'].any() or not data['refined'].any():
        raise ValueError("유효한 그림자 마스크가 없습니다. 미리보기 셀의 후보 설정을 조정하세요.")
    if not isinstance(dilation, int) or dilation < 0:
        raise ValueError("dilation은 0 이상의 정수여야 합니다.")
    output = ex.unique_dir(exp['work'], 'shadow_tracking')
    for folder in ['body_masks', 'shadow_masks', 'common_masks']:
        (output/folder).mkdir()
    done = set(); empty_shadow = []
    with torch.inference_mode(), tracker.autocast_ctx():
        for reverse in [False, True]:
            if reverse and data['frame_index']==0:
                continue
            for index, ids, logits in s.predictor.propagate_in_video(s.inference_state,
                    start_frame_idx=data['frame_index'], reverse=reverse):
                if index in done:
                    continue
                ids = list(ids)
                if data['target_id'] not in ids or data['shadow_id'] not in ids:
                    raise RuntimeError(f"프레임 {index}: 사람/그림자 ID가 추적 결과에서 사라졌습니다.")
                masks = (logits[:, 0] > 0).detach().cpu().numpy()
                body = masks[ids.index(data['target_id'])]
                shadow = masks[ids.index(data['shadow_id'])]
                protected = [i for i, ident in enumerate(ids) if ident not in [data['target_id'], data['shadow_id']]]
                others = np.any(masks[protected], axis=0) if protected else np.zeros_like(body)
                shadow = shadow & ~body & ~others
                if not shadow.any(): empty_shadow.append(int(index))
                common = union_mask(body, shadow, others, dilation)
                for folder, mask in [('body_masks',body),('shadow_masks',shadow),('common_masks',common)]:
                    if not cv2.imwrite(str(output/folder/f'{index:06d}.png'), mask.astype(np.uint8)*255):
                        raise OSError('마스크 저장 실패')
                done.add(int(index))
                if progress: progress(len(done)/exp['frames'], f"사람+그림자 추적 {len(done)}/{exp['frames']}")
    if done != set(range(exp['frames'])):
        raise RuntimeError('추적 프레임이 누락되었습니다.')
    settings = ex.mask_manifest(exp, kind='person_and_shadow', model=s.model_id,
        selected_obj_id=data['target_id'], shadow_obj_id=data['shadow_id'], anchor_frame=data['frame_index'],
        shadow_detection=data['settings'], dilation_iterations=dilation, empty_shadow_frames=empty_shadow,
        protected_other_people=True)
    for folder in ['body_masks','shadow_masks','common_masks']:
        ex.write_json(output/folder/'mask_manifest.json', dict(settings, mask_kind=folder))
        ex.validate_masks(output/folder, exp)
    if empty_shadow:
        print(f'주의: {len(empty_shadow)}개 프레임에서 그림자 추적 마스크가 비어 있습니다. 결과 마스크를 확인하세요.')
    return output/'common_masks'
