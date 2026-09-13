/*
 * 챌린지 등록 (어드민 명세 v0.5 §3.7, 부록 A) — S15P21D104-564.
 *
 * 부록 A 시퀀스: presign → S3 PUT → 슬롯 n개 입력 → 2ⁿ−1 조합 표 → validate → 등록.
 * 길이·해상도는 <video> 가 읽어 폼에 싣는다(§1.4) — core-api 에 ffmpeg 가 없다.
 *
 * 방향·길이 규칙(세로·≤60초 등)은 여기서 미리 막지 않는다. 수치는 서버 설정값
 * (motif.admin.media.rules)이고 VideoRules 가 소유한다 — 화면에 복사하면 한쪽만
 * 고쳤을 때 조용히 어긋난다.
 */

const MAX_SLOTS = 4;        // §3.7.2 하드 상한 4 (기본안)

let artists = [];
let videoKey = null;        // 업로드 완료된 원본 키
let meta = null;            // {durationMs, width, height}

// ── 표 읽기 ────────────────────────────────────────────────────────────────

/** 슬롯 표 → participants. 배열 순서가 곧 화면 좌→우이자 mask 비트 자리다(§3.7.2). */
function readSlots() {
    return [...$('slots').rows].map((tr) => ({
        artistSeq: Number(tr.querySelector('.slot-artist').value) || null,
        bbox: Object.fromEntries([...tr.querySelectorAll('[data-k]')]
            .map((input) => [input.dataset.k, input.value === '' ? null : Number(input.value)])),
    }));
}

function readMatrix() {
    return [...$('matrix').rows].map((tr) => {
        const val = (k) => tr.querySelector(`[data-k="${k}"]`).value.trim();
        return {
            mask: Number(tr.dataset.mask),
            removedSlots: JSON.parse(tr.dataset.removed),
            videoKey: val('videoKey'),
            initialSceneKey: val('initialSceneKey'),
            durationMs: val('durationMs'),
        };
    });
}

// ── 슬롯 ───────────────────────────────────────────────────────────────────

const slotLabel = (tr, i) => {
    const select = tr.querySelector('.slot-artist');
    return select.value ? select.selectedOptions[0].text : `슬롯 ${i}`;
};

function renumber() {
    [...$('slots').rows].forEach((tr, i) => {
        tr.querySelector('.idx').textContent = i;
    });
    $('add-slot').disabled = $('slots').rows.length >= MAX_SLOTS;
}

/** bbox 드래그가 채울 슬롯 — 행 하나만 active 로 둔다. */
function setActive(tr) {
    for (const row of $('slots').rows) row.classList.toggle('active', row === tr);
}

const activeRow = () => $('slots').querySelector('tr.active');

$('add-slot').addEventListener('click', () => {
    const tr = $('slot-row').content.firstElementChild.cloneNode(true);
    tr.querySelector('.slot-artist').append(
        ...artists.map((a) => new Option(artistLabel(a), a.artistSeq)));
    $('slots').append(tr);
    setActive(tr);
    renumber();
    renderMatrix();
});

$('slots').addEventListener('click', (e) => {
    const tr = e.target.closest('tr');
    if (!tr) return;
    if (e.target.classList.contains('del')) {
        tr.remove();
        renumber();
        renderMatrix();
        return;
    }
    setActive(tr);
});

// 슬롯이 바뀌면 조합 수·라벨이 따라 바뀐다
$('slots').addEventListener('input', renderMatrix);

// ── 편집본 매트릭스 ────────────────────────────────────────────────────────

/**
 * 슬롯 n개 → mask 1 … 2ⁿ−1 전 조합을 한 행씩. 서버가 요구하는 집합과 같은 규칙으로
 * 화면이 먼저 만들어 둔다 — 어드민이 조합을 빠뜨릴 여지를 없앤다(§3.7.2 검증 3).
 * 이미 넣은 키는 mask 로 되살린다.
 */
