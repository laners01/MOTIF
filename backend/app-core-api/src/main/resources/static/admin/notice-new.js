/*
 * 공지 작성 (어드민 명세 v0.5 §3.10.2).
 *
 * 이미지는 presign → S3 직행이고 서버에는 키만 보낸다(§1.4). 등록 API 가 그 키를
 * HEAD 로 확인하므로, 업로드가 끝난 것만 목록에 넣는다 — 실패한 파일을 목록에
 * 남겨 두면 등록에서 IMAGE_NOT_UPLOADED 로 막히고 어느 장이 문제인지 알기 어렵다.
 */

const MAX_IMAGES = 10;      // §3.10.2 0~10장

let dragging = null;

const keys = () => [...$('images').children].map((li) => li.dataset.key);

function renumber() {
    [...$('images').children].forEach((li, i) => {
        li.querySelector('.order').textContent = i;
    });
    $('image-count').textContent = `${$('images').children.length} / ${MAX_IMAGES}장`;
    refresh();
}

function imageItem(key, url) {
    const li = $('image-item').content.firstElementChild.cloneNode(true);
    li.dataset.key = key;
    li.querySelector('img').src = url;
    li.querySelector('.del').addEventListener('click', () => {
        li.remove();
        renumber();
    });
    li.addEventListener('dragstart', () => {
        dragging = li;
    });
    li.addEventListener('dragover', (e) => {
        e.preventDefault();
        if (!dragging || dragging === li) return;
        const rect = li.getBoundingClientRect();
        const after = e.clientY > rect.top + rect.height / 2;
        li.parentNode.insertBefore(dragging, after ? li.nextSibling : li);
    });
    li.addEventListener('dragend', () => {
        dragging = null;
        renumber();
    });
    return li;
}

async function addFiles(files) {
    clearError($('err'));
    if ($('images').children.length + files.length > MAX_IMAGES) {
        showError($('err'), new Error(`이미지는 최대 ${MAX_IMAGES}장이다`));
        return;
    }
    for (const file of files) {
        try {
            // presign → S3 직행(§1.4). 여기서 나온 키가 그대로 imageKeys 에 들어간다
            const key = await upload(file, 'NOTICE_IMAGE');
            $('images').append(imageItem(key, URL.createObjectURL(file)));
            renumber();
        } catch (e) {
            showError($('err'), e);
            return;     // 한 장이 실패하면 멈춘다 — 뒤 장을 계속 올려도 목록이 어긋난다
        }
    }
}

/** 아티스트와 본문이 있어야 등록이 열린다. 이미지는 선택이다(§3.10.2 0~10장). */
function refresh() {
    $('count').textContent = `${$('content').value.length} / 5000자`;
    $('submit').disabled = !($('artist').value && $('content').value.trim());
}

async function submit() {
    clearError($('err'));
    $('submit').disabled = true;
    try {
        const {noticeSeq} = await api('/notices', {
            method: 'POST',
            body: {
                artistSeq: Number($('artist').value),
                content: $('content').value.trim(),
                imageKeys: keys(),
            },
        });
        $('result').textContent = `공지 ${noticeSeq} 등록됨. 목록으로 이동한다…`;
        location.href = '/admin/notices';
    } catch (e) {
        showError($('err'), e);
        $('submit').disabled = false;
    }
}

$('content').addEventListener('input', refresh);
$('artist').addEventListener('change', refresh);
$('file').addEventListener('change', (e) => {
    addFiles([...e.target.files]);
    e.target.value = '';        // 같은 파일을 다시 고를 수 있게 비운다
});
$('submit').addEventListener('click', submit);

(async () => {
    try {
        await fillSelect($('artist'), '/artists?size=100', 'artistSeq',
                (a) => (a.group ? `${a.name} (${a.group.groupName})` : a.name));
    } catch (e) {
        showError($('err'), e);
    }
    renumber();
})();
