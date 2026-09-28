# Alternative relighting notebooks

Created from `run_v2.ipynb`: `run_RelightVid.ipynb`, `run_LiveLight.ipynb`,
`run_LightXUni.ipynb`. Use the existing **Python (video-layer)** notebook kernel.
Each GPU backend needs a separate Python 3.10 environment beneath `.model-envs/`.
RelightVid is provisioned locally (2026-09-10). LiveLight and Light-X-Uni remain unprovisioned.
CPU integration checks do not establish GPU compatibility or visual quality.

## Common behavior

- E-0 through E-4 retain tracking, optimized SVOR and foreground quality matching.
- E-5a validates inputs and alpha and prepares aspect-preserving letterboxed PNGs.
- E-5b loads the selected model once, processes every frame in overlapping windows,
  blends overlaps, discards tail padding, and evaluates foreground correction strength.
- E-5c applies smooth linear-light, per-channel low-frequency gains to the original
  foreground only. Exposure is limited to 0.75--1.35 and tint relative to exposure
  to 0.8--1.25 so the model can transfer background lighting colour without directly
  replacing clothing or skin pixels. Previous gains are backward-flow aligned before
  temporal blending. Frame count, FPS and dimensions are retained. Output is silent.
- Strength selection uses same-subject model-reference transfer confidence, exposure-normalized
  structure, newly introduced clipping and motion-aligned flicker over up to 32 sampled frames.
  It does not match different people's clothing/skin RGB. Identity remains available;
  improvements must exceed 1% and 0.0001. It does not guarantee physically correct lighting.
  Existing model references are reevaluated without GPU inference after a policy change.
- Unlike UniRelight's three-HDR probe search, these variants generate one full-video
  reference under automatic background conditioning, then select correction strength.
- Logs/inputs: `work/relighting_variants/<model>/session_*/`.
  Final video and experiment metadata: `output/run_<model>/relighting_*/`.
- Worker inference is offline. No fallback to UniRelight or substitute image filter occurs.

## Required local layouts

The first notebook cell lists missing files without interrupting the existing
inpainting/compositing workflow. E-5a refuses inference until the backend is present.
File presence does not validate every dependency or every checkpoint shard.

### RelightVid

Official source: https://github.com/Aleafy/RelightVid

- Source: `vendor/RelightVid/`
- Python: `.model-envs/relightvid/bin/python`
- Install the official requirements in that environment (official target: Torch 2.1.2).
- Place the official `realistic-vision-v51` snapshot in
  `vendor/RelightVid/models/realistic-vision-v51/`.
- Place `iclight_sd15_fbc.safetensors` and `relvid_mm_sd15_fbc.pth` in its `models/`.
- Keep official `configs/inference_fbc.yaml` and its relative model paths.

Adapter uses the official `init_create_model` and `RelightVidInference` APIs,
20 steps, 16-frame windows with 4-frame overlap, 512×512. It bypasses the demo's
center crop, first-16-background-frame loader and 8 FPS encoder.
The foreground alpha and temporally aligned SVOR background (`BG_VIDEO`) condition
the model. This removes the selected background person; other people can remain.
The original background (`BG_SRC`) and its tracked person masks are retained only
for analysis and strength evaluation. The conditioning video's path and digest are
recorded separately; old RelightVid plans without this field require E-5a rerun.
E-5b displays the generated model reference before final foreground correction.
RelightVid's published repository license is CC BY-NC-SA 4.0.

### LiveLight

Official source: https://github.com/mayuelala/LiveLight

- Source: `vendor/LiveLight/`
- Python: `.model-envs/livelight/bin/python`
- Install official requirements in that environment (official target: Torch 2.1.0).
- Official ModelScope checkpoint files belong below
  `vendor/LiveLight/pretrained_weights/LiveLight/`.
  Exactly one `.pth` starting with each of `reference_unet`, `denoising_unet`,
  `light_guider`, `temporal_module` must exist there.
- Base snapshots: `pretrained_weights/sd-image-variations-diffusers/`
  (including `image_encoder/`) and `pretrained_weights/sd-vae-ft-mse/`.
- The official wrapper additionally invokes
  `vendor/LiveLight/pixel-perfect-depth/run_relight_batch.py` for depth inference.
  That executable integration and its local weights/dependencies must be supplied;
  merely downloading the LiveLight checkpoint is insufficient. The public prompt
  YAML contains development-machine paths, so this worker replaces them with the
  above validated project paths and records the effective YAML in the session.
  The depth integration must accept the official wrapper's `--manifest`,
  `--relative-root`, `--outdir-vis`, `--outdir-npy`, `--skip-existing` arguments.
  It must use local models/caches only and run in the LiveLight environment.

Uses `RelightPerFrameRefLive.render_sequence`, 4 steps and the official sliding
rollout within 64-frame windows with 16-frame overlap. Each window rewarms the
model's temporal state; this is bounded offline processing, not an interactive
streaming frontend. Depth inference can materially increase total runtime.
Light position/color/intensity are conservative background-statistics estimates.

### Light-X-Uni

Official source: https://github.com/TQTQliu/Light-X

- Source: `vendor/Light-X/`
- Python: `.model-envs/lightxuni/bin/python`
- Install the official requirements in that environment.
- Hugging Face `tqliu/Light-X-Uni` snapshot:
  `models/lightxuni/Light-X-Uni/`.
- Hugging Face `alibaba-pai/CogVideoX-Fun-V1.1-5b-InP` snapshot:
  `models/lightxuni/CogVideoX-Fun-V1.1-5b-InP/` (all pipeline components).

Uses official `CrossTransformer3DModel`, `AutoencoderKLCogVideoX` and
`CogVideoXFunInpaintPipeline`, 20 steps, 49-frame windows with 8-frame overlap,
672×384, model CPU offload. The middle background frame supplies the official
reference-image condition (`relit_mask=255*0.25`). It does not use the separate
IC-Light background-generation mode. Camera warping is omitted; no captioner or
camera-depth model is loaded. This avoids demo import-time IC-Light downloads.

## Isolation and validation

All repositories, snapshots, runtime symlink targets, inputs and caches must resolve
under the project root. Use local Python environments; do not point at another
project's setup. No installations/downloads or GPU inference were performed during
verification, as required by `AGENTS.md`.

Real GPU inference still needs validation on the user's video; CPU file checks do not establish visual quality. In particular,
LiveLight's external depth entry point remains a required integration dependency.

## RelightVid local provisioning (2026-09-10)

- Official source revision: `9faad741a0297a5e237b2bde78717b101800b278`.
- Official weight repository: `aleafy/relightvid_models`, revision
  `8072d22ffa2e627fda6b721da74fe034b4365e30`.
- Python 3.10.19, Torch 2.1.2+cu118, torchvision 0.16.2+cu118.
- Inference dependency pins: `source/relighting/relightvid_requirements.txt`.
  Training-only DeepSpeed and the Gradio demo are not required by our worker.
- NumPy 1.26.4 and matching Transformers/PEFT versions are pinned for Torch 2.1 compatibility.
- Official weights are SHA256-checked while downloading. The download manifest and
  installation logs are in `work/relightvid_setup/`.
- The official compact weight bundle has no `model_index.json`. The adapter loads
  components individually; preflight now checks those actual component files instead.
- Keep the existing notebook kernel and COMPOSITE. The E-5a cell already reloads
  `variants`, so rerun E-5a, E-5b, E-5c without repeating tracking or SVOR.
