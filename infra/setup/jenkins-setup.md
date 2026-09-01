# Jenkins CI/CD 셋업 절차

서버에서 한 줄씩 실행한다. **명령을 여러 줄로 나누지 않는다** — 역슬래시 뒤
공백 하나로 줄이 끊겨 반쪽만 실행된 사고가 있었다. 붙여넣기가 불안하면
한 줄씩 확인하고 넘어간다.

`<...>` 는 자리표시자다. 실제 값은 팀 비밀 저장소를 참조하며 이 문서에 적지 않는다.

관련: ADR-0007(레지스트리 없음), ADR-0008(레이어드 jar),
`backend/Jenkinsfile`, `infra/k8s/40-core-api/`, Jira S15P21D104-181

## 이 문서가 만드는 것

```
커밋 push (develop/backend)
  -> Jenkins SCM 폴링(1분)
  -> Gradle bootJar + test          (호스트에서)
  -> docker build                    (레이어드 jar 전개만)
  -> docker save | k3s ctr images import   (레지스트리 없음)
  -> kubectl set image + rollout status
  -> https://j15d104.p.ssafy.io/api/health 200
  -> 실패 시 rollout undo 후 빌드 FAIL
```

## 사전 상태 (2026-09-02 실측)

| 항목 | 값 |
|---|---|
| OS / 아키텍처 | Ubuntu 24.04.4, amd64, 4 vCPU |
| 메모리 | 15Gi (가용 13Gi), **스왑 0** |
| 디스크 | 309G 중 305G 여유 |
| k3s | v1.36.4+k3s1, containerd 2.3.4, 바이너리 `/usr/local/bin/k3s` |
| kubeconfig | `/etc/rancher/k3s/k3s.yaml`, 권한 `644 root:root` |
| 방화벽 | ufw active — 22 / 80 / 443 만 개방 |
| 테일넷 | `100.106.64.43` |
| 기존 Ingress | `hello` 하나가 `/` 를 점유 |
| 인증서 | `motif-tls` Certificate READY=True |
| 설치 안 됨 | java, docker, jenkins (8080 포트 비어 있음) |

스왑이 0이라 메모리가 모자라면 스왑으로 버티지 못하고 바로 OOM 킬이다.
아래 메모리 배분은 그 전제에서 잡은 값이다.

| 프로세스 | 상한 |
|---|---|
| Jenkins JVM | 1g |
| Gradle 데몬 | 2g |
| core-api 파드 | 1Gi (배포 중 순간 2Gi) |
| PostgreSQL 파드 | 1.5Gi |
| RabbitMQ / Redis 파드 | 기존 매니페스트 값 |
| 합계 | 약 8Gi — 15Gi 안에 여유 있음 |

---

## 1. JDK 21 설치

`backend/build.gradle` 의 `JavaLanguageVersion.of(21)` 과 맞춘다. 툴체인을
올리는 날 이 절과 `backend/app-core-api/Dockerfile` 의 베이스 태그를 같이 올린다.

```bash
sudo apt-get update
```

```bash
sudo apt-get install -y openjdk-21-jdk-headless
```

확인 — `21.0.x` 가 나와야 한다.

```bash
java -version
```

## 2. Docker 설치

이미지 빌드용이다. k3s 의 런타임은 containerd 이고 도커가 아니다 — 도커는
빌드만 하고, 결과물은 6절의 임포트로 넘긴다.

```bash
sudo apt-get install -y docker.io
```

```bash
docker --version
```

## 3. Jenkins LTS 설치

```bash
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key | sudo tee /usr/share/keyrings/jenkins-keyring.asc > /dev/null
```

```bash
echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" | sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null
```

```bash
sudo apt-get update
```

```bash
sudo apt-get install -y jenkins
```

## 4. Jenkins 를 공인 IP에 노출하지 않는다

ufw 가 8080 을 막고 있어 지금도 밖에서는 보이지 않는다. 그래도 바인딩
주소를 명시한다 — ufw 규칙이 한 번 잘못 열리면 그때는 인증 없는 Jenkins 가
인터넷에 통째로 노출되고, 그건 서버 전체를 내주는 것과 같다.

`127.0.0.1` 로 묶는다. 접속은 SSH 터널로 한다.

```bash
sudo mkdir -p /etc/systemd/system/jenkins.service.d
```

```bash
sudo bash -c 'printf "[Service]\nEnvironment=\"JENKINS_LISTEN_ADDRESS=127.0.0.1\"\nEnvironment=\"JAVA_OPTS=-Xmx1g -Djava.awt.headless=true\"\n" > /etc/systemd/system/jenkins.service.d/override.conf'
```

