/*
 * 챌린지 상세 (어드민 명세 v0.5 §3.7.3) + 같은 행 수정 두 가지 — S15P21D104-618.
 *
 *  - 슬롯 앵커(§3.7.9): 행을 고르고 썸네일을 클릭 → PATCH /challenges/{seq}/participants/{artistSeq}
 *  - 편집본 키(§3.7.6): 파일 고르면 presign → S3 PUT → PATCH /edited-challenges/{seq}
 *
 * 둘 다 마스크·조합을 건드리지 않아 합성 잡이 참조 중이어도 허용된다. 0017 이전에 등록된
 * 챌린지는 앵커·선택 화면이 비어 있고(null) 여기서 채운다.
 *
 * videoUrl 은 app-media 가 후처리를 끝내면 master.m3u8 로 바뀐다. 그 전에는 원본 MP4 라
 * <video> 가 그대로 재생하고, HLS 가 되면 브라우저에 따라 재생이 안 될 수 있다 —
 * 그때는 링크로 여는 것이 확인 수단이다(그래서 주소를 함께 적는다).
 */

const DEFAULT_R = 0.0995;   // 시안 402 폭 · 지름 80 → 반지름 40/402
const challengeSeq = location.pathname.split('/')[3];
let challenge = null;       // 마지막으로 받은 상세 — 앵커 갱신 시 x·y 를 여기서 읽는다
let artists = [];           // 슬롯 아티스트 교체용 후보 (§3.7.5)

const artistLabel = (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name);

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

// ── 슬롯 · 앵커 ────────────────────────────────────────────────────────────

function slotRow(participant) {
    const tr = $('slot-row').content.firstElementChild.cloneNode(true);
    const a = participant.anchor;
    tr.dataset.artistSeq = participant.artist.artistSeq;
    tr.querySelector('.mask').textContent = participant.participantMask;

    // 아티스트를 고를 수 있다(§3.7.5). dataset 은 서버가 아는 현재 주인이라
    // 앵커 저장(§3.7.9)은 계속 그 값을 경로에 쓴다 — select 는 저장 전까지 후보다
    const select = tr.querySelector('.artist');
    select.append(...artists.map((x) => new Option(artistLabel(x), x.artistSeq)));
    select.value = participant.artist.artistSeq;

    tr.querySelector('.xy').textContent = a ? `${a.x.toFixed(4)}, ${a.y.toFixed(4)}` : '미지정 — 행을 고르고 썸네일을 클릭';
    tr.querySelector('.xy').className = a ? 'xy' : 'xy err';
    tr.querySelector('.r').value = a?.r ?? DEFAULT_R;
    tr.querySelector('.r-label').textContent = (a?.r ?? DEFAULT_R).toFixed(4);
    return tr;
}

const activeRow = () => $('slots').querySelector('tr.active');

$('slots').addEventListener('click', (e) => {
    const tr = e.target.closest('tr');
    if (!tr) return;
    for (const row of $('slots').rows) row.classList.toggle('active', row === tr);
    drawDots();
});

// 반지름만 바꾸면 위치가 이미 있는 슬롯은 바로 저장, 없는 슬롯은 미리보기만
$('slots').addEventListener('input', async (e) => {
    if (!e.target.classList.contains('r')) return;
    const tr = e.target.closest('tr');
    tr.querySelector('.r-label').textContent = Number(e.target.value).toFixed(4);
    drawDots();
});
$('slots').addEventListener('change', async (e) => {
    if (!e.target.classList.contains('r')) return;
    const tr = e.target.closest('tr');
    const cur = participantOf(tr)?.anchor;
    if (cur) await saveAnchor(tr, cur.x, cur.y);
});

const participantOf = (tr) => challenge?.participants.find(
    (p) => String(p.artist?.artistSeq) === tr.dataset.artistSeq);

/** 썸네일 클릭 → 고른 슬롯의 (x, y). 좌표는 썸네일 표시 크기 대비 0~1 비율이다(§3.7.9). */
$('thumb-wrap').addEventListener('click', async (e) => {
    const tr = activeRow();
    const img = $('thumb-wrap').querySelector('img');
    if (!tr || !img) return;
    const r = img.getBoundingClientRect();
    const clamp = (v) => Math.min(Math.max(v, 0), 1);
    await saveAnchor(tr, clamp((e.clientX - r.left) / r.width), clamp((e.clientY - r.top) / r.height));
});

