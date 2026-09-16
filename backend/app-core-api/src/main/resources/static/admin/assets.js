/*
 * 모티프 에셋 목록 + 활성 전환 (어드민 명세 v0.5 §3.8.1 · §3.8.5) — S15P21D104-401.
 *
 * 가격 upsert(§3.8.6)는 /admin/prices 가 한다 — 여기 겹쳐 놓지 않는다.
 * 페이지 넘김은 두지 않았다 — size=100 한 장이면 당분간 전량이다(§1.5 offset).
 */

const set = (scope, selector, text) => {
    scope.querySelector(selector).textContent = text;
};

/**
 * 행 하나를 현재 상태로 다시 칠한다.
 *
 * <p>전환 후 목록을 통째로 다시 부르지 않고 이 함수만 다시 돈다 — 필터·스크롤 위치가
 * 유지되고, 무엇보다 <b>방금 바꾼 행이 어디 갔는지 찾지 않아도 된다</b>. 활성 필터가
 * 걸린 상태에서 전체 재조회를 하면 그 행이 목록에서 사라져 성공했는지 알 수 없다.
 */
function paint(tr, asset) {
    // 활성인데 KR 가격이 없거나 꺼져 있으면 앱에 떠도 못 산다 — 붉게 띄워 먼저 눈에 걸리게 한다
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
    set(tr, '.toggle', asset.isActive ? '비활성화' : '활성화');
}

/**
 * 활성 전환 (§3.8.5).
 *
 * <p><b>비대칭을 화면이 말해 준다.</b> 비활성화는 그 에셋의 가격을 전부 같이 내리지만,
 * 재활성화는 가격을 <b>되살리지 않는다</b>(DB §30). 그래서 활성으로 켜도
 * {@code inactivePriceCount} 가 0 이 아니면 숍에서는 여전히 안 팔린다 — 이 사실을
 * 알려주지 않으면 운영자는 "활성했는데 왜 안 보이지" 를 원인 없이 만난다.
 */
async function toggle(tr, asset) {
    const button = tr.querySelector('.toggle');
    const next = !asset.isActive;
    button.disabled = true;
    clearError($('err'));
    try {
        const result = await api(`/motif-assets/${asset.assetSeq}/active`, {
            method: 'PATCH',
            body: {isActive: next},
        });
        asset.isActive = result.isActive;
        if (asset.price) {
            // 비활성화는 가격까지 내린다 — 서버가 방금 그렇게 했으므로 화면도 맞춘다
            asset.price.isActive = result.isActive ? asset.price.isActive : false;
        }
        paint(tr, asset);
        if (result.isActive && result.inactivePriceCount > 0) {
            showError($('err'), new Error(
                `에셋 ${asset.assetSeq} 를 활성했지만 비활성 가격이 ${result.inactivePriceCount}건 남아 있다`
                + ' — 가격 관리에서 다시 켜야 판매된다'));
        }
    } catch (e) {
        // 이미 같은 상태면 409 다(ASSET_STATE_UNCHANGED). 다른 창에서 먼저 바꿨다는 뜻이라
        // 오류로 띄우기보다 목록을 새로 읽어 맞추는 편이 운영자에게 덜 혼란스럽다
        if (e.code === 'ASSET_STATE_UNCHANGED') {
            await load();
            return;
        }
        showError($('err'), e);
    } finally {
        button.disabled = false;
    }
}

function assetRow(asset) {
    const tr = $('asset-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = asset.assetSeq;
    paint(tr, asset);
    // asset 은 클로저로 들고 간다 — 전환 응답으로 갱신해 다시 칠하는 데 쓴다
    tr.querySelector('.toggle').addEventListener('click', () => toggle(tr, asset));
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
