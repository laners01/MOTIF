"""Local-only adapters for the official OmnimatteZero and MiniMax-Remover APIs."""
from pathlib import Path
import argparse
import math
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from source.util import removal_experiments as ex


def exact_minimax_masks(masks):
    # Official scipy dilation(iterations=0) floods the mask. Preserve the shared mask.
    return (masks > 0.5).float().permute(3, 0, 1, 2).unsqueeze(0).repeat(1, 3, 1, 1, 1)


def padded_inputs(frame_dir, mask_dir, spatial, temporal):
    import numpy as np
    from PIL import Image
    frames = ex.sequence(frame_dir)
    masks = ex.sequence(mask_dir, len(frames))
    images, holes = [], []
    with Image.open(frames[0]) as first:
        w, h = first.size
    pw, ph = math.ceil(w/spatial)*spatial, math.ceil(h/spatial)*spatial
    for frame, mask in zip(frames, masks):
        with Image.open(frame) as im, Image.open(mask) as hole:
            if im.size != (w, h) or hole.size != (w, h):
                raise ValueError("Frame/mask size mismatch")
            rgb = np.asarray(im.convert("RGB"))
            binary = (np.asarray(hole.convert("L")) > 127).astype(np.uint8)*255
        images.append(Image.fromarray(np.pad(rgb, ((0, ph-h), (0, pw-w), (0, 0)), mode="edge")))
        holes.append(Image.fromarray(np.pad(binary, ((0, ph-h), (0, pw-w)), mode="constant")).convert("RGB"))
    padded = math.ceil((len(frames)-1)/temporal)*temporal+1
    images.extend([images[-1]]*(padded-len(frames)))
    holes.extend([holes[-1]]*(padded-len(frames)))
    return images, holes, (w, h), len(frames)



def configure_omnimatte_attention(pipe, backend):
    if backend == "default":
        return
    # attn1 has no text padding mask; attn2 must retain masked SDPA.
    for block in pipe.transformer.transformer_blocks:
        block.attn1.set_attention_backend("_native_flash")
    print("OmnimatteZero attention: self=_native_flash, cross=default", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", choices=["omnimattezero", "minimax-remover"], required=True)
    for name in ("frames", "masks", "output"):
        parser.add_argument("--"+name, required=True)
    parser.add_argument("--fps", type=float, required=True)
    parser.add_argument("--steps", type=int, default=20)
    parser.add_argument("--seed", type=int, default=43)
    parser.add_argument("--attention-backend", choices=["default", "flash"], default="default")
    args = parser.parse_args()
    base = ex.local(ex.PACKAGES[args.model], exists=True)
    output = ex.local(args.output)
    if output.exists():
        raise FileExistsError(output)
    sys.path.insert(0, str(base))
    import numpy as np
    import torch
    from PIL import Image
    if not torch.cuda.is_available():
        raise RuntimeError("이 모델의 공식 구현은 CUDA GPU가 필요합니다.")
    if args.model == "omnimattezero":
        from OmnimatteZero import OmnimatteZero
        from diffusers.pipelines.ltx.pipeline_ltx_condition import LTXVideoCondition
        pipe = OmnimatteZero.from_pretrained(
            str(ex.local(base/"weights/LTX-Video-0.9.7-diffusers", exists=True)),
            torch_dtype=torch.bfloat16, local_files_only=True).to("cuda")
        configure_omnimatte_attention(pipe, args.attention_backend)
        pipe.vae.enable_tiling()
        images, masks, size, count = padded_inputs(args.frames, args.masks,
            pipe.vae_spatial_compression_ratio, pipe.vae_temporal_compression_ratio)
        result = pipe.my_call(conditions=[LTXVideoCondition(video=images, frame_index=0),
            LTXVideoCondition(video=masks, frame_index=0)], prompt="Empty",
            negative_prompt="worst quality, inconsistent motion, blurry, jittery, distorted",
            width=images[0].width, height=images[0].height, num_frames=len(images), frame_rate=args.fps,
            num_inference_steps=args.steps, generator=torch.Generator().manual_seed(args.seed),
            output_type="pil").frames[0]
    else:
        from diffusers.models import AutoencoderKLWan
        from diffusers.schedulers import UniPCMultistepScheduler
        from transformer_minimax_remover import Transformer3DModel
        from pipeline_minimax_remover import Minimax_Remover_Pipeline

        class SharedMaskPipeline(Minimax_Remover_Pipeline):
            def expand_masks(self, masks, iterations):
                return exact_minimax_masks(masks)

        vae = AutoencoderKLWan.from_pretrained(str(base/"vae"), torch_dtype=torch.float16, local_files_only=True)
        transformer = Transformer3DModel.from_pretrained(str(base/"transformer"), torch_dtype=torch.float16, local_files_only=True)
        scheduler = UniPCMultistepScheduler.from_pretrained(str(base/"scheduler"), local_files_only=True)
        pipe = SharedMaskPipeline(vae=vae, transformer=transformer, scheduler=scheduler).to("cuda:0")
        images, masks, size, count = padded_inputs(args.frames, args.masks,
            pipe.vae_scale_factor_spatial*2, pipe.vae_scale_factor_temporal)
        pixels = torch.from_numpy(np.stack([np.asarray(im) for im in images]).astype(np.float32)/127.5-1)
        holes = torch.from_numpy(np.stack([np.asarray(im)[..., :1] for im in masks]).astype(np.float32)/255)
        result = pipe(images=pixels, masks=holes, height=images[0].height, width=images[0].width,
            num_frames=len(images), num_inference_steps=args.steps, iterations=0,
            generator=torch.Generator(device="cuda:0").manual_seed(args.seed), output_type="np").frames[0]
    if len(result) != len(images):
        raise ValueError(f"모델 출력 프레임 수 불일치: {len(result)} != {len(images)}")
    frame_dir = ex.unique_dir(Path(args.frames).parent/"adapter_results", args.model)
    w, h = size
    for i, frame in enumerate(result[:count]):
        if not isinstance(frame, Image.Image):
            array = np.asarray(frame)
            if array.dtype != np.uint8:
                array = (array.clip(0, 1)*255).round().astype(np.uint8)
            frame = Image.fromarray(array)
        if frame.size != images[0].size:
            raise ValueError("모델 출력 해상도 불일치")
        frame.crop((0, 0, w, h)).save(frame_dir/f"{i:06d}.png")
    ex.encode_sequence(ex.sequence(frame_dir, count), output, args.fps)
    ex.write_json(output.with_suffix(".json"), {"frames": count, "padded_frames": len(images),
        "size": size, "padded_size": images[0].size, "fps": args.fps, "steps": args.steps, "seed": args.seed,
        "attention_backend": args.attention_backend})


if __name__ == "__main__":
    main()