async function saveAnchor(tr, x, y) {
    clearError($('err'));
    try {
        challenge = await api(`/challenges/${challengeSeq}/participants/${tr.dataset.artistSeq}`, {
            method: 'PATCH',
            body: {x: Number(x.toFixed(4)), y: Number(y.toFixed(4)), r: Number(tr.querySelector('.r').value)},
        });
        renderSlots(tr.dataset.artistSeq);
    } catch (err) {
        showError($('err'), err);
    }
}

function renderSlots(keepActive) {
    $('slots').replaceChildren(...challenge.participants.map(slotRow));
    for (const row of $('slots').rows) row.classList.toggle('active', row.dataset.artistSeq === String(keepActive));
    $('slot-note').textContent = challenge.participants.length === 0
        ? '슬롯 없음(솔로) — 사용자가 교체해 들어갈 자리가 없다.'
        : `${challenge.participants.length}개. 조합은 2^${challenge.participants.length}−1 = ${(1 << challenge.participants.length) - 1}개가 필요하다.`
          + (challenge.participants.some((p) => !p.anchor) ? ' 앵커가 빈 슬롯은 앱에서 원이 뜨지 않는다.' : '');
    drawDots();
}

/** 썸네일 위에 슬롯 원 — 앱이 그릴 모습(시안 10-1)이다. 값이 비율이라 표시 크기를 곱한다. */
function drawDots() {
    const img = $('thumb-wrap').querySelector('img');
    for (const old of $('thumb-wrap').querySelectorAll('.dot')) old.remove();
    if (!img || !challenge) return;
    const w = img.clientWidth;
    const h = img.clientHeight;
    for (const tr of $('slots').rows) {
        const p = participantOf(tr);
        if (!p?.anchor) continue;
        // 사진은 dot() 공용(admin.js) — 반지름은 저장값이 아니라 슬라이더 현재값이라 조절 중에도 따라온다
        $('thumb-wrap').append(dot(tr.classList.contains('active'), p.anchor.x * w, p.anchor.y * h,
            Number(tr.querySelector('.r').value) * w, p.artist?.profileImageUrl, p.participantMask));
    }
}

// ── 슬롯 아티스트 교체 (§3.7.5) ────────────────────────────────────────────

/*
 * 부분 수정 API 가 없어서 슬롯 구성을 통째로 다시 보낸다. 앵커는 지금 값 그대로,
 * 편집본은 지금 키를 그대로 돌려보내 파일이 바뀌지 않게 한다 — 행 seq 만 새로 난다.
 * 합성 잡이 편집본을 참조 중이면 409 EDITED_IN_USE 로 막힌다(명세 §3.7.5).
 */
const slotsDirty = () => [...$('slots').rows]
        .some((tr) => tr.querySelector('.artist').value !== tr.dataset.artistSeq);

function refreshSlotSaveState() {
    const changed = slotsDirty();
    $('save-slots').disabled = !changed;
    $('slot-save-note').textContent = changed
        ? '저장하면 슬롯·편집본 행이 다시 만들어진다 — 합성 잡이 편집본을 쓰고 있으면 막힌다'
        : '고친 곳이 없다';
}

/** 마스크(비트 합) → removedSlots(슬롯 인덱스 배열). §3.7.5 는 인덱스로 받는다. */
function removedSlotsOf(mask) {
    const idx = [];
    for (let i = 0; mask >> i; i++) {
        if ((mask >> i) & 1) idx.push(i);
    }
    return idx;
}

$('slots').addEventListener('change', (e) => {
    if (e.target.classList.contains('artist')) refreshSlotSaveState();
});

