# Copyright 2026, MiLM Plus, Xiaomi Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import argparse
import numpy as np
import torch
from diffusers import FlowMatchEulerDiscreteScheduler
from omegaconf import OmegaConf
from PIL import Image
from transformers import AutoTokenizer
import scipy
import cv2
from glob import glob
import torch.distributed as dist
from videox_fun.dist import set_multi_gpus_devices
from videox_fun.models import AutoencoderKLWan, WanT5EncoderModel, VaceWanModel
from videox_fun.pipeline import SVORPipeline
from videox_fun.utils.fp8_optimization import (
    convert_model_weight_to_float8,
    replace_parameters_by_name,
    convert_weight_dtype_wrapper,
)
from videox_fun.utils.lora_utils import merge_lora
from videox_fun.utils.utils import save_videos_grid, filter_kwargs


def load_patch_safetensors(path):
    list_tensors = glob(path + "/*.safetensors")
    all = {}
    for x in list_tensors:
        from safetensors.torch import load_file

        tmp = load_file(x)
        all.update(tmp)
    return all


def parse_args():
    parser = argparse.ArgumentParser(description="WanFun Video Editing Script")

    # GPU and memory configuration
    parser.add_argument(
        "--gpu_memory_mode",
        type=str,
        default="model_full_load",
        choices=["model_full_load", "model_cpu_offload", "model_cpu_offload_and_qfloat8", "sequential_cpu_offload"],
        help="GPU memory optimization mode",
    )
    parser.add_argument("--ulysses_degree", type=int, default=1, help="Ulysses degree for multi-GPU configuration")
    parser.add_argument("--ring_degree", type=int, default=1, help="Ring degree for multi-GPU configuration")

    # Model paths
    parser.add_argument(
        "--config_path", type=str, default="config/wan2.1/wan_civitai.yaml", help="Path to model configuration file"
    )
    parser.add_argument("--model_name", type=str, default="models/Wan2.1-VACE-1.3B", help="Path to pretrained model")

    # Generation parameters
    parser.add_argument("--sample_size", type=str, default="720,1280", help="Output size as 'height,width'")
    parser.add_argument("--video_length", type=int, default=81, help="Length of generated video in frames")
    parser.add_argument("--fps", type=float, default=16, help="Frames per second for output video")
    parser.add_argument(
        "--weight_dtype",
        type=str,
        default="bfloat16",
        choices=["float16", "bfloat16"],
        help="Data type for model weights",
    )

    # Prompt and generation settings
    parser.add_argument(
        "--prompt",
        type=str,
        default="Remove the target and fill the content appropriately",
        help="Text prompt for generation",
    )
    parser.add_argument(
        "--negative_prompt",
        type=str,
        default="色调艳丽，过曝，静态，细节模糊不清，字幕，风格，作品，画作，画面，静止，整体发灰，最差质量，低质量，JPEG压缩残留，丑陋的，残缺的，多余的手指，画得不好的手部，画得不好的脸部，畸形的，毁容的，形态畸形的肢体，手指融合，静止不动的画面，杂乱的背景，三条腿，背景人很多，倒着走",
        help="Negative text prompt",
    )
    parser.add_argument("--guidance_scale", type=float, default=6.0, help="Guidance scale for generation")
    parser.add_argument("--seed", type=int, default=43, help="Random seed for reproducibility")
    parser.add_argument("--context_scale", type=float, default=1.0, help="Context scale for vace control")
    parser.add_argument("--dilation", type=int, default=6, help="Dilation for inp mask (only for inpaint mode)")

    # Parameters for SVOR
    parser.add_argument(
        "--lora_path",
        type=str,
        default=["models/remove_model_stage1.safetensors", "models/remove_model_stage2.safetensors"],
        nargs="+",
        help="Optional path to LoRA checkpoint",
    )
    parser.add_argument(
        "--lora_weight", type=float, default=[1.0, 1.0], nargs="+", help="Weight for LoRA model if used"
    )
    parser.add_argument("--input_video", type=str, default=None, required=True, help="Path to input video for editing")
    parser.add_argument(
        "--input_mask_video", type=str, default=None, required=True, help="Path to mask video for editing"
    )
    parser.add_argument("--chunk_frames", type=int, default=0, help="0: full video; otherwise overlapping 4n+1 frame windows")
    parser.add_argument("--chunk_overlap", type=int, default=16)
    parser.add_argument("--num_inference_steps", type=int, default=20, help="Number of inference steps")
    parser.add_argument("--save_dir", type=str, default="samples/SVOR", help="Directory to save generated videos")

    return parser.parse_args()


