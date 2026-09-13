/*
 * 모티프 에셋 목록 (어드민 명세 v0.5 §3.8.1) — S15P21D104-401.
 *
 * 목록만이다. 활성 전환(§3.8.5)·가격 upsert(§3.8.6)는 /admin/prices 가 이미 한다.
 * 페이지 넘김은 두지 않았다 — size=100 한 장이면 당분간 전량이다(§1.5 offset).
 */

const set = (scope, selector, text) => {
    scope.querySelector(selector).textContent = text;
};

function assetRow(asset) {
    const tr = $('asset-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = asset.assetSeq;
    // 활성인데 KR 가격이 없으면 앱에 떠도 못 산다 — 붉게 띄워 먼저 눈에 걸리게 한다
    tr.classList.toggle('missing', asset.isActive && !asset.price?.isActive);
    set(tr, '.seq', asset.assetSeq);
    set(tr, '.artist', asset.artist
        ? (asset.artist.groupName ? `${asset.artist.name} (${asset.artist.groupName})` : asset.artist.name)
        : '');
    set(tr, '.title', asset.title);
    set(tr, '.duration', `${(asset.durationMs / 1000).toFixed(1)}초`);
    set(tr, '.type', asset.assetType);
    set(tr, '.active', asset.isActive ? '활성' : '비활성');
    // price 가 null 이면 그 에셋은 팔리지 않는다(§3.8.1) — 운영이 가장 먼저 봐야 할 상태다
    set(tr, '.price', asset.price
        ? `${Number(asset.price.amount).toLocaleString('ko-KR')} ${asset.price.currency}`
            + (asset.price.isActive ? '' : ' (가격 비활성)')
        : '가격 미설정');
    set(tr, '.purchases', asset.purchaseCount);
    set(tr, '.results', asset.resultCount);
    set(tr, '.reg', new Date(asset.regDttm).toLocaleString('ko-KR'));
    return tr;
}

async function load() {
    clearError($('err'));
    const params = new URLSearchParams({size: '100'});
    if ($('artist').value) params.set('artistSeq', $('artist').value);
    if ($('asset-type').value) params.set('assetType', $('asset-type').value);
    if ($('is-active').value) params.set('isActive', $('is-active').value);
    try {
        const {list, totalElements} = await api(`/motif-assets?${params}`);
        $('assets').replaceChildren(...list.map(assetRow));
        $('count').textContent = `${list.length}건 표시 / 전체 ${totalElements ?? list.length}건`;
    } catch (e) {
        showError($('err'), e);
    }
}

$('reload').addEventListener('click', load);
$('artist').addEventListener('change', load);
$('asset-type').addEventListener('change', load);
$('is-active').addEventListener('change', load);

(async () => {
    // 필터 드롭다운이 비어도 목록은 전량 조회로 돌아간다 — 채우기 실패를 삼킨다
    try {
        await fillSelect($('artist'), '/artists?size=100', 'artistSeq',
            (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name));
    } catch (e) {
        showError($('err'), e);
    }
    await load();
})();