function renderMatrix() {
    const rows = [...$('slots').rows];
    const names = rows.map(slotLabel);
    const kept = new Map(readMatrix().map((r) => [r.mask, r]));

    $('matrix').replaceChildren();
    for (let mask = 1; mask <= (1 << rows.length) - 1; mask++) {
        const removed = names.map((_, i) => i).filter((i) => mask & (1 << i));
        const tr = $('matrix-row').content.firstElementChild.cloneNode(true);
        tr.dataset.mask = mask;
        tr.dataset.removed = JSON.stringify(removed);
        tr.querySelector('.mask').textContent = mask;
        tr.querySelector('.removed').textContent = removed.map((i) => `${i}. ${names[i]}`).join(', ');
        const prev = kept.get(mask);
        if (prev) {
            for (const k of ['videoKey', 'initialSceneKey', 'durationMs']) {
                tr.querySelector(`[data-k="${k}"]`).value = prev[k];
            }
        }
        $('matrix').append(tr);
    }
    refresh();
}

$('matrix').addEventListener('input', refresh);

/**
 * 조합별 입력 완료 표시 + 등록 버튼 잠금.
 *
 * 서버 규칙은 **0개이거나 정확히 2ⁿ−1개**다(SlotRules). 그래서 화면도 두 상태만 연다 —
 * 전부 비었거나 전부 찼거나. 부분 입력이 막히는 것이 요점이다: 조합 하나만 비면
 * 그 조합을 고른 사용자만 촬영 직전에 EDITED_CHALLENGE_NOT_FOUND 를 맞는다.
 *
 * 전부 비우면 "표시 전용" 챌린지다 — 영상 재생과 설명 시트의 참여자 아바타는 정상이고
 * 촬영만 안 된다. AI 편집본이 나오면 PUT /challenges/{seq}/participants 로 얹는다.
 */
function refresh() {
    const rows = readMatrix();
    let filled = 0;
    for (const tr of $('matrix').rows) {
        const row = rows.find((r) => r.mask === Number(tr.dataset.mask));
        const done = Boolean(row.videoKey && row.initialSceneKey);
        if (done) filled++;
        const cell = tr.querySelector('.done');
        cell.textContent = done ? '✓' : '–';
        cell.className = done ? 'done ok' : 'done muted';
    }
    const partial = filled > 0 && filled < rows.length;

    const status = $('matrix-status');
    if (rows.length === 0) {
        status.textContent = '슬롯 0개(솔로) — 편집본 없이 등록된다.';
    } else if (filled === 0) {
        status.textContent = `조합 ${rows.length}개 전부 비어 있다 — 편집본 없이 등록된다`
            + '(촬영 불가, 표시 전용). AI 편집본이 나오면 슬롯 교체로 얹는다.';
    } else {
        status.textContent = `조합 ${rows.length}개 중 ${filled}개 입력 완료`
            + (partial ? ` — 부분 입력은 등록할 수 없다. 전부 채우거나 전부 비워라` : '');
    }
    status.className = partial ? 'err' : 'muted';

    const slots = readSlots();
    $('submit').disabled = !(videoKey && meta
        && $('artist').value && $('song').value && $('description').value.trim()
        && slots.every((s) => s.artistSeq && Object.values(s.bbox).every((v) => v !== null))
        && !partial);
}

function buildRequest() {
    return {
        artistSeq: Number($('artist').value),
        songSeq: Number($('song').value),
        description: $('description').value.trim(),
        videoKey,
        durationMs: meta?.durationMs,
        width: meta?.width,
        height: meta?.height,
        // 선택 — 없으면 워커가 1.5초를 쓴다. 서버가 durationMs 와 대조해
        // 영상 밖이면 THUMBNAIL_AT_OUT_OF_RANGE 로 막는다(§4.8)
        thumbnailAtMs: thumbnailAtMs(),
        participants: readSlots(),
        // 키가 하나도 없으면 빈 배열을 보낸다 — 빈 문자열을 실어 보내면 @NotBlank 400 이다
        editedChallenges: readMatrix().filter((r) => r.videoKey && r.initialSceneKey).map((r) => ({
            removedSlots: r.removedSlots,
            videoKey: r.videoKey,
            initialSceneKey: r.initialSceneKey,
            // 선택 — 넣으면 서버가 원본 ±500ms 를 대조한다(§3.7.2 검증 5)
            ...(r.durationMs ? {durationMs: Number(r.durationMs)} : {}),
        })),
    };
}

