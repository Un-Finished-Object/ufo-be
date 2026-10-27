# 백엔드 배포와 이전 버전 복구

`main` 배포는 새 이미지 다운로드와 기존 서버 상태 확인을 먼저 수행한다. 이전 컨테이너를 삭제하지 않고 중지한 뒤 이름을 바꾸어 보관한다. 새 컨테이너 생성이나 readiness 확인에 실패하면 새 컨테이너만 제거하고 원래 컨테이너를 다시 시작한다. 복구가 성공해도 해당 GitHub Actions 배포는 실패로 남는다.

배포와 복구 로직은 [`scripts/deployment/deploy-backend.sh`](../../scripts/deployment/deploy-backend.sh)에 둔다. 워크플로는 SSH 액션의 `script_path`로 해당 파일을 실행하고, `envs`로 `DOCKER_USERNAME`과 `IMAGE_TAG`를 전달한다. 스크립트를 서버에 따로 복사하거나 배포 때마다 수정할 필요는 없다.

## 서버 준비

- EC2에 Bash, Docker Compose v2, `curl`, `flock`이 필요하다.
- `/home/ubuntu/ufo-backend/docker-compose.yml`과 `.env`는 서버에서 관리한다.
- 운영 Compose의 서비스 이름은 `backend-server`, 실행 컨테이너 이름은 `ufo-backend`로 유지한다.
- 워크플로는 `${DOCKER_USERNAME}/ufo-backend:${IMAGE_TAG}`를 내려받고 이미지 ID로 고정한 Compose 설정을 배포 기록에 저장한다.
- 운영 프로필은 `prod`를 사용하고 자동 스키마 변경을 끈다. DB와 필요한 네트워크는 최초 배포 전에 준비한다. 배포는 DB 컨테이너를 시작하거나 교체하지 않는다.
- 관리 포트 `8081`은 호스트 `127.0.0.1`에만 연결한다. API의 기존 포트와 리버스 프록시 설정은 유지한다.

## 교체와 복구 방식

Compose의 `up`은 서비스 라벨로 기존 컨테이너를 찾아 교체할 수 있다. 이름만 바꿔 보관하면 복구용 컨테이너도 삭제될 수 있어, 새 컨테이너는 다음 옵션으로 만든다. `RECORD`는 이번 배포의 기록 디렉터리이며, `image.yaml`에는 내려받은 이미지 ID가 들어 있다.

```shell
docker compose --env-file .env -f docker-compose.yml -f "$RECORD/image.yaml" run \
  --no-deps --service-ports --use-aliases --pull never \
  --detach --interactive=false -T --name ufo-backend backend-server
docker update --restart=unless-stopped ufo-backend
```

