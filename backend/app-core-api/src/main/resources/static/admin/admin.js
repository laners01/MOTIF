/*
 * 어드민 화면 공통 (어드민 명세 v0.5).
 *
 * 화면은 Thymeleaf 가 뼈대만 그리고 데이터는 전부 /api/v1/admin/** 을 fetch 한다(§1.1).
 * 프레임워크·번들러 없이 바닐라 JS 다 — 화면 수가 적고 로직이 얇다.
 */

const API = '/api/v1/admin';

const $ = (id) => document.getElementById(id);

/** 봉투(§1.5)의 실패를 그대로 실은 예외 — code·data(§5 실패 상세)를 화면이 인라인 표시한다. */
class ApiError extends Error {
    constructor(status, envelope) {
        super(envelope?.message ?? `요청 실패 (${status})`);
        this.status = status;
        this.code = envelope?.code;
        this.data = envelope?.data;
    }
}

/**
 * 어드민 API 호출 — 봉투를 벗겨 data 만 돌려주고 실패는 ApiError 로 던진다.
 *
 * 쿠키는 HttpOnly `motif_admin`(§1.2)이라 JS 가 건드리지 않는다. same-origin 요청이라
 * fetch 기본값으로 실린다 — Authorization 헤더를 따로 만들지 않는다.
 */
