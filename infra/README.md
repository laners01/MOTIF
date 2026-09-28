# MOTIF 인프라

k8s 매니페스트와 서버 셋업 절차. 매니페스트는 여기가 원본이며,
서버에서 직접 `kubectl create/edit` 하지 않고 이 파일들을 고쳐 apply 한다.

## 환경

| 항목 | 값 |
|---|---|
| 서버 | SSAFY 지급 EC2 — Ubuntu 24.04, 15Gi RAM, 4 vCPU, 309GB |
| 도메인 | `j15d104.p.ssafy.io` (공인 IP `3.36.61.156`) |
| 테일넷 IP | `100.106.64.43` (호스트명 `motif-server`) |
| 오케스트레이션 | k3s v1.36.4 (단일 노드, Traefik 내장) |
| 개방 포트 | 22 / 80 / 443 (ufw) |

웹 콘솔이 제공되지 않으므로 방화벽은 ufw로만 제어한다.
**22번 포트는 건드리지 않는다** — 잠기면 복구 불가, 초기화만 가능하다.

## 디렉터리

```
infra/
├── k8s/
│   ├── 00-cert-manager/   ClusterIssuer (Let's Encrypt staging/prod)
│   ├── 10-postgres/       PostgreSQL 16 + 스키마·계정 격리
│   ├── 20-rabbitmq/       RabbitMQ 3.13 + vhost·계정 격리
│   ├── 30-redis/          Redis 7 (무영속)
│   ├── 40-core-api/       core-api 배포 + /api Ingress + /admin Ingress(IP 제한)
│   └── 90-ingress/        Traefik Ingress, HTTPS 리다이렉트, /admin ipAllowList
└── setup/                 서버 셋업 절차서 (사람이 한 줄씩 실행)
```

접두 번호는 적용 순서다.

## 셋업 절차

### 1. Tailscale

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up --hostname=motif-server
```

조인 후 관리 콘솔 → Machines → **Disable key expiry**.
해두지 않으면 180일 뒤 노드가 테일넷에서 떨어진다.

팀원도 각자 기기를 같은 계정으로 조인해야 DB·큐 접근이 가능하다.

#### 어드민(`/admin`) 접근 — 기기마다 hosts 한 줄

`j15d104.p.ssafy.io` 는 **공인 IP 로 풀린다**. 테일스케일에 붙어 있어도 이 도메인으로
가면 트래픽이 공인망으로 나가 출발지가 각자의 공인 IP 가 되고, 앱의 `AdminIpFilter`
가 404 를 낸다(`k8s/40-core-api/configmap.yaml` 의 `ADMIN_ALLOWED_CIDRS`). 테일넷에
조인한 것만으로는 아무것도 달라지지 않는다.

그래서 도메인을 노드의 테일넷 IP 로 덮어쓴다. Ingress 규칙이 `host: j15d104.p.ssafy.io`
라 **IP 로 직접 치면 라우터가 안 잡혀 404 다** — Host·SNI 가 맞아야 하므로 hosts 여야 한다.
TLS 는 공인 도메인과 같은 인증서를 그대로 쓴다(경고 없음).

Windows — **관리자 권한** 터미널에서:

```powershell
Add-Content C:\Windows\System32\drivers\etc\hosts "`n100.106.64.43 j15d104.p.ssafy.io"
ipconfig /flushdns
```

macOS / Linux:

```bash
echo '100.106.64.43 j15d104.p.ssafy.io' | sudo tee -a /etc/hosts
sudo dscacheutil -flushcache; sudo killall -HUP mDNSResponder   # macOS 만
```

확인: `curl -s -o /dev/null -w '%{http_code}\n' https://j15d104.p.ssafy.io/admin/login`
→ `200`. 공인망에서는 같은 주소가 `404` 다(정상).

**curl 은 되는데 브라우저만 안 되면** Chrome 의 보안 DNS(DoH)다 — 켜져 있으면 시스템
리졸버를 건너뛰어 hosts 를 무시한다. `chrome://settings/security` 에서 끄고
`chrome://net-internals/#dns` 에서 Clear host cache.

**테일넷에서 나가기 전에 이 줄을 지운다** — 남아 있으면 사이트 전체가 안 열린다.

