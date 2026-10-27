package com.ufo.ufo.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

@DisplayName("배포 준비 상태와 이전 버전 복구 테스트")
class DeploymentReadinessTest {

    @TempDir
    private Path directory;

    @Test
    @DisplayName("배포 워크플로는 외부 스크립트에 이미지 환경 변수를 전달한다")
    void executesExternalDeploymentScript() throws Exception {
        Map<?, ?> options = deploymentOptions();

        assertThat(options.get("script_path")).isInstanceOf(String.class);
        assertThat(Path.of((String) options.get("script_path"))).isRegularFile();
        assertThat(options.get("envs")).isInstanceOf(String.class);
        assertThat(((String) options.get("envs")).split(",")).contains("DOCKER_USERNAME", "IMAGE_TAG");
        assertThat(options.containsKey("script")).isFalse();
    }

    @Test
    @DisplayName("새 서버가 준비되면 이전 컨테이너의 이미지와 설정을 보관하고 배포를 완료한다")
    void retainsPreviousRuntimeAfterSuccessfulDeployment() throws Exception {
        var result = runDeployment(Map.of("READY_AFTER", "2"));

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("DEPLOY_SUCCEEDED");
        assertThat(result.previousState()).isEqualTo("stopped");
        assertThat(result.replacementState()).isEqualTo("running");
        assertThat(result.replacementRestartPolicy()).isEqualTo("unless-stopped");
        assertThat(result.previousRestartPolicy()).isEqualTo("no");
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.artifact("previous-image-id")).isEqualTo("sha256:old-image");
        assertThat(result.artifact("image.yaml")).contains("sha256:new-image");
        assertThat(result.artifact("previous-container.json")).contains("OLD_SECRET=previous-value");
        assertThat(result.commands()).contains("--no-deps", "--service-ports", "--use-aliases");
        assertThat(result.output()).doesNotContain("OLD_SECRET", "previous-value");
        assertThat(result.commands()).doesNotContain("up -d", "start mysql", "stop mysql", "rm -f old-id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"503", "302", "000"})
    @DisplayName("새 서버가 준비되지 않으면 이전 컨테이너로 복구해도 배포 결과는 실패다")
    void restoresPreviousContainerAfterFailedReadiness(String status) throws Exception {
        var result = runDeployment(Map.of("STATUS", status));

        assertRestored(result);
        assertThat(result.attempts()).isEqualTo(40);
        assertThat(result.output()).contains("ROLLBACK_SUCCEEDED");
        assertThat(result.artifact("result")).isEqualTo("ROLLBACK_SUCCEEDED");
        assertThat(result.commands()).doesNotContain("image prune");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    @DisplayName("실패 컨테이너를 제거하기 전에 애플리케이션 로그를 비공개 기록에 보관한다")
    void preservesApplicationLogsBeforeRemovingFailedContainer(String previous) throws Exception {
        var result = runDeployment(Map.of("PREVIOUS", previous, "STATUS", "503"));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.replacementState()).isEqualTo("missing");
        assertThat(result.artifact("replacement.log")).contains("Application startup failed", "PRIVATE_APP_DETAIL");
        assertThat(result.output()).doesNotContain("Application startup failed", "PRIVATE_APP_DETAIL");
    }

    @Test
    @DisplayName("애플리케이션 로그 수집에 실패해도 이전 버전 복구를 계속한다")
    void continuesRollbackAfterLogCollectionFailure() throws Exception {
        var result = runDeployment(Map.of("FAILURE", "logs", "STATUS", "503"));

        assertRestored(result);
        assertThat(result.output()).contains("ROLLBACK_SUCCEEDED").doesNotContain("PRIVATE_LOG_ERROR");
        assertThat(result.artifact("rollback.log")).contains("애플리케이션 로그 수집 실패");
        assertThat(result.artifact("replacement.log")).contains("PRIVATE_LOG_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"503", "302", "000"})
    @DisplayName("배포와 복구의 readiness 시도와 HTTP 상태를 배포 기록에 남긴다")
    void recordsReadinessAttemptsAndRecovery(String status) throws Exception {
        var result = runDeployment(Map.of("STATUS", status));

        assertRestored(result);
        assertThat(result.artifact("readiness.log"))
                .contains("phase=readiness container=new-id attempt=1/40 status=" + status,
                        "phase=readiness container=new-id attempt=40/40 status=" + status,
                        "phase=rollback container=old-id attempt=1/40 status=200")
                .containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    @ParameterizedTest
    @ValueSource(strings = {"000", "200"})
    @DisplayName("readiness 연결 오류와 curl 종료 코드는 비공개 기록에만 남긴다")
    void preservesReadinessConnectionErrors(String status) throws Exception {
        var result = runDeployment(Map.of("FAILURE", "curl", "STATUS", status));

        assertRestored(result);
        assertThat(result.artifact("readiness.log"))
                .contains("PRIVATE_CURL_ERROR", "status=" + status + " curl_exit=7");
        assertThat(result.output()).doesNotContain("PRIVATE_CURL_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0:8081", "[::]:8081", "", "port-error", "127.0.0.1:8081\n0.0.0.0:8081"})
    @DisplayName("관리 포트가 로컬에만 연결되지 않으면 새 서버를 제거하고 이전 버전을 복구한다")
    void restoresAfterUnsafeManagementBinding(String binding) throws Exception {
        var result = runDeployment(Map.of("BINDING", binding));

        assertRestored(result);
        assertThat(result.attempts()).isZero();
        assertThat(result.output()).contains("ROLLBACK_SUCCEEDED");
    }

    @Test
    @DisplayName("이미지 다운로드 실패는 실행 중인 서버를 중단하지 않는다")
    void doesNotInterruptPreviousServerWhenPullFails() throws Exception {
        var result = runDeployment(Map.of("FAILURE", "pull"));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.previousState()).isEqualTo("running");
        assertThat(result.replacementState()).isEqualTo("missing");
        assertThat(result.output()).contains("PULL_FAILED").doesNotContain("ROLLBACK_SUCCEEDED");
        assertThat(result.commands()).doesNotContain("stop ", "rename ", "image prune");
    }

    @ParameterizedTest
    @ValueSource(strings = {"snapshot", "old-unready", "old-binding", "lock", "config"})
    @DisplayName("배포 전 보관이나 상태 검증이 실패하면 기존 서버를 변경하지 않는다")
    void leavesPreviousServerUntouchedAfterPreflightFailure(String failure) throws Exception {
        var result = runDeployment(Map.of("FAILURE", failure));

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.previousState()).isEqualTo("running");
        assertThat(result.replacementState()).isEqualTo("missing");
        assertThat(result.commands()).doesNotContain("stop ", "rename ", "image prune");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "stop", "rename", "create", "create-partial", "signal", "restart-policy", "disable-restart"
    })
    @DisplayName("컨테이너 교체 도중 실패하거나 종료 신호를 받으면 원래 컨테이너로 복구한다")
    void recoversFromPartialReplacementFailure(String failure) throws Exception {
        var result = runDeployment(Map.of("FAILURE", failure));

        assertRestored(result);
        assertThat(result.output()).contains("ROLLBACK_SUCCEEDED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "always", "unless-stopped", "on-failure:5"})
    @DisplayName("이전 버전 복구 시 원래 재시작 정책과 재시도 횟수를 되돌린다")
    void restoresOriginalRestartPolicy(String policy) throws Exception {
        var result = runDeployment(Map.of("OLD_RESTART", policy, "STATUS", "503"));

        assertRestored(result);
        assertThat(result.previousRestartPolicy()).isEqualTo(policy);
        assertThat(result.artifact("previous-restart-policy")).isEqualTo(policy);
    }

    @Test
    @DisplayName("최초 배포가 실패하면 실패한 서버를 제거하고 복구할 버전이 없음을 알린다")
    void distinguishesFailedFirstDeployment() throws Exception {
        var result = runDeployment(Map.of("PREVIOUS", "false", "STATUS", "503"));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.previousState()).isEqualTo("missing");
        assertThat(result.replacementState()).isEqualTo("missing");
        assertThat(result.output()).contains("FIRST_DEPLOY_FAILED").doesNotContain("ROLLBACK_SUCCEEDED");
        assertThat(result.commands()).doesNotContain("start old-id", "image prune");
    }

    @Test
    @DisplayName("최초 배포에서도 DB를 시작하거나 교체하지 않고 준비된 백엔드를 실행한다")
    void supportsSuccessfulFirstDeployment() throws Exception {
        var result = runDeployment(Map.of("PREVIOUS", "false"));

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.replacementState()).isEqualTo("running");
        assertThat(result.replacementRestartPolicy()).isEqualTo("unless-stopped");
        assertThat(result.commands()).contains("--no-deps").doesNotContain("up -d");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rollback-start", "rollback-unready", "remove"})
    @DisplayName("이전 버전을 복구하지 못하면 롤백 실패와 복구 자료 위치를 남긴다")
    void distinguishesRollbackFailure(String failure) throws Exception {
        var result = runDeployment(Map.of("FAILURE", failure, "STATUS", "503"));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("ROLLBACK_FAILED", ".deploy/")
                .doesNotContain("ROLLBACK_SUCCEEDED", "DEPLOY_SUCCEEDED");
        assertThat(result.artifact("previous-container-id")).isEqualTo("old-id");
        assertThat(result.artifact("result")).isEqualTo("ROLLBACK_FAILED");
        assertThat(result.commands()).doesNotContain("image prune");
    }

    @Test
    @DisplayName("배포 성공 후 이미지 정리 실패는 정상 서버를 롤백하지 않는다")
    void doesNotRollBackHealthyServerWhenPruneFails() throws Exception {
        var result = runDeployment(Map.of("FAILURE", "prune"));

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.replacementState()).isEqualTo("running");
        assertThat(result.previousState()).isEqualTo("stopped");
        assertThat(result.output()).contains("DEPLOY_SUCCEEDED").doesNotContain("ROLLBACK_SUCCEEDED");
    }

    private void assertRestored(Result result) {
        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.previousState()).isEqualTo("running");
        assertThat(result.previousName()).isEqualTo("ufo-backend");
        assertThat(result.replacementState()).isEqualTo("missing");
        assertThat(result.commands()).contains("start old-id").doesNotContain("rm -f old-id", "up -d");
    }