`-Xmx1g` 는 위 메모리 표의 Jenkins 몫이다.

```bash
sudo systemctl daemon-reload
```

```bash
sudo systemctl restart jenkins
```

`127.0.0.1:8080` 으로만 떠 있는지 확인한다. `0.0.0.0:8080` 이 보이면 4절이
적용되지 않은 것이다.

```bash
sudo ss -lntp | grep 8080
```

**UI 접속** — 맥에서 별도 터미널로 터널을 연 뒤 브라우저로 `http://localhost:8080`.

```bash
ssh -i J15D104T.pem -L 8080:127.0.0.1:8080 ubuntu@j15d104.p.ssafy.io
```

> 팀원 여러 명이 UI를 써야 하면 `JENKINS_LISTEN_ADDRESS` 를 테일넷 IP
> (`100.106.64.43`)로 바꾼다. 다만 그때는 jenkins 서비스가 tailscaled 보다
> 먼저 뜨면 바인딩에 실패해 기동하지 않으므로, override 에
> `After=tailscaled.service` / `Wants=tailscaled.service` 도 같이 넣어야 한다.

## 5. 초기 관리자 계정

초기 비밀번호를 읽는다.

```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

브라우저에서 `http://localhost:8080` 접속 → 위 값 입력 →
**Select plugins to install** 선택(Install suggested plugins 아님) →
6절 목록만 체크 → 관리자 계정 생성.

- 사용자명: `admin`
- 비밀번호: `<팀 비밀 저장소 참조: Jenkins admin 비밀번호>`

## 6. 플러그인 최소셋

| 플러그인 | 용도 |
|---|---|
| Git | SCM 체크아웃·폴링 |
| Pipeline (Pipeline: Declarative 포함) | Jenkinsfile 실행 |
| Credentials Binding | PAT 주입 |

GitLab Plugin 은 지금 넣지 않는다. 웹훅 전환 시점에 필요하며 절차는
`infra/setup/webhook-migration.md` 에 있다.

설치 후 **Manage Jenkins > System > # of executors 를 1** 로 둔다.
동시 빌드가 없어야 Gradle 데몬 하나를 재사용하고, 배포가 직렬로 유지된다.

## 7. Credentials 등록 — GitLab PAT

GitLab 에서 먼저 발급한다. **스코프는 `read_repository` 하나만** 준다.
Jenkins 는 소스를 읽기만 하고 push 하지 않는다.

- GitLab > 우상단 아바타 > Edit profile > Access tokens
- 이름 `jenkins-motif`, 스코프 `read_repository`, 만료일 설정

Jenkins UI: **Manage Jenkins > Credentials > System > Global credentials > Add Credentials**

| 필드 | 값 |
|---|---|
| Kind | Username with password |
| Username | `<GitLab 사용자명>` |
| Password | `<팀 비밀 저장소 참조: GitLab PAT (jenkins-motif)>` |
| ID | `gitlab-pat` |

ID 는 반드시 `gitlab-pat` 이다. 8절의 job 설정이 이 ID 로 찾는다.

> PAT 는 만료된다. 만료되면 폴링이 인증 실패로 조용히 멈추고 커밋이 배포되지
> 않는다. 만료일을 팀 캘린더에 넣어둔다.

## 8. jenkins 계정 권한

### 8-1. docker 그룹

```bash
sudo usermod -aG docker jenkins
```

### 8-2. sudoers — k3s 이미지 명령만

전체 sudo 를 열지 않는다. Jenkins 는 인터넷에서 온 코드를 실행하는 프로세스라
sudo 전체를 주면 그 코드가 곧 root 다. 아래 세 명령만 NOPASSWD 로 연다.

임시 파일로 먼저 쓴다.

```bash
sudo bash -c 'printf "jenkins ALL=(root) NOPASSWD: /usr/local/bin/k3s ctr images import -\njenkins ALL=(root) NOPASSWD: /usr/local/bin/k3s ctr images ls -q\njenkins ALL=(root) NOPASSWD: /usr/local/bin/k3s ctr images rm *\n" > /tmp/jenkins-k3s'
```

문법 검증. **여기서 통과하지 못하면 절대 설치하지 않는다** — 깨진 sudoers 는
`sudo` 전체를 못 쓰게 만들고, 그러면 고칠 방법도 사라진다.

```bash
sudo visudo -cf /tmp/jenkins-k3s
```

```bash
sudo install -m 440 -o root -g root /tmp/jenkins-k3s /etc/sudoers.d/jenkins-k3s
```

