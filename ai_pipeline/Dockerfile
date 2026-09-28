# syntax=docker/dockerfile:1.7
# Blackwell(RTX PRO 6000, sm_120)은 CUDA 12.8+ 필요. GVHMR pytorch3d는 Blackwell용
# 프리빌트 wheel이 없어 소스 빌드하므로 nvcc가 든 -devel 베이스를 쓴다.
# ponytail: -devel은 이미지가 크다. 빌드 성공 확인 후 multi-stage(빌드 wheel만 복사)로 축소.
FROM nvidia/cuda:12.8.1-cudnn-devel-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_LINK_MODE=hardlink \
    UV_CACHE_DIR=/opt/uv-cache \
    UV_PYTHON_INSTALL_DIR=/opt/uv-python \
    PATH=/opt/run-final/bin:$PATH \
    PYTHONPATH=/app

# 영상 처리와 Python 패키지 빌드에 필요한 최소 시스템 도구를 설치한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential ca-certificates curl ffmpeg git git-lfs libegl1 libgl1 \
        libglib2.0-0 ninja-build pkg-config \
    && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /bin/

RUN uv python install 3.12 3.10 \
    && uv venv /opt/run-final --python 3.12

COPY requirements.txt /tmp/run-final-requirements.txt

# 메인 워커 환경은 Python 3.12와 CUDA 12.8 PyTorch(Blackwell)로 고정한다.
RUN uv pip install --python /opt/run-final/bin/python \
        --index-url https://download.pytorch.org/whl/cu128 \
        torch==2.7.0 torchvision==0.22.0 \
    && uv pip install --python /opt/run-final/bin/python \
        -r /tmp/run-final-requirements.txt \
    && rm -rf /opt/uv-cache

WORKDIR /app/run_final_cloud_run_job

# model-env 빌드는 vendor/·docker/ 에만 의존한다. 앱 코드(source/*.py 등)보다 먼저 복사해
# 두면, 코드만 바뀐 빌드에서 무거운 model-env 레이어가 캐시 재사용된다(cloudbuild --cache-from).
COPY vendor ./vendor
COPY docker ./docker

# SAM2와 충돌 가능성이 있는 모델들은 각자 격리된 환경에 설치한다.
# model-env 4개를 이 한 RUN에서 생성 → 동일 CUDA 라이브러리가 hardlink로 1벌만 저장(dedup).
# 끝에 uv 캐시 제거: venv의 하드링크는 inode 공유라 데이터가 남고, 캐시의 wheel 아카이브만 사라진다.
RUN SAM2_BUILD_CUDA=0 uv pip install --python /opt/run-final/bin/python \
        --no-build-isolation -e vendor/sam2 \
    && PYTORCH3D_MAX_JOBS=8 bash docker/install_model_envs.sh /app/run_final_cloud_run_job \
    && rm -rf /opt/uv-cache \
    && find /app/run_final_cloud_run_job/.model-envs -depth -type d -name __pycache__ -exec rm -rf {} +

# 코드가 요구하는 ffmpeg ≥5.1 옵션(-fps_mode, -enc_time_base, setts bsf, apad=whole_dur)을 위해
# 최신 static ffmpeg를 설치한다. 베이스(ubuntu22.04) apt ffmpeg(4.4)·imageio(4.2.2)는 5.0 미만이라
# 이 옵션들이 "Option not found"로 실패한다. IMAGEIO_FFMPEG_EXE로 모든 imageio_ffmpeg.get_ffmpeg_exe()
# 호출(compose/precompute 인코딩 전부)이 이 바이너리를 쓰게 강제한다.
RUN curl -fsSL https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz -o /tmp/ffmpeg.tar.xz \
    && mkdir -p /tmp/ffmpeg && tar -xf /tmp/ffmpeg.tar.xz -C /tmp/ffmpeg --strip-components=1 \
    && install -m755 /tmp/ffmpeg/ffmpeg /usr/local/bin/ffmpeg \
    && install -m755 /tmp/ffmpeg/ffprobe /usr/local/bin/ffprobe \
    && rm -rf /tmp/ffmpeg /tmp/ffmpeg.tar.xz \
    && /usr/local/bin/ffmpeg -version | head -1
ENV IMAGEIO_FFMPEG_EXE=/usr/local/bin/ffmpeg

# 나머지 앱 소스(자주 바뀜). model-env 레이어 뒤라 코드 변경이 위 캐시를 깨지 않는다.
COPY . .

# 가중치는 이미지에 굽지 않는다. 런타임에 GCS 버킷을 /mnt/model-weights로 마운트하고,
# 코드가 기대하는 경로를 그 마운트로 심링크한다(빌드 시 1회). 버킷 적재는
# docker/upload_weights_to_gcs.sh로 미리 1회 수행한다.
RUN bash docker/link_weight_mounts.sh /app/run_final_cloud_run_job

# 격리 환경 import만 빌드 시 검증한다. 가중치 존재 검증은 런타임(runtime.py load_runtime)이 한다.
RUN .model-envs/mimicmotion/bin/python -c "import cv2, onnxruntime, scipy; assert 'CUDAExecutionProvider' in onnxruntime.get_available_providers(), 'onnxruntime-gpu 아님'" \
    && .model-envs/relightvid/bin/python -c "import diffusers, open_clip, pytorch_lightning, torch" \
    && .model-envs/svor/bin/python -c "import decord, diffusers, torch, transformers" \
    && .model-envs/gvhmr/bin/python -c "import hydra, pycolmap, pytorch3d, torch, ultralytics"

CMD ["python", "-m", "run_final_cloud_run_job.worker"]
