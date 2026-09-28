from __future__ import annotations

import sys
from pathlib import Path
from typing import Iterator

import numpy as np
import torch
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
MATANYONE_ROOT = ROOT / "vendor" / "matanyone"
MATANYONE2_ROOT = ROOT / "vendor" / "matanyone2"


def _import_matanyone():
    source_path = str(MATANYONE_ROOT)
    if source_path not in sys.path:
        sys.path.insert(0, source_path)

    from hydra import compose, initialize_config_dir
    from hydra.core.global_hydra import GlobalHydra
    from matanyone.inference.inference_core import InferenceCore
    from matanyone.model.matanyone import MatAnyone
    from omegaconf import open_dict

    return compose, initialize_config_dir, GlobalHydra, InferenceCore, MatAnyone, open_dict


def load_matanyone(checkpoint: Path, device: str):
    (
        compose,
        initialize_config_dir,
        GlobalHydra,
        InferenceCore,
        MatAnyone,
        open_dict,
    ) = _import_matanyone()

    GlobalHydra.instance().clear()
    config_dir = MATANYONE_ROOT / "matanyone" / "config"
    with initialize_config_dir(config_dir=str(config_dir), version_base="1.3"):
        config = compose(config_name="eval_matanyone_config")
    with open_dict(config):
        config.weights = str(checkpoint)
        # The released MatAnyone checkpoint already contains both ResNet encoders.
        # Avoid a second, unnecessary internet download during model construction.
        config.model.pretrained_resnet = False

    model = MatAnyone(config, single_object=True).to(device).eval()
    weights = torch.load(str(checkpoint), map_location=device, weights_only=True)
    model.load_weights(weights)
    return model, config, InferenceCore


def load_matanyone2(checkpoint: Path, device: str):
    source_path = str(MATANYONE2_ROOT)
    if source_path not in sys.path:
        sys.path.insert(0, source_path)

    from hydra import compose, initialize_config_dir
    from hydra.core.global_hydra import GlobalHydra
    from matanyone2.inference.inference_core import InferenceCore
    from matanyone2.model.matanyone2 import MatAnyone2
    from omegaconf import open_dict

    GlobalHydra.instance().clear()
    config_dir = MATANYONE2_ROOT / "matanyone2" / "config"
    with initialize_config_dir(config_dir=str(config_dir), version_base="1.3"):
        config = compose(config_name="eval_matanyone_config")
    with open_dict(config):
        config.weights = str(checkpoint)
        # The released checkpoint already carries both encoders; keep startup offline.
        config.model.pretrained_resnet = False

    model = MatAnyone2(config, single_object=True).to(device).eval()
    weights = torch.load(str(checkpoint), map_location=device, weights_only=True)
    model.load_weights(weights)
    return model, config, InferenceCore