$('save-slots').addEventListener('click', async () => {
    clearError($('err'));
    $('save-slots').disabled = true;
    try {
        const participants = [...$('slots').rows].map((tr) => {
            const now = challenge.participants
                    .find((p) => String(p.artist.artistSeq) === tr.dataset.artistSeq);
            return {
                artistSeq: Number(tr.querySelector('.artist').value),
                // 앵커가 아직 없는 슬롯은 반지름만 화면 값으로 채워 보낸다 —
                // x·y 는 썸네일 클릭으로 따로 찍는다
                anchor: now?.anchor ?? {x: 0.5, y: 0.4, r: Number(tr.querySelector('.r').value)},
            };
        });
        const editedChallenges = challenge.editedChallenges.map((e) => ({
            removedSlots: removedSlotsOf(e.participantMask),
            videoKey: e.videoKey,
            initialSceneKey: e.initialSceneKey,
            previewImageKey: e.previewImageKey,
        }));
        await api(`/challenges/${challengeSeq}/participants`,
                {method: 'PUT', body: {participants, editedChallenges}});
        await load();
        $('slot-save-note').textContent = '저장했다';
    } catch (e) {
        showError($('err'), e);
        refreshSlotSaveState();
    }
});

// ── 편집본 · 키 교체 ───────────────────────────────────────────────────────

function editedRow(edited) {
    const tr = $('edited-row').content.firstElementChild.cloneNode(true);
    tr.dataset.seq = edited.editedChallengeSeq;
    tr.querySelector('.seq').textContent = edited.editedChallengeSeq;
    tr.querySelector('.mask').textContent = edited.participantMask;
    tr.querySelector('.video .link').replaceChildren(link(edited.videoUrl, '열기'));
    tr.querySelector('.scene .link').replaceChildren(link(edited.initialSceneUrl, '열기'));
    tr.querySelector('.preview .link').replaceChildren(
        edited.previewImageUrl ? link(edited.previewImageUrl, '열기')
            : Object.assign(document.createElement('span'), {className: 'err', textContent: '없음 — 올려야 앱 선택 화면이 바뀐다'}));
    tr.querySelector('.jobs').textContent = edited.jobRefCount;
    return tr;
}

/** 파일 → presign(edited/{challengeSeq}/) → S3 PUT → §3.7.6 PATCH. 영상은 길이도 실어 서버가 원본과 대조한다. */
$('edited').addEventListener('change', async (e) => {
    const input = e.target;
    if (input.type !== 'file' || !input.files[0]) return;
    const tr = input.closest('tr');
    const file = input.files[0];
    clearError($('err'));
    input.disabled = true;
    try {
        const body = {};
        if (input.dataset.for === 'videoKey') {
            body.durationMs = (await videoMeta(file, document.createElement('video'))).durationMs;
        }
        body[input.dataset.for] = await upload(file, input.dataset.kind, Number(challengeSeq));
        await api(`/edited-challenges/${tr.dataset.seq}`, {method: 'PATCH', body});
        await load();
    } catch (err) {
        showError($('err'), err);
        input.disabled = false;
    }
});

// ── 곡 · 소개글 (§3.7.4) ───────────────────────────────────────────────────

/*
 * 고칠 수 있는 것은 이 둘뿐이다. 원본 영상·메인 아티스트·길이는 등록 후 불변이고
 * (R1 원본 교체 금지) 요청에 실으면 400 이라, 화면에서도 입력 자체를 두지 않는다.
 *
 * 곡 목록은 한 번만 받는다 — load() 가 앵커·편집본 갱신마다 다시 도는데 그때마다
 * 부르면 select 가 계속 불어난다.
 */
let songsLoaded = false;

const dirty = () => challenge && (
    $('description').value !== (challenge.description ?? '')
    || $('song').value !== String(challenge.song?.songSeq ?? ''));

function refreshSaveState() {
    const changed = dirty();
    $('save').disabled = !changed;
    $('save-note').textContent = changed ? '저장하지 않은 변경이 있다' : '고친 곳이 없다';
}

$('description').addEventListener('input', refreshSaveState);
$('song').addEventListener('change', refreshSaveState);

