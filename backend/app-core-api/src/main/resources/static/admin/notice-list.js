/*
 * 공지 목록 (어드민 명세 v0.5 §3.10.1 · §3.10.5) — S15P21D104-572.
 *
 * 페이지 넘김은 두지 않았다 — size=100 한 장이면 당분간 전량이다.
 * 넘치면 그때 page 파라미터를 붙인다(§1.5 offset).
 */

const set = (scope, selector, text) => {
    scope.querySelector(selector).textContent = text;
};

function noticeRow(notice) {
    const tr = $('notice-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = notice.noticeSeq;
    tr.classList.toggle('deleted', notice.isDeleted);
    set(tr, '.seq', notice.noticeSeq);
    set(tr, '.artist', notice.artist?.name ?? '');
    set(tr, '.preview', notice.contentPreview);
    set(tr, '.images', notice.imageCount);
    set(tr, '.likes', notice.likeCount);
    set(tr, '.comments', notice.commentCount);
    set(tr, '.reg', new Date(notice.regDttm).toLocaleString('ko-KR'));
    if (notice.isDeleted) {
        // 삭제분은 회색으로 보여만 준다 — 수정·재삭제할 대상이 아니다
        tr.querySelector('.actions').textContent = '삭제됨';
    } else {
        tr.querySelector('.edit').href = `/admin/notices/${notice.noticeSeq}/edit`;
    }
    return tr;
}

async function load() {
    clearError($('err'));
    const params = new URLSearchParams({size: '100'});
    if ($('artist').value) params.set('artistSeq', $('artist').value);
    if ($('include-deleted').checked) params.set('includeDeleted', 'true');
    try {
        const {list, totalElements} = await api(`/notices?${params}`);
        $('notices').replaceChildren(...list.map(noticeRow));
        $('count').textContent = `${list.length}건 표시 / 전체 ${totalElements ?? list.length}건`;
    } catch (e) {
        showError($('err'), e);
    }
}

$('reload').addEventListener('click', load);
$('artist').addEventListener('change', load);
$('include-deleted').addEventListener('change', load);

$('notices').addEventListener('click', async (e) => {
    if (!e.target.classList.contains('del')) return;
    const seq = e.target.closest('tr').dataset.seq;
    // 소프트 삭제지만 복구 API 가 없다 — 화면에서는 되돌릴 수 없는 작업이다
    if (!confirm(`공지 ${seq} 을(를) 삭제합니다.\n\n앱에서 즉시 비노출되며 되돌릴 수 없습니다.`)) return;
    try {
        await api(`/notices/${seq}`, {method: 'DELETE'});
        await load();
    } catch (err) {
        showError($('err'), err);
    }
});

(async () => {
    try {
        await fillSelect($('artist'), '/artists?size=100', 'artistSeq',
            (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name));
    } catch (e) {
        showError($('err'), e);
    }
    await load();
})();
