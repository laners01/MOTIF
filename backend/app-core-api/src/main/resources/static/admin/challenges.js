/*
 * 챌린지 목록 (어드민 명세 v0.5 §3.7.1) — S15P21D104-395.
 *
 * 목록이다. 등록은 /admin/challenges/new(부록 A), # 칸이 상세(§3.7.3)로 간다.
 * 슬롯 교체·삭제는 별도 티켓이다.
 * 페이지 넘김은 두지 않았다 — size=100 한 장이면 당분간 전량이다(§1.5 offset).
 */

const set = (scope, selector, text) => {
    scope.querySelector(selector).textContent = text;
};

function challengeRow(challenge) {
    const tr = $('challenge-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = challenge.challengeSeq;
    tr.classList.toggle('deleted', challenge.isDeleted);
    const seqLink = document.createElement('a');
    seqLink.href = `/admin/challenges/${challenge.challengeSeq}`;
    seqLink.textContent = challenge.challengeSeq;
    tr.querySelector('.seq').replaceChildren(seqLink);
    set(tr, '.artist', challenge.artist?.name ?? '');
    set(tr, '.song', challenge.song ? `${challenge.song.title} / ${challenge.song.singerName}` : '');
    set(tr, '.duration', `${(challenge.durationMs / 1000).toFixed(1)}초`);
    set(tr, '.slots', challenge.slotCount);
    set(tr, '.edited', challenge.editedCount);
    set(tr, '.views', challenge.viewCount);
    set(tr, '.participants', challenge.participantCount);
    set(tr, '.likes', challenge.likeCount);
    set(tr, '.comments', challenge.commentCount);
    set(tr, '.reg', new Date(challenge.regDttm).toLocaleString('ko-KR'));
    return tr;
}

async function load() {
    clearError($('err'));
    const params = new URLSearchParams({size: '100'});
    if ($('artist').value) params.set('artistSeq', $('artist').value);
    if ($('song').value) params.set('songSeq', $('song').value);
    if ($('include-deleted').checked) params.set('includeDeleted', 'true');
    try {
        const {list, totalElements} = await api(`/challenges?${params}`);
        $('challenges').replaceChildren(...list.map(challengeRow));
        $('count').textContent = `${list.length}건 표시 / 전체 ${totalElements ?? list.length}건`;
    } catch (e) {
        showError($('err'), e);
    }
}

$('reload').addEventListener('click', load);
$('artist').addEventListener('change', load);
$('song').addEventListener('change', load);
$('include-deleted').addEventListener('change', load);

(async () => {
    // 필터 드롭다운이 비어도 목록은 전량 조회로 돌아간다 — 채우기 실패를 삼킨다
    try {
        await fillSelect($('artist'), '/artists?size=100', 'artistSeq',
            (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name));
        await fillSelect($('song'), '/songs?size=100', 'songSeq',
            (s) => `${s.title} / ${s.singerName}`);
    } catch (e) {
        showError($('err'), e);
    }
    await load();
})();
