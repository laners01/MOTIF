/*
 * 챌린지 등록 (어드민 명세 v0.5 §3.7, 부록 A) — S15P21D104-564 · 618.
 *
 * 부록 A 시퀀스: presign → S3 PUT → 슬롯 n개 입력 → 2ⁿ−1 조합 표 → validate → 등록.
 * 길이·해상도는 <video> 가 읽어 폼에 싣는다(§1.4) — core-api 에 ffmpeg 가 없다.
 *
 * 방향·길이 규칙(세로·≤60초 등)은 여기서 미리 막지 않는다. 수치는 서버 설정값
 * (motif.admin.media.rules)이고 VideoRules 가 소유한다 — 화면에 복사하면 한쪽만
 * 고쳤을 때 조용히 어긋난다.
 */

const MAX_SLOTS = 4;        // §3.7.2 하드 상한 4 (기본안)
const MATRIX_KEYS = ['videoKey', 'initialSceneKey', 'previewImageKey', 'durationMs'];

let artists = [];
let videoKey = null;        // 업로드 완료된 원본 키
let meta = null;            // {durationMs, width, height}

// ── 표 읽기 ────────────────────────────────────────────────────────────────

/** 슬롯 표 → participants. 배열 순서가 곧 화면 좌→우이자 mask 비트 자리다(§3.7.2). */
function readSlots() {
    return [...$('slots').rows].map((tr) => ({
        artistSeq: Number(tr.querySelector('.slot-artist').value) || null,
        anchor: Object.fromEntries([...tr.querySelectorAll('[data-k]')]
            .map((input) => [input.dataset.k, input.value === '' ? null : Number(input.value)])),
    }));
}

function readMatrix() {
    return [...$('matrix').rows].map((tr) => {
        const val = (k) => tr.querySelector(`[data-k="${k}"]`).value.trim();
        return {
            mask: Number(tr.dataset.mask),
            removedSlots: JSON.parse(tr.dataset.removed),
            ...Object.fromEntries(MATRIX_KEYS.map((k) => [k, val(k)])),
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

/** 프레임 클릭이 채울 슬롯 — 행 하나만 active 로 둔다. */
function setActive(tr) {
    for (const row of $('slots').rows) row.classList.toggle('active', row === tr);
    drawDots();
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
        drawDots();
        return;
    }
    setActive(tr);
});

// 슬롯이 바뀌면 조합 수·라벨·원이 따라 바뀐다
$('slots').addEventListener('input', (e) => {
    if (e.target.dataset.k === 'r') {
        e.target.closest('tr').querySelector('.r-label').textContent = Number(e.target.value).toFixed(4);
    }
    renderMatrix();
    drawDots();
});

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
            for (const k of MATRIX_KEYS) {
                tr.querySelector(`[data-k="${k}"]`).value = prev[k];
            }
        }
        $('matrix').append(tr);
    }
    refresh();
}

$('matrix').addEventListener('input', refresh);

/**
 * 매트릭스 파일 → presign → S3 PUT → 옆 키 칸에 채운다(§3.2). AI팀이 키를 직접 주면
 * 텍스트 칸에 붙여도 된다 — 서버는 키만 받는다. 아직 challengeSeq 가 없어
 * edited/pending/ 아래로 발급된다. 영상은 <video> 로 길이를 읽어 durationMs 도 채운다 —
 * 서버가 원본 ±500ms 를 대조한다.
 */
$('matrix').addEventListener('change', async (e) => {
    const input = e.target;
    if (input.type !== 'file' || !input.files[0]) return;
    const tr = input.closest('tr');
    const keyInput = tr.querySelector(`[data-k="${input.dataset.for}"]`);
    const file = input.files[0];
    clearError($('err'));
    input.disabled = true;
    keyInput.value = '';
    keyInput.placeholder = '업로드 중…';
    try {
        if (input.dataset.for === 'videoKey') {
            const m = await videoMeta(file, document.createElement('video'));
            tr.querySelector('[data-k="durationMs"]').value = m.durationMs;
        }
        keyInput.value = await upload(file, input.dataset.kind);
    } catch (err) {
        showError($('err'), err);
    } finally {
        input.disabled = false;
        refresh();
    }
});

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
const rowDone = (r) => Boolean(r.videoKey && r.initialSceneKey && r.previewImageKey);

