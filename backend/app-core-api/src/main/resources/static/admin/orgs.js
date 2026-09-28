/*
 * 소속사 · 그룹 관리 (어드민 명세 v0.5 §3.3 · §3.4) — S15P21D104-387.
 *
 * 두 탭이 하는 일이 같다 — 목록을 행으로 펴고, 행 안에서 고치고, 저장·삭제 결과를
 * 그 행에 적는다. 삭제는 소프트 삭제라 참조가 남아 있으면 409 로 돌아오는데(COMPANY_IN_USE ·
 * GROUP_IN_USE) 그 data(참조 건수)를 행에 그대로 보여 줘야 운영자가 무엇을 옮길지 안다.
 */

/** 소속사 목록 캐시 — 그룹 행마다 <select> 를 채워야 해서 한 번만 받아 돌려 쓴다. */
let companies = [];

/** 행 안에 결과를 적는다 — 전역 오류 상자로 올리면 어느 행인지 사라진다(prices.js 와 같은 규칙). */
function msg(scope, text, kind = 'muted') {
    const el = scope.querySelector('.msg');
    el.textContent = text;
    el.className = `msg ${kind}`;
}

function day(iso) {
    return iso ? iso.slice(0, 10) : '';
}

/** 저장·삭제 공통 — 실패는 코드째 그 행에 남긴다. 성공하면 목록을 다시 읽어 참조 수를 맞춘다. */
async function rowAction(row, run, done) {
    try {
        await run();
        msg(row, done, 'ok');
        return true;
    } catch (err) {
        msg(row, `[${err.code}] ${err.message}${err.data ? ' ' + JSON.stringify(err.data) : ''}`, 'err');
        return false;
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

// ── 소속사 (§3.3) ──────────────────────────────────────────────────────────

function companyRow(company) {
    const tr = $('company-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = company.companySeq;
    tr.querySelector('.seq').textContent = company.companySeq;
    tr.querySelector('.name').value = company.companyName;
    tr.querySelector('.group-count').textContent = company.groupCount;
    tr.querySelector('.artist-count').textContent = company.artistCount;
    tr.querySelector('.reg').textContent = day(company.regDttm);
    // 삭제분은 includeDeleted 로만 보인다 — 다시 고칠 수 없으니 입력을 잠근다
    if (company.isDeleted) {
        for (const el of tr.querySelectorAll('input, button')) el.disabled = true;
        msg(tr, '삭제됨');
    }
    return tr;
}

async function loadCompanies() {
    clearError($('company-err'));
    const params = new URLSearchParams({size: '100'});
    if ($('company-q').value.trim()) params.set('q', $('company-q').value.trim());
    if ($('company-deleted').checked) params.set('includeDeleted', 'true');
    try {
        const {list} = await api(`/companies?${params}`);
        $('companies').replaceChildren(...list.map(companyRow));
    } catch (e) {
        showError($('company-err'), e);
    }
}

/** 그룹 탭의 <select> 두 개가 소속사 목록을 쓴다 — 소속사가 바뀌면 같이 다시 채운다. */
async function reloadCompanyOptions() {
    const {list} = await api('/companies?size=100');
    companies = list;
    for (const select of [$('group-company'), $('group-new-company')]) {
        const keep = select.value;
        select.replaceChildren(select.firstElementChild.cloneNode(true));   // "전체"/"소속사 선택"
        select.append(...list.map((c) => new Option(c.companyName, c.companySeq)));
        select.value = keep;
    }
}

$('company-reload').addEventListener('click', loadCompanies);
$('company-q').addEventListener('search', loadCompanies);
$('company-deleted').addEventListener('change', loadCompanies);

$('company-create').addEventListener('click', async () => {
    clearError($('company-err'));
    const companyName = $('company-new-name').value.trim();
    if (!companyName) return;
    try {
        await api('/companies', {method: 'POST', body: {companyName}});
        $('company-new-name').value = '';
        await Promise.all([loadCompanies(), reloadCompanyOptions()]);
    } catch (e) {
        showError($('company-err'), e);       // COMPANY_NAME_DUPLICATE 가 여기로 온다
    }
});

$('companies').addEventListener('click', async (e) => {
    const tr = e.target.closest('tr');
    if (!tr) return;
    const path = `/companies/${tr.dataset.seq}`;

    if (e.target.classList.contains('save')) {
        const ok = await rowAction(tr,
            () => api(path, {method: 'PATCH', body: {companyName: tr.querySelector('.name').value.trim()}}),
            '저장됨');
        if (ok) await reloadCompanyOptions();
    }
    if (e.target.classList.contains('delete')) {
        const ok = await rowAction(tr, () => api(path, {method: 'DELETE'}), '삭제됨');
        if (ok) await Promise.all([loadCompanies(), reloadCompanyOptions()]);
    }
});

// ── 그룹 (§3.4) ────────────────────────────────────────────────────────────

function groupRow(group) {
    const tr = $('group-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = group.groupSeq;
    tr.querySelector('.seq').textContent = group.groupSeq;
    tr.querySelector('.name').value = group.groupName;
    tr.querySelector('.member-count').textContent = group.memberCount;
    tr.querySelector('.reg').textContent = day(group.regDttm);

    const select = tr.querySelector('.company');
    select.append(...companies.map((c) => new Option(c.companyName, c.companySeq)));
    // 삭제된 소속사는 목록에 없다 — 그 값을 잃지 않도록 현재 소속사를 보강한다
    if (!companies.some((c) => c.companySeq === group.company.companySeq)) {
        select.append(new Option(group.company.companyName, group.company.companySeq));
    }
    select.value = group.company.companySeq;

    if (group.isDeleted) {
        for (const el of tr.querySelectorAll('input, button, select')) el.disabled = true;
        msg(tr, '삭제됨');
    } else if (group.memberCount > 0) {
        msg(tr, `멤버 ${group.memberCount}명 — 소속사를 바꾸면 함께 옮겨진다`);
    }
    return tr;
}

async function loadGroups() {
    clearError($('group-err'));
    const params = new URLSearchParams({size: '100'});
    if ($('group-q').value.trim()) params.set('q', $('group-q').value.trim());
    if ($('group-company').value) params.set('companySeq', $('group-company').value);
    if ($('group-deleted').checked) params.set('includeDeleted', 'true');
    try {
        const {list} = await api(`/groups?${params}`);
        $('groups').replaceChildren(...list.map(groupRow));
    } catch (e) {
        showError($('group-err'), e);
    }
}

$('group-reload').addEventListener('click', loadGroups);
$('group-q').addEventListener('search', loadGroups);
$('group-company').addEventListener('change', loadGroups);
$('group-deleted').addEventListener('change', loadGroups);

$('group-create').addEventListener('click', async () => {
    clearError($('group-err'));
    const companySeq = $('group-new-company').value;
    const groupName = $('group-new-name').value.trim();
    if (!companySeq || !groupName) {
        showError($('group-err'), {message: '소속사와 그룹명을 모두 입력해야 한다'});
        return;
    }
    try {
        await api('/groups', {method: 'POST', body: {companySeq: Number(companySeq), groupName}});
        $('group-new-name').value = '';
        await loadGroups();
    } catch (e) {
        showError($('group-err'), e);
    }
});

$('groups').addEventListener('click', async (e) => {
    const tr = e.target.closest('tr');
    if (!tr) return;
    const path = `/groups/${tr.dataset.seq}`;

    if (e.target.classList.contains('save')) {
        const ok = await rowAction(tr, () => api(path, {
            method: 'PATCH',
            body: {
                groupName: tr.querySelector('.name').value.trim(),
                companySeq: Number(tr.querySelector('.company').value),
            },
        }), '저장됨');
        if (ok) await loadGroups();     // 멤버가 함께 옮겨졌을 수 있다 — 수를 다시 읽는다
    }
    // 별칭(§3.4.5~8)은 그룹 수정과 트랜잭션이 다르다 — 패널이 행마다 따로 저장한다
    if (e.target.classList.contains('alias')) {
        const name = tr.querySelector('.name').value;
        await openAliases(path, `그룹 #${tr.dataset.seq} ${name}`);
        $('alias-panel').scrollIntoView();
    }
    if (e.target.classList.contains('delete')) {
        const ok = await rowAction(tr, () => api(path, {method: 'DELETE'}), '삭제됨');
        if (ok) {
            // 방금 지운 그룹을 패널이 보고 있으면 닫는다 — 삭제분은 별칭 API 가 404 다
            if (aliasBase === path) closeAliases();
            await loadGroups();
        }
    }
});

// ── 초기화 ─────────────────────────────────────────────────────────────────

(async () => {
    try {
        await reloadCompanyOptions();
    } catch (e) {
        showError($('company-err'), e);
    }
    // 소속사 목록이 먼저 있어야 그룹 행의 <select> 를 채울 수 있다
    await loadCompanies();
    await loadGroups();
})();