def process_video(
    input_video_path,
    input_mask_video_path,
    video_length,
    sample_size,
    dilation=0,
    start_frame=0,
    frame_count=None,
):
    """Process input video and mask for editing"""
    if input_video_path is not None:
        cap = cv2.VideoCapture(input_video_path)
        cap.set(cv2.CAP_PROP_POS_FRAMES, start_frame)
        frames = []
        while cap.isOpened() and (frame_count is None or len(frames) < frame_count):
            ret, frame = cap.read()
            if not ret:
                break
            frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            frames.append(Image.fromarray(frame))
        cap.release()

        if not frames or (frame_count is not None and len(frames) != frame_count):
            raise ValueError("SVOR 입력 구간 프레임 수 불일치")
        frames = frames[:video_length]
        if len(frames) < video_length:
            frames += [frames[-1]] * (video_length - len(frames))

        resized_frames = [frame.resize([sample_size[1], sample_size[0]]) for frame in frames]

        input_video = (
            torch.stack([torch.from_numpy(np.array(frame)).permute(2, 0, 1) for frame in resized_frames])
            .permute(1, 0, 2, 3)
            .unsqueeze(0)
        )  # [1, C, T, H, W]

    else:
        input_video = torch.zeros((1, 3, video_length, sample_size[0], sample_size[1])).float()
    if input_mask_video_path is not None:
        mask_cap = cv2.VideoCapture(input_mask_video_path)
        mask_cap.set(cv2.CAP_PROP_POS_FRAMES, start_frame)
        mask_frames = []
        while mask_cap.isOpened() and (frame_count is None or len(mask_frames) < frame_count):
            ret, frame = mask_cap.read()
            if not ret:
                break
            if len(frame.shape) == 3:
                frame = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            _, mask = cv2.threshold(frame, 127, 255, cv2.THRESH_BINARY)
            if dilation > 0:
                mask_np = (mask > 0).astype(np.uint8)
                mask = scipy.ndimage.binary_dilation(mask_np, iterations=dilation).astype(np.uint8) * 255
            mask_frames.append(mask)
        mask_cap.release()

        if not mask_frames or (frame_count is not None and len(mask_frames) != frame_count):
            raise ValueError("SVOR 마스크 구간 프레임 수 불일치")
        mask_frames = mask_frames[:video_length]
        if len(mask_frames) < video_length:
            mask_frames += [mask_frames[-1]] * (video_length - len(mask_frames))

        resized_masks = [Image.fromarray(mask).resize([sample_size[1], sample_size[0]]) for mask in mask_frames]

        input_video_mask = (
            torch.stack([torch.from_numpy(np.array(mask)) for mask in resized_masks]).unsqueeze(0).unsqueeze(0) / 255.0
        )  # [1, 1, T, H, W]

    else:
        input_video_mask = torch.ones((1, 1, video_length, sample_size[0], sample_size[1])).float()

    if input_video_path is not None and input_video is not None:
        input_video = input_video * (torch.tile(input_video_mask, [1, 3, 1, 1, 1]) < 0.5) + (128.0) * (
            torch.tile(input_video_mask, [1, 3, 1, 1, 1]) >= 0.5
        )
    input_video = input_video.div_(127.5).sub_(1.0)

    return input_video, input_video_mask