```bash
rm /tmp/jenkins-k3s
```

### 8-3. 반영

docker 그룹은 다시 로그인해야 붙는다. Jenkins 는 데몬이므로 재시작한다.

```bash
sudo systemctl restart jenkins
```

검증 — 세 줄 모두 오류 없이 통과해야 한다.

```bash
sudo -u jenkins docker ps
```

```bash
sudo -u jenkins sudo /usr/local/bin/k3s ctr images ls -q | head -3
```

```bash
sudo -u jenkins kubectl get nodes
```

세 번째가 실패하면 9절을 먼저 본다.

## 9. kubectl / kubeconfig

k3s 가 `--write-kubeconfig-mode=644` 로 설치돼 있어 jenkins 가 그냥 읽을 수 있다.
확인한다 — `644` 여야 한다.

```bash
stat -c '%a %U:%G' /etc/rancher/k3s/k3s.yaml
```

k3s 는 `kubectl` 을 심볼릭 링크로 깔아두지만 `KUBECONFIG` 가 필요하다.
jenkins 계정에만 걸어준다.

```bash
sudo -u jenkins bash -c 'echo "export KUBECONFIG=/etc/rancher/k3s/k3s.yaml" >> /var/lib/jenkins/.profile'
```

Jenkins 파이프라인의 `sh` 는 `.profile` 을 읽지 않는다. 확실히 하려면
**Manage Jenkins > System > Global properties > Environment variables** 에
`KUBECONFIG = /etc/rancher/k3s/k3s.yaml` 를 추가한다. 이쪽이 정본이고
위 `.profile` 은 사람이 `sudo -u jenkins` 로 디버깅할 때를 위한 것이다.

## 10. Gradle 데몬 메모리

repo 에 `gradle.properties` 가 없다. 팀원 로컬 빌드까지 바뀌지 않도록
서버의 jenkins 계정에만 설정한다.

```bash
sudo -u jenkins mkdir -p /var/lib/jenkins/.gradle
```

```bash
sudo -u jenkins bash -c 'printf "org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m\norg.gradle.daemon=true\n" > /var/lib/jenkins/.gradle/gradle.properties'
```

`-Xmx2g` 근거는 위 메모리 표다. 데몬을 켜두는 이유는 executor 1 이라 빌드가
직렬이고, 데몬을 재사용해야 매 빌드 JVM 기동을 다시 하지 않기 때문이다.

## 11. Pipeline job 생성

Jenkins UI: **New Item > 이름 `motif-core-api` > Pipeline > OK**

| 항목 | 값 |
|---|---|
| Pipeline > Definition | Pipeline script from SCM |
| SCM | Git |
| Repository URL | `https://lab.ssafy.com/s15-ai-image-sub1/S15P21D104.git` |
| Credentials | `gitlab-pat` |
| **Branch Specifier** | **`*/develop/backend`** |
| Script Path | `backend/Jenkinsfile` |
| Lightweight checkout | 체크 해제 |

**Branch Specifier 를 `*/develop` 으로 두면 안 된다.** 팀은 파트별 develop
라인(`develop/backend`, `develop/infra`, `develop/android`)을 쓰고 단일
`develop` 브랜치는 존재하지 않는다. 잘못 두면 폴링이 아무것도 찾지 못하고
빌드가 영영 트리거되지 않는다.

Lightweight checkout 을 끄는 이유: 켜두면 Jenkinsfile 만 받아오느라 변경
목록이 기록되지 않고, Jenkinsfile 의 `changeset 'backend/**'` 조건이 항상
거짓이 되어 전 단계가 스킵된다.

폴링 주기는 Jenkinsfile 의 `pollSCM('H/1 * * * *')` 가 정하므로 UI 에서
따로 체크하지 않아도 된다. 단 **폴링 트리거는 job 이 한 번 이상 빌드된
뒤에 등록된다** — 최초 1회는 아래 12절에서 수동으로 돌린다.

## 12. 최초 배포 (수동)

여기서부터는 되돌리기가 번거로우니 순서를 지킨다.

### 12-1. Secret 생성

값은 새로 만들지 않는다. 필요한 비밀번호는 **이미 클러스터 안에 있다**
— `postgres-secret.CORE_APP_PASSWORD` 와 `rabbitmq-secret.RABBITMQ_CORE_PASSWORD` 다.
그래서 사람이 값을 찾아 입력하지 않고 클러스터 안에서 그대로 옮긴다.
값이 눈에도 셸 히스토리에도 남지 않고, 오타·개행 혼입 사고가 원천적으로 없다.

