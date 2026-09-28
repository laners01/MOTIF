# run_enhancement: conservative dance correction

`run_enhancement.ipynb` retains run_v2 tracking, SVOR, placement, edge handling,
and UniRelight. D-1 through D-4 now use `correction.py` and
`correction_worker.py`, rather than the previous MimicMotion/SVD generation path.
Previously downloaded generation weights and existing outputs are retained.

## Flow

1. Extract the foreground RGBA sequence and remove the selected background person.
2. Select a remaining background dancer (automatic if exactly one remains), then
   track and extract that dancer with the existing SAM2 pipeline.
3. Run DWPose ONNX on both isolated sequences in the existing dedicated CPU
   environment. Only single-person detections are accepted. Save pose arrays so a
   retry with unchanged inputs can reuse them.
4. Resample the foreground timeline to background FPS, holding the last frame if
   short. Compare eight arm/leg bone directions with banded dynamic time warping.
   Evaluate ordinary and mirrored correspondence; choose one globally, requiring
   a score margin of 0.03 to switch to mirrored correspondence. Mirroring changes
   the correspondence, not the actual foreground image.
5. Timing offsets are restricted to +/-0.6 seconds. Require at least 60% usable
   pose pairs, alignment cost below 0.65, and a 5% score improvement over unchanged
   timing. Otherwise retain original timing. Evaluate beat alignment on nearest original-frame poses, as in v1; do not
   interpolate confidence scores for beat acceptance. Smooth the accepted
   displacement curve with a Gaussian time kernel, then fit it using constrained
   least squares (SciPy SLSQP): 0.8–1.2x speed, <=1.0x/sec acceleration,
   +/-0.6-second shift, and fixed endpoints. Constraints act on each interval;
   a difficult interval no longer scales down the whole clip.
   Keep fractional source times, including FPS conversion. Bidirectional Farneback
   flow estimates motion to the nearer original frame. Joint and temporal inverse
   coordinate maps are composed, then that source RGB/alpha is sampled once with
   cubic interpolation. No cross-frame RGB blend is used. Flow consistency and
   photometric agreement attenuate only temporal displacement. Unreliable flow
   falls back to the joint-corrected nearer source, so adjacent confidence changes
   cannot cancel parts of a limb's joint transform and tear its silhouette.
   Integer times without joint correction retain exact original frames. Both baseline and corrected clips use this same
   interpolation for FPS conversion. Rapid motion/occlusions can still produce
   artifacts; this is CPU optical flow, not a trained frame-generation model.
6. For confident matched limbs, retain original bone lengths and apply 35% of
   the direction difference, capped at 8 degrees. Skip differences above 35
   degrees. Disable angle correction when global alignment is unreliable.
   Smooth displacements over three frames and cap final displacement to 1.5% of
   the crop's shorter side. A piecewise affine mesh transforms premultiplied RGB
   and alpha together. Border/face/torso landmarks remain fixed. Mesh inversions
   cause the frame to retain its original geometry.
7. Use corrected RGBA directly; no new diffusion video or segmentation pass.
   Temporal v5 separates joint geometry from flow confidence and still samples
   RGB/alpha once, without interior blur. Running D-3 with an older plan creates
   a new work directory and reuses saved poses, preserving previous outputs.
8. Composite normally. The last comparison cell can run immediately after E-4,
   independently of the optional subsequent UniRelight execution. It builds a
   baseline with the same placement and compositing settings, then horizontally
   concatenates baseline and corrected composites into one MP4. Playback, pause,
   and seeking are inherently synchronized. Both sides exclude UniRelight so the
   comparison isolates dance correction; the existing final relit video is still
   displayed by E-5c. The comparison is silent.

This is experimental 2D correction, not 3D reconstruction. Occlusions, missing
joints, large motion differences and loose clothing can cause artifacts. A low
confidence clip may intentionally receive no correction. Inspect `alignment.npz`
(source/baseline frame indices and proposed joint offsets), pose arrays, and
`plan.json` (mirror, coverage, acceptance and time shift) in the session work folder.

## Environment

The already installed `.model-envs/mimicmotion` environment supplies NumPy,
OpenCV, SciPy and ONNX Runtime. Required weights are only
`vendor/MimicMotion/models/DWPose/yolox_l.onnx` and
`vendor/MimicMotion/models/DWPose/dw-ll_ucoco_384.onnx`.
No additional model downloads or installs are required for this change.
The worker uses CPU ONNX providers and explicitly sets `MPLBACKEND=Agg` before
imports; the notebook inline backend is not inherited by inference.
All input, cache, work and output paths are confined to the project root.

## Verification

CPU checks with the required video-layer Python cover notebook syntax and root
guard/SVOR preservation, normal/mirror/delayed/low-confidence alignment, bounded
monotone time maps, alpha-safe mesh transforms, short-foreground handling,
fractional output FPS and a decoded side-by-side comparison artifact.
No GPU inference was run. End-to-end pose extraction was not rerun for these checks.

## Temporal correction v5

E-4 and the final comparison cell use the original `BG_SRC` as the audio source.
The comparison contains one background soundtrack. Automatic relighting also
retains this audio; older completed silent exports can be upgraded without model
inference. Audio is padded or trimmed to video duration; video frames/FPS are kept.

