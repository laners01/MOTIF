/*
 * 공지 탐색 (어드민 명세 ADMIN-11 · §3.10.1).
 *
 * 댓글 스레드는 act-as-thread.js 가 참여 영상 화면과 함께 쓴다 — 대상만 notice 다.
 */

const SIZE = 20;

let page = 0;
let total = 0;

async function reload() {
    clearError($('err'));
    // includeDeleted 를 넘기지 않는다 — 삭제된 공지에는 댓글을 달 수 없다(§9.3 실패표).
    // 목록에 띄우면 눌러 보고 나서야 알게 된다
    const query = new URLSearchParams({page, size: SIZE});
    const artistSeq = $('artist-seq').value.trim();
    if (artistSeq) query.set('artistSeq', artistSeq);

    try {
        const data = await api(`/notices?${query}`);
        total = data.totalElements ?? 0;
        render(data.list);
        $('page-hint').textContent = data.list.length
                ? `${total}건 중 ${page * SIZE + 1}~${page * SIZE + data.list.length}`
                : '결과 없음';
        $('prev').disabled = page === 0;
        $('next').disabled = (page + 1) * SIZE >= total;
    } catch (e) {
        showError($('err'), e);
    }
}

function render(notices) {
    const tbody = $('notices');
    tbody.replaceChildren();

    for (const notice of notices) {
        const row = $('notice-row').content.cloneNode(true);
        row.querySelector('.seq').textContent = notice.noticeSeq;
        row.querySelector('.artist').textContent = notice.artist?.name ?? '';
        row.querySelector('.preview').textContent = notice.contentPreview;
        row.querySelector('.images').textContent = notice.imageCount;
        row.querySelector('.likes').textContent = notice.likeCount;
        row.querySelector('.comments').textContent = notice.commentCount;
        row.querySelector('.reg').textContent = new Date(notice.regDttm).toLocaleDateString('ko-KR');

        const like = row.querySelector('.like');
        renderNoticeLike(like, notice);
        like.addEventListener('click', () => toggleNoticeLike(like, notice));

        const tr = row.querySelector('tr');
        row.querySelector('.open').addEventListener('click', () =>
                toggleThread(tr, {type: 'notice', seq: notice.noticeSeq}));
        tbody.append(row);
    }
}

/*
 * 공지 좋아요 대행 (§10.2 · §3.12).
 *
 * 영상과 달리 "활동 중 아티스트가 눌렀는가" 를 목록이 알려 주지 않는다 — 공지에는
 * has_artist_like 같은 칸이 없고 §3.10.1 응답에 isLiked 도 없다. 그래서 버튼은 누른
 * 결과만 반영하고, 새로고침하면 다시 ♡ 로 보인다. 카운터는 서버 값이라 정확하다.
 *
 * ponytail: 초기 상태 미표시. 어드민 목록 응답에 isLiked 가 생기면 그때 그린다
 */
function renderNoticeLike(button, notice) {
    button.textContent = `${notice.liked ? '♥' : '♡'} ${notice.likeCount}`;
}

async function toggleNoticeLike(button, notice) {
    const next = !notice.liked;
    button.disabled = true;
    try {
        await api(`${AS}/notices/${notice.noticeSeq}/like`, {method: next ? 'POST' : 'DELETE'});
        notice.liked = next;
        notice.likeCount += next ? 1 : -1;
        renderNoticeLike(button, notice);
    } catch (e) {
        showError($('err'), e);
    } finally {
        button.disabled = false;
    }
}

if (requireActing()) {
    $('reload').addEventListener('click', () => {
        page = 0;
        reload();
    });
    // 자기 공지에 다는 것이 흔한 쓰임이라 한 번 누르면 되게 둔다
    $('mine').addEventListener('click', () => {
        $('artist-seq').value = acting.artistSeq;
        page = 0;
        reload();
    });
    $('prev').addEventListener('click', () => {
        page -= 1;
        reload();
    });
    $('next').addEventListener('click', () => {
        page += 1;
        reload();
    });
    reload();
}
