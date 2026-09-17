/*
 * 참여 영상 탐색 (어드민 명세 ADMIN-11 · §3.12.1).
 *
 * 목록은 어드민 탐색 API 로, 댓글 스레드는 대행 API 로 부른다 — 스레드 쪽은
 * act-as-thread.js 가 공지 화면과 함께 쓴다.
 */

const SIZE = 20;

let page = 0;
let total = 0;

async function reload() {
    clearError($('err'));
    // PUBLIC 만 넘긴다 — PRIVATE 는 대행 대상이 아니다(ADMIN-11 제약). 목록에 띄우면
    // 댓글을 달려다 404 를 보고 나서야 알게 된다
    const query = new URLSearchParams({visibility: 'PUBLIC', page, size: SIZE});
    for (const [id, key] of [['challenge-seq', 'challengeSeq'], ['user-seq', 'userSeq']]) {
        const v = $(id).value.trim();
        if (v) query.set(key, v);
    }

    try {
        const data = await api(`/videos?${query}`);
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

function render(videos) {
    const tbody = $('videos');
    tbody.replaceChildren();

    for (const video of videos) {
        const row = $('video-row').content.cloneNode(true);
        row.querySelector('.seq').textContent = video.videoSeq;
        const img = row.querySelector('.thumb');
        if (video.thumbnailUrl) img.src = video.thumbnailUrl;
        else img.replaceWith(Object.assign(document.createElement('span'),
                {className: 'thumb-none', textContent: '없음'}));
        row.querySelector('.desc').textContent = video.description;
        row.querySelector('.author').textContent = video.author?.nickname ?? '';
        row.querySelector('.comments').textContent = video.commentCount;
        row.querySelector('.badges').textContent = [
            video.hasArtistLike && '♥',
            video.hasArtistComment && '댓글',
        ].filter(Boolean).join(' ');

        const like = row.querySelector('.like');
        renderVideoLike(like, video);
        like.addEventListener('click', () => toggleVideoLike(like, video));

        const open = row.querySelector('.open');
        const tr = row.querySelector('tr');
        if (!video.commentEnabled) {
            open.disabled = true;
            open.title = '댓글이 잠긴 영상이다 — 새 댓글은 403 이다(§9.3)';
        }
        open.addEventListener('click', () => toggleThread(tr, {type: 'video', seq: video.videoSeq}));
        tbody.append(row);
    }
}

/**
 * 영상 좋아요 대행 (§8.3 · §3.12).
 *
 * <p>목록이 돌려주는 hasArtistLike 는 <b>활동 중 아티스트가 눌렀는가</b>와 같은 값이다 —
 * 영상에 걸린 아티스트가 하나뿐이라 그렇다. 그래서 별도 isLiked 조회 없이 이것으로
 * 버튼 상태를 그린다.
 */
function renderVideoLike(button, video) {
    button.textContent = `${video.hasArtistLike ? '♥' : '♡'} ${video.likeCount}`;
}

async function toggleVideoLike(button, video) {
    const next = !video.hasArtistLike;
    button.disabled = true;
    try {
        await api(`${AS}/videos/${video.videoSeq}/like`, {method: next ? 'POST' : 'DELETE'});
        video.hasArtistLike = next;
        video.likeCount += next ? 1 : -1;
        renderVideoLike(button, video);
        // 배지도 같이 움직인다 — 목록의 "아티스트" 칸이 이 플래그다
        button.closest('tr').querySelector('.badges').textContent = [
            video.hasArtistLike && '♥',
            video.hasArtistComment && '댓글',
        ].filter(Boolean).join(' ');
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