def process_single_task(
    pipeline,
    args,
    input_video_path,
    input_mask_video_path,
    prompt,
    start_frame=0,
    frame_count=None,
):
    """Process a single video editing task"""
    if input_video_path is not None:
        # Get video resolution
        cap = cv2.VideoCapture(input_video_path)
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        cap.release()

        # Calculate aspect ratio preserving size
        aspect_ratio = height / width
        max_area = int(args.sample_size.split(",")[0]) * int(args.sample_size.split(",")[1])
        new_height = round(np.sqrt(max_area * aspect_ratio))
        new_height = (new_height + 16 - 1) // 16 * 16
        new_width = round(np.sqrt(max_area / aspect_ratio))
        new_width = (new_width + 16 - 1) // 16 * 16
        sample_size = [new_height, new_width]
    else:
        sample_size = [int(args.sample_size.split(",")[0]), int(args.sample_size.split(",")[1])]

    generator = torch.Generator(device=pipeline.device).manual_seed(args.seed)

    with torch.no_grad():
        video_length = (
            int(
                (args.video_length - 1)
                // pipeline.vae.config.temporal_compression_ratio
                * pipeline.vae.config.temporal_compression_ratio
            )
            + 1
            if args.video_length != 1
            else 1
        )

        # Process video and mask
        (
            input_video,
            input_video_mask,
        ) = process_video(
            input_video_path,
            input_mask_video_path,
            video_length=video_length,
            sample_size=sample_size,
            dilation=args.dilation,
            start_frame=start_frame,
            frame_count=frame_count,
        )

        # Generate edited video
        sample = pipeline(
            prompt,
            negative_prompt=args.negative_prompt,
            height=sample_size[0],
            width=sample_size[1],
            generator=generator,
            guidance_scale=args.guidance_scale,
            num_inference_steps=args.num_inference_steps,
            video=input_video,
            mask_video=input_video_mask,
            context_scale=args.context_scale,
        ).videos

    return sample, video_length


def save_results(sample, args, video_length, fps, task_name=None):
    """Save the generated results"""
    if not os.path.exists(args.save_dir):
        os.makedirs(args.save_dir, exist_ok=True)

    prefix = task_name
    if video_length == 1:
        video_path = os.path.join(args.save_dir, prefix + ".png")
        image = sample[0, :, 0]
        image = image.transpose(0, 1).transpose(1, 2)
        image = (image * 255).numpy().astype(np.uint8)
        image = Image.fromarray(image)
        image.save(video_path)
    else:
        video_path = os.path.join(args.save_dir, prefix + ".mp4")
        save_videos_grid(sample, video_path, fps=fps)


