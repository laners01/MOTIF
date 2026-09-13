/*
 * 가격 관리 (어드민 명세 v0.5 §3.8.6 · §3.9) — S15P21D104-570.
 *
 * 에셋 가격과 패스 가격을 한 화면 두 탭으로 둔다. 둘 다 upsert 한 방이라 화면이
 * 하는 일은 같다 — 칸을 서버 값으로 채우고, 저장하고, 실패를 그 자리에 적는다.
 */

const COUNTRIES = ['KR'];       // §3.8.6 "현재 KR" — 국가 목록 API 가 없어 상수로 둔다
const MONTHS = [1, 3, 6, 12];   // 기본 열. 서버에 다른 개월이 있으면 합쳐 보여준다

/** 행·칸 안에 결과를 적는다 — 전역 오류 상자로 올리면 어느 가격인지 사라진다. */
function msg(scope, text, kind = 'muted') {
    const el = scope.querySelector('.msg');
    el.textContent = text;
    el.className = `msg ${kind}`;
}

/**
 * 가격 upsert 공통(§3.8.6 · §3.9.2) — 성공하면 서버가 돌려준 값으로 칸을 되맞춘다.
 * 실패(PRICE_ACTIVATION_BLOCKED 등)는 그 칸에 코드째 남긴다.
 */
async function savePrice(scope, path, note) {
    try {
        const price = await api(path, {
            method: 'PUT',
            body: {
                amount: scope.querySelector('.amount').value.trim(),
                currency: 'KRW',
                isActive: scope.querySelector('.price-active').checked,
            },
        });
        scope.querySelector('.amount').value = price.amount;
        scope.querySelector('.price-active').checked = price.isActive;
        msg(scope, note(price), 'ok');
    } catch (err) {
        msg(scope, `[${err.code}] ${err.message}`, 'err');
    }
}

// ── 탭 ─────────────────────────────────────────────────────────────────────

for (const tab of document.querySelectorAll('[data-tab]')) {
    tab.addEventListener('click', () => {
        for (const other of document.querySelectorAll('[data-tab]')) {
            other.classList.toggle('active', other === tab);
        }
        for (const panel of document.querySelectorAll('[data-panel]')) {
            panel.hidden = panel.dataset.panel !== tab.dataset.tab;
        }
    });
}

// ── 에셋 가격 (§3.8.1 목록 · §3.8.5 활성 · §3.8.6 가격) ────────────────────

function assetRow(asset) {
    const tr = $('asset-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = asset.assetSeq;
    tr.querySelector('.title').textContent = asset.title;
    tr.querySelector('.artist').textContent = asset.artist?.name ?? '';
    tr.querySelector('.type').textContent = asset.assetType;
    tr.querySelector('.asset-active').checked = asset.isActive;
    tr.querySelector('.amount').value = asset.price?.amount ?? '';
    tr.querySelector('.price-active').checked = asset.price?.isActive ?? false;
    msg(tr, asset.price ? '' : 'KR 가격 미설정');
    return tr;
}

async function loadAssets() {
    clearError($('asset-err'));
    const params = new URLSearchParams({size: '100'});
    if ($('asset-artist').value) params.set('artistSeq', $('asset-artist').value);
    if ($('asset-state').value) params.set('isActive', $('asset-state').value);
    try {
        const {list} = await api(`/motif-assets?${params}`);
        $('assets').replaceChildren(...list.map(assetRow));
    } catch (e) {
        showError($('asset-err'), e);
    }
}

$('asset-reload').addEventListener('click', loadAssets);
$('asset-artist').addEventListener('change', loadAssets);
$('asset-state').addEventListener('change', loadAssets);

$('assets').addEventListener('change', async (e) => {
    if (!e.target.classList.contains('asset-active')) return;
    const tr = e.target.closest('tr');
    try {
        const result = await api(`/motif-assets/${tr.dataset.seq}/active`,
            {method: 'PATCH', body: {isActive: e.target.checked}});
        const state = result.isActive ? '활성' : '비활성';
        // 3.8.5 — 다시 켜도 가격은 자동 재활성되지 않는다. 그대로 두면 판매되지 않는다
        msg(tr, result.inactivePriceCount
            ? `에셋 ${state} — 비활성 가격 ${result.inactivePriceCount}건, 가격도 켜야 판매된다`
            : `에셋 ${state}`, result.inactivePriceCount ? 'err' : 'ok');
    } catch (err) {
        e.target.checked = !e.target.checked;   // 서버가 거부했으면 체크도 되돌린다
        msg(tr, `[${err.code}] ${err.message}`, 'err');
    }
});

$('assets').addEventListener('click', (e) => {
    if (!e.target.classList.contains('save')) return;
    const tr = e.target.closest('tr');
    savePrice(tr, `/motif-assets/${tr.dataset.seq}/prices/KR`,
        (price) => `저장됨 — ${price.amount} ${price.currency}`);
});

// ── 패스 가격 (§3.9) ───────────────────────────────────────────────────────

function th(text) {
    const cell = document.createElement('th');
    cell.textContent = text;
    return cell;
}

function passCell(artistSeq, countryCode, durationMonths, price) {
    const td = $('pass-cell').content.firstElementChild.cloneNode(true);
    td.dataset.path = `/artists/${artistSeq}/pass-prices/${countryCode}/${durationMonths}`;
    td.querySelector('.amount').value = price?.amount ?? '';
    td.querySelector('.price-active').checked = price?.isActive ?? false;
    msg(td, price ? `구독 ${price.activePassCount}건` : '미설정');
    return td;
}

async function loadPass() {
    clearError($('pass-err'));
    $('pass-head').replaceChildren();
    $('pass-grid').replaceChildren();
    const artistSeq = $('pass-artist').value;
    if (!artistSeq) return;
    try {
        const {list} = await api(`/artists/${artistSeq}/pass-prices`);
        // 서버에 이미 있는 국가·개월은 기본 목록에 없어도 칸을 만들어야 편집할 수 있다
        const months = [...new Set([...MONTHS, ...list.map((p) => p.durationMonths)])].sort((a, b) => a - b);
        const countries = [...new Set([...COUNTRIES, ...list.map((p) => p.countryCode)])];
        const found = new Map(list.map((p) => [`${p.countryCode}/${p.durationMonths}`, p]));

        const head = document.createElement('tr');
        head.append(th('국가'), ...months.map((m) => th(`${m}개월`)));
        $('pass-head').append(head);

        $('pass-grid').replaceChildren(...countries.map((code) => {
            const tr = document.createElement('tr');
            tr.append(th(code), ...months.map((m) => passCell(artistSeq, code, m, found.get(`${code}/${m}`))));
            return tr;
        }));
    } catch (e) {
        showError($('pass-err'), e);
    }
}

$('pass-artist').addEventListener('change', loadPass);

$('pass-grid').addEventListener('click', (e) => {
    if (!e.target.classList.contains('save')) return;
    const td = e.target.closest('td');
    savePrice(td, td.dataset.path, (price) => `저장됨 · 구독 ${price.activePassCount}건`);
});

// ── 초기화 ─────────────────────────────────────────────────────────────────

(async () => {
    const label = (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name);
    try {
        const artists = await fillSelect($('asset-artist'), '/artists?size=100', 'artistSeq', label);
        $('pass-artist').append(...artists.map((a) => new Option(label(a), a.artistSeq)));
    } catch (e) {
        showError($('asset-err'), e);
    }
    await loadAssets();
})();
