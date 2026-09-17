/*
 * 챌린지 탐색 (어드민 명세 ADMIN-11 · §3.7.1).
 *
 * 댓글 스레드는 act-as-thread.js 가 참여 영상·공지 화면과 함께 쓴다 — 대상만 challenge 다.
 * 스레드를 공용으로 빼 둔 덕에 이 화면은 목록과 좋아요만 갖는다.
 */

const SIZE = 20;

let page = 0;
let total = 0;

async function reload() {
    clearError($('err'));
    // includeDeleted 를 넘기지 않는다 — 삭제된 챌린지는 좋아요도 댓글도 404 다(§7.3·§9.3)
    const query = new URLSearchParams({page, size: SIZE});
    const artistSeq = $('artist-seq').value.trim();
    if (artistSeq) query.set('artistSeq', artistSeq);

    try {
        const data = await api(`/challenges?${query}`);
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

function render(challenges) {
    const tbody = $('challenges');
    tbody.replaceChildren();

    for (const challenge of challenges) {
        const row = $('challenge-row').content.cloneNode(true);
        row.querySelector('.seq').textContent = challenge.challengeSeq;
        const img = row.querySelector('.thumb');
        if (challenge.thumbnailUrl) img.src = challenge.thumbnailUrl;
        else img.replaceWith(Object.assign(document.createElement('span'),
                {className: 'thumb-none', textContent: '없음'}));
        row.querySelector('.song').textContent = challenge.song?.title ?? '';
        row.querySelector('.artist').textContent = challenge.artist?.name ?? '';
        row.querySelector('.participants').textContent = challenge.participantCount;
        row.querySelector('.comments').textContent = challenge.commentCount;

        const like = row.querySelector('.like');
        renderChallengeLike(like, challenge);
        like.addEventListener('click', () => toggleChallengeLike(like, challenge));

        const tr = row.querySelector('tr');
        row.querySelector('.open').addEventListener('click', () =>
                toggleThread(tr, {type: 'challenge', seq: challenge.challengeSeq}));
        tbody.append(row);
    }
}

/*
 * 공지(577)와 같은 한계다 — 어드민 목록(§3.7.1) 응답에 isLiked 가 없어 초기 상태를
 * 그리지 못한다. 누른 결과만 반영하고 새로고침하면 ♡ 로 돌아온다. 카운터는 서버 값이다.
 *
 * ponytail: 초기 상태 미표시. 어드민 목록에 isLiked 가 생기면 그때 그린다
 */
function renderChallengeLike(button, challenge) {
    button.textContent = `${challenge.liked ? '♥' : '♡'} ${challenge.likeCount}`;
}

async function toggleChallengeLike(button, challenge) {
    const next = !challenge.liked;
    button.disabled = true;
    try {
        await api(`${AS}/challenges/${challenge.challengeSeq}/like`,
                {method: next ? 'POST' : 'DELETE'});
        challenge.liked = next;
        challenge.likeCount += next ? 1 : -1;
        renderChallengeLike(button, challenge);
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
    // 자기 챌린지에 다는 것이 흔한 쓰임이라 한 번 누르면 되게 둔다
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
