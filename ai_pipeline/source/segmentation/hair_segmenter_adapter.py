from __future__ import annotations

import os
from pathlib import Path
from typing import Iterator

import cv2
import numpy as np
import torch
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
MPL_CACHE = ROOT / ".cache" / "matplotlib"
MPL_CACHE.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("MPLCONFIGDIR", str(MPL_CACHE))


class SAM2HairVideoPredictor:
    """Add MediaPipe HairSegmenter detail to a SAM 2.1 Large video mask."""

    # Category masks are binary. Confidence is used only as a conservative
    # second gate so weak background predictions never reach the SAM mask.
    returns_alpha = False
    HAIR_CATEGORY_ID = 1
    MIN_HAIR_CONFIDENCE = 0.90

    def __init__(self, sam_predictor, hair_model_path: Path) -> None:
        import mediapipe as mp
        from mediapipe.tasks import python
        from mediapipe.tasks.python import vision

        options = vision.ImageSegmenterOptions(
            base_options=python.BaseOptions(model_asset_path=str(hair_model_path)),
            running_mode=vision.RunningMode.IMAGE,
            output_confidence_masks=True,
            output_category_mask=True,
        )
        self.sam_predictor = sam_predictor
        self.segmenter = vision.ImageSegmenter.create_from_options(options)
        self.mp = mp
        self.hair_frames_refined = 0
        self.hair_added_pixels_total = 0

    def close(self) -> None:
        self.segmenter.close()

    def init_state(self, video_path: str, **kwargs) -> dict:
        state = self.sam_predictor.init_state(video_path=video_path, **kwargs)
        frame_dir = Path(video_path)
        state["_hair_frame_paths"] = sorted(
            path
            for path in frame_dir.iterdir()
            if path.suffix.lower() in {".jpg", ".jpeg", ".png"}
        )
        return state

    def reset_state(self, inference_state: dict) -> None:
        self.sam_predictor.reset_state(inference_state)
        self.hair_frames_refined = 0
        self.hair_added_pixels_total = 0

    def add_new_points_or_box(self, **kwargs):
        return self.sam_predictor.add_new_points_or_box(**kwargs)

    @staticmethod
    def _crop_bounds(mask: np.ndarray) -> tuple[int, int, int, int] | None:
        ys, xs = np.nonzero(mask)
        if not len(xs):
            return None
        height, width = mask.shape
        x0, x1 = int(xs.min()), int(xs.max()) + 1
        y0, y1 = int(ys.min()), int(ys.max()) + 1
        box_width = x1 - x0
        box_height = y1 - y0
        pad_x = max(12, round(box_width * 0.18))
        pad_top = max(12, round(box_height * 0.18))
        pad_bottom = max(8, round(box_height * 0.08))
        return (
            max(0, x0 - pad_x),
            max(0, y0 - pad_top),
            min(width, x1 + pad_x),
            min(height, y1 + pad_bottom),
        )

    @staticmethod
    def _connected_hair_category(
        hair_category: np.ndarray,
        hair_probability: np.ndarray,
        person_mask: np.ndarray,
    ) -> np.ndarray:
        # Category is the primary decision. The high confidence gate, mandatory
        # overlap with SAM, and limited expansion suppress background islands.
        candidate = (hair_category == SAM2HairVideoPredictor.HAIR_CATEGORY_ID) & (
            hair_probability >= SAM2HairVideoPredictor.MIN_HAIR_CONFIDENCE
        )
        if not candidate.any():
            return np.zeros_like(hair_probability, dtype=np.float32)

        box_height = max(1, int(np.count_nonzero(person_mask.any(axis=1))))
        radius = max(2, min(10, round(box_height * 0.010)))
        kernel = cv2.getStructuringElement(
            cv2.MORPH_ELLIPSE, (radius * 2 + 1, radius * 2 + 1)
        )
        allowed_zone = cv2.dilate(person_mask.astype(np.uint8), kernel) > 0
        count, labels, stats, _ = cv2.connectedComponentsWithStats(
            candidate.astype(np.uint8), connectivity=8
        )
        accepted = np.zeros_like(candidate)
        for component_id in range(1, count):
            area = int(stats[component_id, cv2.CC_STAT_AREA])
            if area < 3:
                continue
            component = labels == component_id
            overlap = int(np.count_nonzero(component & person_mask))
            if overlap < max(3, round(area * 0.05)):
                continue
            accepted |= component & allowed_zone

        return accepted.astype(np.float32)

    def _refine_mask(self, frame_path: Path, person_mask: np.ndarray) -> np.ndarray:
        bounds = self._crop_bounds(person_mask)
        base_alpha = person_mask.astype(np.float32)
        if bounds is None:
            return base_alpha

        x0, y0, x1, y1 = bounds
        image = np.asarray(Image.open(frame_path).convert("RGB"))
        crop = np.ascontiguousarray(image[y0:y1, x0:x1])
        result = self.segmenter.segment(
            self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=crop)
        )
        if result.category_mask is None or len(result.confidence_masks) < 2:
            return base_alpha

        hair_category = np.asarray(
            result.category_mask.numpy_view(), dtype=np.uint8
        ).squeeze()
        hair_probability = np.asarray(
            result.confidence_masks[1].numpy_view(), dtype=np.float32
        ).squeeze()
        crop_person = person_mask[y0:y1, x0:x1]
        hair_mask = self._connected_hair_category(
            hair_category, hair_probability, crop_person
        )
        added = hair_mask * (~crop_person)
        base_alpha[y0:y1, x0:x1] = np.maximum(
            base_alpha[y0:y1, x0:x1], hair_mask
        )
        self.hair_frames_refined += 1
        self.hair_added_pixels_total += int(np.count_nonzero(added > 0.05))
        return base_alpha

    @torch.inference_mode()
    def propagate_in_video(
        self,
        inference_state: dict,
        start_frame_idx: int = 0,
        reverse: bool = False,
        **kwargs,
    ) -> Iterator[tuple[int, list[int], torch.Tensor]]:
        frame_paths = inference_state["_hair_frame_paths"]
        iterator = self.sam_predictor.propagate_in_video(
            inference_state,
            start_frame_idx=start_frame_idx,
            reverse=reverse,
            **kwargs,
        )
        for frame_index, object_ids, logits in iterator:
            object_ids = list(object_ids)
            combined = (logits > 0.0).detach().float().cpu()
            if 1 in object_ids:
                position = object_ids.index(1)
                person_mask = combined[position, 0].numpy().astype(bool)
                refined = self._refine_mask(frame_paths[int(frame_index)], person_mask)
                combined[position, 0] = torch.from_numpy(refined)
            yield int(frame_index), object_ids, combined


def build_sam2_hair_predictor(sam_predictor, hair_model_path: Path):
    if not hair_model_path.exists():
        raise RuntimeError(
            f"HairSegmenter 모델이 없습니다: {hair_model_path}. setup.sh를 실행해 주세요."
        )
    return SAM2HairVideoPredictor(sam_predictor, hair_model_path)
