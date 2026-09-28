from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
import torch
from PIL import Image


@dataclass(frozen=True)
class PeopleDetection:
    """앵커 프레임 하나에서 검출한 사람 전원. boxes[i] ↔ confidences[i], id는 i+1."""

    frame_index: int
    boxes: list[np.ndarray]  # xyxy, confidence 내림차순 (id1 = 최고 confidence)
    confidences: list[float]
    annotated_rgb: np.ndarray  # 전원 박스 + id 라벨을 그린 프레임
    runtime: str


class RFDETRSmallPersonDetector:
    """RF-DETR-S person detector with TensorRT-first CUDA execution."""

    def __init__(self, device: str) -> None:
        self.device = device
        self.runtime = ""
        self.model = None
        self._is_inference_models = False

        if device == "cuda":
            try:
                from inference_models import AutoModel, BackendType

                self.model = AutoModel.from_pretrained(
                    "rfdetr-small", backend=BackendType.TRT
                )
                self.runtime = "TensorRT FP16 (CUDA)"
                self._is_inference_models = True
                return
            except Exception as exc:
                print(f"RF-DETR TensorRT 초기화 실패, PyTorch CUDA로 전환: {exc}")

        from rfdetr import RFDETRSmall

        # RF-DETR's current MPS path is not reliable, while SAM 2 itself can
        # continue to use MPS. Keep the detector portable by using CPU on Mac.
        detector_device = "cpu" if device == "mps" else device
        self.model = RFDETRSmall(device=detector_device)
        self.model.inference(
            compile=False,
            dtype=torch.float16 if detector_device == "cuda" else torch.float32,
            inplace=True,
        )
        self.runtime = (
            f"PyTorch {detector_device.upper()} "
            "(TensorRT FP16은 CUDA 서버에서 자동 사용)"
        )

    @staticmethod
    def _person_candidates(detections) -> list[tuple[np.ndarray, float]]:
        candidates: list[tuple[np.ndarray, float]] = []
        boxes = np.asarray(detections.xyxy)
        confidences = np.asarray(detections.confidence)
        class_ids = np.asarray(detections.class_id)
        class_names = None
        data = getattr(detections, "data", None)
        if isinstance(data, dict):
            class_names = data.get("class_name")

        for position, (box, confidence, class_id) in enumerate(
            zip(boxes, confidences, class_ids)
        ):
            is_person = int(class_id) == 0
            if class_names is not None and position < len(class_names):
                is_person = str(class_names[position]).casefold() == "person"
            if is_person:
                candidates.append((np.asarray(box, dtype=np.float32), float(confidence)))
        return candidates

    def detect(self, rgb: np.ndarray, threshold: float = 0.45):
        if self._is_inference_models:
            bgr = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
            prediction = self.model(bgr)[0]
            detections = prediction.to_supervision()
            candidates = [
                item
                for item in self._person_candidates(detections)
                if item[1] >= threshold
            ]
        else:
            detections = self.model.predict(Image.fromarray(rgb), threshold=threshold)
            candidates = self._person_candidates(detections)
        return candidates

    def find_people(
        self,
        frame_paths: list[Path],
        fps: float,
        threshold: float = 0.45,
        max_samples: int = 24,
    ) -> PeopleDetection:
        """사람이 가장 많이 잡히는 프레임을 앵커로 골라 그 프레임의 사람 전원을 반환한다.

        여러 명을 하나의 앵커 프레임에서 검출해야 SAM 2.1에 함께 등록해 서로 구분시킬 수 있다.
        동점(같은 인원수)이면 confidence 합이 큰 프레임을 고른다.
        """
        if not frame_paths:
            raise RuntimeError("검출할 프레임이 없습니다.")

        sample_stride = max(1, round(fps))
        sample_indices = list(range(0, len(frame_paths), sample_stride))
        if len(sample_indices) > max_samples:
            sample_indices = np.linspace(
                0, len(frame_paths) - 1, max_samples, dtype=int
            ).tolist()
        if len(frame_paths) - 1 not in sample_indices:
            sample_indices.append(len(frame_paths) - 1)

        # (인원수, confidence 합)이 최대인 프레임을 앵커로.
        best: tuple[tuple[int, float], int, np.ndarray, list[tuple[np.ndarray, float]]] | None = None
        for frame_index in dict.fromkeys(sample_indices):
            rgb = np.asarray(Image.open(frame_paths[frame_index]).convert("RGB"))
            candidates = self.detect(rgb, threshold=threshold)
            if not candidates:
                continue
            key = (len(candidates), float(sum(c for _b, c in candidates)))
            if best is None or key > best[0]:
                best = (key, frame_index, rgb, candidates)

        if best is None:
            raise RuntimeError(
                f"RF-DETR-S가 신뢰도 {threshold:.2f} 이상인 사람을 찾지 못했습니다."
            )

        _, frame_index, rgb, candidates = best
        height, width = rgb.shape[:2]
        # confidence 내림차순 → id1이 가장 또렷한 사람.
        candidates = sorted(candidates, key=lambda bc: bc[1], reverse=True)

        boxes: list[np.ndarray] = []
        confidences: list[float] = []
        annotated = rgb.copy()
        for position, (raw_box, confidence) in enumerate(candidates, start=1):
            box = raw_box.copy()
            box[[0, 2]] = np.clip(box[[0, 2]], 0, width - 1)
            box[[1, 3]] = np.clip(box[[1, 3]], 0, height - 1)
            boxes.append(box)
            confidences.append(confidence)
            x1, y1, x2, y2 = np.rint(box).astype(int).tolist()
            cv2.rectangle(annotated, (x1, y1), (x2, y2), (40, 220, 40), 4)
            label = f"ID {position} | {confidence:.3f}"
            text_y = max(30, y1 - 10)
            for color, thickness in ((0, 0, 0), 5), ((40, 220, 40), 2):
                cv2.putText(
                    annotated,
                    label,
                    (max(4, x1), text_y),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.8,
                    color,
                    thickness,
                    cv2.LINE_AA,
                )
        return PeopleDetection(
            frame_index=frame_index,
            boxes=boxes,
            confidences=confidences,
            annotated_rgb=annotated,
            runtime=self.runtime,
        )

    def close(self) -> None:
        self.model = None
