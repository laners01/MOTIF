# 부록 — SCM 폴링에서 GitLab 웹훅으로 전환

`infra/setup/jenkins-setup.md` 로 구성한 파이프라인은 SCM 폴링(1분)으로
움직인다. 이 문서는 그것을 웹훅으로 바꾸는 절차다.

**아직 실행하지 말 것.** 전제 조건이 갖춰지기 전에는 폴링이 정답이다.

## 왜 지금은 폴링인가

프로젝트 GitLab 권한이 **Developer** 다. 웹훅 등록
(Settings > Webhooks)은 **Maintainer 이상**만 할 수 있다. 즉 지금은
기술적 선택이 아니라 권한 문제다.

폴링의 비용은 두 가지다.

- 최대 1분 지연. Phase 0 에서는 문제가 되지 않는다.
- 1분마다 `git ls-remote` 가 나간다. 커밋이 없으면 빌드도 없으므로 부하는 무시할 만하다.

## 전제 조건

- [ ] GitLab 프로젝트 권한이 **Maintainer 이상**
- [ ] `jenkins-setup.md` 의 파이프라인이 폴링으로 정상 동작 중
      (동작하지 않는 파이프라인을 웹훅으로 바꾸면 원인이 둘로 늘어난다)

---

## 1. 진짜 문제 — GitLab 이 Jenkins 에 닿아야 한다

폴링은 Jenkins 가 밖으로 나가는 방향이라 방화벽을 건드릴 일이 없었다.
웹훅은 방향이 반대다. `lab.ssafy.com` 이 인터넷에서 우리 서버로 들어와야 한다.

그런데 Jenkins 는 `127.0.0.1:8080` 에 묶여 있고 ufw 는 22/80/443 만 연다
(`jenkins-setup.md` §4). **이 설계를 깨지 않는 것이 이 전환의 핵심이다.**

인증 없는 Jenkins UI 가 인터넷에 노출되면 서버 전체를 내주는 것과 같다.
그래서 8080 을 열지 않는다. 대신 **이미 열려 있는 443(Traefik)으로 들어와
웹훅 경로 하나만 Jenkins 로 보낸다.**

```
인터넷(GitLab)
  -> 443 Traefik
     -> /project/*  만  ->  호스트 Jenkins 8080
     -> 그 외 경로  ->  기존대로 core-api
UI(/, /manage 등)는 여전히 밖에서 접근 불가 — SSH 터널로만.
```

## 2. Jenkins 바인딩 주소 변경

Traefik 은 파드 안에서 돈다. 호스트의 `127.0.0.1` 에는 닿을 수 없다.
파드에서 닿을 수 있는 주소, 즉 노드의 테일넷 IP 로 바꾼다
(k3s 가 `--flannel-iface=tailscale0` 로 설치돼 있어 이 IP 가 노드 내부 IP 다).

```bash
sudo bash -c 'printf "[Service]\nEnvironment=\"JENKINS_LISTEN_ADDRESS=100.106.64.43\"\nEnvironment=\"JAVA_OPTS=-Xmx1g -Djava.awt.headless=true\"\nAfter=tailscaled.service\nWants=tailscaled.service\n" > /etc/systemd/system/jenkins.service.d/override.conf'
```

`After`/`Wants` 를 반드시 같이 넣는다. tailscaled 보다 Jenkins 가 먼저 뜨면
바인딩할 IP 가 없어 서비스가 기동에 실패한다.

```bash
sudo systemctl daemon-reload && sudo systemctl restart jenkins
```

```bash
sudo ss -lntp | grep 8080
```

**여기서 반드시 확인할 것** — 파드에서 이 주소에 닿는가. 닿지 않으면
3절의 매니페스트를 아무리 잘 써도 502 만 본다.

```bash
kubectl run curl-probe --rm -it --image=curlimages/curl:8.11.1 --restart=Never -- curl -sS -o /dev/null -w '%{http_code}\n' http://100.106.64.43:8080/login
```

`200` 또는 `403` 이면 통한 것이다. 타임아웃이면 여기서 멈추고 원인부터 찾는다.

