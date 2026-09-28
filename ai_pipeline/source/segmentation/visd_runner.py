"""Execute the official VISD detector in its local environment (no downloads)."""
import argparse
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from source.util import removal_experiments as ex
from source.segmentation.visd_adapter import CONFIG, WEIGHTS


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--frame", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    frame, output = ex.local(args.frame, exists=True), ex.local(args.output)
    if output.exists():
        raise FileExistsError(output)
    import cv2
    import numpy as np
    import torch
    from adet.config import get_cfg
    from detectron2.engine.defaults import DefaultPredictor

    config = ex.local(CONFIG, exists=True)
    # Validate inherited YAML paths before allowing the vendor config loader to read them.
    import yaml
    def check_bases(path, visited):
        path = ex.local(path, exists=True)
        if path in visited:
            return
        visited.add(path)
        data = yaml.safe_load(path.read_text())
        bases = data.get('_BASE_', [])
        for base in ([bases] if isinstance(bases, str) else bases):
            check_bases(path.parent / base, visited)
    check_bases(config, set())
    cfg = get_cfg()
    cfg.merge_from_file(str(config))
    cfg.MODEL.WEIGHTS = str(ex.local(WEIGHTS, exists=True))
    cfg.MODEL.DEVICE = "cuda"
    cfg.MODEL.FCOS.INFERENCE_TH_TEST = 0.1
    cfg.DATASETS.TRAIN = ()
    cfg.DATASETS.TEST = ()
    cfg.OUTPUT_DIR = str(output.parent)
    cfg.freeze()
    image = cv2.imread(str(frame))
    if image is None:
        raise ValueError(f"프레임을 읽을 수 없습니다: {frame}")
    with torch.inference_mode():
        # The official VisualizationDemo indexes DefaultPredictor's result at [0].
        instances = DefaultPredictor(cfg)(image)[0]['instances']
        if instances is None:
            raise ValueError("VISD 물체–그림자 연결 결과가 없습니다.")
        instances = instances.to('cpu')
    for key in ('pred_masks', 'pred_classes', 'pred_associations', 'scores'):
        if not instances.has(key):
            raise ValueError(f"VISD 공식 연결 출력 필드가 없습니다: {key}")
    def array(value):
        return value.detach().cpu().numpy() if torch.is_tensor(value) else np.asarray(value)
    np.savez_compressed(output, masks=array(instances.pred_masks), classes=array(instances.pred_classes),
                        associations=array(instances.pred_associations), scores=array(instances.scores))
    print("VISD object-shadow associations saved:", output)


if __name__ == '__main__':
    main()
