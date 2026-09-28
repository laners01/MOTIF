# Foreground replacement and occlusion: investigation, 2026-09-14

Status: conservative local occlusion estimation is wired into run_enhancement E-4 and both comparison sides.
The single-source interpolation change is implemented separately in temporal v4.

Recommended first implementation: reuse full-frame A masks from E-2 and B masks
from D-2, infer local occlusion order on the original video with temporal
consistency, and restore original B pixels only in the B-in-front overlap regions
of the C composite. Avoid another extraction/export of B where its masks already
exist. Keep effective C alpha consistent with occlusion so later relighting does
not modify B. Preserve C's existing time map, automatic placement, and shared
before/after comparison transform.

SAM2 masks locate visible objects; they do not directly produce relative depth or
complete hidden body shape. Mask/logit overlap is not a trustworthy depth test.
A whole-person front/back flag is insufficient for intertwined arms, and changes
in C's silhouette can introduce overlaps that never existed between A and B.
Use original boundary ownership, optical-flow/temporal occlusion evidence and
pose continuity first, with uncertainty recorded; optional temporally consistent
monocular depth can help ambiguous overlap intervals but is not ground truth.
If A hid part of B and C no longer covers that part, original B pixels do not
exist there: reconstruction or temporal recovery is needed. SAM2 alone cannot
recover that texture. Reassess fixed automatic placement if the original A moves
substantially; original layer order is meaningful only with compatible placement.

Sources:
- SAM2 official repository (multi-object tracking): https://github.com/facebookresearch/sam2
- Local temporal occlusion ordering: https://brianrtaylor.github.io/cvos/
- Video Depth Anything (temporal depth alternative): https://videodepthanything.github.io/
- Visible versus amodal segmentation: CVPR 2025: Segment Anything Even Occluded

Measured evidence, latest AESPA clip: 190 frames, 576x1024, 30.0984 FPS.
D-2 metadata: model propagation 7.444 s; tracking/output total 12.672 s;
video load 4.987 s. These are existing-run timings, not a new GPU benchmark.
B masks already exist, so reusing them adds no tracking pass. A CPU microbenchmark
of an additional RGB mask blend measured about 4.82 ms/frame (0.92 s/190 frames),
excluding decode, mask I/O, ordering, depth estimation and encoding. Extra total
runtime cannot be certified before ordering is implemented and measured. If B
must be tracked anew, budget roughly 18 seconds using this run as reference,
plus order estimation and compositing. Depth or hidden-texture reconstruction
requires separate measurement; neither should be implied to fit the blend time.


Implemented local estimator
---------------------------
`occlusion.py` reuses original full-frame A masks and D-2 B masks. Small mask-gap
completion supplies boundary-ownership cues. Adjacent original frames provide
motion-compensated A support using CPU optical flow with brightness rejection.
Conflicting local ownership cues inhibit B restoration; short-range temporal
filtering stabilizes accepted cues. Evidence extends only locally inside visible
B, excluding visible A. Unknown regions retain the existing composite. This is a
heuristic and can miss long/full occlusions or misclassify imperfect masks; it is
not a depth model or an amodal reconstruction system.

E-4 restores original B pixels at supported C overlap, saves effective C alpha
with those regions removed, and records affected pixels in composition metadata.
The baseline comparison reuses the same occlusion manifest. Later relighting
therefore uses the reduced C alpha and avoids B. Mask/source hashes validate
cache reuse. Newly prepared D-2 plans retain B mask provenance; older plans locate
B masks beside the exact driver crop and validate its metadata.

CPU tests cover reversed local ownership, unknown regions, fractional alpha,
cache reuse, actual tiny-video compositing and saved relighting alpha. An audit
of existing AESPA masks (190 frames) took 14.10 seconds for estimation and found
local evidence in 26 frames. The strongest evidence frame was visually inspected
at B's arm crossing A. This is not a complete accuracy evaluation. Existing
outputs were preserved and no GPU inference was run.
