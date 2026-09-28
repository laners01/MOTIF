/*
 * 공지 수정 (어드민 명세 v0.5 §3.10.3 · §3.10.4) — S15P21D104-572.
 *
 * imageKeys 는 손댔을 때만 보낸다 — 3.10.4 는 오면 전체 교체(기존 행 하드 삭제 후
 * 재생성), 생략하면 이미지 불변이다. 본문만 고치는 흔한 경우에 이미지 행을 통째로
 * 갈아 끼우지 않기 위한 구분이다.
 */

const MAX_IMAGES = 10;      // §3.10.2 0~10장 (기본안)

const noticeSeq = location.pathname.split('/')[3];

let imagesDirty = false;    // 추가·삭제·순서 변경이 있었나
let orderBeforeDrag = null;
let dragging = null;

/**
 * 상세 응답(3.10.3)은 imageUrl(공개 CDN) 만 준다 — PATCH 는 imageKey 를 요구한다.
 * CDN URL 은 오브젝트 키 위에 그대로 얹히므로 경로에서 키를 되돌린다.
 *
 * ponytail: URL → 키 역산. 명세가 3.10.3 상세에 imageKey 를 실어 주면 이 함수는 지운다
 */
const keyOf = (imageUrl) => new URL(imageUrl, location.origin).pathname.replace(/^\//, '');

const keys = () => [...$('images').children].map((li) => li.dataset.key);

function markDirty() {
    imagesDirty = true;
    renumber();
}

function renumber() {
    [...$('images').children].forEach((li, i) => {
        li.querySelector('.order').textContent = `#${i}`;
    });
    $('image-count').textContent = `${$('images').children.length} / ${MAX_IMAGES}장`
        + (imagesDirty ? ' — 저장 시 전체 교체된다' : '');
    $('replace-warning').className = imagesDirty ? 'err' : 'muted';
}

function imageItem(key, src) {
    const li = $('image-item').content.firstElementChild.cloneNode(true);
    li.dataset.key = key;
    li.querySelector('img').src = src;
    li.title = key;
    return li;
}

// ── 조회 ───────────────────────────────────────────────────────────────────

async function load() {
    clearError($('err'));
    try {
        const notice = await api(`/notices/${noticeSeq}`);
        $('head').textContent = `#${notice.noticeSeq} · ${notice.artist?.name ?? ''}`
            + ` · 등록 ${new Date(notice.regDttm).toLocaleString('ko-KR')}`;
        $('content').value = notice.content;
        $('images').replaceChildren(
            ...[...notice.images]
                .sort((a, b) => a.displayOrder - b.displayOrder)
                .map((image) => imageItem(keyOf(image.imageUrl), image.imageUrl)));
        imagesDirty = false;
        renumber();
    } catch (e) {
        showError($('err'), e);
    }
}

// ── 이미지 추가·삭제 ───────────────────────────────────────────────────────

$('file').addEventListener('change', async () => {
    const files = [...$('file').files];
    $('file').value = '';
    if (!files.length) return;
    clearError($('err'));
    if ($('images').children.length + files.length > MAX_IMAGES) {
        showError($('err'), new Error(`이미지는 최대 ${MAX_IMAGES}장이다`));
        return;
    }
    try {
        for (const file of files) {
            // presign → S3 직행(§1.4). 여기서 나온 키가 그대로 imageKeys 에 들어간다
            const key = await upload(file, 'NOTICE_IMAGE');
            $('images').append(imageItem(key, URL.createObjectURL(file)));
            markDirty();
        }
    } catch (e) {
        showError($('err'), e);
    }
});

$('images').addEventListener('click', (e) => {
    if (!e.target.classList.contains('del')) return;
    e.target.closest('li').remove();
    markDirty();
});

// ── 순서 드래그 ────────────────────────────────────────────────────────────

$('images').addEventListener('dragstart', (e) => {
    dragging = e.target.closest('li');
    orderBeforeDrag = keys().join();
    e.dataTransfer.effectAllowed = 'move';
});

$('images').addEventListener('dragover', (e) => {
    e.preventDefault();
    const over = e.target.closest('li');
    if (!over || !dragging || over === dragging) return;
    const draggingIsAfter = over.compareDocumentPosition(dragging) & Node.DOCUMENT_POSITION_FOLLOWING;
    $('images').insertBefore(dragging, draggingIsAfter ? over : over.nextSibling);
});

$('images').addEventListener('drop', (e) => e.preventDefault());

$('images').addEventListener('dragend', () => {
    dragging = null;
    // 제자리로 돌아온 드래그까지 dirty 로 치면 멀쩡한 이미지 행을 지웠다 다시 만든다
    if (keys().join() !== orderBeforeDrag) markDirty();
});

// ── 저장 ───────────────────────────────────────────────────────────────────

$('save').addEventListener('click', async () => {
    clearError($('err'));
    $('save').disabled = true;
    const body = {content: $('content').value};
    if (imagesDirty) body.imageKeys = keys();
    try {
        await api(`/notices/${noticeSeq}`, {method: 'PATCH', body});
        await load();                       // 저장 결과를 상세 재조회로 확인한다
        $('result').className = 'ok';
        $('result').textContent = `저장됨 · ${new Date().toLocaleTimeString('ko-KR')}`;
    } catch (e) {
        showError($('err'), e);             // IMAGE_NOT_UPLOADED 는 data 에 부재 키 목록
        $('result').textContent = '';
    }
    $('save').disabled = false;
});

load();
