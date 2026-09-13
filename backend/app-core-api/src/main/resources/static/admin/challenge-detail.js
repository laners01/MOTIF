/*
 * 챌린지 상세 (어드민 명세 v0.5 §3.7.3).
 *
 * 읽기 전용이다. 등록 직후 무엇이 들어갔는지 확인하는 화면이라 숫자뿐 아니라
 * 원본 영상과 슬롯 좌표를 실제로 보여준다.
 *
 * videoUrl 은 app-media 가 후처리를 끝내면 master.m3u8 로 바뀐다. 그 전에는 원본 MP4 라
 * <video> 가 그대로 재생하고, HLS 가 되면 브라우저에 따라 재생이 안 될 수 있다 —
 * 그때는 링크로 여는 것이 확인 수단이다(그래서 주소를 함께 적는다).
 */

const challengeSeq = location.pathname.split('/')[3];

const set = (id, text) => {
    $(id).textContent = text;
};

const link = (url, label) => {
    if (!url) return document.createTextNode('—');
    const a = document.createElement('a');
    a.href = url;
    a.textContent = label;
    a.target = '_blank';
    a.rel = 'noreferrer';
    return a;
};

function slotRow(participant) {
    const tr = $('slot-row').content.firstElementChild.cloneNode(true);
    const bbox = participant.bbox;
    tr.querySelector('.mask').textContent = participant.participantMask;
    tr.querySelector('.artist').textContent = participant.artist?.name ?? '';
    tr.querySelector('.lt').textContent = `${bbox.leftTopX}, ${bbox.leftTopY}`;
    tr.querySelector('.rb').textContent = `${bbox.rightBottomX}, ${bbox.rightBottomY}`;
    return tr;
}

function editedRow(edited) {
    const tr = $('edited-row').content.firstElementChild.cloneNode(true);
    tr.querySelector('.seq').textContent = edited.editedChallengeSeq;
    tr.querySelector('.mask').textContent = edited.participantMask;
    tr.querySelector('.video').replaceChildren(link(edited.videoUrl, '열기'));
    tr.querySelector('.scene').replaceChildren(link(edited.initialSceneUrl, '열기'));
    tr.querySelector('.jobs').textContent = edited.jobRefCount;
    return tr;
}

async function load() {
    clearError($('err'));
    try {
        const c = await api(`/challenges/${challengeSeq}`);

        $('video').src = c.videoUrl ?? '';
        $('video-note').textContent = c.videoUrl
            ? `${c.videoUrl}${c.videoUrl.endsWith('.m3u8') ? ' — HLS. 브라우저가 못 열면 링크로 확인해라' : ' — 원본 MP4. app-media 후처리 전이다'}`
            : '영상 URL 이 없다 — motif.media.public-base-url 미설정이거나 키가 비었다';

        set('seq', `${c.challengeSeq}${c.isDeleted ? ' (삭제됨)' : ''}`);
        set('artist', c.artist?.name ?? '');
        set('song', c.song ? `${c.song.title} / ${c.song.singerName}` : '');
        set('description', c.description ?? '');
        set('duration', `${(c.durationMs / 1000).toFixed(1)}초`);
        set('counts', `조회 ${c.viewCount} · 참여 ${c.participantCount} · 좋아요 ${c.likeCount} · 댓글 ${c.commentCount}`);
        set('jobs', `진행 중 ${c.activeJobCount}건`);

        $('slots').replaceChildren(...c.participants.map(slotRow));
        $('slot-note').textContent = c.participants.length === 0
            ? '슬롯 없음(솔로) — 사용자가 교체해 들어갈 자리가 없다.'
            : `${c.participants.length}개. 조합은 2^${c.participants.length}−1 = ${(1 << c.participants.length) - 1}개가 필요하다.`;

        $('edited').replaceChildren(...c.editedChallenges.map(editedRow));
        $('edited-note').textContent = c.editedChallenges.length === 0
            ? '편집본 없음 — 표시 전용이다. 사용자가 촬영을 시도하면 EDITED_CHALLENGE_NOT_FOUND 가 난다. AI 산출물이 나오면 슬롯 교체(§3.7.5)로 얹는다.'
            : `${c.editedChallenges.length}건.`;
    } catch (e) {
        showError($('err'), e);
    }
}

load();
