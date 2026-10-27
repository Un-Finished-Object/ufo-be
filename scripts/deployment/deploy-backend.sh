#!/usr/bin/env bash
set -Eeuo pipefail
: "${DOCKER_USERNAME:?DOCKER_USERNAME 환경 변수가 필요합니다.}"
: "${IMAGE_TAG:?IMAGE_TAG 환경 변수가 필요합니다.}"
umask 077
exec 3>&1
cd /home/ubuntu/ufo-backend
COMPOSE_FILE=./docker-compose.yml
ENV_FILE=./.env
BACKEND_NAME=ufo-backend
EXPECTED_IMAGE="$DOCKER_USERNAME/ufo-backend:$IMAGE_TAG"

mkdir -p .deploy
chmod 700 .deploy
exec 9>.deploy/deploy.lock
if ! flock -n 9; then
  echo 'PREFLIGHT_FAILED: 다른 배포가 실행 중입니다.'
  exit 1
fi
RECORD=$(mktemp -d ".deploy/${IMAGE_TAG}.XXXXXXXX")
IMAGE_OVERRIDE="$RECORD/image.yaml"
OLD_ID=''
OLD_RESTART_POLICY=''
REPLACEMENT_STARTED=false
DEPLOYED=false
FAILURE_RESULT=PREFLIGHT_FAILED
PHASE=preflight

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -f "$IMAGE_OVERRIDE" "$@"
}

wait_ready() {
  local container_id=$1 max_attempts=${2:-40} address status attempt
  address=$(docker port "$container_id" 8081/tcp 2>>"$RECORD/readiness.log") || return 1
  if [[ ! "$address" =~ ^127\.0\.0\.1:[0-9]+$ ]]; then
    echo '관리 포트를 호스트 127.0.0.1에만 연결해야 합니다.' >>"$RECORD/readiness.log"
    return 1
  fi
  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --noproxy '*' --connect-timeout 2 --max-time 3 \
      "http://$address/actuator/health/readiness" || true)
    if [ "$status" = 200 ] &&
        [ "$(docker inspect --format '{{.State.Running}}' "$container_id")" = true ]; then
      return 0
    fi
    if [ "$attempt" -lt "$max_attempts" ]; then sleep 2; fi
  done
  return 1
}

remove_replacement() {
  local current_id
  if ! current_id=$(docker inspect --format '{{.Id}}' "$BACKEND_NAME" 2>>"$RECORD/rollback.log"); then
    # 컨테이너가 없는 경우와 Docker 데몬 장애를 구분한다.
    docker info >>"$RECORD/rollback.log" 2>&1 || return 1
    return 0
  fi
  if [ "$current_id" = "$OLD_ID" ]; then return 0; fi
  printf '%s\n' "$current_id" >"$RECORD/replacement-container-id"
  docker inspect "$current_id" >"$RECORD/replacement-container.json" 2>>"$RECORD/rollback.log" || true
  docker rm -f "$current_id" >>"$RECORD/rollback.log" 2>&1
}

restore_previous() {
  remove_replacement || return 1
  local previous_name
  previous_name=$(docker inspect --format '{{.Name}}' "$OLD_ID" 2>>"$RECORD/rollback.log") || return 1
  if [ "$previous_name" != "/$BACKEND_NAME" ]; then
    docker rename "$OLD_ID" "$BACKEND_NAME" >>"$RECORD/rollback.log" 2>&1 || return 1
  fi
  docker update --restart="$OLD_RESTART_POLICY" "$OLD_ID" >>"$RECORD/rollback.log" 2>&1 || return 1
  docker start "$OLD_ID" >>"$RECORD/rollback.log" 2>&1 || return 1
  wait_ready "$OLD_ID"
}

