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

@DisplayName("배포 준비 상태 확인 테스트")
class DeploymentReadinessTest {

    @TempDir
    private Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"503", "302"})
    @DisplayName("준비 상태가 실패하거나 로그인으로 리다이렉트되면 배포를 실패로 처리해야 한다")
    void rejectsUnreadyServer(String status) throws Exception {
        var result = runDeployment("127.0.0.1:8081", status, 0);
        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.commands()).doesNotContain("image prune");
    }

    @Test
    @DisplayName("서버가 준비되면 배포를 완료하고 사용하지 않는 이미지를 정리해야 한다")
    void waitsForSuccessfulReadiness() throws Exception {
        var result = runDeployment("127.0.0.1:8081", "503", 2);
        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.commands()).contains("image prune");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0:8081", ""})
    @DisplayName("관리 포트가 외부에 공개되거나 호스트에 연결되지 않으면 배포를 실패로 처리해야 한다")
    void rejectsUnsafeManagementBinding(String binding) throws Exception {
        var result = runDeployment(binding, "200", 0);
        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.attempts()).isZero();
    }

    private Result runDeployment(String binding, String status, int readyAfter) throws Exception {
        Files.createDirectories(directory.resolve("config"));
        Files.writeString(directory.resolve("config/application-prod.yml"), "spring: {}\n");
        Files.writeString(directory.resolve("compose.yml"), "services: {}\n");
        Files.writeString(directory.resolve("attempts"), "0");
        Files.writeString(directory.resolve("commands"), "");
        String doubles = """
                cd() { builtin cd "$UFO_TEST_DIR"; }
                docker() {
                  printf '%s\n' "$*" >> "$UFO_TEST_DIR/commands"
                  case "$*" in
                    *'port backend-server 8081'*) printf '%s\n' "$UFO_TEST_BINDING" ;;
                  esac
                  return 0
                }
                curl() {
                  local count
                  count=$(< "$UFO_TEST_DIR/attempts")
                  count=$((count + 1))
                  printf '%s' "$count" > "$UFO_TEST_DIR/attempts"
                  if [ "$UFO_TEST_READY_AFTER" -gt 0 ] && [ "$count" -ge "$UFO_TEST_READY_AFTER" ]; then
                    printf '200'
                  else
                    printf '%s' "$UFO_TEST_STATUS"
                  fi
                }
                sleep() { :; }
                """;
        String bash = Files.exists(Path.of("C:/Program Files/Git/usr/bin/bash.exe"))
                ? "C:/Program Files/Git/usr/bin/bash.exe" : "/bin/bash";
        Path script = directory.resolve("deploy-test.sh");
        Files.writeString(script, doubles + deploymentScript());
        var builder = new ProcessBuilder(bash, script.toAbsolutePath().toString().replace('\\', '/'))
                .directory(directory.toFile()).redirectErrorStream(true);
        var environment = builder.environment();
        environment.put("UFO_TEST_DIR", directory.toAbsolutePath().toString().replace('\\', '/'));
        environment.put("UFO_TEST_BINDING", binding);
        environment.put("UFO_TEST_STATUS", status);
        environment.put("UFO_TEST_READY_AFTER", Integer.toString(readyAfter));
        Path output = directory.resolve("output");
        builder.redirectOutput(output.toFile());
        var process = builder.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertThat(finished).isTrue();
        return new Result(process.exitValue(), Files.readString(output),
                Integer.parseInt(Files.readString(directory.resolve("attempts"))),
                Files.readString(directory.resolve("commands")));
    }

    private String deploymentScript() throws Exception {
        try (var input = Files.newInputStream(Path.of(".github/workflows/deploy.yml"))) {
            Map<?, ?> workflow = new Yaml().load(input);
            Map<?, ?> jobs = (Map<?, ?>) workflow.get("jobs");
            Map<?, ?> job = (Map<?, ?>) jobs.get("build-and-deploy");
            List<?> steps = (List<?>) job.get("steps");
            Map<?, ?> deploy = (Map<?, ?>) steps.stream()
                    .filter(step -> "Deploy to AWS EC2".equals(((Map<?, ?>) step).get("name")))
                    .findFirst().orElseThrow();
            return (String) ((Map<?, ?>) deploy.get("with")).get("script");
        }
    }

    private record Result(int exitCode, String output, int attempts, String commands) {
    }
}
