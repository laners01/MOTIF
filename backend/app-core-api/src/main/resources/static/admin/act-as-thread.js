/*
 * 대행 댓글 스레드 — 참여 영상(412)·공지(413) 공용 (어드민 명세 ADMIN-11 · API §9).
 *
 * 두 화면의 스레드는 대상 종류만 다르고 규칙이 같다(깊이 2단·본인 댓글만 삭제·
 * 정렬 1순위). 화면마다 두면 한쪽만 고쳐졌을 때 영상에서는 되고 공지에서는 안 되는
 * 상태가 되는데, 규칙이 서버에 있어 화면만 보고는 어느 쪽이 맞는지 알 수 없다.
 *
 * 스레드를 <b>대행 경로로</b> 읽는 것이 핵심이다 — isMine 과 정렬 1순위가 "누가 보느냐"
 * 로 갈리므로 어드민 시점으로 읽으면 아티스트 본인 댓글이 위로 오지도 않고 삭제 버튼도
 * 붙지 않는다(§9.1).
 */

const acting = actingArtist();
const AS = acting ? `/as-artist/${acting.artistSeq}` : null;

/** 대상 종류 → 댓글 목록·작성 경로의 리소스 이름. 답글·삭제는 type 을 그대로 쓴다(§9.2·§9.4). */
const RESOURCE = {video: 'videos', notice: 'notices'};

/** 행 아래에 스레드 패널을 펼친다. 별도 화면을 두면 목록으로 돌아오는 길이 필요하다. */
function toggleThread(tr, target) {
    const next = tr.nextElementSibling;
    if (next?.classList.contains('detail-panel')) {
        next.remove();
        return;
    }
    const panel = document.getElementById('thread-row').content.cloneNode(true);
    const box = panel.querySelector('.thread');
    tr.after(panel);
    loadThread(box, target);
}

/** @param target {type: 'video'|'notice', seq} */
async function loadThread(box, target) {
    box.replaceChildren(muted('불러오는 중…'));
    try {
        const data = await api(`${AS}/${RESOURCE[target.type]}/${target.seq}/comments?page=0&size=50`);
        box.replaceChildren();
        box.append(writeForm(target, null, box));
        if (!data.list.length) box.append(muted('댓글이 없다.'));
        for (const comment of data.list) box.append(node(comment, target, box));
    } catch (e) {
        box.replaceChildren();
        const err = document.createElement('p');
        err.className = 'err';
        showError(err, e);
        box.append(err);
    }
}

/** 댓글·답글 한 칸. 답글은 children 에 같은 모양으로 붙는다. */
function node(comment, target, box, isReply = false) {
    const el = document.getElementById('comment-node').content.cloneNode(true);
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
        replies.addEventListener('click', () => loadReplies(root, comment, target, box));
    }

    // 답글의 답글은 없다 — 깊이 2단이라 루트에만 붙인다(§9.3)
    const reply = el.querySelector('.reply');
    if (isReply) reply.remove();
    else reply.addEventListener('click', () =>
            root.querySelector('.children').prepend(writeForm(target, comment.commentSeq, box)));

    const del = el.querySelector('.del');
    if (!comment.isMine) del.remove();
    else del.addEventListener('click', () => remove(comment, target, box, root));

    return el;
}

async function loadReplies(root, comment, target, box) {
    const button = root.querySelector('.replies');
    button.disabled = true;
    try {
        const data = await api(`${AS}/comments/${target.type}/${comment.commentSeq}/replies?page=0&size=50`);
        const children = root.querySelector('.children');
        for (const item of data.list) children.append(node(item, target, box, true));
        button.remove();
    } catch (e) {
        button.disabled = false;
        showError(root.querySelector('.msg'), e);
    }
}

/** 작성 폼. parentCommentSeq 가 있으면 답글이다 — 엔드포인트는 같다(§9.3). */
function writeForm(target, parentCommentSeq, box) {
    const form = document.createElement('p');
    const input = document.createElement('textarea');
    input.rows = 2;
    input.placeholder = `${acting.name}(으)로 ${parentCommentSeq ? '답글' : '댓글'} 작성`;
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
            await api(`${AS}/${RESOURCE[target.type]}/${target.seq}/comments`, {
                method: 'POST',
                body: {content, parentCommentSeq},
            });
            await loadThread(box, target);      // 카운터·정렬은 서버 규칙대로 다시 받는다
        } catch (e) {
            submit.disabled = false;
            showError(msg, e);
        }
    });

    form.append(input, submit, msg);
    return form;
}

async function remove(comment, target, box, root) {
    if (!confirm('이 댓글을 삭제한다. 답글이 있으면 함께 지워진다.')) return;
    try {
        await api(`${AS}/comments/${target.type}/${comment.commentSeq}`, {method: 'DELETE'});
        await loadThread(box, target);
    } catch (e) {
        showError(root.querySelector('.msg'), e);
    }
}

const muted = (s) => Object.assign(document.createElement('p'), {className: 'muted', textContent: s});

/** 컨텍스트가 없으면 목록을 부르지 않는다 — 대행 경로가 전부 artistSeq 를 요구한다. */
function requireActing() {
    if (acting) {
        document.getElementById('browse').hidden = false;
        return true;
    }
    document.getElementById('no-acting').hidden = false;
    return false;
}