$('save').addEventListener('click', async () => {
    clearError($('err'));
    $('save').disabled = true;
    try {
        // 부분 수정이라 바뀐 것만 싣는다 — 곡을 안 건드렸는데 같은 값을 보내면
        // 서버 로그에 의미 없는 변경으로 남는다
        const body = {};
        if ($('description').value !== (challenge.description ?? '')) {
            body.description = $('description').value.trim();
        }
        if ($('song').value !== String(challenge.song?.songSeq ?? '')) {
            body.songSeq = Number($('song').value);
        }
        await api(`/challenges/${challengeSeq}`, {method: 'PATCH', body});
        await load();
        $('save-note').textContent = '저장했다';
    } catch (e) {
        showError($('err'), e);
        refreshSaveState();
    }
});

// ── 로드 ───────────────────────────────────────────────────────────────────

async function load() {
    clearError($('err'));
    try {
        const c = await api(`/challenges/${challengeSeq}`);
        challenge = c;

        $('video').src = c.videoUrl ?? '';
        $('video-note').textContent = c.videoUrl
            ? `${c.videoUrl}${c.videoUrl.endsWith('.m3u8') ? ' — HLS. 브라우저가 못 열면 링크로 확인해라' : ' — 원본 MP4. app-media 후처리 전이다'}`
            : '영상 URL 이 없다 — motif.media.public-base-url 미설정이거나 키가 비었다';

        // 썸네일은 app-media 산출물이라 후처리 전에는 키만 있고 파일이 없다(404).
        // 깨진 아이콘 대신 "생성 중" 으로 보여 어드민이 고장과 구분할 수 있게 한다.
        // 앵커는 이 썸네일 위에 찍으므로 썸네일이 없으면 앵커도 못 찍는다
        if (c.thumbnailUrl) {
            const img = document.createElement('img');
            img.src = c.thumbnailUrl;
            img.alt = '';
            img.onload = drawDots;
            img.onerror = () => {
                img.replaceWith(Object.assign(document.createElement('p'),
                        {className: 'thumb-none', textContent: '썸네일 생성 중 — 후처리가 끝나면 표시된다. 앵커는 그 뒤에 찍는다'}));
            };
            $('thumb-wrap').replaceChildren(img);
            $('thumb-note').textContent = c.thumbnailUrl;
        } else {
            $('thumb-wrap').replaceChildren();
            $('thumb-note').textContent = '썸네일 URL 이 없다 — 키가 비었거나 CDN 설정이 빠졌다';
        }

        set('seq', `${c.challengeSeq}${c.isDeleted ? ' (삭제됨)' : ''}`);
        set('artist', c.artist?.name ?? '');
        set('duration', `${(c.durationMs / 1000).toFixed(1)}초`);

        // 곡·아티스트 목록은 한 번만 받는다 — load() 는 앵커·편집본 갱신마다 다시
        // 도는데 그때마다 부르면 select 가 계속 불어난다
        if (!songsLoaded) {
            await fillSelect($('song'), '/songs?size=100', 'songSeq',
                    (s) => `${s.title} — ${s.singerName}`);
            const {list} = await api('/artists?size=100');
            artists = list;
            songsLoaded = true;
        }
        $('song').value = c.song?.songSeq ?? '';
        $('description').value = c.description ?? '';
        refreshSaveState();
        set('counts', `조회 ${c.viewCount} · 참여 ${c.participantCount} · 좋아요 ${c.likeCount} · 댓글 ${c.commentCount}`);
        set('jobs', `진행 중 ${c.activeJobCount}건`);

        renderSlots(activeRow()?.dataset.artistSeq);

        $('edited').replaceChildren(...c.editedChallenges.map(editedRow));
        $('edited-note').textContent = c.editedChallenges.length === 0
            ? '편집본 없음 — 표시 전용이다. 사용자가 촬영을 시도하면 EDITED_CHALLENGE_NOT_FOUND 가 난다. AI 산출물이 나오면 슬롯 교체(§3.7.5)로 얹는다.'
            : `${c.editedChallenges.length}건.`
              + (c.editedChallenges.some((e) => !e.previewImageUrl) ? ' 선택 화면이 빈 조합은 앱이 썸네일로 대신 보여준다.' : '');
    } catch (e) {
        showError($('err'), e);
    }
}

load();
