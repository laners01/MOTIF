/*
 * 별칭 패널 (어드민 명세 §3.4.5~8 · §3.5.6~9) — S15P21D104-358.
 *
 * 아티스트와 그룹이 같은 마크업·같은 로직을 쓴다. 계약이 경로 말고는 완전히 같아서
 * (등록·수정 본문 {languageCode, alias}, 목록 항목, 하드 삭제) 두 화면에 복붙하면
 * 한쪽만 고치는 일이 생긴다. 호출부는 대상 경로만 넘긴다:
 *
 *   openAliases('/artists/3', '아티스트 #3 카리나')
 *   openAliases('/groups/2',  '그룹 #2 aespa')
 *
 * 두 화면이 같은 요소 id 를 갖는다(#alias-panel · #aliases · #alias-row …) — 화면이
 * 둘뿐이고 한 화면에 패널이 하나라, id 대신 인자로 요소를 넘기는 구조는 과하다.
 *
 * 저장은 별칭 행마다 따로다. 아티스트 폼의 "수정" 버튼과 얽히지 않는다 — 별칭은
 * artists 테이블이 아니라 artist_aliases 에 있고, 아티스트 수정(§3.5.4)과 트랜잭션이
 * 다르다. 폼 저장에 묶으면 이름만 고치려 해도 별칭 전부가 함께 나간다.
 */

/** 지금 패널이 보고 있는 대상 경로. null 이면 패널이 닫혀 있다. */
let aliasBase = null;

function aliasRow(alias) {
    const tr = $('alias-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = alias.aliasSeq;
    tr.querySelector('.seq').textContent = alias.aliasSeq;
    tr.querySelector('.lang').value = alias.languageCode;
    tr.querySelector('.alias').value = alias.alias;
    tr.querySelector('.reg').textContent = new Date(alias.regDttm).toLocaleString('ko-KR');
    return tr;
}

async function loadAliases() {
    clearError($('alias-err'));
    try {
        const {list} = await api(`${aliasBase}/aliases`);
        $('aliases').replaceChildren(...list.map(aliasRow));
        $('alias-count').textContent = `${list.length}건`;
    } catch (e) {
        showError($('alias-err'), e);
    }
}

/**
 * 패널을 대상에 붙이고 연다.
 *
 * @param base  '/artists/{seq}' 또는 '/groups/{seq}'
 * @param label 헤더에 띄울 사람이 읽는 이름
 */
async function openAliases(base, label) {
    aliasBase = base;
    $('alias-panel').hidden = false;
    $('alias-target').textContent = label;
    $('alias-lang').value = '';
    $('alias-text').value = '';
    $('alias-msg').textContent = '';
    await loadAliases();
}

function closeAliases() {
    aliasBase = null;
    $('alias-panel').hidden = true;
    $('aliases').replaceChildren();
    $('alias-count').textContent = '';
    clearError($('alias-err'));
}

async function addAlias() {
    clearError($('alias-err'));
    $('alias-msg').textContent = '';
    const languageCode = $('alias-lang').value.trim().toLowerCase();
    const alias = $('alias-text').value.trim();
    if (!/^[a-z]{2}$/.test(languageCode) || !alias) {
        showError($('alias-err'),
            new Error('언어 코드는 소문자 2자(ko·en·ja), 별칭은 1~100자입니다'));
        return;
    }
    $('alias-add').disabled = true;
    try {
        // 중복은 막지 않는다(DB 에 UNIQUE 가 없다) — 화면에서도 미리 거르지 않는다.
        // 같은 언어에 표기 변형이 여럿인 것이 정상이다(aespa·æspa)
        await api(`${aliasBase}/aliases`, {method: 'POST', body: {languageCode, alias}});
        $('alias-lang').value = '';
        $('alias-text').value = '';
        await loadAliases();
        $('alias-msg').textContent = '추가됨';
    } catch (e) {
        showError($('alias-err'), e);
    } finally {
        $('alias-add').disabled = false;
    }
}

async function saveAlias(tr) {
    const cell = tr.querySelector('.msg');
    cell.textContent = '';
    clearError($('alias-err'));
    const languageCode = tr.querySelector('.lang').value.trim().toLowerCase();
    const alias = tr.querySelector('.alias').value.trim();
    try {
        // PUT 이다 — 부분 수정이 아니라 전체 치환이라 두 칸을 항상 함께 보낸다
        await api(`${aliasBase}/aliases/${tr.dataset.seq}`,
            {method: 'PUT', body: {languageCode, alias}});
        cell.textContent = '저장됨';
    } catch (e) {
        showError($('alias-err'), e);
    }
}

async function removeAlias(tr) {
    const alias = tr.querySelector('.alias').value;
    // 하드 삭제라 되돌릴 수 없다 — 아티스트·그룹 삭제(소프트)와 다르다
    if (!confirm(`별칭 "${alias}" 을(를) 삭제합니다. 되돌릴 수 없습니다.`)) return;

    clearError($('alias-err'));
    try {
        await api(`${aliasBase}/aliases/${tr.dataset.seq}`, {method: 'DELETE'});
        await loadAliases();
        $('alias-msg').textContent = '삭제됨';
    } catch (e) {
        showError($('alias-err'), e);
    }
}

$('alias-add').addEventListener('click', addAlias);
$('aliases').addEventListener('click', (e) => {
    const tr = e.target.closest('tr');
    if (!tr) return;
    if (e.target.classList.contains('save')) saveAlias(tr);
    if (e.target.classList.contains('delete')) removeAlias(tr);
});
