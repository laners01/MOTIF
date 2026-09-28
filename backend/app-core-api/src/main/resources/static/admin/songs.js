/*
 * 곡 목록·등록 (어드민 명세 v0.5 §3.6) — §1.7 "곡 — 목록/등록".
 *
 * 수정은 없다 — §3.6.3 이 삭제됐다(R1, 곡은 불변). §1.7 표에는 "목록/등록/수정" 으로
 * 남아 있지만 API 가 없으므로 화면도 만들지 않는다.
 *
 * 페이지 넘김은 두지 않았다 — size=100 한 장이면 당분간 전량이다(§1.5 offset).
 */

const set = (scope, selector, text) => {
    scope.querySelector(selector).textContent = text;
};

/** 마지막으로 조회한 목록 — 등록 전 동일 곡명 경고에 쓴다(§3.6.2). */
let loaded = [];

function songRow(song) {
    const tr = $('song-row').content.firstElementChild.cloneNode(true);
    set(tr, '.seq', song.songSeq);
    set(tr, '.title', song.title);
    set(tr, '.singer', song.singerName);
    set(tr, '.challenges', song.challengeCount);
    set(tr, '.reg', new Date(song.regDttm).toLocaleString('ko-KR'));
    return tr;
}

async function load() {
    clearError($('list-err'));
    const params = new URLSearchParams({size: '100'});
    if ($('q').value.trim()) params.set('q', $('q').value.trim());
    try {
        const {list, totalElements} = await api(`/songs?${params}`);
        loaded = list;
        $('songs').replaceChildren(...list.map(songRow));
        $('count').textContent = `${list.length}건 표시 / 전체 ${totalElements ?? list.length}건`;
    } catch (e) {
        showError($('list-err'), e);
    }
}

/**
 * 동일 곡명 경고 (§3.6.2) — 막지 않는다. 리믹스·재녹음이 같은 제목을 쓰므로
 * DB 에 UNIQUE 가 없고 서버도 그대로 받는다. 실수를 알아채라는 표시일 뿐이다.
 *
 * ponytail: 지금 화면에 떠 있는 목록(최대 100건·검색어 적용분)만 본다. 곡이 100건을
 * 넘어가면 경고를 놓칠 수 있다 — 그때 `/songs?q=` 로 한 번 더 조회해 검사한다.
 */
function warnDuplicate() {
    const title = $('title').value.trim().toLowerCase();
    const hits = title ? loaded.filter((s) => s.title.toLowerCase() === title) : [];
    $('dup').hidden = hits.length === 0;
    $('dup').textContent = hits.length === 0 ? ''
        : `같은 곡명이 이미 ${hits.length}건 있습니다 — ${hits.map((s) => `#${s.songSeq} ${s.singerName}`).join(', ')}. `
            + '리믹스·재녹음이면 그대로 등록하면 됩니다';
}

async function save() {
    clearError($('form-err'));
    $('msg').textContent = '';
    const title = $('title').value.trim();
    const singerName = $('singer-name').value.trim();
    if (!title || !singerName) {
        showError($('form-err'), new Error('곡명과 가수명을 모두 입력하세요'));
        return;
    }
    $('save').disabled = true;
    try {
        const {songSeq} = await api('/songs', {method: 'POST', body: {title, singerName}});
        $('msg').textContent = `등록 완료 — songSeq ${songSeq}`;
        $('title').value = '';
        $('singer-name').value = '';
        $('dup').hidden = true;
        await load();
    } catch (e) {
        showError($('form-err'), e);
    } finally {
        $('save').disabled = false;
    }
}

$('reload').addEventListener('click', load);
$('q').addEventListener('search', load);
$('title').addEventListener('input', warnDuplicate);
$('save').addEventListener('click', save);

load();