class MatAnyoneVideoPredictor:
    """Expose MatAnyone through the small predictor API used by the web app."""

    returns_alpha = True
    requires_first_frame = True

    def __init__(
        self,
        model,
        config,
        inference_core,
        device: str,
        bootstrap_mode: str,
        bootstrap_predictor=None,
        continuous_correction: bool = False,
        model_label: str = "MatAnyone",
    ) -> None:
        self.model = model
        self.config = config
        self.inference_core = inference_core
        self.device = device
        self.model_label = model_label
        self.bootstrap_mode = bootstrap_mode
        self.bootstrap_predictor = bootstrap_predictor
        self.continuous_correction = continuous_correction
        self.correction_interval = 60
        self.correction_min_gap = 10
        self.correction_iou_threshold = 0.50
        self.correction_warmups = 2
        self.correction_count = 0
        self.correction_events: list[dict] = []

    def init_state(self, video_path: str, **kwargs) -> dict:
        frame_dir = Path(video_path)
        frame_paths = sorted(
            path
            for path in frame_dir.iterdir()
            if path.suffix.lower() in {".jpg", ".jpeg", ".png"}
        )
        if not frame_paths:
            raise RuntimeError(f"{self.model_label}이 읽을 프레임이 없습니다.")

        bootstrap_state = None
        if self.bootstrap_predictor is not None:
            bootstrap_state = self.bootstrap_predictor.init_state(
                video_path=video_path,
                **kwargs,
            )
        return {
            "frame_paths": frame_paths,
            "bootstrap_state": bootstrap_state,
            "initial_mask": None,
        }

    def reset_state(self, inference_state: dict) -> None:
        inference_state["initial_mask"] = None
        self.correction_count = 0
        self.correction_events.clear()
        if self.bootstrap_predictor is not None:
            self.bootstrap_predictor.reset_state(inference_state["bootstrap_state"])

    def add_new_points_or_box(
        self,
        *,
        inference_state: dict,
        frame_idx: int,
        obj_id: int,
        points: np.ndarray,
        labels: np.ndarray,
        **kwargs,
    ):
        if int(frame_idx) != 0:
            raise RuntimeError(f"{self.model_label} 모드는 첫 프레임(0)에서 대상을 클릭해 주세요.")
        if self.bootstrap_predictor is None:
            raise RuntimeError(
                f"{self.model_label} 첫 마스크를 만들 부트스트랩 추적기(SAM2/EfficientTAM)가 없습니다."
            )

        # 첫 프레임의 클릭 프롬프트로 SAM2/EfficientTAM이 초기 마스크를 만든다.
        output = self.bootstrap_predictor.add_new_points_or_box(
            inference_state=inference_state["bootstrap_state"],
            frame_idx=frame_idx,
            obj_id=obj_id,
            points=points,
            labels=labels,
            **kwargs,
        )
        _, object_ids, logits = output
        object_ids = list(object_ids)
        position = object_ids.index(obj_id)
        mask = (logits[position, 0] > 0.0).detach().float().cpu() * 255.0
        inference_state["initial_mask"] = mask
        return output

    @staticmethod
    def _read_frame(path: Path, device: str) -> torch.Tensor:
        image = np.array(Image.open(path).convert("RGB"), dtype=np.float32)
        return torch.from_numpy(image).permute(2, 0, 1).contiguous().to(device) / 255.0

    @staticmethod
    def _tracker_mask(iterator, expected_frame_index: int) -> torch.Tensor | None:
        while True:
            try:
                frame_index, object_ids, logits = next(iterator)
            except StopIteration:
                return None
            frame_index = int(frame_index)
            if frame_index < expected_frame_index:
                continue
            if frame_index > expected_frame_index:
                raise RuntimeError(
                    f"EfficientTAM 프레임 순서가 맞지 않습니다: "
                    f"예상 {expected_frame_index}, 실제 {frame_index}"
                )
            object_ids = list(object_ids)
            if 1 not in object_ids:
                return None
            position = object_ids.index(1)
            return (logits[position, 0] > 0.0).detach()

    def _initialize_processor(self, inference_core, image, mask, warmups: int):
        processor = inference_core(self.model, cfg=self.config, device=self.device)
        processor.step(image, mask.float() * 255.0, objects=[1])
        output = None
        for _ in range(warmups):
            output = processor.step(image, first_frame_pred=True)
        return processor, output

    def _correction_reason(
        self,
        frame_index: int,
        alpha: torch.Tensor,
        tracker_mask: torch.Tensor | None,
        previous_tracker_area: float | None,
        last_correction_frame: int,
    ) -> tuple[str | None, float | None, float | None]:
        if tracker_mask is None:
            return None, None, previous_tracker_area

        tracker_mask = tracker_mask.bool()
        alpha_mask = alpha > 0.5
        tracker_area = float(tracker_mask.sum().item())
        alpha_area = float(alpha_mask.sum().item())
        if tracker_area < 64:
            return None, None, tracker_area

        tracker_stable = True
        if previous_tracker_area and previous_tracker_area >= 64:
            tracker_ratio = tracker_area / previous_tracker_area
            tracker_stable = 0.25 <= tracker_ratio <= 4.0
        if not tracker_stable:
            return None, None, tracker_area

        intersection = float((tracker_mask & alpha_mask).sum().item())
        union = float((tracker_mask | alpha_mask).sum().item())
        iou = intersection / union if union else 0.0
        area_ratio = alpha_area / tracker_area
        gap = frame_index - last_correction_frame

        if alpha_area < max(32.0, tracker_area * 0.15):
            return "MatAnyone 마스크 소실", iou, tracker_area
        if gap < self.correction_min_gap:
            return None, iou, tracker_area
        if iou < self.correction_iou_threshold:
            return "EfficientTAM과 IoU 불일치", iou, tracker_area
        if area_ratio < 0.35 or area_ratio > 2.8:
            return "마스크 면적 급변", iou, tracker_area
        if frame_index % self.correction_interval == 0:
            return "정기 교정", iou, tracker_area
        return None, iou, tracker_area

    @torch.inference_mode()
    def propagate_in_video(
        self,
        inference_state: dict,
        start_frame_idx: int = 0,
        reverse: bool = False,
        **kwargs,
    ) -> Iterator[tuple[int, list[int], torch.Tensor]]:
        if reverse:
            return
        if int(start_frame_idx) != 0:
            raise RuntimeError(f"{self.model_label} 모드는 첫 프레임(0)부터 정방향으로 처리합니다.")
        initial_mask = inference_state.get("initial_mask")
        if initial_mask is None:
            raise RuntimeError("먼저 첫 프레임에서 사람을 클릭해 주세요.")

        InferenceCore = self.inference_core
        frame_paths = inference_state["frame_paths"]
        first_image = self._read_frame(frame_paths[0], self.device)
        mask = initial_mask.to(self.device) / 255.0
        self.correction_count = 0
        self.correction_events.clear()

        tracker_iterator = None
        previous_tracker_area = None
        last_correction_frame = 0
        if self.continuous_correction:
            if self.bootstrap_predictor is None:
                raise RuntimeError("연속 교정 모드에 EfficientTAM 추적기가 없습니다.")
            tracker_iterator = self.bootstrap_predictor.propagate_in_video(
                inference_state["bootstrap_state"],
                start_frame_idx=0,
                reverse=False,
            )
            tracker_mask = self._tracker_mask(tracker_iterator, 0)
            if tracker_mask is not None:
                mask = tracker_mask.to(self.device).float()

        # Match the official MatAnyone inference warm-up sequence.
        processor, output = self._initialize_processor(InferenceCore, first_image, mask, 11)
        alpha = processor.output_prob_to_mask(output).clamp(0.0, 1.0)
        yield 0, [1], alpha.unsqueeze(0).unsqueeze(0)

        last_index = len(frame_paths) - 1
        for frame_index, frame_path in enumerate(frame_paths[1:], start=1):
            image = self._read_frame(frame_path, self.device)
            output = processor.step(image, end=frame_index == last_index)
            alpha = processor.output_prob_to_mask(output).clamp(0.0, 1.0)

            if tracker_iterator is not None:
                tracker_mask = self._tracker_mask(tracker_iterator, frame_index)
                if tracker_mask is not None and tracker_mask.shape != alpha.shape:
                    tracker_mask = torch.nn.functional.interpolate(
                        tracker_mask.float().unsqueeze(0).unsqueeze(0),
                        size=alpha.shape[-2:],
                        mode="nearest",
                    )[0, 0].bool()
                reason, iou, previous_tracker_area = self._correction_reason(
                    frame_index,
                    alpha,
                    tracker_mask,
                    previous_tracker_area,
                    last_correction_frame,
                )
                if reason is not None and tracker_mask is not None:
                    processor, output = self._initialize_processor(
                        InferenceCore,
                        image,
                        tracker_mask.to(self.device),
                        self.correction_warmups,
                    )
                    alpha = processor.output_prob_to_mask(output).clamp(0.0, 1.0)
                    last_correction_frame = frame_index
                    self.correction_count += 1
                    self.correction_events.append(
                        {
                            "frame": frame_index,
                            "reason": reason,
                            "iou_before_correction": iou,
                        }
                    )
            yield frame_index, [1], alpha.unsqueeze(0).unsqueeze(0)


def build_matanyone_predictor(
    checkpoint: Path,
    device: str,
    bootstrap_mode: str,
    bootstrap_predictor=None,
    continuous_correction: bool = False,
) -> MatAnyoneVideoPredictor:
    model, config, inference_core = load_matanyone(checkpoint, device)
    return MatAnyoneVideoPredictor(
        model=model,
        config=config,
        inference_core=inference_core,
        device=device,
        bootstrap_mode=bootstrap_mode,
        bootstrap_predictor=bootstrap_predictor,
        continuous_correction=continuous_correction,
    )


def build_matanyone2_predictor(
    checkpoint: Path,
    device: str,
    bootstrap_mode: str,
    bootstrap_predictor=None,
) -> MatAnyoneVideoPredictor:
    model, config, inference_core = load_matanyone2(checkpoint, device)
    return MatAnyoneVideoPredictor(
        model=model,
        config=config,
        inference_core=inference_core,
        device=device,
        bootstrap_mode=bootstrap_mode,
        bootstrap_predictor=bootstrap_predictor,
        continuous_correction=False,
        model_label="MatAnyone 2",
    )