def main():
    args = parse_args()

    # Validate arguments
    if args.input_video is None and args.input_mask_video is None:
        raise ValueError("Must provide either --input_video and --input_mask_video")

    # Convert weight dtype
    weight_dtype = torch.bfloat16 if args.weight_dtype == "bfloat16" else torch.float16
    device = set_multi_gpus_devices(args.ulysses_degree, args.ring_degree)
    config = OmegaConf.load(args.config_path)

    # SVOR 로딩 구간 하트비트: 첫 청크(아래 405줄) 전까지 무음이라, 어느 로드 단계에서
    # 지연/정지하는지 안 보였다. 각 단계 끝에 누적 시간을 찍어 병목 위치를 드러낸다.
    import time
    _load0 = time.perf_counter()
    def _hb(msg):
        print(f"[SVOR 로딩] {msg} · 누적 {time.perf_counter()-_load0:.1f}s", flush=True)
    _hb(f"시작 (dtype={args.weight_dtype}, mode={args.gpu_memory_mode}) → transformer")

    # Initialize transformer
    # ponytail(Attempt#3): gcsfuse 랜덤 mmap이 6.66GB를 29분(v8)~20분(v9)으로 읽음. 로컬 스테이징(v10)은
    # tmpfs ENOSPC로 실패(Cloud Run FS=메모리-backed, 여유 없음). → FS를 안 쓰는 방법: from_pretrained
    # 내부의 safetensors load_file(mmap 랜덤)을 "파일을 RAM으로 순차 read 후 bytes에서 load"로 몽키패치.
    # 순차 read는 gcsfuse가 빠름(text encoder .pth 10.58GB 3.6분 순차 로드가 증거), FS 미사용 → ENOSPC 무관.
    # 6.66GB bytes + 텐서화로 피크 ~13GB RAM(80GB 내). from_pretrained이 함수 내부에서 load_file을
    # import하므로, 호출 전에 safetensors.torch.load_file을 갈아끼우면 그 import가 패치본을 잡는다.
    import safetensors.torch as _st
    _orig_load_file = _st.load_file
    def _seq_load_file(path, device="cpu"):
        _c0 = time.perf_counter()
        with open(path, "rb") as _fh:
            data = _fh.read()                  # 순차 벌크 read (gcsfuse 빠름, FS 미사용)
        _hb(f"transformer safetensors 순차 read 완료({len(data)/2**30:.2f}GiB) · {time.perf_counter()-_c0:.1f}s")
        sd = _st.load(data)                    # bytes에서 로드(mmap 없음)
        return sd if device in (None, "cpu") else {k: v.to(device) for k, v in sd.items()}
    _st.load_file = _seq_load_file
    try:
        transformer = VaceWanModel.from_pretrained(
            os.path.join(args.model_name,
                         config["transformer_additional_kwargs"].get("transformer_subpath", "transformer")),
            transformer_additional_kwargs=OmegaConf.to_container(config["transformer_additional_kwargs"]),
            low_cpu_mem_usage=True,
            torch_dtype=weight_dtype,
        )
    finally:
        _st.load_file = _orig_load_file
    _hb("transformer 완료 → vae")

    # Get Vae
    vae = AutoencoderKLWan.from_pretrained(
        os.path.join(args.model_name, config["vae_kwargs"].get("vae_subpath", "vae")),
        additional_kwargs=OmegaConf.to_container(config["vae_kwargs"]),
    ).to(weight_dtype)
    _hb("vae 완료 → text encoder")

    # Get Tokenizer
    tokenizer = AutoTokenizer.from_pretrained(
        os.path.join(args.model_name, config["text_encoder_kwargs"].get("tokenizer_subpath", "tokenizer")),
    )

    # Get Text encoder — 프롬프트/네거티브가 고정이라 T5 임베딩 캐시가 있으면 로딩(10.58GB, ~3.6분)을 스킵.
    # 캐시 히트 시 text_encoder 자리에 .dtype만 가진 stub을 넣고(파이프라인이 line 499에서 dtype만 참조),
    # 아래(device 이동 후)에서 encode_prompt를 캐시 반환으로 몽키패치한다. 실패는 정상 경로로 폴백.
    import sys as _sys
    from pathlib import Path as _Path
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))
    _pc = None
    _prompt_cache = None
    try:
        from source.inpainting import svor_prompt_cache as _pc
        _prompt_cache = _pc.load(args.prompt, args.negative_prompt, weight_dtype, args.model_name)
    except Exception as _e:
        print(f"[SVOR 로딩] 프롬프트 캐시 조회 실패(무시): {_e}", flush=True)
    if _prompt_cache is not None:
        class _EncoderStub:
            def __init__(self, dtype):
                self.dtype = dtype
        text_encoder = _EncoderStub(weight_dtype)
        _hb("text encoder 캐시 히트 → 로딩 스킵")
    else:
        text_encoder = WanT5EncoderModel.from_pretrained(
            os.path.join(args.model_name, config["text_encoder_kwargs"].get("text_encoder_subpath", "text_encoder")),
            additional_kwargs=OmegaConf.to_container(config["text_encoder_kwargs"]),
        ).to(weight_dtype)
        text_encoder = text_encoder.eval()
        _hb("text encoder 완료 → pipeline/scheduler")

    # Get Scheduler
    Choosen_Scheduler = {
        "Flow": FlowMatchEulerDiscreteScheduler,
    }["Flow"]
    scheduler = Choosen_Scheduler(
        **filter_kwargs(Choosen_Scheduler, OmegaConf.to_container(config["scheduler_kwargs"]))
    )

    # Get Pipeline
    pipeline = SVORPipeline(
        transformer=transformer,
        vae=vae,
        tokenizer=tokenizer,
        text_encoder=text_encoder,
        scheduler=scheduler,
    )

    if args.ulysses_degree > 1 or args.ring_degree > 1:
        transformer.enable_multi_gpus_inference()

    if args.gpu_memory_mode == "sequential_cpu_offload":
        replace_parameters_by_name(
            transformer,
            [
                "modulation",
            ],
            device=device,
        )
        transformer.freqs = transformer.freqs.to(device=device)
        pipeline.enable_sequential_cpu_offload(device=device)
    elif args.gpu_memory_mode == "model_cpu_offload_and_qfloat8":
        convert_model_weight_to_float8(
            transformer,
            exclude_module_name=[
                "modulation",
            ],
        )
        convert_weight_dtype_wrapper(transformer, weight_dtype)
        pipeline.enable_model_cpu_offload(device=device)
    elif args.gpu_memory_mode == "model_cpu_offload":
        pipeline.enable_model_cpu_offload(device=device)
    else:
        pipeline.to(device=device)
    _hb(f"pipeline device 이동 완료 → 청크 추론 시작 (device={device})")

    # T5 임베딩 캐시: 히트면 encode_prompt를 캐시 반환으로 대체(text encoder 없이 동작),
    # 미스면 지금(인코더 로딩된 상태) 한 번 계산해 S3에 저장 → 다음 실행부터 로딩 스킵.
    try:
        if _prompt_cache is not None:
            _pe_cache, _npe_cache = _prompt_cache
            def _cached_encode_prompt(*_a, **_k):
                _dev = _k.get("device", device)
                _mv = lambda xs: None if xs is None else [t.to(_dev) for t in xs]
                return _mv(_pe_cache), _mv(_npe_cache)
            pipeline.encode_prompt = _cached_encode_prompt
        elif _pc is not None and os.environ.get("S3_BUCKET", "").strip():
            _pe_new, _npe_new = pipeline.encode_prompt(
                args.prompt, args.negative_prompt, args.guidance_scale > 1.0,
                num_videos_per_prompt=1, max_sequence_length=512, device=device)
            if _pc.save(args.prompt, args.negative_prompt, weight_dtype, args.model_name, _pe_new, _npe_new):
                print("[SVOR 로딩] 프롬프트 임베딩 캐시 저장 완료", flush=True)
    except Exception as _e:
        print(f"[SVOR 로딩] 프롬프트 캐시 처리 실패(정상 경로로 진행): {_e}", flush=True)

    if args.lora_path is not None:
        if len(args.lora_weight) != len(args.lora_path):
            args.lora_weight = [args.lora_weight[0]] * len(args.lora_path)
        for lora_path, lora_weight in zip(args.lora_path, args.lora_weight):
            print(f"[INFO] Loading LoRA: {lora_path}, weight: {lora_weight}")
            pipeline = merge_lora(pipeline, lora_path, lora_weight)

    if not os.path.exists(args.save_dir):
        os.makedirs(args.save_dir, exist_ok=True)

    if args.chunk_frames:
        import sys
        import time
        from pathlib import Path
        from copy import copy
        root = Path(__file__).resolve().parents[2]
        sys.path.insert(0, str(root))
        from source.inpainting.svor_chunks import windows, blended_frames
        from source.util import removal_experiments as ex
        cap = cv2.VideoCapture(str(ex.local(args.input_video, exists=True)))
        count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        source_fps = cap.get(cv2.CAP_PROP_FPS)
        cap.release()
        plan = list(windows(count, args.chunk_frames, args.chunk_overlap))
        timings = []
        def predict_chunk(start, end):
            chunk_args = copy(args)
            chunk_args.video_length = ((end - start - 1 + 3) // 4) * 4 + 1
            print(f"SVOR chunk {len(timings)+1}/{len(plan)}: frames [{start}, {end})", flush=True)
            started = time.perf_counter()
            sample, _ = process_single_task(pipeline, chunk_args, args.input_video,
                args.input_mask_video, args.prompt, start_frame=start, frame_count=end-start)
            if sample.shape[2] != chunk_args.video_length:
                raise ValueError("SVOR 패딩 구간 출력 길이 불일치")
            result = sample[0, :, :end-start].detach().float().cpu().permute(1, 2, 3, 0).numpy()
            timings.append({"start":start, "end":end, "padded_frames":chunk_args.video_length,
                            "seconds":round(time.perf_counter()-started, 3)})
            print(f"SVOR chunk completed: {timings[-1]['seconds']:.3f}s", flush=True)
            return result
        frames = blended_frames(count, args.chunk_frames, args.chunk_overlap, predict_chunk)
        bgr = ((frame.clip(0, 1)*255).round().astype(np.uint8)[..., ::-1].copy() for frame in frames)
        output = ex.local(Path(args.save_dir) / "svor_chunked.mp4")
        ex.encode_frames(bgr, output, source_fps)
        ex.write_json(output.with_suffix(".json"), {"frames":count, "fps":source_fps,
            "chunk_frames":args.chunk_frames, "chunk_overlap":args.chunk_overlap,
            "steps":args.num_inference_steps, "seed":args.seed, "chunks":timings})
        return

    # Single task processing
    sample, video_length = process_single_task(pipeline, args, args.input_video, args.input_mask_video, args.prompt)

    video_basename = os.path.splitext(os.path.basename(args.input_video))[0]
    if not dist.is_initialized() or dist.get_rank() == 0:
        save_results(sample, args, video_length, args.fps, task_name=video_basename)


if __name__ == "__main__":
    main()