```bash
kubectl create secret generic core-api-secret --from-literal=DB_PASSWORD="$(kubectl get secret postgres-secret -o jsonpath='{.data.CORE_APP_PASSWORD}' | base64 -d)" --from-literal=MQ_PASSWORD="$(kubectl get secret rabbitmq-secret -o jsonpath='{.data.RABBITMQ_CORE_PASSWORD}' | base64 -d)"
```

원본과 길이가 같은지만 확인한다. 값 자체는 출력하지 않는다.
두 줄의 숫자가 같아야 한다.

```bash
kubectl get secret core-api-secret -o jsonpath='{.data.DB_PASSWORD}' | base64 -d | wc -c
```

```bash
kubectl get secret postgres-secret -o jsonpath='{.data.CORE_APP_PASSWORD}' | base64 -d | wc -c
```

MQ 쪽도 같은 방식으로 한 번 본다.

```bash
kubectl get secret core-api-secret -o jsonpath='{.data.MQ_PASSWORD}' | base64 -d | wc -c
```

```bash
kubectl get secret rabbitmq-secret -o jsonpath='{.data.RABBITMQ_CORE_PASSWORD}' | base64 -d | wc -c
```

> 원본 Secret 의 비밀번호를 바꾸면 이 Secret 은 **따라 바뀌지 않는다**.
> 회전할 때는 위 `create` 를 `kubectl delete secret core-api-secret` 후 다시
> 실행하고, 파드를 재시작해야 한다(`kubectl rollout restart deployment/core-api`).

### 12-2. 소스 받기

```bash
git clone https://lab.ssafy.com/s15-ai-image-sub1/S15P21D104.git ~/motif
```

```bash
cd ~/motif && git checkout develop/backend
```

### 12-3. 첫 이미지 빌드·임포트

```bash
cd ~/motif/backend && ./gradlew :app-core-api:bootJar
```

```bash
cd ~/motif && docker build -t registry.local/motif/core-api:0 backend/app-core-api
```

```bash
docker save registry.local/motif/core-api:0 | sudo /usr/local/bin/k3s ctr images import -
```

임포트 확인 — 이 줄이 비면 다음 단계가 `ErrImageNeverPull` 로 죽는다.

```bash
sudo /usr/local/bin/k3s ctr images ls -q | grep registry.local/motif/core-api:0
```

태그 `0` 은 부트스트랩용이다. Jenkins 첫 빌드가 `1` 을 만들면서 곧 대체된다.

### 12-4. Deployment / Service apply

`REPLACE_ME` 를 실제 태그로 치환한다. 치환하지 않고 apply 하면 파드가
영원히 뜨지 않는다.

```bash
cd ~/motif && git checkout develop/infra && sed -i 's/core-api:REPLACE_ME/core-api:0/' infra/k8s/40-core-api/deployment.yaml
```

> 주의: 이 `sed` 는 작업 사본만 고친다. **커밋하지 않는다.** repo 의 정본은
> `REPLACE_ME` 로 남아야 한다 — 그래야 나중에 이 파일을 다시 apply 할 때
> 운영 태그를 덮어쓰는 사고를 사람이 알아챈다.

```bash
kubectl apply -f infra/k8s/40-core-api/deployment.yaml
```

파드가 Running / READY 1/1 이 될 때까지 본다. 기동 시 Liquibase 가 DB 에
붙으므로 30초 안팎 걸린다.

```bash
kubectl get pods -l app=core-api -w
```

안 뜨면 로그부터 본다.

```bash
kubectl logs -l app=core-api --tail=50
```

Ingress 전에 클러스터 내부에서 먼저 확인한다.

```bash
kubectl run curl-probe --rm -it --image=curlimages/curl:8.11.1 --restart=Never -- curl -sf http://core-api:8081/api/health
```

`{"status":"UP"}` 가 나와야 다음으로 간다.

### 12-5. Ingress 전환 (hello 교체)

`/api` 만 가져가므로 hello(`/`)와 잠시 공존한다. **올리고 나서 지운다** —
반대로 하면 실패했을 때 되돌릴 자리가 없다.

```bash
kubectl apply -f infra/k8s/40-core-api/ingress.yaml
```

```bash
curl -sf https://j15d104.p.ssafy.io/api/health
```

`{"status":"UP"}` 를 확인한 **뒤에** hello Ingress 를 지운다.

```bash
kubectl delete ingress hello
```

인증서가 살아 있는지 즉시 확인한다. `motif-tls` Certificate 의 ownerRef 가
hello Ingress 를 가리키고 있었다면 잠깐 사라졌다 재생성될 수 있다.
`READY=True` 가 될 때까지 기다린다.

