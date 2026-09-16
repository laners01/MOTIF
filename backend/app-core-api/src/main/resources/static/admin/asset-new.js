/*
 * 모티프 에셋 등록 (어드민 명세 v0.5 §3.8.2).
 *
 * 영상은 presign → S3 직행이고 서버에는 키만 보낸다(§1.4). 길이·해상도는 여기서
 * <video> 로 읽어 폼에 실어 보낸다 — core-api 에는 ffmpeg 가 없다(v0.5, MediaProbeService 삭제).
 * 서버는 VideoRules.checkAsset 으로 대조만 한다.
 *
 * ── 화면이 먼저 거르는 이유 ────────────────────────────────────────────────
 * 규격 위반을 서버에 맡기면 이미 S3 에 바이트가 올라간 뒤에 거절당한다. 그 객체는
 * 등록되지 않은 채 버킷에 남고 지우는 주체가 없다 — 2026-09-15 에 challenges/ 에서
 * 실제로 확인했다(원본 12개 중 8개가 등록 안 된 고아). 그래서 업로드 전에 막는다.
 */

const MAX_DURATION_MS = 20_000;     // §1.4 asset-max-duration: 20s

/** 업로드가 끝난 S3 키. 등록 요청의 videoKey 다. */
let videoKey = null;
/** <video> 에서 읽은 값 — 서버가 VideoRules 로 대조한다. */
let meta = null;

/**
 * 가로·길이 검증 (§1.4).
 *
 * 에셋은 <b>가로</b>다 — 챌린지(세로 9:16)와 반대다. 숍 화면에서 재생되고
 * 피드에 섞이지 않아 HLS 가공도 하지 않는다(§8 #17 — MP4 유지).
 */
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
    videoKey = null;
    meta = null;
    $('meta').textContent = '';
    step('upload', null);
    refresh();
    if (!file) return;

    $('preview').hidden = false;
    try {
        meta = await videoMeta(file, $('preview'));
    } catch (e) {
        showError($('err'), e);
        return;
    }

    const reason = reject(meta);
    $('meta').textContent =
            `${meta.width}×${meta.height} · ${(meta.durationMs / 1000).toFixed(1)}초`;
    if (reason) {
        // 업로드하지 않는다 — 버킷에 고아 객체를 남기지 않기 위해서다(위 주석)
        meta = null;
        showError($('err'), new Error(reason));
        return;
    }

    step('upload', 'doing');
    try {
        videoKey = await upload(file, 'ASSET_VIDEO');
        step('upload', 'done');
    } catch (e) {
        step('upload', 'fail');
        showError($('err'), e);
    }
    refresh();
}

/** 영상 업로드가 끝나고 표시명·가격이 있어야 등록이 열린다(§3.8.2 필수 항목). */
function refresh() {
    $('submit').disabled = !(videoKey && meta
            && $('artist').value && $('title').value.trim() && Number($('amount').value) > 0);
}

async function submit() {
    clearError($('err'));
    $('submit').disabled = true;
    try {
        const {assetSeq} = await api('/motif-assets', {
            method: 'POST',
            body: {
                artistSeq: Number($('artist').value),
                title: $('title').value.trim(),
                description: $('description').value.trim() || null,
                assetType: $('asset-type').value,
                videoKey,
                durationMs: meta.durationMs,
                width: meta.width,
                height: meta.height,
                // NUMERIC(10,2) 라 문자열로 보낸다 — 부동소수를 태우지 않는다(DB §31)
                price: {amount: `${Number($('amount').value).toFixed(2)}`, currency: 'KRW'},
            },
        });
        // 썸네일은 아직 없다 — app-media 가 만들 때까지 404 다(§8 #19). 목록에서 깨진
        // 이미지로 보이는 것이 정상이므로 등록 성공을 여기서 분명히 말해 준다
        $('result').textContent = `에셋 ${assetSeq} 등록됨 (썸네일은 가공 후 보인다). 목록으로 이동한다…`;
        location.href = '/admin/assets';
    } catch (e) {
        showError($('err'), e);
        $('submit').disabled = false;
    }
}

$('file').addEventListener('change', (e) => pick(e.target.files[0]));
['artist', 'title', 'amount'].forEach((id) => {
    $(id).addEventListener('input', refresh);
    $(id).addEventListener('change', refresh);
});
$('submit').addEventListener('click', submit);

(async () => {
    try {
        await fillSelect($('artist'), '/artists?size=100', 'artistSeq',
                (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name));
    } catch (e) {
        showError($('err'), e);
    }
})();
