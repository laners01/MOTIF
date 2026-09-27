/*
 * 참여 영상 목록 (어드민 명세 v0.5 §3.12.1).
 *
 * 재생 주소는 보호 등급(private/)이라 서명이 붙어 온다 — playVideo 가 조각 요청에도
 * 같은 서명을 붙인다(admin.js).
 */

let page = 0;

const set = (scope, selector, text) => {
    scope.querySelector(selector).textContent = text ?? '';
};

function play(v) {
    $('player-box').hidden = false;
    $('player-note').hidden = true;
    $('player-title').textContent = `#${v.videoSeq} · ${v.author?.nickname ?? ''}`;
    $('player').onerror = () => {
        $('player-note').textContent = `재생하지 못했다 — ${v.videoUrl}`;
        $('player-note').hidden = false;
    };
    playVideo($('player'), v.videoUrl).catch((e) => showError($('err'), e));
}

function row(v) {
    const tr = $('row').content.firstElementChild.cloneNode(true);
    // 삭제분은 붉게 — 신고 처리에서 "이미 지워졌다" 를 바로 보이게 한다
    tr.classList.toggle('missing', v.isDeleted);
    set(tr, '.seq', v.videoSeq);
    tr.querySelector('.thumb').src = v.thumbnailUrl ?? '';
    set(tr, '.challenge', v.challengeSeq);
    set(tr, '.author', v.author ? `${v.author.nickname} (#${v.author.userSeq})` : '');
    set(tr, '.desc', v.description);
    set(tr, '.duration', v.durationMs ? `${(v.durationMs / 1000).toFixed(1)}초` : '');
    set(tr, '.visibility', v.visibility + (v.isDeleted ? ' · 삭제' : ''));
    set(tr, '.views', v.viewCount);
    set(tr, '.likes', v.likeCount);
    set(tr, '.comments', v.commentCount);
    set(tr, '.reports', v.reportCount);
    set(tr, '.reg', new Date(v.regDttm).toLocaleString('ko-KR'));
    tr.querySelector('.play').addEventListener('click', () => play(v));
    return tr;
}

async function load() {
    clearError($('err'));
    const params = new URLSearchParams({page: String(page), size: '20'});
    if ($('challenge').value) params.set('challengeSeq', $('challenge').value);
    if ($('user').value) params.set('userSeq', $('user').value);
    if ($('visibility').value) params.set('visibility', $('visibility').value);
    if ($('deleted').checked) params.set('includeDeleted', 'true');
    try {
        const data = await api(`/videos?${params}`);
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
$('visibility').addEventListener('change', reload);
$('deleted').addEventListener('change', reload);
$('prev').addEventListener('click', () => { page--; load(); });
$('next').addEventListener('click', () => { page++; load(); });

load();