> UI 접속은 계속 SSH 터널을 쓴다. 바인딩이 테일넷으로 바뀌었으므로
> 팀원은 Tailscale 조인 후 `http://100.106.64.43:8080` 으로 직접 붙어도 된다.
> 어느 쪽이든 **공인 IP 로는 열리지 않는다** — ufw 가 8080 을 막고 있다.

## 3. Traefik 라우팅 — 웹훅 경로만

셀렉터 없는 Service 와 수동 EndpointSlice 로 클러스터 밖(호스트 프로세스)을
가리킨다. 셀렉터를 비워야 쿠버네티스가 엔드포인트를 자동으로 덮어쓰지 않는다.

아래를 `infra/k8s/50-jenkins-webhook/webhook-route.yaml` 로 만든 뒤 apply 한다.
**전환을 실행하는 시점에 파일을 만든다** — 지금 커밋해두면 아직 쓰지 않는
경로가 열려 있는 것처럼 보인다.

```yaml
# Jenkins 웹훅 수신 경로만 Traefik 으로 외부에 연다.
# UI(/, /manage 등)는 열지 않는다 — 인증 없는 Jenkins 노출은 서버 전체 유출과 같다.
#
# 셀렉터 없는 Service + 수동 EndpointSlice = 클러스터 밖 호스트 프로세스 지향.
# 셀렉터를 넣으면 쿠버네티스가 엔드포인트를 자동 관리하며 아래 주소를 지워버린다.
apiVersion: v1
kind: Service
metadata:
  name: jenkins-host
spec:
  ports:
    - name: http
      port: 8080
      targetPort: 8080
---
apiVersion: discovery.k8s.io/v1
kind: EndpointSlice
metadata:
  name: jenkins-host
  labels:
    # 이 라벨이 Service 와 EndpointSlice 를 잇는다. 틀리면 조용히 502 다.
    kubernetes.io/service-name: jenkins-host
addressType: IPv4
ports:
  - name: http
    port: 8080
endpoints:
  # 노드의 테일넷 IP. Jenkins 가 여기에 바인딩돼 있어야 한다(§2).
  - addresses:
      - 100.106.64.43
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: jenkins-webhook
  annotations:
    traefik.ingress.kubernetes.io/router.middlewares: default-redirect-https@kubernetescrd
spec:
  ingressClassName: traefik
  tls:
    - hosts:
        - j15d104.p.ssafy.io
      secretName: motif-tls
  rules:
    - host: j15d104.p.ssafy.io
      http:
        paths:
          # GitLab Plugin 의 수신 엔드포인트는 /project/<job 이름> 이다.
          # 이 프리픽스 하나만 연다. /login, /manage 는 열리지 않는다.
          - path: /project/
            pathType: Prefix
            backend:
              service:
                name: jenkins-host
                port:
                  number: 8080
```

이 경로는 인터넷에 열린다. 실제 방어선은 4~5절의 **Secret Token** 이다.
토큰이 없거나 틀리면 Jenkins 가 요청을 버린다.

> 더 조이고 싶으면 Traefik IPAllowList 미들웨어로 GitLab 출발지 IP 만
> 허용한다. IP 는 아래로 확인하되, 바뀔 수 있으므로 고정하면 어느 날
> 조용히 웹훅이 끊긴다. 토큰만으로 충분하다고 보면 넘어간다.
>
> ```bash
> dig +short lab.ssafy.com
> ```

## 4. Jenkins 쪽 설정

### 4-1. 플러그인 설치

**Manage Jenkins > Plugins > Available** 에서 `GitLab` 설치 후 재시작.

### 4-2. job 트리거 설정

`motif-core-api` > **Configure > Build Triggers**

- [x] **Build when a change is pushed to GitLab**
  - 표시되는 웹훅 URL 을 적어둔다. `http://100.106.64.43:8080/project/motif-core-api`
    형태다. **GitLab 에 넣을 때는 호스트를 공개 주소로 바꾼다** →
    `https://j15d104.p.ssafy.io/project/motif-core-api`
  - Enabled GitLab triggers: **Push Events** 만 체크
  - **Advanced > Allowed branches > Filter branches by name**
    - Include: `develop/backend`
  - **Advanced > Secret token > Generate** → 생성된 값을 복사해
    팀 비밀 저장소에 `<Jenkins GitLab 웹훅 Secret Token>` 으로 저장