이 명령은 서비스의 환경 변수, 볼륨, 포트와 네트워크 설정을 사용한다. 의존 서비스는 시작하지 않고, 이미지도 다시 다운로드하지 않는다. 별도 명령을 전달하지 않아 서비스의 시작 명령을 사용한다. 옵션의 의미는 [Docker Compose run 문서](https://docs.docker.com/reference/cli/docker/compose/run/)를 참고한다.

`compose run`은 서비스의 재시작 정책을 초기화한다. 워크플로는 새 컨테이너에 `unless-stopped`를 설정하고 적용 여부를 확인한다. 보관할 이전 컨테이너는 자동 재시작을 끄고, 복구할 때 원래 정책과 재시도 횟수를 되돌린다. 호스트를 재시작해도 보관한 이전 버전이 함께 켜져 포트를 차지하지 않도록 하기 위해서다.

이 컨테이너는 Compose의 일회성 컨테이너로 표시된다. 배포와 복구는 워크플로를 통해 수행하고, 상태는 `docker inspect ufo-backend`와 `docker ps -a`로 확인한다. 운영 중 `compose up`, `compose down`, 컨테이너 일괄 정리를 실행하면 복구용 컨테이너까지 영향을 받을 수 있다.

기존 서버가 있다면 실행 상태, 관리 포트와 readiness 200 응답을 교체 전에 확인한다. 새 서버와 복구한 서버는 각각 최대 40회 readiness를 확인한다. HTTP 요청은 연결 2초, 전체 3초로 제한하고 재시도 사이에 2초를 기다린다. curl이 정상 종료하고 HTTP 200을 반환해야 준비 완료로 판단한다. 302 리다이렉트나 응답 수신 중 연결 오류는 성공으로 취급하지 않는다. readiness에는 애플리케이션과 DB 연결 확인이 포함된다.

동일한 호스트 포트를 사용하므로 교체와 복구 사이에 요청을 처리하지 못하는 시간이 생긴다. 이 절차는 무중단 배포를 보장하지 않는다.

## 결과와 보관 자료

배포 기록은 서버의 `.deploy/<배포별 디렉터리>/`에 저장한다. `.deploy`는 소유자만 접근할 수 있는 권한으로 만들고, 기록 파일도 소유자만 읽고 쓰도록 한다. 컨테이너 조회 결과에는 비밀 환경 변수가 포함되므로 Actions 로그나 공개 아티팩트로 올리지 않는다.

| 파일 | 용도 |
| --- | --- |
| `previous-container-id` | 복구할 원래 컨테이너 ID |
| `previous-image-id` | 원래 컨테이너가 사용하는 변경 불가능한 이미지 ID |
| `previous-container.json` | 환경 변수, 포트, 네트워크, 볼륨 등을 포함한 실행 설정 |
| `previous-image-tag` | 이미지 정리로부터 보호하는 로컬 보관 태그 |
| `previous-restart-policy` | 복구할 컨테이너의 원래 재시작 정책과 재시도 횟수 |
| `image.yaml` | 새 컨테이너에 사용할 이미지 ID를 지정한 Compose 설정 |
| `replacement-container-id` | 생성된 새 컨테이너 ID |
| `replacement.log` | 삭제 전 수집한 실패 컨테이너의 타임스탬프 포함 애플리케이션 로그, 최근 200줄 |
| `readiness.log` | UTC 확인 시각, 단계, 컨테이너 ID, 시도 횟수, HTTP 상태, curl 종료 코드와 연결 오류 |
| `*.log` | Docker 명령 실패 진단 자료, 서버에서만 열람 |
| `result` | 배포 또는 복구 결과 |

자동 복구는 현재 `.env`로 이전 버전을 재생성하지 않는다. 원래 컨테이너를 다시 시작하므로 배포 전 환경 변수와 실행 설정을 그대로 사용한다. 컨테이너 조회 JSON은 진단 자료이며 실행 가능한 복원 스크립트가 아니다.

실패 원인은 `replacement.log`와 `readiness.log`에서 먼저 확인한다. readiness 기록의 `phase=rollback`은 이전 버전 복구 후 확인한 결과다. 애플리케이션 로그 수집에 실패하면 `rollback.log`에 남기고 컨테이너 제거와 복구를 계속한다. 로그에도 비밀값이 포함될 수 있으므로 서버에서만 열람한다.

| 결과 | 의미와 조치 |
| --- | --- |
| `DEPLOY_SUCCEEDED` | 새 버전 readiness 확인 완료 |
| `PULL_FAILED` | 다운로드 실패, 기존 컨테이너는 유지 |
| `PREFLIGHT_FAILED` | 이미지, 상태 확인 또는 자료 보관 실패, 기존 서버 교체 전 중단 |
| `ROLLBACK_SUCCEEDED` | 새 배포 실패 후 이전 버전 readiness 확인 완료, 배포 작업은 실패 |
| `FIRST_DEPLOY_FAILED` | 복구할 이전 컨테이너가 없는 최초 배포 실패, 실패한 컨테이너 제거 |
| `ROLLBACK_FAILED` | 컨테이너 제거, 이전 버전 시작 또는 복구 readiness 확인 실패, 수동 조치 필요 |

실행 중인 컨테이너를 중단하기 전 모든 복구 자료를 보관한다. 성공 후 이미지 정리 실패는 경고로 남기고 정상 서버를 롤백하지 않는다. 배포 잠금을 얻지 못하면 다른 배포에 영향을 주지 않고 종료한다.

## 수동 복구

자동 복구가 실패했거나 SSH 연결, 호스트 장애 등으로 절차가 중단되면 서버에 접속해 조치한다. `SIGTERM`, `SIGINT`에는 복구를 시도하지만 `SIGKILL`, Docker 데몬 또는 호스트 장애에는 트랩 실행을 보장할 수 없다.

먼저 다른 배포가 실행 중이지 않은지 확인하고, 작업할 기록 디렉터리를 직접 선택한다. 아래 변수에는 자동으로 최신 디렉터리를 추정하지 말고 Actions 로그에 나온 경로를 넣는다.

```shell
cd /home/ubuntu/ufo-backend
exec 9>.deploy/deploy.lock
flock -n 9 || exit 1
DEPLOY_RECORD=.deploy/선택한_배포_디렉터리
PREVIOUS_ID=$(cat "$DEPLOY_RECORD/previous-container-id")
docker inspect --format '{{.Id}} {{.Name}} {{.State.Status}} {{.Image}}' "$PREVIOUS_ID"
docker ps -a --format '{{.ID}} {{.Names}} {{.Status}}'
```

기록의 `replacement-container-id`와 `ufo-backend`가 같은 실패 컨테이너인지 확인한다. 제거 대상 ID가 `PREVIOUS_ID`와 다르고 실패한 교체 컨테이너임을 확인한 뒤 해당 ID만 중지하고 제거한다. DB, 다른 서비스나 보관된 이전 컨테이너를 제거하지 않는다.

`ufo-backend` 이름과 포트가 비어 있으면 원래 컨테이너를 복원한다. 이미 원래 이름이라면 `rename`은 생략한다.

```shell
docker rename "$PREVIOUS_ID" ufo-backend
PREVIOUS_RESTART_POLICY=$(cat "$DEPLOY_RECORD/previous-restart-policy")
docker update --restart="$PREVIOUS_RESTART_POLICY" "$PREVIOUS_ID"
docker start "$PREVIOUS_ID"
docker port "$PREVIOUS_ID" 8081/tcp
```

출력된 관리 주소가 `127.0.0.1`인지 확인한 뒤 그 주소로 `/actuator/health/readiness`를 요청한다. DB 장애가 있으면 이전 버전도 준비되지 않을 수 있으므로 DB와 네트워크 상태를 별도로 확인한다. 컨테이너 환경 변수나 애플리케이션 로그를 공개 이슈와 Actions 로그에 그대로 복사하지 않는다.

## 보관과 DB 호환성

복구용 컨테이너와 이미지 태그는 자동 삭제하지 않는다. 배포 후 이전 버전이 더 이상 필요 없는지 확인한 운영자가 해당 기록의 ID와 태그를 대조해 개별 정리한다. 반복 배포 시 디스크 사용량을 확인하고, 현재 버전과 필요한 복구 버전은 남긴다. 볼륨 데이터와 외부 DB 데이터는 컨테이너 보관만으로 이전 시점에 복원되지 않는다.

배포 스크립트는 DB를 직접 변경하지 않지만, `prod` 애플리케이션은 시작 시 Flyway 마이그레이션을 실행한다. 기존 DB에 처음 도입할 때는 [스키마 마이그레이션 절차](../database/schema-migrations.md)에 따라 백업과 baseline 등록을 먼저 마쳐야 한다. 마이그레이션이 실패하면 새 서버가 준비되지 않아 이전 컨테이너로 복구한다.

이전 버전으로 되돌아갈 수 있도록 DB 변경은 이전 애플리케이션과 호환돼야 한다. 컬럼 삭제나 이름 변경처럼 호환성을 깨는 변경은 애플리케이션 롤백만으로 복구할 수 없다. DB의 기준 스키마와 테스트 정책은 [마이그레이션 설계](../design/database-migrations.md)에 정리한다.