async function api(path, {method = 'GET', body} = {}) {
    const res = await fetch(API + path, {
        method,
        headers: body === undefined ? undefined : {'Content-Type': 'application/json'},
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 401) {
        location.href = '/admin/login';           // 12시간 만료, refresh 없음(§1.2)
        throw new ApiError(401, null);
    }
    if (res.status === 204) return null;          // 좋아요 대행 등 본문 없는 성공(§1.5)
    const envelope = await res.json();
    if (!res.ok || envelope.result === 'FAIL') throw new ApiError(res.status, envelope);
    return envelope.data;
}

/**
 * presign(§3.2) → S3 PUT → objectKey.
 *
 * 바이트는 브라우저에서 S3 로 직행하고 서버는 키만 받는다(§1.4 Claim-Check).
 * Content-Type·크기는 presign 서명에 박혀 있어 다른 값으로 PUT 하면 S3 가 거부한다.
 *
 * EDITED_* 는 키가 슬롯에 매여 있어 두 seq 를 함께 보낸다 — 서버가 실재·소속을
 * 대조하고 안 맞으면 400 PARTICIPANT_INVALID 다. 그 밖의 kind 에서는 무시된다.
 */
async function upload(file, kind, challengeSeq, challengeParticipantSeq) {
    const {uploadUrl, objectKey} = await api('/media/presign', {
        method: 'POST',
        body: {
            kind, contentType: file.type, contentLength: file.size,
            challengeSeq, challengeParticipantSeq,
        },
    });
    let put;
    try {
        put = await fetch(uploadUrl, {method: 'PUT', headers: {'Content-Type': file.type}, body: file});
    } catch (e) {
        // fetch 가 던졌다 = 응답을 못 봤다. PUT 은 단순 요청이 아니라 프리플라이트가 먼저
        // 나가는데, 버킷에 CORS 규칙이 없으면 그 OPTIONS 가 403 이고 브라우저가 여기서 끊는다.
        // 상태코드가 없으므로 S3 탓인지 네트워크 탓인지 이 자리에서 구분되지 않는다
        throw new Error('S3 업로드가 브라우저에서 차단됐다 — 버킷 CORS 규칙(PUT 허용)을 확인해라. ' + e.message);
    }
    if (!put.ok) {
        // S3 는 실패를 XML 로 준다. <Code> 를 그대로 보여준다 — AccessDenied(권한),
        // SignatureDoesNotMatch(서명 헤더 불일치), RequestTimeTooSkewed(시계) 가 전부 403 이라
        // 상태코드만 봐서는 어느 쪽인지 알 수 없다
        const body = await put.text().catch(() => '');
        const code = (body.match(/<Code>([^<]+)<\/Code>/) || [])[1];
        const message = (body.match(/<Message>([^<]+)<\/Message>/) || [])[1];
        throw new Error(`S3 업로드 실패 (${put.status}${code ? ' ' + code : ''})${message ? ' — ' + message : ''}`);
    }
    return objectKey;
}

/**
 * 영상 길이·해상도를 <video> 로 읽는다 — core-api 에는 ffmpeg 가 없다(§1.4).
 * 화면이 읽어 폼에 싣고 서버는 VideoRules 로 대조만 한다.
 *
 * @param el 화면에 이미 붙어 있는 <video>. 넘기면 그대로 써서 첫 프레임도 보여준다
 */
function videoMeta(file, el = document.createElement('video')) {
    return new Promise((resolve, reject) => {
        el.preload = 'metadata';
        el.onloadedmetadata = () => resolve({
            durationMs: Math.round(el.duration * 1000),
            width: el.videoWidth,
            height: el.videoHeight,
        });
        el.onerror = () => reject(new Error('영상 메타데이터를 읽지 못했습니다 — 파일이 손상됐거나 브라우저가 못 읽는 코덱입니다'));
        el.src = URL.createObjectURL(file);
    });
}

/*
 * ── 썸네일 시각 스크럽 (§4.8) ─────────────────────────────────────────────
 *
 * 챌린지 원본과 모티프 에셋이 함께 쓴다. 썸네일 이미지를 따로 올리지 않는다 —
 * 여기서 고른 지점을 워커가 원본에서 뽑으므로 화질이 원본 그대로다.
 *
 * 세 화면(challenge-new · asset-new · asset-edit)이 전부 규격 검증 때문에 이미
 * <video> 를 띄워 두고 있어 스크럽 대상이 공짜로 있다. 복붙하지 않는 이유는 기본값
 * 1.5초와 상한 보정이 서버·워커 규칙과 맞물려 있어서다 — 한쪽만 고치면 그 화면에서만
 * 조용히 다른 프레임이 나온다.
 *
 * 화면은 #thumb-pick · #thumb-at · #thumb-at-label 세 요소를 갖는다.
 */

/** 워커 기본값과 같은 값(HlsPackager.THUMBNAIL_AT) — 첫 프레임은 페이드인·모션블러가 흔하다. */
const THUMB_DEFAULT_SEC = 1.5;

/**
 * 슬라이더 범위를 이 영상 길이에 맞추고 스크럽을 연다.
 *
 * 상한을 길이보다 조금 앞에 두는 이유: 정확히 끝 지점은 마지막 프레임 다음이라
 * ffmpeg 가 빈 프레임을 뱉고, 서버도 THUMBNAIL_AT_OUT_OF_RANGE 로 막는다(배타적 경계).
 *
 * @param video 화면에 이미 떠 있는 <video> — videoMeta 에 넘긴 그 요소다
 */
function initThumbPick(video, durationMs) {
    const max = Math.max(0, durationMs / 1000 - 0.1);
    const at = $('thumb-at');
    at.max = max.toFixed(2);
    at.value = Math.min(THUMB_DEFAULT_SEC, max).toFixed(2);
    // addEventListener 가 아니다 — 파일을 다시 고를 때마다 핸들러가 쌓인다
    at.oninput = () => showThumbAt(video);
    $('thumb-pick').hidden = false;
    showThumbAt(video);
}

/** 스크럽한 지점을 그대로 보여준다 — 등록 후 나올 썸네일이 이 프레임이다. */
function showThumbAt(video) {
    const v = Number($('thumb-at').value);
    $('thumb-at-label').textContent = `${v.toFixed(2)}초`;
    video.currentTime = v;
}

/** 파일을 다시 고르거나 취소했을 때 — 이전 영상의 값이 폼에 남지 않게 한다. */
function resetThumbPick() {
    $('thumb-pick').hidden = true;
}

/** 스크럽이 닫혀 있으면 undefined — 선택 필드라 없으면 워커 기본값(1.5초)이 쓰인다. */
const thumbnailAtMs = () =>
    ($('thumb-pick').hidden ? undefined : Math.round(Number($('thumb-at').value) * 1000));

/*
 * ── 영상 재생 (§1.7) ─────────────────────────────────────────────────────
 *
 * 가공이 끝난 영상은 HLS(master.m3u8)라 Chrome 의 <video> 가 그대로 열지 못한다.
 * hls.js 를 쓴다 — 재생하는 화면에서만 필요하니 처음 부를 때 받는다.
 *
 * 보호 등급(private/)은 master 주소에 프리픽스 서명이 쿼리로 붙어 온다(ADR-0012).
 * 재생목록 안의 주소는 상대 경로라 서명이 따라가지 않으므로, 앱의 OkHttp 인터셉터처럼
 * 같은 쿼리를 자식 요청마다 붙인다. 네이티브 HLS(Safari)는 그걸 못 해서 쓰지 않는다.
 */

const HLS_JS = 'https://cdnjs.cloudflare.com/ajax/libs/hls.js/1.6.15/hls.min.js';
let hlsLoading = null;

function loadHls() {
    hlsLoading ??= new Promise((resolve, reject) => {
        const s = Object.assign(document.createElement('script'), {src: HLS_JS});
        s.onload = resolve;
        s.onerror = () => {
            hlsLoading = null;
            reject(new Error('hls.js 를 받지 못했다'));
        };
        document.head.append(s);
    });
    return hlsLoading;
}

/**
 * <video> 에 재생 주소를 건다. MP4 는 그대로, HLS 는 hls.js 로.
 * 치명적 재생 실패는 <video> 의 error 이벤트로 알린다 — 화면이 거기서 안내를 띄운다.
 */
async function playVideo(el, url) {
    el._hls?.destroy();
    el._hls = null;
    if (!url || !new URL(url, location.href).pathname.endsWith('.m3u8')) {
        el.src = url ?? '';
        return;
    }
    await loadHls();
    if (!Hls.isSupported()) {
        el.src = url;
        return;
    }
    const query = new URL(url).search;
    const hls = new Hls({
        xhrSetup: (xhr, u) => {
            if (query && !u.includes('?')) xhr.open('GET', u + query, true);
        },
    });
    hls.on(Hls.Events.ERROR, (_, d) => {
        if (d.fatal) el.dispatchEvent(new Event('error'));
    });
    hls.loadSource(url);
    hls.attachMedia(el);
    el._hls = hls;
}

/** 목록 API(§1.5 offset 페이지) 를 <select> 에 채우고 항목 배열을 돌려준다. */
async function fillSelect(select, path, valueKey, label) {
    const {list} = await api(path);
    select.append(...list.map((item) => new Option(label(item), item[valueKey])));
    return list;
}

function showError(box, err) {
    box.textContent = [
        err.code && `[${err.code}]`,
        err.message,
        err.data && JSON.stringify(err.data),
    ].filter(Boolean).join(' ');
    box.hidden = false;
}

function clearError(box) {
    box.hidden = true;
    box.textContent = '';
}

/** 진행 표시(부록 A) — 단계에 doing/done/fail 중 하나만 남긴다. */
function step(name, state) {
    const li = document.querySelector(`.progress [data-step="${name}"]`);
    if (li) li.className = state ?? '';
}

/*
 * ── 활동 대행 컨텍스트 (ADMIN-11 · §1.7) ─────────────────────────────────
 *
 * 고른 아티스트를 브라우저에 두고 헤더에 띄운다(ADMIN-01). 서버에 두지 않는 이유는
 * 어드민 토큰이 무상태이기 때문이다(§1.2) — 대행 API 는 요청마다 artistSeq 를 경로로
 * 받으므로 서버가 "지금 누구로 활동 중" 을 알 필요가 없다. 세션을 새로 만들면 토큰만
 * 있으면 되던 구조에 상태 저장소가 하나 붙는다.
 *
 * localStorage 라 탭을 옮겨도 남는다. 대행은 목록 화면과 상세 화면을 오가며 하는
 * 작업이라 탭마다 다시 고르게 하면 엉뚱한 아티스트로 댓글이 달린다.
 */

const ACTING_KEY = 'motif.admin.acting-artist';

/** 활동 중 아티스트 {artistSeq, name} 또는 null. */
function actingArtist() {
    try {
        return JSON.parse(localStorage.getItem(ACTING_KEY));
    } catch (e) {
        return null;                              // 손상된 값은 없는 것으로 본다
    }
}

/** null 을 넣으면 해제다. 헤더 표시는 여기서만 갱신한다. */
function setActingArtist(artist) {
    if (artist) localStorage.setItem(ACTING_KEY, JSON.stringify(artist));
    else localStorage.removeItem(ACTING_KEY);
    renderActing();
}

function renderActing() {
    const el = $('acting');
    if (!el) return;                              // 로그인 화면은 layout 을 쓰지 않는다
    const artist = actingArtist();
    el.textContent = artist ? `활동 중: ${artist.name}` : '';
    el.hidden = !artist;
}

/*
 * nav 의 계정 표시와 로그아웃 (§3.1.2·§3.1.3).
 *
 * 두 API 는 588·608 에서 만들어졌는데 부르는 화면이 없어 <b>로그아웃할 방법이 없었다</b>.
 * layout.html 이 이 파일을 모든 화면에 싣고, 로그인 화면은 layout 을 쓰지 않으므로
 * 여기 요소가 없다 — 그래서 존재 확인 후에만 붙인다.
 */
(() => {
    const who = document.getElementById('who');
    const button = document.getElementById('logout');
    if (!who || !button) return;

    renderActing();

    api('/auth/me')
            .then((me) => {
                who.textContent = me.name;
            })
            .catch(() => {
                // 토큰이 만료됐거나 쿠키가 없다. 화면은 어차피 다음 호출에서 막히므로
                // 여기서 리다이렉트까지 하지는 않는다 — 로그인 페이지로 튕기는 판단은
                // 각 화면의 조회 실패가 한다
                who.textContent = '';
            });

    button.addEventListener('click', async () => {
        button.disabled = true;
        try {
            await api('/auth/logout', {method: 'POST'});
        } catch (e) {
            // 이미 만료된 세션이면 로그아웃도 실패한다. 그래도 로그인 화면으로 보낸다 —
            // 사용자가 원한 것은 "나가기" 지 "성공적인 로그아웃" 이 아니다
        }
        // 다음 사람이 같은 브라우저로 로그인하면 남의 컨텍스트로 대행하게 된다
        setActingArtist(null);
        location.href = '/admin/login';
    });
})();


/*
 * ── 슬롯 프로필 원 (§3.7.2 anchor) ─────────────────────────────────────────
 *
 * 챌린지 등록·상세가 프레임/썸네일 위에 같은 모양으로 그린다 — 앱(시안 10-1)이 그릴
 * 모습의 미리보기다. 좌표는 이미 표시 px 로 환산해 넘긴다(비율 × 표시 크기).
 * 사진이 없으면(프로필 미등록·아티스트 미선택) 라벨(슬롯 번호·마스크)을 대신 넣는다.
 */
function dot(active, cx, cy, radius, imageUrl, label) {
    const el = document.createElement('div');
    el.className = active ? 'dot active' : 'dot';
    Object.assign(el.style, {
        left: `${cx - radius}px`,
        top: `${cy - radius}px`,
        width: `${radius * 2}px`,
        height: `${radius * 2}px`,
    });
    if (imageUrl) {
        const img = document.createElement('img');
        img.src = imageUrl;
        img.alt = '';
        el.append(img);
    } else {
        el.append(Object.assign(document.createElement('span'), {textContent: label}));
    }
    return el;
}