// ── 1. 원본 업로드 ─────────────────────────────────────────────────────────

$('file').addEventListener('change', async () => {
    const file = $('file').files[0];
    if (!file) return;
    clearError($('err'));
    videoKey = null;
    step('upload', 'doing');
    try {
        $('frame-wrap').hidden = false;
        meta = await videoMeta(file, $('frame'));
        $('frame').currentTime = 0.01;      // 첫 프레임을 실제로 그리게 한다(bbox 드래그 배경)
        initThumbPick();
        $('meta-line').textContent = `${describe()} — 업로드 중…`;
        videoKey = await upload(file, 'CHALLENGE_VIDEO');
        $('meta-line').textContent = `${describe()} · ${videoKey}`;
        step('upload', 'done');
    } catch (e) {
        step('upload', 'fail');
        showError($('err'), e);
    }
    refresh();
});

const describe = () => `${meta.width}×${meta.height} · ${(meta.durationMs / 1000).toFixed(2)}초`;

// ── 썸네일 시각 ────────────────────────────────────────────────────────────

/**
 * 슬라이더 범위를 이 영상 길이에 맞춘다. 상한을 길이보다 조금 앞에 두는 이유:
 * 정확히 끝 지점은 마지막 프레임 다음이라 ffmpeg 가 빈 프레임을 뱉는다.
 */
function initThumbPick() {
    const max = Math.max(0, meta.durationMs / 1000 - 0.1);
    const at = $('thumb-at');
    at.max = max.toFixed(2);
    at.value = Math.min(1.5, max).toFixed(2);
    $('thumb-pick').hidden = false;
    showThumbAt();
}

/** 스크럽한 지점을 그대로 보여준다 — 등록 후 나올 썸네일이 이 프레임이다. */
function showThumbAt() {
    const v = Number($('thumb-at').value);
    $('thumb-at-label').textContent = `${v.toFixed(2)}초`;
    $('frame').currentTime = v;
}

$('thumb-at').addEventListener('input', showThumbAt);

/** 영상을 아직 안 골랐으면 보내지 않는다 — 선택 필드라 없으면 워커 기본값이다. */
const thumbnailAtMs = () => (meta ? Math.round(Number($('thumb-at').value) * 1000) : undefined);

// ── 첫 프레임 드래그 → bbox ────────────────────────────────────────────────

let dragFrom = null;

/** 포인터 위치를 표시 크기 기준 CSS px 로 — 영상 밖으로 나가면 가장자리에 붙인다. */
function point(e) {
    const r = $('frame').getBoundingClientRect();
    return {
        x: Math.min(Math.max(e.clientX - r.left, 0), r.width),
        y: Math.min(Math.max(e.clientY - r.top, 0), r.height),
    };
}

function drawBox(a, b) {
    Object.assign($('box').style, {
        left: `${Math.min(a.x, b.x)}px`,
        top: `${Math.min(a.y, b.y)}px`,
        width: `${Math.abs(a.x - b.x)}px`,
        height: `${Math.abs(a.y - b.y)}px`,
    });
    $('box').hidden = false;
}