function refresh() {
    const rows = readMatrix();
    let filled = 0;
    for (const tr of $('matrix').rows) {
        const row = rows.find((r) => r.mask === Number(tr.dataset.mask));
        const done = rowDone(row);
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
        && slots.every((s) => s.artistSeq && Object.values(s.anchor).every((v) => v !== null))
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
        editedChallenges: readMatrix().filter(rowDone).map((r) => ({
            removedSlots: r.removedSlots,
            videoKey: r.videoKey,
            initialSceneKey: r.initialSceneKey,
            previewImageKey: r.previewImageKey,
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
    resetThumbPick();       // 앞서 고른 영상의 시각이 남지 않게 한다
    step('upload', 'doing');
    try {
        $('frame-wrap').hidden = false;
        meta = await videoMeta(file, $('frame'));
        $('frame').currentTime = 0.01;      // 첫 프레임을 실제로 그리게 한다(앵커 클릭 배경)
        initThumbPick($('frame'), meta.durationMs);
        $('meta-line').textContent = `${describe()} — 업로드 중…`;
        videoKey = await upload(file, 'CHALLENGE_VIDEO');
        $('meta-line').textContent = `${describe()} · ${videoKey}`;
        step('upload', 'done');
    } catch (e) {
        step('upload', 'fail');
        showError($('err'), e);
    }
    refresh();
    drawDots();
});

const describe = () => `${meta.width}×${meta.height} · ${(meta.durationMs / 1000).toFixed(2)}초`;

// 썸네일 시각 스크럽은 admin.js 공용이다(initThumbPick·thumbnailAtMs) — 에셋 등록·
// 교체 화면이 같은 것을 쓴다. 앵커 클릭 배경으로 이미 띄워 둔 <video> 를 넘긴다.

// ── 프레임 클릭 → 앵커 ─────────────────────────────────────────────────────
//
// 서버가 받는 앵커는 썸네일 대비 0~1 비율이다(§3.7.2). 썸네일은 원본과 같은 비율로
// 뽑히므로 지금 띄운 <video> 표시 크기로 나누면 그대로 비율이 된다 — 원본 px 를 거칠
// 이유가 없다. 안드로이드도 같은 식으로 뷰 크기를 곱한다.

$('frame-wrap').addEventListener('click', (e) => {
    const tr = activeRow();
    if (!meta || !tr) return;
    const r = $('frame').getBoundingClientRect();
    const put = (k, v) => {
        tr.querySelector(`[data-k="${k}"]`).value = Math.min(Math.max(v, 0), 1).toFixed(4);
    };
    put('x', (e.clientX - r.left) / r.width);
    put('y', (e.clientY - r.top) / r.height);
    refresh();
    drawDots();
});

/** 슬롯마다 원 하나 — 시안(10-1)처럼 썸네일 위에 프로필 사진이 앉는 모습을 그대로 미리 본다. */
function drawDots() {
    const r = $('frame').getBoundingClientRect();
    $('dots').replaceChildren(...[...$('slots').rows].flatMap((tr, i) => {
        const v = (k) => tr.querySelector(`[data-k="${k}"]`).value;
        if (v('x') === '' || v('y') === '') return [];
        const artist = artists.find((a) => String(a.artistSeq) === tr.querySelector('.slot-artist').value);
        return [dot(tr.classList.contains('active'), Number(v('x')) * r.width, Number(v('y')) * r.height,
            Number(v('r')) * r.width, artist?.profileImageUrl, i)];
    }));
}

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
