# UFO Backend agent instructions

UFO is a Java 21 Spring backend for pattern discovery, yarn recommendations, credit purchases, and chat. This file holds instructions that coding agents need while working here. Follow [CONTRIBUTING.md](CONTRIBUTING.md) for issue, branch, commit, and PR formats, and [README.md](README.md) for local setup. Add tool-specific instruction files only when a tool needs them, and link back here instead of copying these rules.

## Project Structure & Module Organization
- Main code lives in `src/main/java/com/ufo/ufo`; shared code is under `global/` and business code is grouped under `domain/`.
- Runtime configuration is in `src/main/resources/application.yaml`; secrets must be provided through environment variables or an untracked local profile file.
- Tests mirror production packages in `src/test/java/com/ufo/ufo` with fixture builders in `support/fixture`.

## Build and Test Approval
- Before running any command that compiles the project or runs tests, ask the user for explicit approval and wait for confirmation.
- This includes `bootRun`, `clean build`, `test`, `asciidoctor`, and equivalent Gradle tasks. Use `gradlew.bat` on Windows.

## Coding Guidelines
- Java 21 toolchain is required (`build.gradle`).
- Follow the existing domain package structure and Spring layers: `api`, `application`, `dao`, `domain`, and `dto`.
- Keep request and response DTOs separate and preserve domain package boundaries.

## Testing Guidelines
- Mirror production package paths, name tests with the `*Test` suffix, and reuse `support/fixture` where practical.
- Add tests at the layers affected by a behavior change.

## Issue and PR workflow
- Use the GitHub MCP to read open and closed issues before writing a new one. Compare the request with the current source and related docs, then create the issue through the GitHub MCP or update an existing open issue. Write a local draft only when the user explicitly asks for one. Use related docs as context without naming internal files or explaining how the issue was derived unless asked.
- When an issue number is known and the request includes implementation, fetch the latest issue details through the GitHub MCP, check the working tree, and switch to an existing issue branch or create one before editing code.
- When asked to write a PR description, review the recent branch commits and write it to `PR.md`. Follow the PR template and the title and label rules in `CONTRIBUTING.md`.
- After creating or updating a GitHub issue, fetch it again through the GitHub MCP to verify its title, body, and labels. When creating a PR, copy all labels from the issue named by `closes: #<issue-number>` and verify the PR title, body, and labels.

## Security & Configuration Tips
- Never commit credentials or local secret configuration.
- Review auth/security changes in `global/security` carefully and include regression tests for token/OAuth flows.
