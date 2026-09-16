/*
 * 모티프 에셋 수정 (어드민 명세 v0.5 §3.8.3 상세 · §3.8.4 수정).
 *
 * 에셋만 등록 후 고칠 방법이 없었다 — 다른 도메인(아티스트·챌린지·공지)은 전부
 * 수정이 있다. 이 화면과 §3.8.3/§3.8.4 API 를 같은 티켓에서 메웠다(S15P21D104-401).
 */

const MAX_DURATION_MS = 20_000;     // §1.4 asset-max-duration: 20s

const assetSeq = Number(location.pathname.split('/')[3]);

/** 불러온 현재 값. "무엇이 바뀌었나" 판단의 기준이다. */
let loaded = null;
/** 영상을 바꿀 때만 채워진다. null 이면 요청에 videoKey 를 싣지 않는다. */
let replacement = null;

/** 가로·길이 (§1.4) — 등록 화면과 같은 규칙이다. */
function reject(m) {
    if (m.width <= m.height) {
        return `가로 영상만 등록할 수 있다 — 지금 ${m.width}×${m.height} 다`;
    }
    if (m.durationMs > MAX_DURATION_MS) {
        return `20초 이내여야 한다 — 지금 ${(m.durationMs / 1000).toFixed(1)}초다`;
    }
    return null;
}

async function pick(file) {
    clearError($('err'));
    replacement = null;
    $('meta').textContent = '';
    step('upload', null);
    refresh();
    if (!file) return;     // 파일 선택을 취소하면 "안 바꾼다" 로 돌아간다

    $('preview').hidden = false;
    let meta;
    try {
        meta = await videoMeta(file, $('preview'));
    } catch (e) {
        showError($('err'), e);
        return;
    }

    const reason = reject(meta);
    $('meta').textContent = `${meta.width}×${meta.height} · ${(meta.durationMs / 1000).toFixed(1)}초`;
    if (reason) {
        // 업로드하지 않는다 — 등록 화면과 같은 이유다. 규격 위반 파일을 S3 에 올리면
        // 등록되지 않은 채 버킷에 남고 지우는 주체가 없다
        showError($('err'), new Error(reason));
        return;
    }

    step('upload', 'doing');
    try {
        replacement = {videoKey: await upload(file, 'ASSET_VIDEO'), ...meta};
        step('upload', 'done');
    } catch (e) {
        step('upload', 'fail');
        showError($('err'), e);
    }
    refresh();
}

/** 바뀐 것이 하나라도 있어야 저장이 열린다 — 빈 PATCH 를 보내지 않는다. */
function changed() {
    if (!loaded) return false;
    return replacement !== null
        || $('title').value.trim() !== loaded.title
        || $('description').value !== (loaded.description ?? '')
        || $('asset-type').value !== loaded.assetType;
}

function refresh() {
    $('submit').disabled = !($('title').value.trim() && changed());
}

async function load() {
    try {
        loaded = await api(`/motif-assets/${assetSeq}`);
    } catch (e) {
        showError($('err'), e);
        $('summary').textContent = '불러오지 못했다.';
        return;
    }
    const artist = loaded.artist
        ? (loaded.artist.groupName ? `${loaded.artist.name} (${loaded.artist.groupName})` : loaded.artist.name)
        : '';
    $('summary').textContent =
        `#${loaded.assetSeq} · ${artist} · ${(loaded.durationMs / 1000).toFixed(1)}초`
        + ` · ${loaded.isActive ? '활성' : '비활성'}`
        + ` · 구매 ${loaded.purchaseCount} · 결과물 ${loaded.resultCount}`;
    $('title').value = loaded.title;
    $('description').value = loaded.description ?? '';
    $('asset-type').value = loaded.assetType;
    // 썸네일은 app-media 가 만들 때까지 404 다(§8 #19) — 깨진 이미지 대신 안내를 띄운다
    $('thumb').src = loaded.thumbnailUrl ?? '';
    $('thumb').hidden = !loaded.thumbnailUrl;
    $('thumb').addEventListener('error', () => {
        $('thumb').hidden = true;
        $('thumb-note').hidden = false;
    });
    refresh();
}

async function submit() {
    clearError($('err'));
    $('submit').disabled = true;
    try {
        // 부분 수정이다(§3.8.4) — 안 바뀐 필드는 아예 싣지 않는다. 특히 videoKey 는
        // 실으면 서버가 재검증·썸네일 키 교체까지 하므로 "그대로 둔다" 와 다르다
        const body = {};
        if ($('title').value.trim() !== loaded.title) body.title = $('title').value.trim();
        if ($('description').value !== (loaded.description ?? '')) body.description = $('description').value;
        if ($('asset-type').value !== loaded.assetType) body.assetType = $('asset-type').value;
        if (replacement) {
            body.videoKey = replacement.videoKey;
            body.durationMs = replacement.durationMs;
            body.width = replacement.width;
            body.height = replacement.height;
        }
        const replacedVideo = Boolean(body.videoKey);
        loaded = await api(`/motif-assets/${assetSeq}`, {method: "PATCH", body});
        replacement = null;
        $("result").textContent = replacedVideo
            ? "저장됨. 새 썸네일은 가공 후에 보인다."
            : "저장됨.";
        location.href = '/admin/assets';
    } catch (e) {
        showError($('err'), e);
        $('submit').disabled = false;
    }
}

$('file').addEventListener('change', (e) => pick(e.target.files[0]));
['title', 'description', 'asset-type'].forEach((id) => {
    $(id).addEventListener('input', refresh);
    $(id).addEventListener('change', refresh);
});
$('submit').addEventListener('click', submit);

load();
