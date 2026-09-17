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
