/*
 * 모티프 결과물 목록 (어드민 명세 v0.5 §3.17.1).
 *
 * 결과물은 전부 보호 등급이라 재생 주소에 서명이 붙어 온다 — playVideo 가 처리한다.
 * 아티스트 이름은 응답에 없다 — 필터용으로 받은 아티스트 목록에서 찾는다.
 */

let page = 0;
const artistNames = new Map();

const set = (scope, selector, text) => {
    scope.querySelector(selector).textContent = text ?? '';
};

function play(r) {
    $('player-box').hidden = false;
    $('player-note').hidden = true;
    $('player-title').textContent = `#${r.resultSeq} · ${r.userNickname} · ${r.assetTitle}`;
    $('player').onerror = () => {
        $('player-note').textContent = `재생하지 못했다 — ${r.videoUrl}`;
        $('player-note').hidden = false;
    };
    playVideo($('player'), r.videoUrl).catch((e) => showError($('err'), e));
}

function row(r) {
    const tr = $('row').content.firstElementChild.cloneNode(true);
    tr.classList.toggle('missing', r.isDeleted);
    set(tr, '.seq', r.resultSeq + (r.isDeleted ? ' (삭제)' : ''));
    tr.querySelector('.thumb').src = r.thumbnailUrl ?? '';
    set(tr, '.user', `${r.userNickname} (#${r.userSeq})`);
    set(tr, '.artist', artistNames.get(r.artistSeq) ?? `#${r.artistSeq}`);
    set(tr, '.asset', `${r.assetTitle} (#${r.assetSeq})`);
    set(tr, '.desc', r.description);
    set(tr, '.duration', r.durationMs ? `${(r.durationMs / 1000).toFixed(1)}초` : '');
    set(tr, '.reg', new Date(r.regDttm).toLocaleString('ko-KR'));
    tr.querySelector('.play').addEventListener('click', () => play(r));
    return tr;
}

async function load() {
    clearError($('err'));
    const params = new URLSearchParams({page: String(page), size: '20'});
    if ($('artist').value) params.set('artistSeq', $('artist').value);
    if ($('asset').value) params.set('assetSeq', $('asset').value);
    if ($('user').value) params.set('userSeq', $('user').value);
    if ($('deleted').checked) params.set('includeDeleted', 'true');
    try {
        const data = await api(`/motif-results?${params}`);
        $('rows').replaceChildren(...data.list.map(row));
        $('count').textContent = `${data.page + 1} / ${Math.max(data.totalPages, 1)} 페이지 · 전체 ${data.totalElements}건`;
        $('prev').disabled = !data.hasPrevious;
        $('next').disabled = !data.hasNext;
    } catch (e) {
        showError($('err'), e);
    }
}

function reload() {
    page = 0;
    load();
}

$('reload').addEventListener('click', reload);
$('artist').addEventListener('change', reload);
$('deleted').addEventListener('change', reload);
$('prev').addEventListener('click', () => { page--; load(); });
$('next').addEventListener('click', () => { page++; load(); });

(async () => {
    // 드롭다운이 비어도 목록은 돈다 — 이름 대신 #seq 가 보일 뿐이다
    try {
        const artists = await fillSelect($('artist'), '/artists?size=100', 'artistSeq',
            (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name));
        artists.forEach((a) => artistNames.set(a.artistSeq, a.name));
    } catch (e) {
        showError($('err'), e);
    }
    await load();
})();
