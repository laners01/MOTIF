/*
 * 신고 처리 화면 (어드민 명세 §3.13).
 *
 * 접수순 고정이라 정렬 선택이 없다 — 오래 방치된 것이 먼저 와야 한다.
 * 상세는 별도 화면이 아니라 행을 펼쳐 보여준다.
 */

const SIZE = 20;
let page = 0;
let total = 0;

const TARGET_LABEL = {
    VIDEO: '참여 영상',
    CHALLENGE_COMMENT: '챌린지 댓글',
    VIDEO_COMMENT: '영상 댓글',
    NOTICE_COMMENT: '공지 댓글',
};
const STATUS_LABEL = {RECEIVED: '접수', ACCEPTED: '인용', REJECTED: '기각'};

async function load() {
    clearError($('err'));
    const query = new URLSearchParams({page, size: SIZE});
    if ($('status').value) query.set('status', $('status').value);
    if ($('target-type').value) query.set('targetType', $('target-type').value);
    try {
        const data = await api(`/reports?${query}`);
        total = data.totalElements ?? 0;
        render(data.list);
        $('page-hint').textContent = `${total}건 중 ${page * SIZE + 1}~${page * SIZE + data.list.length}`;
        $('prev').disabled = page === 0;
        $('next').disabled = (page + 1) * SIZE >= total;
    } catch (e) {
        showError($('err'), e);
    }
}

function render(reports) {
    const tbody = $('reports');
    tbody.replaceChildren();
    reports.forEach((report) => {
        const row = $('report-row').content.cloneNode(true);
        const tr = row.querySelector('tr');
        tr.querySelector('.seq').textContent = report.reportSeq;
        tr.querySelector('.target').textContent = TARGET_LABEL[report.targetType] ?? report.targetType;
        tr.querySelector('.reason').textContent = report.reasonCode;
        tr.querySelector('.reporter').textContent =
                `${report.reporter.nickname ?? '(탈퇴)'} (#${report.reporter.userSeq})`;
        tr.querySelector('.same-count').textContent = report.sameTargetReportCount;
        tr.querySelector('.status').textContent = STATUS_LABEL[report.status] ?? report.status;
        tr.querySelector('.reg').textContent = (report.regDttm ?? '').slice(0, 10);

        const msg = tr.querySelector('.msg');
        const done = report.status !== 'RECEIVED';
        // 처리된 신고는 버튼을 막는다 — 누르면 409 가 오지만 눌러 보고 알 일이 아니다
        tr.querySelector('.accept').disabled = done;
        tr.querySelector('.reject').disabled = done;
        if (done) {
            msg.textContent = `처리자 #${report.processedBy ?? '-'} · ${(report.processedDttm ?? '').slice(0, 10)}`;
        }
        // 대상 미리보기가 없으면 §9 미구현이라 인용해도 할 일이 없다
        if (!report.target) {
            tr.querySelector('.detail').disabled = true;
            tr.querySelector('.accept').disabled = true;
            if (!done) msg.textContent = '대상 미리보기 미지원 — §9 댓글 구현 후';
        }

        tr.querySelector('.detail').addEventListener('click', () => togglePreview(tr, report));
        tr.querySelector('.accept').addEventListener('click', () => process(report, 'ACCEPTED', msg));
        tr.querySelector('.reject').addEventListener('click', () => process(report, 'REJECTED', msg));
        tbody.append(row);
    });
}

/** 상세를 아래 행으로 펼친다. 다시 누르면 접는다. */
function togglePreview(tr, report) {
    const next = tr.nextElementSibling;
    if (next && next.classList.contains('preview')) {
        next.remove();
        return;
    }
    const row = $('preview-row').content.cloneNode(true);
    const box = row.querySelector('.target-preview');
    const target = report.target;
    box.replaceChildren();

    const meta = document.createElement('p');
    meta.className = 'muted';
    meta.textContent = [
        `영상 #${target.videoSeq}`,
        `작성자 ${target.author?.nickname ?? '?'} (#${target.author?.userSeq ?? '?'})`,
        target.visibility,
        target.isDeleted ? '이미 삭제됨' : '노출 중',
        `신고 ${target.reportCount}건`,
    ].join(' · ');
    box.append(meta);

    if (target.thumbnailUrl) {
        const img = document.createElement('img');
        img.src = target.thumbnailUrl;
        img.alt = '';
        img.height = 160;
        box.append(img);
    }
    // 재생 주소는 보호 등급이면 core-api 재생목록이라 <video> 가 못 연다(§1.7).
    // 링크로 준다 — 어드민이 필요하면 직접 확인한다
    if (target.videoUrl) {
        const link = document.createElement('a');
        link.href = target.videoUrl;
        link.textContent = ' 재생 주소 열기';
        link.target = '_blank';
        link.rel = 'noreferrer';
        box.append(link);
    }
    const desc = document.createElement('p');
    desc.textContent = target.description ?? '';
    box.append(desc);

    tr.after(row);
}

async function process(report, decision, msg) {
    const label = decision === 'ACCEPTED' ? '인용' : '기각';
    const warn = decision === 'ACCEPTED'
            ? `신고 #${report.reportSeq} 을 인용한다. 대상 영상이 삭제되고 되돌릴 수 없다.`
            : `신고 #${report.reportSeq} 을 기각한다.`;
    if (!confirm(warn)) return;

    msg.textContent = `${label} 중…`;
    try {
        const result = await api(`/reports/${report.reportSeq}/process`, {
            method: 'POST',
            body: {decision},
        });
        // 응답의 실감소분을 그대로 보여준다 — 멱등이라 0 이 나올 수 있고,
        // 그게 "이미 지워져 있었다" 는 뜻이다
        const delta = result.sideEffects?.participantCountDelta ?? 0;
        msg.textContent = `${label} 완료`
                + (result.targetDeleted ? ' · 대상 삭제됨' : '')
                + (delta !== 0 ? ` · 참여 수 ${delta}` : '');
        await load();
    } catch (e) {
        msg.textContent = `${e.code ?? ''} ${e.message}`;
    }
}

$('reload').addEventListener('click', () => {
    page = 0;
    load();
});
$('prev').addEventListener('click', () => {
    page -= 1;
    load();
});
$('next').addEventListener('click', () => {
    page += 1;
    load();
});
load();
