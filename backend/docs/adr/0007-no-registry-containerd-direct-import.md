# ADR-0007. 컨테이너 레지스트리를 두지 않고 containerd에 직접 임포트한다

- 상태: 승인
- 일자: 2026-09-02
- 결정자: 백엔드·인프라
- 관련: ADR-0004(스택 선정 — "GHCR + 빌드번호 태그" 행을 **부분 대체**),
  ADR-0008(레이어드 jar), Jira S15P21D104-181,
  `backend/Jenkinsfile`, `infra/k8s/40-core-api/`

## 컨텍스트

ADR-0004는 배포 이미지를 **GHCR + 빌드번호 태그**로 정했다. 전제는 두 가지였다.
하나는 "k3s 롤링은 레지스트리가 전제"라는 것, 다른 하나는 MODERA에서 겪은
`:latest` 운영 참조의 롤백 불가 문제를 태그 불변성으로 푼다는 것이다.

배포를 실제로 붙이는 단계에서 전제 하나가 무너졌다.

- **GitLab Container Registry가 인스턴스 레벨에서 비활성이다.** SSAFY가 운영하는
  `lab.ssafy.com` 설정이라 프로젝트에서 켤 수 있는 종류가 아니다.
- 대안으로 GHCR을 검토했으나, 팀 GitLab 밖의 외부 계정과 토큰을 하나 더
  도입해야 한다. 그 토큰은 Jenkins(push)와 k3s(pull) 양쪽에 심어야 하고,
  둘 중 하나만 만료돼도 배포가 조용히 멈춘다.

여기서 드러난 사실은 **레지스트리가 필요한 이유가 노드 간 이미지 배포**라는
점이다. 지금 클러스터는 단일 노드다. 이미지를 만드는 호스트와 이미지를 쓰는
kubelet이 같은 기계에 있으므로, 둘 사이에 네트워크 왕복을 넣을 이유가 없다.

## 결정

**D1. 레지스트리를 쓰지 않는다.** Jenkins 호스트의 도커가 만든 이미지를
`docker save | sudo k3s ctr images import -` 로 k3s의 containerd에 직접 넣는다.

k3s는 도커가 아니라 containerd를 쓴다. 호스트 도커에 이미지가 있어도 kubelet은
보지 못하므로 이 임포트가 반드시 필요하다 — "도커에 있으니 되겠지"가 이 구성의
1순위 함정이다.

**D2. 이미지 이름은 `registry.local/motif/core-api:${BUILD_NUMBER}`.**

- `registry.local` 은 **실재하지 않는 가짜 프리픽스**다. 어딘가에서 pull을
  시도하면 DNS에서 즉시 실패한다. 프리픽스가 없으면 도커/containerd가
  Docker Hub를 기본 레지스트리로 가정해 엉뚱한 곳을 조회한다.
- **빌드번호 태그만 운영 참조로 쓴다.** `latest` 는 어느 커밋인지 지목할 수
  없어 롤백 대상을 특정할 수 없다 — MODERA에서 이미 겪은 문제이고,
  ADR-0004가 태그 불변성을 요구한 이유이기도 하다. 레지스트리가 사라졌을 뿐
  그 요구는 그대로 유지된다.

**D3. `imagePullPolicy: Never`.** 로컬 containerd에 이미지가 없으면
`ErrImageNeverPull` 로 즉시 멈춘다. `IfNotPresent` 로 두면 임포트가 실패했을 때
파드가 가짜 호스트를 조회하다 `ImagePullBackOff` 로 빠져, 진짜 원인(임포트 누락)이
네트워크 오류처럼 보인다.

## 근거

- **자격증명 0개.** push용·pull용 토큰이 없으므로 만료로 배포가 멈출 경로가 없다.
  Jenkins에 두는 비밀은 GitLab PAT(`read_repository`) 하나뿐이다.
- **외부 왕복 0회.** 79MB 이미지를 인터넷으로 올렸다 내리지 않는다. 배포 시간이
  네트워크가 아니라 파드 기동 시간에만 좌우된다.
- **토폴로지와 일치한다.** 단일 노드에서 레지스트리는 같은 디스크에 이미지를
  한 벌 더 두는 일이다. 15Gi/309GB 서버에서 무료가 아니다.
- **원복이 싸다.** 레지스트리로 돌아가는 변경은 Jenkinsfile의 임포트 단계를
  push로 바꾸고 Deployment의 image 프리픽스와 pullPolicy를 고치는 것이 전부다.

## 검토한 대안

| 대안 | 기각 사유 |
|---|---|
| GitLab Container Registry | 인스턴스 레벨 비활성 — 프로젝트 권한으로 켤 수 없다. 선택지가 아니다 |
| GHCR | 팀 GitLab 밖 외부 계정·토큰 도입. Jenkins/k3s 양쪽에 심어야 하고 만료 시 조용히 멈춘다. 단일 노드에서 얻는 것이 없다 |
| 클러스터 내 `registry:2` | 램·디스크를 쓰면서 지금은 아무 문제도 풀지 않는다. **노드가 2개가 되는 시점의 정답이므로 폐기가 아니라 보류다** |
| 호스트 도커를 k3s 런타임으로(`--docker`) | k3s의 containerd 기본값을 벗어난다. 임포트 한 줄을 없애자고 런타임을 바꾸는 것은 교환비가 맞지 않는다 |

## 결과

- 커밋 → 배포 경로에 외부 서비스가 하나도 없다. GitLab이 죽어도 이미 받은
  소스로 재배포가 된다.
- **멀티 노드로 갈 수 없다.** 이 결정의 유일한 실질 비용이다. 이미지가 한
  노드의 containerd에만 있으므로, 워커 노드에 스케줄된 파드는 이미지를 찾지
  못한다.
- **전환 조건과 경로를 미리 못박는다.** EC2 워커 노드 조인(`workload=media`)이
  결정되는 순간 이 ADR은 유효하지 않다. 그때는 **테일넷 내부에 `registry:2`를
  올리고**(공인 노출 없음, TLS는 테일넷이 대신함) Jenkinsfile의
  `Import to k3s` 단계를 `docker push` 로, Deployment의 image 프리픽스와
  `imagePullPolicy: IfNotPresent` 로 바꾼다. 새 ADR로 이 문서를 대체한다.
- `sudo` 표면이 생긴다. jenkins 계정에 `k3s ctr images` 계열 명령만
  NOPASSWD로 연다 — 전체 sudo는 열지 않는다(`infra/setup/jenkins-setup.md` §6).
- 이미지가 호스트 디스크에 쌓인다. 파이프라인이 빌드번호 기준 최근 5개만
  남기고 정리한다.