    private Result runDeployment(Map<String, String> options) throws Exception {
        Files.writeString(directory.resolve("docker-compose.yml"), "services: {}\n");
        Files.writeString(directory.resolve(".env"), "IMAGE_TAG=test\n");
        Files.writeString(directory.resolve("attempts"), "0");
        Files.writeString(directory.resolve("old-state"),
                "false".equals(options.get("PREVIOUS")) ? "missing" : "running");
        Files.writeString(directory.resolve("old-name"), "ufo-backend");
        Files.writeString(directory.resolve("new-state"), "missing");
        Files.writeString(directory.resolve("old-restart"), options.getOrDefault("OLD_RESTART", "unless-stopped"));
        Files.writeString(directory.resolve("new-restart"), "no");
        Files.writeString(directory.resolve("commands"), "");
        Path script = directory.resolve("deploy-test.sh");
        String deployment = deploymentScript();
        Files.writeString(script, doubles() + deployment);
        String bash = Files.exists(Path.of("C:/Program Files/Git/usr/bin/bash.exe"))
                ? "C:/Program Files/Git/usr/bin/bash.exe" : "/bin/bash";
        var builder = new ProcessBuilder(bash, script.toAbsolutePath().toString().replace('\\', '/'))
                .directory(directory.toFile()).redirectErrorStream(true);
        var environment = builder.environment();
        environment.put("DOCKER_USERNAME", "test-user");
        environment.put("IMAGE_TAG", "test-sha");
        environment.put("UFO_TEST_DIR", directory.toAbsolutePath().toString().replace('\\', '/'));
        environment.put("UFO_TEST_STATUS", "200");
        environment.put("UFO_TEST_BINDING", "127.0.0.1:8081");
        environment.put("UFO_TEST_FAILURE", "none");
        environment.put("UFO_TEST_READY_AFTER", "0");
        options.forEach((key, value) -> environment.put("UFO_TEST_" + key, value));
        Path output = directory.resolve("output");
        builder.redirectOutput(output.toFile());
        var process = builder.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertThat(finished).as("배포 스크립트가 제한 시간 안에 종료되어야 한다").isTrue();
        return new Result(process.exitValue(), Files.readString(output),
                Integer.parseInt(Files.readString(directory.resolve("attempts"))),
                Files.readString(directory.resolve("commands")),
                Files.readString(directory.resolve("old-state")),
                Files.readString(directory.resolve("old-name")),
                Files.readString(directory.resolve("new-state")),
                Files.readString(directory.resolve("old-restart")),
                Files.readString(directory.resolve("new-restart")), directory);
    }

