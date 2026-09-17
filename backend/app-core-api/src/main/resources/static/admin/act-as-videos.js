/*
 * 참여 영상 탐색 + 댓글 대행 (어드민 명세 ADMIN-11 · §3.12.1·§3.12.2).
 *
 * 목록은 어드민 탐색 API(/videos)로, 댓글은 대행 API(/as-artist/{artistSeq}/…)로 부른다.
 * 스레드를 대행 경로로 읽는 것이 중요하다 — isMine 과 정렬 1순위가 "누가 보느냐" 로
 * 갈리므로, 어드민 시점으로 읽으면 아티스트 본인 댓글이 위로 오지도 않고 삭제 버튼도
 * 붙지 않는다(§9.1).
 */

const SIZE = 20;

let page = 0;
let total = 0;

const acting = actingArtist();
const AS = acting ? `/as-artist/${acting.artistSeq}` : null;

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
        open.addEventListener('click', () => toggleThread(tr, video));
        tbody.append(row);
    }
}

/** 행 아래에 스레드 패널을 펼친다 — 별도 화면을 두면 목록으로 돌아오는 길이 필요하다. */
function toggleThread(tr, video) {
    const next = tr.nextElementSibling;
    if (next?.classList.contains('detail-panel')) {
        next.remove();
        return;
    }
    const panel = $('thread-row').content.cloneNode(true);
    const box = panel.querySelector('.thread');
    tr.after(panel);
    loadThread(box, video);
}

async function loadThread(box, video) {
    box.replaceChildren(text('불러오는 중…'));
    try {
        const data = await api(`${AS}/videos/${video.videoSeq}/comments?page=0&size=50`);
        box.replaceChildren();
        box.append(writeForm(video, null, box));
        if (!data.list.length) box.append(text('댓글이 없다.'));
        for (const comment of data.list) box.append(node(comment, video, box));
    } catch (e) {
        box.replaceChildren();
        const err = document.createElement('p');
        err.className = 'err';
        showError(err, e);
        box.append(err);
    }
}

/** 댓글·답글 한 칸. 답글은 children 에 같은 모양으로 붙는다(깊이 2단 — §9.3). */
function node(comment, video, box, isReply = false) {
    const el = $('comment-node').content.cloneNode(true);
    const root = el.querySelector('.comment');
    el.querySelector('.who').textContent =
            comment.author?.nickname + (comment.author?.isArtist ? ' (아티스트)' : '');
    el.querySelector('.body').textContent = comment.content;
    el.querySelector('.meta').textContent =
            ` · ♥${comment.likeCount} · ${new Date(comment.regDttm).toLocaleString('ko-KR')}`;

    const replies = el.querySelector('.replies');
    if (isReply || !comment.replyCount) replies.remove();
    else {
        replies.textContent = `답글 ${comment.replyCount}개 펼치기`;
        replies.addEventListener('click', () => loadReplies(root, comment, video, box));
    }

    // 답글의 답글은 없다 — 깊이 2단이라 루트에만 붙인다
    const reply = el.querySelector('.reply');
    if (isReply) reply.remove();
    else reply.addEventListener('click', () => {
        const children = root.querySelector('.children');
        children.prepend(writeForm(video, comment.commentSeq, box));
    });

    const del = el.querySelector('.del');
    if (!comment.isMine) del.remove();
    else del.addEventListener('click', () => remove(comment, video, box, root));

    return el;
}

async function loadReplies(root, comment, video, box) {
    const children = root.querySelector('.children');
    const button = root.querySelector('.replies');
    button.disabled = true;
    try {
        const data = await api(`${AS}/comments/video/${comment.commentSeq}/replies?page=0&size=50`);
        for (const item of data.list) children.append(node(item, video, box, true));
        button.remove();
    } catch (e) {
        button.disabled = false;
        showError(root.querySelector('.msg'), e);
    }
}

/** 작성 폼. parentCommentSeq 가 있으면 답글이다(§9.3 같은 엔드포인트). */
function writeForm(video, parentCommentSeq, box) {
    const form = document.createElement('p');
    const input = document.createElement('textarea');
    input.rows = 2;
    input.placeholder = parentCommentSeq
            ? `${acting.name}(으)로 답글 작성` : `${acting.name}(으)로 댓글 작성`;
    const submit = document.createElement('button');
    submit.type = 'button';
    submit.textContent = '등록';
    const msg = document.createElement('span');
    msg.className = 'msg muted';

    submit.addEventListener('click', async () => {
        const content = input.value.trim();
        if (!content) return;
        submit.disabled = true;
        try {
            await api(`${AS}/videos/${video.videoSeq}/comments`, {
                method: 'POST',
                body: {content, parentCommentSeq},
            });
            await loadThread(box, video);       // 카운터·정렬이 서버 규칙대로 다시 온다
        } catch (e) {
            submit.disabled = false;
            showError(msg, e);
        }
    });

    form.append(input, submit, msg);
    return form;
}

async function remove(comment, video, box, root) {
    if (!confirm('이 댓글을 삭제한다. 답글이 있으면 함께 지워진다.')) return;
    try {
        await api(`${AS}/comments/video/${comment.commentSeq}`, {method: 'DELETE'});
        await loadThread(box, video);
    } catch (e) {
        showError(root.querySelector('.msg'), e);
    }
}

const text = (s) => Object.assign(document.createElement('p'), {className: 'muted', textContent: s});

if (!acting) {
    $('no-acting').hidden = false;
} else {
    $('browse').hidden = false;
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
