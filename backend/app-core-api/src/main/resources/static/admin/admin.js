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
 */
async function upload(file, kind, challengeSeq) {
    const {uploadUrl, objectKey} = await api('/media/presign', {
        method: 'POST',
        body: {kind, contentType: file.type, contentLength: file.size, challengeSeq},
    });
    const put = await fetch(uploadUrl, {method: 'PUT', headers: {'Content-Type': file.type}, body: file});
    if (!put.ok) throw new Error(`S3 업로드 실패 (${put.status})`);
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