/** 표시 px → 원본 px(좌상단 원점). 서버가 받는 bbox 는 원본 해상도 기준이다(§3.7.2). */
function writeBox(a, b) {
    const tr = activeRow();
    const r = $('frame').getBoundingClientRect();
    const put = (k, v) => {
        tr.querySelector(`[data-k="${k}"]`).value = Math.round(v);
    };
    put('leftTopX', Math.min(a.x, b.x) * meta.width / r.width);
    put('leftTopY', Math.min(a.y, b.y) * meta.height / r.height);
    put('rightBottomX', Math.max(a.x, b.x) * meta.width / r.width);
    put('rightBottomY', Math.max(a.y, b.y) * meta.height / r.height);
    refresh();
}

$('frame-wrap').addEventListener('pointerdown', (e) => {
    if (!meta || !activeRow()) return;
    dragFrom = point(e);
    $('frame-wrap').setPointerCapture(e.pointerId);
    drawBox(dragFrom, dragFrom);
});

$('frame-wrap').addEventListener('pointermove', (e) => {
    if (dragFrom) drawBox(dragFrom, point(e));
});

$('frame-wrap').addEventListener('pointerup', (e) => {
    if (!dragFrom) return;
    writeBox(dragFrom, point(e));
    dragFrom = null;
});

// ── 5. 검증·등록 ───────────────────────────────────────────────────────────

/** 409 EDITED_SET_INCOMPLETE 의 마스크 목록을 표에서 짚어 준다(§3.7.2 검증 3). */
function markMasks(data) {
    const bad = new Set([...(data?.missingMasks ?? []), ...(data?.duplicateMasks ?? [])]);
    for (const tr of $('matrix').rows) tr.classList.toggle('missing', bad.has(Number(tr.dataset.mask)));
}

function setResult(text, ok, extra) {
    $('result').className = ok ? 'ok' : 'err';
    $('result').replaceChildren(text, ...(extra ? [extra] : []));
}

$('validate').addEventListener('click', async () => {
    clearError($('err'));
    $('issues').replaceChildren();
    markMasks(null);
    step('validate', 'doing');
    try {
        // 3.7.8 — 등록과 같은 본문으로 검증만. 제출 전에 매트릭스 완결성을 확인한다
        const {valid, computedMasks = [], issues = []} = await api('/challenges/validate',
            {method: 'POST', body: buildRequest()});
        step('validate', valid ? 'done' : 'fail');
        $('issues').append(...issues.map((issue) => {
            const li = document.createElement('li');
            li.className = 'err';
            li.textContent = typeof issue === 'string' ? issue : JSON.stringify(issue);
            return li;
        }));
        setResult(valid ? `검증 통과 — 마스크 ${computedMasks.join(', ')}` : '검증 실패', valid);
    } catch (e) {
        step('validate', 'fail');
        markMasks(e.data);
        showError($('err'), e);
    }
});

$('submit').addEventListener('click', async () => {
    clearError($('err'));
    $('submit').disabled = true;
    step('create', 'doing');
    try {
        const created = await api('/challenges', {method: 'POST', body: buildRequest()});
        step('create', 'done');
        const detail = document.createElement('a');
        detail.href = `/admin/challenges/${created.challengeSeq}`;
        detail.textContent = '상세 보기';
        setResult(`등록 완료 — challengeSeq ${created.challengeSeq},`
            + ` 편집본 ${created.editedChallenges.length}건. `, true, detail);
    } catch (e) {
        step('create', 'fail');
        markMasks(e.data);
        showError($('err'), e);
        $('submit').disabled = false;
    }
});

// ── 초기화 ─────────────────────────────────────────────────────────────────

const artistLabel = (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name);

for (const id of ['artist', 'song', 'description']) $(id).addEventListener('input', refresh);

(async () => {
    try {
        // 목록은 규모가 작다(§3.6.1 "규모 소") — 한 번에 받아 select 에 채운다
        artists = await fillSelect($('artist'), '/artists?size=100', 'artistSeq', artistLabel);
        await fillSelect($('song'), '/songs?size=100', 'songSeq', (s) => `${s.title} — ${s.singerName}`);
    } catch (e) {
        showError($('err'), e);
    }
    renumber();
    refresh();
})();
