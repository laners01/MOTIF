/*
 * 활동 대행 컨텍스트 선택 (어드민 명세 ADMIN-11 · §1.7).
 *
 * 컨텍스트 자체는 admin.js 가 들고 있다(actingArtist·setActingArtist) — 헤더 표시가
 * 모든 화면에 걸리므로 저장·렌더를 한 곳에 둔다. 이 화면은 고르는 일만 한다.
 */

const SIZE = 20;

let page = 0;
let total = 0;

async function reload() {
    clearError($('err'));
    const query = new URLSearchParams({page, size: SIZE});
    const q = $('q').value.trim();
    if (q) query.set('q', q);

    try {
        // includeDeleted 를 넘기지 않는다 — 기본이 비삭제고, 삭제 아티스트는 대행 대상이
        // 아니다(ActAsArtistResolver 가 404). 목록에 띄우면 눌러 보고 나서야 알게 된다
        const data = await api(`/artists?${query}`);
        total = data.totalElements ?? 0;
        render(data.list);
        $('page-hint').textContent = data.list.length
                ? `${total}명 중 ${page * SIZE + 1}~${page * SIZE + data.list.length}`
                : '결과 없음';
        $('prev').disabled = page === 0;
        $('next').disabled = (page + 1) * SIZE >= total;
    } catch (e) {
        showError($('err'), e);
    }
}

function render(artists) {
    const tbody = $('artists');
    tbody.replaceChildren();
    const acting = actingArtist();

    for (const artist of artists) {
        const row = $('artist-row').content.cloneNode(true);
        row.querySelector('.seq').textContent = artist.artistSeq;
        row.querySelector('.name').textContent = artist.name;
        row.querySelector('.group').textContent = artist.group?.groupName ?? '솔로';
        row.querySelector('.company').textContent = artist.company?.companyName ?? '';
        row.querySelector('.subs').textContent = artist.subscriberCount;

        const pick = row.querySelector('.pick');
        if (acting?.artistSeq === artist.artistSeq) {
            pick.textContent = '활동 중';
            pick.disabled = true;
        }
        pick.addEventListener('click', () => {
            setActingArtist({artistSeq: artist.artistSeq, name: artist.name});
            showCurrent();
            render(artists);            // 다른 행의 "활동 중" 표시를 옮긴다
        });
        tbody.append(row);
    }
}

function showCurrent() {
    const artist = actingArtist();
    $('current').textContent = artist
            ? `${artist.name}(#${artist.artistSeq})로 활동 중입니다. 좋아요·댓글이 이 아티스트 이름으로 남습니다.`
            : '활동 중인 아티스트가 없습니다.';
    $('clear').hidden = !artist;
}

$('reload').addEventListener('click', () => {
    page = 0;
    reload();
});
$('q').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        page = 0;
        reload();
    }
});
$('prev').addEventListener('click', () => {
    page -= 1;
    reload();
});
$('next').addEventListener('click', () => {
    page += 1;
    reload();
});
$('clear').addEventListener('click', () => {
    setActingArtist(null);
    showCurrent();
    reload();
});

showCurrent();
reload();
