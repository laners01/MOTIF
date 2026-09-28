/*
 * 챌린지 등록 (어드민 명세 v0.5 §3.7, 부록 A) — S15P21D104-564 · 618.
 *
 * 부록 A 시퀀스: presign → S3 PUT → 슬롯 n개 입력 → validate → 등록.
 * 길이·해상도는 <video> 가 읽어 폼에 싣는다(§1.4) — core-api 에 ffmpeg 가 없다.
 *
 * 2ⁿ−1 조합 표가 없어졌다(0019) — 편집본이 슬롯 1개당 1개다.
 *
 * 편집본 입력도 없어졌다(2026-09-22). 키가 private/edited/{challengeSeq}/{slotSeq}/…
 * 라 슬롯 seq 를 품고 있어, 이 화면에서는 아직 자리를 정할 수 없다. 등록으로 슬롯을
 * 만들고 나면 상세 화면이 슬롯별로 §3.7.6 을 부른다.
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

/** 슬롯 표 → participants. 배열 순서가 곧 화면 좌→우이자 slotOrder 다(§3.7.2). */
function readSlots() {
    const anchorOf = (tr) => Object.fromEntries(['x', 'y', 'r'].map((k) => {
        const v = tr.querySelector(`[data-k="${k}"]`).value;
        return [k, v === '' ? null : Number(v)];
    }));
    return [...$('slots').rows].map((tr) => ({
        artistSeq: Number(tr.querySelector('.slot-artist').value) || null,
        anchor: anchorOf(tr),
    }));
}

// ── 슬롯 ───────────────────────────────────────────────────────────────────

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
    refresh();
});

$('slots').addEventListener('click', (e) => {
    const tr = e.target.closest('tr');
    if (!tr) return;
    if (e.target.classList.contains('del')) {
        tr.remove();
        renumber();
        refresh();
        drawDots();
        return;
    }
    setActive(tr);
});

// 슬롯이 바뀌면 완료 표시·원이 따라 바뀐다
$('slots').addEventListener('input', (e) => {
    if (e.target.dataset.k === 'r') {
        e.target.closest('tr').querySelector('.r-label').textContent = Number(e.target.value).toFixed(4);
    }
    refresh();
    drawDots();
});

/** 슬롯 입력 상태 표시 + 등록 버튼 잠금. 편집본은 여기서 다루지 않는다. */
function refresh() {
    const rows = readSlots();

    const status = $('matrix-status');
    status.className = 'muted';
    status.textContent = rows.length === 0
        ? '슬롯 0개(솔로) — 사용자가 교체해 들어갈 자리가 없다.'
        : `슬롯 ${rows.length}개. 등록하면 전부 편집본이 빈 상태로 나고`
          + ' — 그때는 앱에서 고를 수 없다 — 상세 화면에서 슬롯별로 올린다(§3.7.6).';

    $('submit').disabled = !(videoKey && meta
        && $('artist').value && $('song').value && $('description').value.trim()
        && rows.every((s) => s.artistSeq && Object.values(s.anchor).every((v) => v !== null)));
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

/**
 * 400 PARTICIPANT_INVALID 의 위반 목록을 표에서 짚어 준다(§3.7.2 검증 2).
 * {@code data} 는 {@code ["ANCHOR_INVALID[1]", …]} 처럼 슬롯 인덱스를 끝에 달고 온다.
 */
function markSlots(data) {
    const bad = new Set((Array.isArray(data) ? data : [])
        .map((v) => Number(String(v).match(/\[(\d+)]$/)?.[1]))
        .filter((i) => !Number.isNaN(i)));
    [...$('slots').rows].forEach((tr, i) => tr.classList.toggle('missing', bad.has(i)));
}

function setResult(text, ok, extra) {
    $('result').className = ok ? 'ok' : 'err';
    $('result').replaceChildren(text, ...(extra ? [extra] : []));
}

$('validate').addEventListener('click', async () => {
    clearError($('err'));
    $('issues').replaceChildren();
    markSlots(null);
    step('validate', 'doing');
    try {
        // 3.7.8 — 등록과 같은 본문으로 검증만. 제출 전에 슬롯 규칙을 확인한다
        const {valid, issues = []} = await api('/challenges/validate',
            {method: 'POST', body: buildRequest()});
        step('validate', valid ? 'done' : 'fail');
        $('issues').append(...issues.map((issue) => {
            const li = document.createElement('li');
            li.className = 'err';
            li.textContent = typeof issue === 'string' ? issue : JSON.stringify(issue);
            return li;
        }));
        setResult(valid ? '검증 통과' : '검증 실패', valid);
        if (!valid) markSlots(issues[0]?.data);
    } catch (e) {
        step('validate', 'fail');
        markSlots(e.data);
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
        // 편집본은 여기서 못 올린다 — 상세 화면이 슬롯 seq 를 알아야 키 자리가 정해진다
        detail.textContent = created.participants.length
            ? '상세로 가서 편집본 올리기'
            : '상세 보기';
        setResult(`등록 완료 — challengeSeq ${created.challengeSeq},`
            + ` 슬롯 ${created.participants.length}개(편집본은 아직 없다). `, true, detail);
    } catch (e) {
        step('create', 'fail');
        markSlots(e.data);
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