finish() {
  local exit_code=$? result=$FAILURE_RESULT
  trap - EXIT
  trap '' INT TERM
  if [ "$exit_code" -eq 0 ] || [ "$DEPLOYED" = true ]; then return; fi
  set +e
  if [ "$REPLACEMENT_STARTED" = true ]; then
    if [ -n "$OLD_ID" ]; then
      if restore_previous; then result=ROLLBACK_SUCCEEDED
      else result=ROLLBACK_FAILED; fi
    elif remove_replacement; then result=FIRST_DEPLOY_FAILED
    else result=ROLLBACK_FAILED; fi
  fi
  printf '%s\n' "$result" >"$RECORD/result"
  echo "DEPLOY_FAILED phase=$PHASE result=$result record=$RECORD" >&3
  exit "$exit_code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

test -f "$ENV_FILE"
test -f "$COMPOSE_FILE"
docker info >"$RECORD/preflight.log" 2>&1
printf 'services:\n  backend-server:\n    image: "%s"\n' "$EXPECTED_IMAGE" >"$IMAGE_OVERRIDE"
compose config --quiet >>"$RECORD/preflight.log" 2>&1

PHASE=pull
if ! compose pull backend-server >"$RECORD/pull.log" 2>&1; then
  FAILURE_RESULT=PULL_FAILED
  exit 1
fi
NEW_IMAGE_ID=$(docker image inspect --format '{{.Id}}' "$EXPECTED_IMAGE")
printf 'services:\n  backend-server:\n    image: "%s"\n' "$NEW_IMAGE_ID" >"$IMAGE_OVERRIDE"

PHASE=snapshot
OLD_ID=$(docker container ls --all --quiet --no-trunc --filter "name=^/${BACKEND_NAME}$")
if [ -n "$OLD_ID" ]; then
  test "$(docker inspect --format '{{index .Config.Labels "com.docker.compose.service"}}' "$OLD_ID")" = backend-server
  test "$(docker inspect --format '{{.State.Running}}' "$OLD_ID")" = true
  wait_ready "$OLD_ID" 1
  OLD_IMAGE_ID=$(docker inspect --format '{{.Image}}' "$OLD_ID")
  printf '%s\n' "$OLD_ID" >"$RECORD/previous-container-id"
  printf '%s\n' "$OLD_IMAGE_ID" >"$RECORD/previous-image-id"
  docker inspect "$OLD_ID" >"$RECORD/previous-container.json" 2>>"$RECORD/preflight.log"
  OLD_RESTART_POLICY=$(docker inspect --format '{{.HostConfig.RestartPolicy.Name}}' "$OLD_ID")
  case "$OLD_RESTART_POLICY" in
    no|always|unless-stopped) ;;
    on-failure)
      RETRY_COUNT=$(docker inspect --format '{{.HostConfig.RestartPolicy.MaximumRetryCount}}' "$OLD_ID")
      [[ "$RETRY_COUNT" =~ ^[0-9]+$ ]]
      OLD_RESTART_POLICY="on-failure:$RETRY_COUNT"
      ;;
    *) exit 1 ;;
  esac
  printf '%s\n' "$OLD_RESTART_POLICY" >"$RECORD/previous-restart-policy"
  ROLLBACK_TAG="ufo-backend-rollback:${IMAGE_TAG}-${OLD_ID:0:12}"
  docker image tag "$OLD_IMAGE_ID" "$ROLLBACK_TAG"
  printf '%s\n' "$ROLLBACK_TAG" >"$RECORD/previous-image-tag"
fi

PHASE=replace
REPLACEMENT_STARTED=true
if [ -n "$OLD_ID" ]; then
  # 호스트 재시작 때 보관한 컨테이너가 함께 켜지는 것을 막는다.
  docker update --restart=no "$OLD_ID" >"$RECORD/stop.log" 2>&1
  docker stop "$OLD_ID" >>"$RECORD/stop.log" 2>&1
  docker rename "$OLD_ID" "${BACKEND_NAME}-rollback-${RECORD##*/}" >>"$RECORD/stop.log" 2>&1
fi
# up을 쓰면 서비스 라벨이 같은 복구용 컨테이너도 교체될 수 있다.
compose run --no-deps --service-ports --use-aliases --pull never \
  --detach --interactive=false -T --name "$BACKEND_NAME" backend-server >"$RECORD/create.log" 2>&1
NEW_ID=$(docker inspect --format '{{.Id}}' "$BACKEND_NAME")
printf '%s\n' "$NEW_ID" >"$RECORD/replacement-container-id"
test "$(docker inspect --format '{{.Image}}' "$NEW_ID")" = "$NEW_IMAGE_ID"
# compose run은 서비스의 재시작 정책을 초기화한다.
docker update --restart=unless-stopped "$NEW_ID" >>"$RECORD/create.log" 2>&1
test "$(docker inspect --format '{{.HostConfig.RestartPolicy.Name}}' "$NEW_ID")" = unless-stopped
PHASE=readiness
wait_ready "$NEW_ID"

printf 'DEPLOY_SUCCEEDED\n' >"$RECORD/result"
DEPLOYED=true
echo "DEPLOY_SUCCEEDED record=$RECORD"
if ! docker image prune -f >"$RECORD/prune.log" 2>&1; then
  echo "이미지 정리에 실패했습니다. 배포는 유지합니다. record=$RECORD"
fi