New plans use `correction-v6`. D-3 accepts validated v1/v2/v3/v4/v5 inputs, creates a new work
folder, and copies only pose caches. Existing output frames are not overwritten by
the migration. Rerun D-3, D-4, E-3/E-4 and the comparison cell for new results.
`alignment.npz` stores fractional source/baseline frame positions. `plan.json`
records speed extrema, interpolated frame count and held tail frames. Speed bounds
refer to the time warp before the unavoidable clamp at the end of a short input.
No new dependencies or downloads: optical flow uses the installed OpenCV.
CPU verification covers bounds at several FPS values, translated textured RGBA
interpolation, hidden-color invariance, exact integer frames, fractional FPS and
legacy-plan migration. The v5 regression also varies flow confidence across adjacent
pixels and checks that it cannot cancel parts of the joint transform.

Using the saved AESPA poses, 190 frames were regenerated with unchanged source
times and joint offsets. The jagged trouser silhouette at frame 135 was reproduced
with v4 and visibly reduced with v5, without added RGB blur. Frame count, FPS and
resolution were preserved. The comparison holds placement and depth masks fixed
to isolate this change; normal E-4 recomputes those for the new RGBA.
Audit: `work/depth_setup/edge_audit_20260914/`.

Using the same saved JENNY/JENNIE poses, CPU verification restored beat acceptance
(14.3% cost improvement), with a 0.403-second maximum shift and 0.8–1.2x speed.
This checks the calculated path, not the visual quality of newly rendered video.

## Automatic placement

E-3 previews automatic placement; E-4 calculates a complete frame-by-frame path
without manual controls. `placement.py` uses cached A/C anatomical poses with
their original masks/alpha. Hip centers (shoulders or silhouette centers when
unavailable) determine horizontal placement. Several reliable bone-length ratios
estimate size; log-scale changes are smoothed and limited to 0.45 per second.

The v4 placement path no longer shifts the entire person to keep an outstretched
hand or foot inside the screen. The compositor clips off-screen pixels while
retaining the body-center trajectory. Vertical placement aligns estimated ground
levels: ankle confidence, image bounds, nearby low-foot heights and foot velocity
select contact observations. Missing contacts are interpolated between supported
frames, with nearest valid levels at the ends. Raised feet and brief detection
losses do not immediately redefine the ground. The foreground's jump height
remains in its pose. These are 2D contact estimates, not physical floor recovery;
long missing intervals, prolonged jumps and strong camera changes can still drift.

`transform.trajectory` stores one x/y/scale per background frame. RGB placement,
saved alpha and original-B occlusion restoration use that frame's transform.
Both dance-comparison sides reuse the same path. Foreground blur and appearance
effects remain disabled. Rerun E-4 and the last comparison cell to apply v4;
E-4 reloads the anatomical module as well as placement. Depth inference and pose
extraction reuse unchanged caches; depth occlusion masks are rebuilt for the new path.

CPU regression covers missing/out-of-frame planted feet, a brief jump, gradual
ground translation, and silhouette-width changes that must not push the body.
On the saved 243-frame JENNY example, the maximum horizontal placement-offset
step fell from 66.5 to 9.8 pixels; median body-center error fell from 28.0 to
4.2 pixels. Path estimation with cached poses took 5.24 seconds. These measure
placement coordinates, not optical motion or absolute ground-contact accuracy.
Checks and render records: `work/dance_setup/ground_fix/`. Prior outputs are retained.

## Occlusion-aware replacement

E-4 now analyzes the original A/B masks with `occlusion.py` and restores original
B pixels where local evidence places B in front of C. D-2 tracking is reused;
no new SAM2 pass or model install is needed. Unknown regions stay unchanged.
Both comparison sides use the same occlusion manifest; saved C alpha excludes
restored B so subsequent relighting leaves those pixels alone. E-4 reports
supported frame count and actual overlap pixels. See OCCLUSION.md for the
heuristic, limitations and measured CPU cost. Compositing options in this
notebook explicitly disable softening/appearance effects to preserve the user's
sharpness-first settings regardless of shared compositor defaults.

## Placement stability and local limb correction (2026-09-15)

`correction-v6` keeps the v5 single-source temporal sampler and replaces the
whole-body Delaunay warp with bounded rigid limb maps. Face and torso interiors
are protected when their landmarks are reliable. Short, plausible joint gaps
are bridged; long gaps and conflicting views withdraw corrections gradually.
Large angle differences remain uncorrected. No interior RGB blur is added.

Placement v5 measures torso/thigh/shin scale independently for each person.
Only their shared, supported change updates scale, with a deadband and rate
limits. Hip-to-shoulder fallback is offset-calibrated; observed shoe boundaries
replace a fixed footwear offset when available. Permanently cropped feet use
a shared pelvis/neck anchor instead of an invented ground point.

See [DANCE_STABILITY.md](DANCE_STABILITY.md) for the video inventory,
verification scope, limitations, and generated comparison. Rerun D-3 → D-4 →
E-4 → comparison in `run_depth.ipynb`; E-3 is an optional preview.
