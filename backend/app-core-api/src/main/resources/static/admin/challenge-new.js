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

/** 조합별 입력 완료 표시 + 미완이면 등록 버튼 잠금. */
function refresh() {
    const rows = readMatrix();
    let missing = 0;
    for (const tr of $('matrix').rows) {
        const row = rows.find((r) => r.mask === Number(tr.dataset.mask));
        const done = Boolean(row.videoKey && row.initialSceneKey);
        if (!done) missing++;
        const cell = tr.querySelector('.done');
        cell.textContent = done ? '✓' : '–';
        cell.className = done ? 'done ok' : 'done muted';
    }

    const status = $('matrix-status');
    status.textContent = rows.length === 0
        ? '슬롯 0개(솔로) — 편집본 없이 등록된다.'
        : `조합 ${rows.length}개 중 ${rows.length - missing}개 입력 완료`
        + (missing ? ` — 미완 ${missing}개, 등록할 수 없다` : '');
    status.className = missing ? 'err' : 'muted';

    const slots = readSlots();
    $('submit').disabled = !(videoKey && meta
        && $('artist').value && $('song').value && $('description').value.trim()
        && slots.every((s) => s.artistSeq && Object.values(s.bbox).every((v) => v !== null))
        && missing === 0);
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
        participants: readSlots(),
        editedChallenges: readMatrix().map((r) => ({
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