### 2. k3s

```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--flannel-iface=tailscale0 --write-kubeconfig-mode=644" sh -
```

- `--flannel-iface=tailscale0` — 노드 간 통신을 테일넷으로 강제. EC2 워커 조인 시 필요.
- `--write-kubeconfig-mode=644` — sudo 없이 kubectl 사용.

`--disable=servicelb`를 넣으면 안 된다. Traefik이 호스트 80/443을 잡는 것은
servicelb(klipper-lb)가 하는 일이라, 끄면 EXTERNAL-IP가 pending에 머물고
외부에서 접속되지 않는다.

확인:

```bash
kubectl get nodes -o wide    # INTERNAL-IP가 100.106.64.43 인지
```

### 3. cert-manager

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.2/cert-manager.yaml
kubectl get pods -n cert-manager   # 3개 모두 Running 확인 후 다음 단계
kubectl apply -f infra/k8s/00-cert-manager/
```

webhook이 Ready 되기 전에 ClusterIssuer를 만들면 실패한다.

### 4. Ingress

```bash
kubectl apply -f infra/k8s/90-ingress/
kubectl get certificate            # motif-tls READY=True 대기 (1~2분)
curl -v https://j15d104.p.ssafy.io/
```

발급 설정을 바꿀 때는 `letsencrypt-staging`으로 먼저 검증한다.
production은 발급 실패가 주당 5회로 제한된다.

### 5. PostgreSQL

Secret을 먼저 만든다. **값은 팀 비밀 저장소 참조** — 커밋 금지.

```bash
kubectl create secret generic postgres-secret --from-literal=POSTGRES_PASSWORD='...' --from-literal=CORE_APP_PASSWORD='...' --from-literal=NOTIF_APP_PASSWORD='...'
```

```bash
kubectl apply -f infra/k8s/10-postgres/
kubectl logs -l app=postgres --tail=30
```

로그에 `database system is ready to accept connections`가 나오면 성공.

초기화 스크립트는 **PVC가 비어 있을 때만** 실행된다. 스키마 구성을 고쳤다면
PVC를 지우고 다시 만들어야 반영된다(데이터도 함께 사라진다).

격리 검증:

```bash
kubectl exec -it deploy/postgres -- psql -U notif_app -d motif -c "CREATE TABLE motif_core.x(i int);"
# permission denied for schema motif_core  <- 정답
```

## 수동 관리 항목 (커밋하지 않음)

| 항목 | 위치 |
|---|---|
| `postgres-secret` | 서버에서 `kubectl create secret`, 값은 팀 비밀 저장소 |
| SSH 키 (`*.pem`) | 팀 비밀 저장소 |
| `core-api-secret` | 서버에서 `kubectl create secret`, 값은 `k8s/40-core-api/secret.example.yaml.txt` 참조 |
| GitLab PAT (Jenkins) | Jenkins Credentials `gitlab-pat`, 값은 팀 비밀 저장소 |
| k3s 설치 옵션 | 이 문서 2절 |

## 미완 / 후속

- [ ] 팀원 Tailscale 조인, key expiry 해제
- [x] `/admin` 접근 제한 — klipper-lb SNAT 확인 후 Traefik hostPort 직결로 원 IP 를
      보존하고, 제한은 앱 `AdminIpFilter` 가 404 로 낸다(403 은 어드민 존재를 드러내
      §1.1 위반). `ipAllowList` 미들웨어는 어느 라우터에도 걸지 않는다 — 파일 헤더 참조.
      팀원 접근은 위 1절의 hosts 한 줄
- [ ] Postgres 테일넷 노출 (팀원 DB 툴 접속용)
- [ ] `pg_dump` → S3 백업 크론 (RDS 미사용에 따른 대체)
- [x] RabbitMQ + Redis 배포
- [x] Docker + Jenkins 파이프라인 — `setup/jenkins-setup.md` (절차서 실행은 사람 몫)
- [ ] 트리거를 폴링에서 웹훅으로 — `setup/webhook-migration.md` (GitLab Maintainer 확보 후)
- [ ] EC2 워커 노드 조인 (`workload=media`)
- [ ] 미사용 RDS 인스턴스 삭제