```bash
kubectl get certificate motif-tls
```

```bash
curl -sf https://j15d104.p.ssafy.io/api/health
```

> hello Deployment/Service 를 마저 지울지는 판단에 맡긴다. 램 16Mi 라
> 남겨둬도 부담이 없고, Ingress 없이도 클러스터 내부 점검용으로 쓸 수 있다.
> 지우기로 했다면 `infra/k8s/90-ingress/ingress.yaml` 에서 해당 정의를 빼는
> 커밋도 같이 남긴다 — 파일과 클러스터가 어긋나면 다음 사람이 속는다.

### 12-6. Jenkins 첫 빌드

Jenkins UI 에서 `motif-core-api` > **Build Now**.

전 단계가 녹색이면 성공이다. 이 빌드가 태그 `1` 이미지를 만들어
`kubectl set image` 로 교체한다. 이후부터 폴링 트리거가 등록된다.

## 13. 검증 체크리스트

**전부 통과해야 완료다.** 하나라도 빨간불이면 다음으로 넘어가지 않는다.

- [ ] **1. 폴링 감지** — `develop/backend` 에 빈 커밋을 push 하고 1~2분 안에
      빌드가 자동으로 시작되는지.
      `git commit --allow-empty -m "chore: CI 폴링 검증"` 후 push.
- [ ] **2. 전 스테이지 녹색** — Checkout / Build & Test / Docker Build /
      Import to k3s / Deploy / Verify / Cleanup.
- [ ] **3. DoD** — `curl -sf https://j15d104.p.ssafy.io/api/health` 가
      `{"status":"UP"}` 를 200으로 반환.
- [ ] **4. 이미지 태그가 올라갔는지** — 아래가 직전 빌드번호와 같은지.
      `kubectl get deployment core-api -o jsonpath='{.spec.template.spec.containers[0].image}'`
- [ ] **5. 무관한 커밋은 스킵** — `docs/` 나 `infra/` 만 고친 커밋을
      `develop/backend` 에 push 했을 때 `CI/CD` 단계가 통째로 스킵되는지.
- [ ] **6. 실패 시 자동 롤백** — 아래 참조.
- [ ] **7. 롤백 후에도 서비스 정상** — 6번 직후 다시 `/api/health` 200.

### 6번 — 실패 주입 방법

`app-core-api` 에는 아직 테스트 소스가 없다(`:app-core-api:test` 가
`NO-SOURCE`). 그래서 "테스트를 깨는" 방법을 쓸 수 없다. 대신 **배포 게이트가
실제로 막고 되돌리는지**를 확인해야 하므로, 기동에 실패하는 커밋을 넣는다.

`backend/app-core-api/src/main/resources/application-prod.yml` 의
`spring.datasource.url` 기본값을 존재하지 않는 호스트로 바꿔 push 한다.
예: `jdbc:postgresql://nosuchhost:5432/motif`

기대 동작:

1. Build & Test / Docker Build / Import 통과
2. 새 파드가 startupProbe 를 150초 동안 통과하지 못함
3. `rollout status --timeout=180s` 실패
4. `kubectl rollout undo` 자동 실행 → 직전 리비전 복귀
5. 빌드 FAIL (빨간불)
6. **그 동안에도 `/api/health` 는 계속 200** — `maxUnavailable: 0` 이라
   기존 파드가 죽지 않았기 때문이다. 이게 이 구성의 핵심이므로 반드시 확인한다.

확인 후 원복 커밋을 push 하고 다시 녹색이 되는 것까지 본다.

```bash
kubectl rollout history deployment/core-api
```

## 14. 되돌리기

셋업 자체를 되물릴 일이 생기면 역순으로 한다.

```bash
kubectl delete ingress core-api
```

```bash
kubectl apply -f infra/k8s/90-ingress/ingress.yaml
```

```bash
kubectl delete -f infra/k8s/40-core-api/deployment.yaml
```

```bash
kubectl delete secret core-api-secret
```

```bash
sudo systemctl stop jenkins && sudo systemctl disable jenkins
```

```bash
sudo rm /etc/sudoers.d/jenkins-k3s
```

## 15. 남은 것

- [ ] 트리거를 폴링에서 웹훅으로 — `infra/setup/webhook-migration.md`
      (GitLab 권한이 Maintainer 로 올라간 뒤)
- [ ] `app-notification` / `app-gateway` 파이프라인 (같은 Dockerfile 형태 복제)
- [ ] 빌드 실패 알림 (현재는 UI 를 봐야 안다)
- [ ] PAT 만료일 관리
