/*
 * 아티스트 관리 (어드민 명세 v0.5 §3.5) — S15P21D104-388.
 *
 * 페이지 넘김은 두지 않았다 — size=100 한 장이면 당분간 전량이다(공지 목록과 같은 판단).
 * 넘치면 그때 page 파라미터를 붙인다(§1.5 offset).
 *
 * 소속사·그룹 목록(§3.3.1·§3.4.1)은 별도 티켓이라 **아직 없을 수 있다**. 드롭다운이
 * 비어도 목록·등록은 그대로 돌아가야 하므로 채우기 실패를 삼키고 안내만 남긴다 —
 * 참조 목록 하나 때문에 화면 전체가 멎으면 운영이 막힌다.
 */

const set = (scope, selector, text) => {
    scope.querySelector(selector).textContent = text;
};

/** §3.4.1 항목들 — 그룹을 고르면 여기서 소속사를 찾아 채우고 잠근다(§3.5.2). */
let groups = [];

/** 수정 중인 아티스트. null 이면 등록 폼이다. */
let editing = null;

function artistRow(artist) {
    const tr = $('artist-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = artist.artistSeq;
    tr.classList.toggle('deleted', artist.isDeleted);
    set(tr, '.seq', artist.artistSeq);
    set(tr, '.name', artist.name);
    set(tr, '.group', artist.group?.groupName ?? '솔로');
    set(tr, '.company', artist.company?.companyName ?? '');
    set(tr, '.subscribers', artist.subscriberCount);
    set(tr, '.challenges', artist.challengeCount);
    set(tr, '.assets', artist.assetCount);
    set(tr, '.notices', artist.noticeCount);
    set(tr, '.reg', new Date(artist.regDttm).toLocaleString('ko-KR'));
    if (artist.isDeleted) {
        tr.querySelector('.actions').textContent = '삭제됨';   // 수정 대상이 아니다
    }
    return tr;
}

async function load() {
    clearError($('list-err'));
    const params = new URLSearchParams({size: '100'});
    if ($('q').value.trim()) params.set('q', $('q').value.trim());
    if ($('f-group').value) params.set('groupSeq', $('f-group').value);
    if ($('f-company').value) params.set('companySeq', $('f-company').value);
    if ($('include-deleted').checked) params.set('includeDeleted', 'true');
    try {
        const {list, totalElements} = await api(`/artists?${params}`);
        $('artists').replaceChildren(...list.map(artistRow));
        $('count').textContent = `${list.length}건 표시 / 전체 ${totalElements ?? list.length}건`;
    } catch (e) {
        showError($('list-err'), e);
    }
}

/**
 * 그룹 선택 → 소속사 자동 채움·잠금 (§3.5.2).
 *
 * 서버가 "그룹 소속사 ≠ companySeq" 를 400 으로 막으므로 화면이 애초에 어긋난 짝을
 * 못 만들게 한다. 그룹 목록을 못 받았으면(§3.4.1 미구현) 잠글 근거가 없으니 소속사를
 * 그대로 열어 둔다.
 */
function syncCompanyLock() {
    const group = groups.find((g) => String(g.groupSeq) === $('group').value);
    if (group) {
        $('company').value = group.company?.companySeq ?? '';
    }
    $('company').disabled = Boolean(group);
    $('company-lock').hidden = !group;
}

/**
 * 현재 소속을 <select> 에 앉힌다 — 옵션에 없으면 그 항목을 끼워 넣는다.
 *
 * 그룹·소속사 목록(§3.4.1·§3.3.1)을 못 받았을 때가 그렇다. 그냥 대입하면 <select> 가
 * 값을 삼켜 빈 문자열이 되고, 저장이 {@code groupSeq: null} 로 나가 <b>솔로로 바뀐다</b>
 * (§3.5.4). 참조 목록 하나가 죽었다고 아티스트 소속을 날릴 수는 없다.
 */
function selectOrAdd(el, seq, label) {
    el.value = seq ?? '';
    if (seq != null && el.value !== String(seq)) {
        el.append(new Option(label, seq));
        el.value = seq;
    }
}

function toCreateForm() {
    editing = null;
    $('form-title').textContent = '아티스트 등록';
    $('save').textContent = '등록';
    $('reset').hidden = true;
    $('name').value = '';
    $('bio').value = '';
    $('group').value = '';
    $('company').value = '';
    $('profile').value = '';
    $('motif-bg').value = '';
    syncCompanyLock();
    clearError($('form-err'));
    $('msg').textContent = '';
}

async function toEditForm(artistSeq) {
    clearError($('form-err'));
    try {
        const {summary, bio} = await api(`/artists/${artistSeq}`);
        editing = artistSeq;
        $('form-title').textContent = `아티스트 수정 — #${artistSeq} ${summary.name}`;
        $('save').textContent = '수정';
        $('reset').hidden = false;
        $('name').value = summary.name;
        $('bio').value = bio ?? '';
        selectOrAdd($('group'), summary.group?.groupSeq, summary.group?.groupName);
        selectOrAdd($('company'), summary.company?.companySeq, summary.company?.companyName);
        syncCompanyLock();
        $('profile').value = '';
        $('motif-bg').value = '';
        $('msg').textContent = '';
        $('form-title').scrollIntoView();
    } catch (e) {
        showError($('form-err'), e);
    }
}

async function save() {
    clearError($('form-err'));
    $('msg').textContent = '';
    const name = $('name').value.trim();
    if (!name) {
        showError($('form-err'), new Error('이름을 입력하세요 (1~50자)'));
        return;
    }
    $('save').disabled = true;
    step('upload', null);
    step('submit', null);
    try {
        // §3.2 presign → S3 PUT → 키 제출. 고른 파일이 없으면 키를 아예 보내지 않는다
        // (수정에서 null/생략은 "이미지 그대로" 다).
        step('upload', 'doing');
        const profileImageKey = $('profile').files[0]
            && await upload($('profile').files[0], 'ARTIST_PROFILE');
        const motifBgImageKey = $('motif-bg').files[0]
            && await upload($('motif-bg').files[0], 'ARTIST_MOTIF_BG');
        step('upload', 'done');

        step('submit', 'doing');
        // groupSeq 는 항상 실어 보낸다. 생략하면 "소속사 단독 변경" 으로 읽혀
        // 그룹 소속 아티스트에서 409 ARTIST_COMPANY_LOCKED 가 난다(§3.5.4).
        const body = {
            name,
            bio: $('bio').value,
            groupSeq: $('group').value ? Number($('group').value) : null,
            companySeq: $('company').value ? Number($('company').value) : null,
        };
        if (profileImageKey) body.profileImageKey = profileImageKey;
        if (motifBgImageKey) body.motifBgImageKey = motifBgImageKey;

        const creating = editing === null;
        const done = creating
            ? await api('/artists', {method: 'POST', body})
            : await api(`/artists/${editing}`, {method: 'PATCH', body});
        step('submit', 'done');
        // 폼을 먼저 비우고 메시지를 쓴다 — toCreateForm 이 msg 도 지운다
        if (creating) toCreateForm();
        $('msg').textContent = creating
            ? `등록 완료 — artistSeq ${done.artistSeq} · userSeq ${done.userSeq}`
            : '수정 완료';
        await load();
    } catch (e) {
        // 업로드에서 터졌는지 저장에서 터졌는지 — 진행 중이던 단계를 그대로 실패로 남긴다
        document.querySelectorAll('.progress li.doing').forEach((li) => {
            li.className = 'fail';
        });
        showError($('form-err'), e);
    } finally {
        $('save').disabled = false;
    }
}

$('reload').addEventListener('click', load);
$('q').addEventListener('search', load);
$('f-group').addEventListener('change', load);
$('f-company').addEventListener('change', load);
$('include-deleted').addEventListener('change', load);
$('group').addEventListener('change', syncCompanyLock);
$('save').addEventListener('click', save);
$('reset').addEventListener('click', toCreateForm);

$('artists').addEventListener('click', (e) => {
    if (e.target.classList.contains('edit')) toEditForm(e.target.closest('tr').dataset.seq);
});

(async () => {
    // 참조 목록 2종(§3.3.1·§3.4.1). 실패해도 목록·등록은 이어 간다 — 그룹 없이
    // 솔로 아티스트를 등록하는 길이 남아 있어야 한다.
    try {
        groups = await fillSelect($('group'), '/groups?size=100', 'groupSeq',
            (g) => (g.company ? `${g.groupName} (${g.company.companyName})` : g.groupName));
        $('f-group').append(...groups.map((g) => new Option(g.groupName, g.groupSeq)));
    } catch (e) {
        showError($('form-err'), e);
        $('form-err').textContent += ' — 그룹 목록(§3.4.1)을 못 받았습니다. 솔로 등록만 가능합니다';
    }
    try {
        const companies = await fillSelect($('company'), '/companies?size=100', 'companySeq',
            (c) => c.companyName);
        $('f-company').append(...companies.map((c) => new Option(c.companyName, c.companySeq)));
    } catch (e) {
        showError($('list-err'), e);
        $('list-err').textContent += ' — 소속사 목록(§3.3.1)을 못 받았습니다';
    }
    syncCompanyLock();
    await load();
})();