Secret Token 을 Jenkinsfile 에 적을 수 없어 이 설정은 UI 에 남는다.
파이프라인 코드에 넣으면 비밀값을 repo 에 커밋하는 셈이 된다.
브랜치 필터도 여기서 잡는다.

## 5. GitLab 쪽 설정

프로젝트 > **Settings > Webhooks > Add new webhook**

| 필드 | 값 |
|---|---|
| URL | `https://j15d104.p.ssafy.io/project/motif-core-api` |
| Secret token | `<팀 비밀 저장소 참조: Jenkins GitLab 웹훅 Secret Token>` |
| Trigger | **Push events** 만 체크 |
| Branch name or wildcard | `develop/backend` |
| Enable SSL verification | 체크 (Let's Encrypt 정식 인증서라 통과한다) |

저장 후 하단 **Test > Push events** 를 눌러 `HTTP 200` 을 확인한다.

| 응답 | 원인 |
|---|---|
| `403` | Secret Token 불일치 |
| `404` | URL 의 job 이름이 틀림 |
| `502` / 타임아웃 | §2 의 파드→호스트 도달 실패, 또는 EndpointSlice 라벨 오타 |

## 6. 폴링 트리거 제거

여기까지 통과한 **뒤에** 한다. 먼저 지우면 웹훅이 안 될 때 아무 트리거도
남지 않는다.

`backend/Jenkinsfile` 에서 아래 블록을 지운다.

```groovy
triggers {
    pollSCM('H/1 * * * *')
}
```

주석도 함께 고친다 — "트리거는 SCM 폴링이다" 로 시작하는 파일 상단 문단이
더 이상 사실이 아니게 된다.

이 변경은 `backend/**` 에 속하므로 `develop/backend` 로 MR 을 낸다. 그리고
그 MR 의 머지 자체가 웹훅 동작의 첫 검증이 된다.

## 7. 검증

- [ ] `develop/backend` 에 빈 커밋 push → **1분을 기다리지 않고 즉시** 빌드 시작
      (`git commit --allow-empty -m "chore: 웹훅 검증"`)
- [ ] 빌드 원인이 폴링이 아닌지 — 빌드 페이지에 `Started by GitLab push` 표시
- [ ] `develop/infra` 에 push 했을 때는 빌드가 **트리거되지 않는지**
      (4-2 의 브랜치 필터)
- [ ] GitLab > Settings > Webhooks > **Edit > Recent Deliveries** 가 200
- [ ] 웹훅 URL 의 UI 경로가 여전히 막혀 있는지 — 404 나 연결 거부여야 한다.
      **200 이 나오면 즉시 중단하고 3절 Ingress 의 path 를 다시 본다.**
      `curl -s -o /dev/null -w '%{http_code}\n' https://j15d104.p.ssafy.io/login`
- [ ] `/api/health` 가 여전히 200 (새 Ingress 가 기존 라우팅을 건드리지 않았는지)

## 8. 되돌리기

웹훅이 말썽이면 폴링으로 즉시 복귀할 수 있다. 순서대로.

```bash
kubectl delete -f infra/k8s/50-jenkins-webhook/webhook-route.yaml
```

```bash
sudo bash -c 'printf "[Service]\nEnvironment=\"JENKINS_LISTEN_ADDRESS=127.0.0.1\"\nEnvironment=\"JAVA_OPTS=-Xmx1g -Djava.awt.headless=true\"\n" > /etc/systemd/system/jenkins.service.d/override.conf'
```

```bash
sudo systemctl daemon-reload && sudo systemctl restart jenkins
```

그리고 `backend/Jenkinsfile` 에 `triggers { pollSCM('H/1 * * * *') }` 를
되돌리는 MR 을 낸다. GitLab 쪽 웹훅은 지우지 않고 **Disable** 만 해두면
재시도할 때 토큰을 다시 만들지 않아도 된다.
