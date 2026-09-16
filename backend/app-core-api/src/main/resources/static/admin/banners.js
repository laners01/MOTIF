/*
 * 숍 배너 화면 (어드민 명세 §3.11).
 *
 * 순서는 화면에서만 옮겨 두고 "순서 저장" 에서 한 번에 보낸다(§3.11.4 전량 교체).
 * 한 건씩 보내는 API 가 없는 것은 display_order 가 UNIQUE 라 중간 상태가 반드시
 * 부딪히기 때문이다 — 화면도 같은 이유로 낙관적 미리보기를 하지 않는다.
 */

let banners = [];        // 서버가 준 순서
let dirty = false;       // 화면에서 순서를 옮겼는가

async function load() {
    clearError($('err'));
    try {
        banners = (await api('/shop-banners')).list;
        dirty = false;
        render();
    } catch (e) {
        showError($('err'), e);
    }
}

function render() {
    const tbody = $('banners');
    tbody.replaceChildren();
    banners.forEach((banner, index) => {
        const row = $('banner-row').content.cloneNode(true);
        const tr = row.querySelector('tr');
        tr.querySelector('.seq').textContent = banner.shopBannerSeq;
        // base URL 미설정(로컬·CI)이면 imageUrl 이 null 이다 — 키라도 보여준다
        const img = tr.querySelector('.thumb');
        if (banner.imageUrl) {
            img.src = banner.imageUrl;
        } else {
            img.replaceWith(document.createTextNode(banner.imageKey));
        }
        tr.querySelector('.order').textContent = banner.displayOrder;
        tr.querySelector('.active').checked = banner.isActive;
        tr.querySelector('.reg').textContent = (banner.regDttm ?? '').slice(0, 10);

        const msg = tr.querySelector('.msg');
        tr.querySelector('.up').disabled = index === 0;
        tr.querySelector('.down').disabled = index === banners.length - 1;
        tr.querySelector('.up').addEventListener('click', () => move(index, -1));
        tr.querySelector('.down').addEventListener('click', () => move(index, 1));
        tr.querySelector('.active').addEventListener('change', (e) =>
                patch(banner.shopBannerSeq, {isActive: e.target.checked}, msg));
        tr.querySelector('.replace').addEventListener('click', () => replaceImage(banner, msg));
        tr.querySelector('.delete').addEventListener('click', () => remove(banner, msg));
        tbody.append(row);
    });

    $('save-order').disabled = !dirty;
    $('order-hint').textContent = dirty ? '순서가 바뀌었다 — 저장해야 반영된다' : '';
}

function move(index, delta) {
    const to = index + delta;
    [banners[index], banners[to]] = [banners[to], banners[index]];
    dirty = true;
    render();
}

async function saveOrder() {
    clearError($('err'));
    $('save-order').disabled = true;
    try {
        // 응답이 재정렬된 목록이다 — 다시 조회하지 않는다
        banners = (await api('/shop-banners/order', {
            method: 'PUT',
            body: {orderedBannerSeqs: banners.map((b) => b.shopBannerSeq)},
        })).list;
        dirty = false;
        render();
    } catch (e) {
        showError($('err'), e);
        // 서버가 거절했으면 화면의 순서는 믿을 수 없다 — 서버 상태로 되돌린다
        await load();
    }
}

async function patch(shopBannerSeq, body, msg) {
    msg.textContent = '저장 중…';
    try {
        const updated = await api(`/shop-banners/${shopBannerSeq}`, {method: 'PATCH', body});
        const index = banners.findIndex((b) => b.shopBannerSeq === shopBannerSeq);
        banners[index] = updated;
        msg.textContent = '저장됨';
    } catch (e) {
        msg.textContent = `${e.code ?? ''} ${e.message}`;
        await load();
    }
}

/** 파일 입력을 그때그때 만든다 — 행마다 <input type="file"> 을 깔아 두지 않는다. */
function pickImage() {
    return new Promise((resolve) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/jpeg,image/png';
        input.onchange = () => resolve(input.files[0] ?? null);
        input.click();
    });
}

async function replaceImage(banner, msg) {
    const file = await pickImage();
    if (!file) return;
    msg.textContent = '업로드 중…';
    try {
        const imageKey = await upload(file, 'BANNER_IMAGE');
        await patch(banner.shopBannerSeq, {imageKey}, msg);
        render();
    } catch (e) {
        msg.textContent = e.message;
    }
}

async function remove(banner, msg) {
    if (!confirm(`#${banner.shopBannerSeq} 배너를 삭제한다. 되돌릴 수 없다.`)) return;
    msg.textContent = '삭제 중…';
    try {
        await api(`/shop-banners/${banner.shopBannerSeq}`, {method: 'DELETE'});
        await load();
    } catch (e) {
        msg.textContent = `${e.code ?? ''} ${e.message}`;
    }
}

async function create() {
    const file = $('new-image').files[0];
    if (!file) return;
    clearError($('err'));
    $('create').disabled = true;
    try {
        // displayOrder 를 싣지 않는다 — 서버가 MAX+1 로 맨 뒤에 붙인다(§3.11.2).
        // 화면이 계산해 보내면 그 사이 다른 운영자가 등록했을 때 409 가 난다
        await api('/shop-banners', {
            method: 'POST',
            body: {imageKey: await upload(file, 'BANNER_IMAGE')},
        });
        $('new-image').value = '';
        await load();
    } catch (e) {
        showError($('err'), e);
    } finally {
        $('create').disabled = !$('new-image').files[0];
    }
}

$('new-image').addEventListener('change', () => {
    $('create').disabled = !$('new-image').files[0];
});
$('create').addEventListener('click', create);
$('reload').addEventListener('click', load);
$('save-order').addEventListener('click', saveOrder);
load();
