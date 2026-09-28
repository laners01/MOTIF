# Anatomical placement, preservation and occlusion update

The run_enhancement notebook uses AESPA_Serenade_background.mp4 (remove id2,
retain id1) and AESPA_Serenade_user.mp4 (id1) as the saved current selection.

1. Placement now extracts full-frame A and corrected-C DWPose landmarks using the
   existing CPU environment. Horizontal alignment uses hip center (shoulders or
   mask center only when hips are unavailable), not whichever foot happens to be
   lowest. Left/right ankle identities are maintained with support hysteresis.
   Vertical alignment uses the respective supporting ankle with a small sole
   allowance. Source and reference support legs need not be the same anatomical
   side in mirror mode; the body center remains the horizontal anchor. A visibility
   guard prevents the foreground from leaving the frame while the reference is
   visible. Pose caches are keyed by source and mask/frame hashes.
2. Scale uses confident torso, thigh, shin and shoulder segment ratios. Median
   filtering, temporal smoothing and local rate bounds reject posture-related
   outliers while allowing camera/person distance changes. This is projected 2D
   anatomy, not calibrated 3D; substantial out-of-plane rotation can still change
   estimated lengths.
3. The continuous SVOR background is retained, and original B pixels are restored
   using B's mask. A narrow exterior band around B blends into SVOR, excluding A's
   removal mask so original A cannot return through the blend. Before B masks
   are available, the background is the unmodified SVOR result. The former
   A-shaped original/SVOR blend and local color correction were removed because
   they created ghost silhouettes. This preserves B exactly before encoding,
   not the entire original background. The intermediate is lossless H264 YUV444
   (RGB conversion can round values); final MP4 encoding adds normal compression.
4. Local original A/B boundary and motion evidence is retained. Where reliable
   ankle observations show a sustained ground-contact depth difference, a shared-
   floor ordering hint extends B visibility beyond the tiny boundary fragments.
   Opposing local A-front cues override this hint. This is a heuristic appropriate
   to the current shared-floor dance scene, not a universal depth solution. The
   final effective C alpha excludes B so relighting cannot affect restored B.

CPU checks exercise original-pixel preservation, foot switches without lateral
body jumps, anatomical scale and occlusion alpha. The user's explicit request to
execute all notebook cells is handled as the production run (including GPU SVOR
and UniRelight), separately from these CPU verification checks.

Runner: work/dance_setup/execute_enhancement.py. It executes each code cell in a
fresh video-layer kernel, retains real execution counts/outputs in the notebook,
and writes progress and failures under work/dance_setup/full_execution. Previous
notebook state is archived there. All caches/connections/output paths stay under
the project root. Final decoded-video audit is performed by
work/dance_setup/audit_completed_run.py without any model inference.

After inspecting the first complete run, low-frequency fill-color alignment was
added to preservation. work/dance_setup/refine_enhancement.py reuses that run's
tracking/pose files and executes E-4 through the final comparison again. Its real
cell outputs replace those cells after completion; the original complete-run
record and videos are retained. Progress is in work/dance_setup/refinement_execution.
