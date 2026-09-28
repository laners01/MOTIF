/*
 * 회원 관리 화면 (어드민 명세 §3.14).
 *
 * 가입 역순 고정이라 정렬 선택이 없다. 상세는 별도 화면이 아니라 행을 펼쳐 보여준다
 * — 신고 화면(reports.js)과 같은 규칙이다.
 */

const SIZE = 20;
let page = 0;
let total = 0;

const ROLE_LABEL = {USER: '일반', ARTIST: '아티스트', ADMIN: '어드민'};
const STATUS_LABEL = {ACTIVE: '활성', SUSPENDED: '정지'};

async function load() {
    clearError($('err'));
    const query = new URLSearchParams({page, size: SIZE});
    if ($('q').value.trim()) query.set('q', $('q').value.trim());
    if ($('user-seq').value) query.set('userSeq', $('user-seq').value);
    if ($('role').value) query.set('role', $('role').value);
    if ($('account-status').value) query.set('accountStatus', $('account-status').value);
    try {
        const data = await api(`/users?${query}`);
        total = data.totalElements ?? 0;
        render(data.list);
        $('page-hint').textContent = data.list.length
                ? `${total}명 중 ${page * SIZE + 1}~${page * SIZE + data.list.length}`
                : '결과 없음';
        $('prev').disabled = page === 0;
        $('next').disabled = (page + 1) * SIZE >= total;
    } catch (e) {
        showError($('err'), e);
    }
}

function render(users) {
    const tbody = $('users');
    tbody.replaceChildren();
    users.forEach((user) => {
        const row = $('user-row').content.cloneNode(true);
        const tr = row.querySelector('tr');
        tr.querySelector('.seq').textContent = user.userSeq;
        tr.querySelector('.nickname').textContent = user.nickname;
        tr.querySelector('.role').textContent = ROLE_LABEL[user.role] ?? user.role;
        tr.querySelector('.reg').textContent = (user.regDttm ?? '').slice(0, 10);

        if (user.profileImageUrl) {
            const img = document.createElement('img');
            img.src = user.profileImageUrl;
            img.alt = '';
            img.height = 28;
            tr.querySelector('.avatar').append(img);
        }

        // 계정 상태와 탈퇴는 다른 축이다 — 탈퇴한 회원도 정지 상태일 수 있다
        tr.querySelector('.status').textContent =
                [STATUS_LABEL[user.accountStatus] ?? (user.accountStatus ?? '계정 없음'),
                    user.isDeleted ? '탈퇴' : null].filter(Boolean).join(' · ');

        const msg = tr.querySelector('.msg');
        const suspend = tr.querySelector('.suspend');
        const unsuspend = tr.querySelector('.unsuspend');
        // 아티스트·어드민은 user_accounts 행이 없어 서버가 400 을 준다. 눌러 보고 알 일이 아니다
        const targetable = user.role === 'USER';
        suspend.disabled = !targetable || user.accountStatus === 'SUSPENDED';
        unsuspend.disabled = !targetable || user.accountStatus !== 'SUSPENDED';
        if (!targetable) msg.textContent = '정지 대상 아님';

        tr.querySelector('.detail').addEventListener('click', () => toggleDetail(tr, user));
        suspend.addEventListener('click', () => suspendUser(user, msg));
        unsuspend.addEventListener('click', () => unsuspendUser(user, msg));
        tbody.append(row);
    });
}

/** 상세를 아래 행으로 펼친다. 다시 누르면 접는다. */
async function toggleDetail(tr, user) {
    const next = tr.nextElementSibling;
    if (next && next.classList.contains('detail-panel')) {
        next.remove();
        return;
    }
    const row = $('detail-row').content.cloneNode(true);
    const box = row.querySelector('.user-detail');
    box.textContent = '불러오는 중…';
    tr.after(row);
    // 위에서 after 로 붙인 실제 노드를 다시 잡는다 — cloneNode 조각은 삽입 후 비어 있다
    const panel = tr.nextElementSibling.querySelector('.user-detail');

    try {
        const detail = await api(`/users/${user.userSeq}`);
        panel.replaceChildren();

        const stats = document.createElement('p');
        stats.className = 'muted';
        stats.textContent = [
            `영상 ${detail.stats.videoCount}`,
            `구독 ${detail.stats.subscriptionCount}`,
            `보유 에셋 ${detail.stats.activePurchaseCount}`,
            `패스 ${detail.stats.activePassCount}`,
            `피신고 ${detail.stats.reportedCount}`,
            `신고 ${detail.stats.reportCount}`,
        ].join(' · ');
        panel.append(stats);

        const passes = document.createElement('p');
        passes.textContent = detail.activePasses.length
                ? '패스: ' + detail.activePasses
                        .map((p) => `${p.artistName} (~${(p.expiresDttm ?? '').slice(0, 10)})`)
                        .join(', ')
                : '보유 중인 패스 없음';
        panel.append(passes);
    } catch (e) {
        panel.textContent = `${e.code ?? ''} ${e.message}`;
    }
}

async function suspendUser(user, msg) {
    // 사유는 저장되지 않는다(§3.14.3) — 감사 로그에만 남으므로 화면이 그 사실을 말해 준다
    const reason = prompt(
            `#${user.userSeq} ${user.nickname} 을(를) 정지한다.\n`
            + '모든 기기의 refresh 토큰이 폐기되어 재로그인이 막힌다.\n\n'
            + '사유 (저장되지 않고 감사 로그에만 남는다)');
    if (reason === null) return;
    if (!reason.trim()) {
        msg.textContent = '사유는 비울 수 없다';
        return;
    }

    msg.textContent = '정지 중…';
    try {
        const result = await api(`/users/${user.userSeq}/suspend`, {
            method: 'POST',
            body: {reason: reason.trim()},
        });
        // 결과를 행에 쓰면 곧바로 이어지는 load() 가 그 행을 새로 그려 지워 버린다.
        // 폐기 건수는 남아야 하는 정보라 목록이 다시 그려진 뒤 요약 줄에 붙인다
        await load();
        $('page-hint').textContent += ` · #${user.userSeq} 정지 완료 (토큰 ${result.revokedTokenCount}건 폐기)`;
    } catch (e) {
        msg.textContent = `${e.code ?? ''} ${e.message}`;
    }
}

async function unsuspendUser(user, msg) {
    if (!confirm(`#${user.userSeq} ${user.nickname} 의 정지를 해제한다.\n`
            + '폐기된 토큰은 복구되지 않는다 — 회원이 다시 로그인해야 한다.')) return;

    msg.textContent = '해제 중…';
    try {
        await api(`/users/${user.userSeq}/unsuspend`, {method: 'POST'});
        await load();
        $('page-hint').textContent += ` · #${user.userSeq} 해제 완료`;
    } catch (e) {
        msg.textContent = `${e.code ?? ''} ${e.message}`;
    }
}

$('reload').addEventListener('click', () => {
    page = 0;
    load();
});
$('q').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        page = 0;
        load();
    }
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
