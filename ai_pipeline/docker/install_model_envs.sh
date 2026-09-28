#!/usr/bin/env bash
set -euo pipefail

root="${1:?server root is required}"
env_root="$root/.model-envs"
mkdir -p "$env_root"

# CPU DWPose 보정 환경.
uv venv "$env_root/mimicmotion" --python 3.12
uv pip install --python "$env_root/mimicmotion/bin/python" \
  -r "$root/docker/requirements-mimicmotion.txt"

# RelightVid: 공식 cu118/torch2.1.2는 Blackwell(sm_120) 미지원 → cu128/torch2.7로 승격.
# ponytail: torch 2.1→2.7 점프. pytorch-lightning/open-clip 조합을 실빌드로 검증할 것.
uv venv "$env_root/relightvid" --python 3.10
uv pip install --python "$env_root/relightvid/bin/python" \
  --index-url https://download.pytorch.org/whl/cu128 \
  torch==2.7.0 torchvision==0.22.0
uv pip install --python "$env_root/relightvid/bin/python" \
  -r "$root/docker/requirements-relightvid.txt"

# SVOR: cu124/torch2.5.1 → cu128/torch2.7 (Blackwell).
uv venv "$env_root/svor" --python 3.10
uv pip install --python "$env_root/svor/bin/python" \
  --index-url https://download.pytorch.org/whl/cu128 \
  torch==2.7.0 torchvision==0.22.0
uv pip install --python "$env_root/svor/bin/python" \
  -r "$root/docker/requirements-svor.txt"

# GVHMR: 공식 pin(torch2.3+cu121, pytorch3d cu121 wheel)은 Blackwell 미지원.
# torch를 cu128/2.7로 올리고, pytorch3d는 프리빌트 wheel이 없어 소스 빌드한다.
uv venv "$env_root/gvhmr" --python 3.10
uv pip install --python "$env_root/gvhmr/bin/python" pip setuptools wheel
uv pip install --python "$env_root/gvhmr/bin/python" \
  --index-url https://download.pytorch.org/whl/cu128 \
  torch==2.7.0 torchvision==0.22.0
# chumpy 0.70은 선언하지 않은 pip를 빌드 중 import한다.
# ponytail: chumpy는 numpy>=1.24의 제거된 별칭(np.bool 등)을 쓴다. 실빌드에서 깨지면 패치 필요.
uv pip install --python "$env_root/gvhmr/bin/python" \
  --no-build-isolation chumpy==0.70
uv pip install --python "$env_root/gvhmr/bin/python" lightning==2.5.2 numpy==1.26.4
# GVHMR 요구사항에서 Blackwell 비호환 pin(torch/torchvision/cu121 인덱스/pytorch3d/lightning/numpy)만 제외.
grep -viE '^[[:space:]]*(torch|torchvision|--extra-index-url|pytorch3d|lightning|numpy|chumpy)([[:space:]=@]|$)' \
  "$root/vendor/GVHMR/requirements.txt" > /tmp/gvhmr-req.txt
uv pip install --python "$env_root/gvhmr/bin/python" -r /tmp/gvhmr-req.txt
# GVHMR requirements 미선언 전이 의존성(chumpy와 동일): hmr4d/network/hmr2(HMR2.0 코드)가 yacs를 직접 import.
uv pip install --python "$env_root/gvhmr/bin/python" yacs==0.1.8
# pytorch3d: 프리빌트 wheel 없음 → 소스 빌드(nvcc 필요, -devel 베이스 전제).
# arch 9.0(Hopper/H100)+12.0(Blackwell/RTX PRO 6000) 동시 컴파일 → 두 GPU 모두 커널 매칭.
# L4(8.9)는 배제(24GB로 full_load OOM). Vertex H100 이전 대비 sm_90 포함이 필수.
# ponytail: 소스 빌드 수 분·취약. @stable이 torch2.7에 안 맞으면 태그 고정 필요. Blackwell 실빌드 검증 필수.
# MAX_JOBS: nvcc 병렬 컴파일이 코어당 수 GB를 먹어 빌드 워커가 OOM(Cloud Build INTERNAL_ERROR) 나므로
# 병렬도를 제한한다. 메모리 여유가 크면 PYTORCH3D_MAX_JOBS로 올려 빌드를 빠르게 할 수 있다.
pytorch3d_wheel="$(ls "$root"/docker/wheels/pytorch3d-*.whl 2>/dev/null | head -1 || true)"
if [ -n "$pytorch3d_wheel" ]; then
  # 사전빌드 wheel(cloudbuild.pytorch3d.yaml 산출물)이 있으면 설치만 → 컴파일 생략(~25분 절감).
  echo "pytorch3d: 사전빌드 wheel 사용 → $pytorch3d_wheel"
  uv pip install --python "$env_root/gvhmr/bin/python" --no-deps "$pytorch3d_wheel"
else
  echo "pytorch3d: wheel 없음 → 소스 컴파일(느림, 폴백)"
  TORCH_CUDA_ARCH_LIST="9.0;12.0" FORCE_CUDA=1 MAX_JOBS="${PYTORCH3D_MAX_JOBS:-2}" \
    uv pip install --python "$env_root/gvhmr/bin/python" \
    --no-build-isolation "git+https://github.com/facebookresearch/pytorch3d.git@stable"
fi

# Video Depth Anything worker는 메인 환경 + 이 --target 경로를 sys.path로 함께 쓴다(depth_worker.py).
# VDA 모델 정의가 einops를 import(motion_module). decord/OpenEXR/xformers는 이 코드 경로에서
# 쓰지 않으므로(그리고 3.12 빌드·torch2.7 충돌 회피) 넣지 않는다.
uv pip install --target "$env_root/video-depth-deps" easydict==1.13 einops==0.4.1