    private String deploymentScript() throws Exception {
        return Files.readString(Path.of((String) deploymentOptions().get("script_path")));
    }

    private Map<?, ?> deploymentOptions() throws Exception {
        try (var input = Files.newInputStream(Path.of(".github/workflows/deploy.yml"))) {
            Map<?, ?> workflow = new Yaml().load(input);
            Map<?, ?> jobs = (Map<?, ?>) workflow.get("jobs");
            Map<?, ?> job = (Map<?, ?>) jobs.get("build-and-deploy");
            List<?> steps = (List<?>) job.get("steps");
            Map<?, ?> deploy = (Map<?, ?>) steps.stream()
                    .filter(step -> "Deploy to AWS EC2".equals(((Map<?, ?>) step).get("name")))
                    .findFirst().orElseThrow();
            return (Map<?, ?>) deploy.get("with");
        }
    }

    private String doubles() {
        return """
                cd() { builtin cd "$UFO_TEST_DIR"; }
                flock() { [ "$UFO_TEST_FAILURE" != lock ]; }
                docker() {
                  printf '%s\\n' "$*" >> "$UFO_TEST_DIR/commands"
                  local target="${!#}" id=''
                  case "$*" in
                    info) return 0 ;;
                    'container ls '*)
                      if [ "$(< "$UFO_TEST_DIR/old-state")" != missing ]; then printf 'old-id'; fi
                      return 0 ;;
                    'image inspect '*) printf 'sha256:new-image'; return 0 ;;
                    'image tag '*) return 0 ;;
                    'image prune '*) [ "$UFO_TEST_FAILURE" != prune ]; return ;;
                    'logs '*new-id)
                      [ "$(< "$UFO_TEST_DIR/new-state")" != missing ] || return 1
                      if [ "$UFO_TEST_FAILURE" = logs ]; then
                        printf 'PRIVATE_LOG_ERROR\\n' >&2
                        return 1
                      fi
                      printf 'Application startup failed: PRIVATE_APP_DETAIL\\n'
                      return 0 ;;
                    *'config --images backend-server')
                      printf 'test-user/ufo-backend:test-sha\\nmysql:8.4'
                      return 0 ;;
                    *'config --quiet') [ "$UFO_TEST_FAILURE" != config ]; return ;;
                    *'pull backend-server') [ "$UFO_TEST_FAILURE" != pull ]; return ;;
                    *'up -d backend-server')
                      printf missing > "$UFO_TEST_DIR/old-state"
                      printf running > "$UFO_TEST_DIR/new-state"
                      return 0 ;;
                    *'run '*backend-server)
                      case " $* " in *' --no-tty '*) return 125 ;; esac
                      case " $* " in *' --no-deps '*) ;; *) return 1 ;; esac
                      if [ "$UFO_TEST_FAILURE" = create ]; then return 1; fi
                      printf running > "$UFO_TEST_DIR/new-state"
                      printf no > "$UFO_TEST_DIR/new-restart"
                      if [ "$UFO_TEST_FAILURE" = create-partial ]; then return 1; fi
                      if [ "$UFO_TEST_FAILURE" = signal ]; then kill -TERM "$$"; return 1; fi
                      printf 'new-id'
                      return 0 ;;
                    'inspect '*)
                      case "$target" in
                        old-id) id=old ;;
                        new-id) id=new ;;
                        ufo-backend)
                          if [ "$(< "$UFO_TEST_DIR/new-state")" != missing ]; then id=new
                          elif [ "$(< "$UFO_TEST_DIR/old-state")" != missing ] &&
                               [ "$(< "$UFO_TEST_DIR/old-name")" = ufo-backend ]; then id=old
                          else return 1; fi ;;
                        *) return 1 ;;
                      esac
                      [ "$(< "$UFO_TEST_DIR/$id-state")" != missing ] || return 1
                      case "$*" in
                        *'{{.Id}}'*) printf '%s-id' "$id" ;;
                        *'{{.Image}}'*) printf 'sha256:%s-image' "$id" ;;
                        *'{{.Name}}'*) printf '/%s' "$(< "$UFO_TEST_DIR/old-name")" ;;
                        *'{{.State.Running}}'*)
                          if [ "$(< "$UFO_TEST_DIR/$id-state")" = running ]; then printf true
                          else printf false; fi ;;
                        *'com.docker.compose.service'*) printf backend-server ;;
                        *'{{.HostConfig.RestartPolicy.Name}}'*)
                          local policy
                          policy=$(< "$UFO_TEST_DIR/$id-restart")
                          printf '%s' "${policy%%:*}"
                          ;;
                        *'{{.HostConfig.RestartPolicy.MaximumRetryCount}}'*)
                          local policy
                          policy=$(< "$UFO_TEST_DIR/$id-restart")
                          if [[ "$policy" = *:* ]]; then printf '%s' "${policy#*:}"
                          else printf 0; fi
                          ;;
                        *)
                          [ "$UFO_TEST_FAILURE" != snapshot ] || return 1
                          printf '[{"Id":"old-id","Image":"sha256:old-image",'
                          printf '"Config":{"Env":["OLD_SECRET=previous-value"]}}]'
                          ;;
                      esac
                      return 0 ;;
                    'update --restart='*)
                      local policy="${2#--restart=}"
                      if [ "$target" = old-id ]; then id=old; else id=new; fi
                      printf '%s' "$policy" > "$UFO_TEST_DIR/$id-restart"
                      if [ "$id" = new ] && [ "$UFO_TEST_FAILURE" = restart-policy ]; then return 1; fi
                      if [ "$id" = old ] && [ "$policy" = no ] &&
                         [ "$UFO_TEST_FAILURE" = disable-restart ]; then return 1; fi
                      return 0 ;;
                    'port '*)
                      if [ "$2" = old-id ]; then
                        if [ "$UFO_TEST_FAILURE" = old-binding ]; then printf '0.0.0.0:8081'
                        else printf '127.0.0.1:8081'; fi
                      elif [ "$UFO_TEST_BINDING" = port-error ]; then return 1
                      else printf '%s' "$UFO_TEST_BINDING"; fi
                      return 0 ;;
                    'stop old-id')
                      printf stopped > "$UFO_TEST_DIR/old-state"
                      [ "$UFO_TEST_FAILURE" != stop ]; return ;;
                    'rename old-id '*)
                      [ "$UFO_TEST_FAILURE" != rename ] || return 1
                      printf '%s' "$target" > "$UFO_TEST_DIR/old-name"
                      return 0 ;;
                    'start old-id')
                      [ "$UFO_TEST_FAILURE" != rollback-start ] || return 1
                      printf running > "$UFO_TEST_DIR/old-state"
                      printf true > "$UFO_TEST_DIR/rolled-back"
                      return 0 ;;
                    'rm -f new-id')
                      [ "$UFO_TEST_FAILURE" != remove ] || return 1
                      printf missing > "$UFO_TEST_DIR/new-state"
                      return 0 ;;
                    *'stop backend-server') printf stopped > "$UFO_TEST_DIR/new-state"; return 0 ;;
                    *'port backend-server 8081') printf '%s' "$UFO_TEST_BINDING"; return 0 ;;
                    *' ps') return 0 ;;
                  esac
                  printf 'Unexpected docker command: %s\\n' "$*" >&2
                  return 99
                }
                curl() {
                  if [ "$(< "$UFO_TEST_DIR/new-state")" = running ]; then
                    local count
                    count=$(< "$UFO_TEST_DIR/attempts")
                    count=$((count + 1))
                    printf '%s' "$count" > "$UFO_TEST_DIR/attempts"
                    if [ "$UFO_TEST_FAILURE" = curl ]; then
                      printf 'PRIVATE_CURL_ERROR\\n' >&2
                      printf '%s' "$UFO_TEST_STATUS"
                      return 7
                    fi
                    if [ "$UFO_TEST_READY_AFTER" -gt 0 ] && [ "$count" -lt "$UFO_TEST_READY_AFTER" ]; then
                      printf 503
                    else printf '%s' "$UFO_TEST_STATUS"; fi
                  elif [ "$(< "$UFO_TEST_DIR/old-state")" = running ]; then
                    if [ "$UFO_TEST_FAILURE" = old-unready ] ||
                       { [ "$UFO_TEST_FAILURE" = rollback-unready ] && [ -f "$UFO_TEST_DIR/rolled-back" ]; }; then
                      printf 503
                    else printf 200; fi
                  else printf 503; fi
                }
                sleep() { :; }
                """;
    }

    private record Result(int exitCode, String output, int attempts, String commands, String previousState,
                          String previousName, String replacementState, String previousRestartPolicy,
                          String replacementRestartPolicy, Path directory) {

        String artifact(String name) throws Exception {
            try (var paths = Files.walk(directory.resolve(".deploy"))) {
                Path artifact = paths.filter(path -> path.getFileName().toString().equals(name))
                        .findFirst().orElseThrow();
                return Files.readString(artifact).strip();
            }
        }
    }
}
