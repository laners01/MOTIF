# UniRelight local setup

Prepared 2026-09-09 for the H200 server. Open `relighting_model_test.ipynb` in the existing video-layer kernel and run its cells from the top. Inference runs in a separate Python process; changing the notebook kernel is unnecessary.

- Python: `.model-envs/unirelight/bin/python` (3.10.19)
- Torch: 2.6.0, CUDA 12.4; torchvision: 0.21.0
- Transformer Engine: 1.12.0; Apex: locally built 0.1; nvdiffrast: 0.4.0
- Native extensions compiled for compute capability 9.0 (H200).
- Source: `vendor/unirelight/`; weights and tokenizer: its `checkpoints/` directory.
- Build toolchain: `.model-envs/unirelight-cuda/` (CUDA 12.4, GCC 12).
- Download staging: `video/downloads/unirelight/`; model files are hard-linked into checkpoints.
- Logs, source revisions, exact packages, checksums and verification: `work/relighting_model_test/setup/`.

The procedural studio environment works without a separate HDR download. Additional 2:1 HDR/EXR maps can be placed under `models/relighting/envmaps/`.

## Verified

Official inference CLI `--help`; experiment configuration registration/validation; native extension imports; official CPU frame loader and last-frame padding; published model MD5; tokenizer encoder/decoder CPU loading and normalization statistics; notebook/adapter syntax and required asset paths. Earlier CPU checks cover HDR editing and frame/FPS-preserving result assembly.

No GPU inference was performed. The notebook's execution cell is the first actual relighting run. Results are saved without audio, using the source FPS and full frame count. Logs are in the result directory's `inference.log`.

## Compatibility changes

The notebook adapter validates the Python symlink target for isolation but invokes `.model-envs/unirelight/bin/python` itself. Resolving that link before invocation bypasses the virtual environment and loses its installed packages. The actual generated command was checked with `--help` after this fix.

1. Official UniRelight entrypoint: `torch.enable_grad(False)` changed to `torch.set_grad_enabled(False)`; the former raises at startup.
2. Notebook adapter: config filename passed relative to the vendor working directory, as required by the official module loader.
3. Apex normalization type annotations changed from built-in `list`/`tuple` generics to existing `typing.List`/`Tuple` imports for Torch 2.6 custom-op schema support. Computational code is unchanged; the local wheel includes the fix.

Upstream decord and transformer-engine-cu12 wheels include cp36/cp38 tags inside their WHEEL metadata despite broader distribution filenames. `pip check` reports these platform tags. Both native libraries successfully import in this Python 3.10 environment; the official entrypoint and configuration checks also pass. The warnings and exact setup are recorded in the setup logs.
