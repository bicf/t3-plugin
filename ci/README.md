# CI on a self-hosted Docker runner

The Build workflow (`.github/workflows/build.yml`) runs on the runner that the repository variable `CI_RUNS_ON` selects:

| `CI_RUNS_ON` | Runner |
|---|---|
| not set | `ubuntu-latest` (GitHub-hosted) |
| `["self-hosted","linux","t3-ci"]` | the Docker runner `t3-ci` of this directory |

The Release workflow (`release.yml`) stays on `ubuntu-latest`.

## Runner `t3-ci`

The image `t3-ci-runner` (`runner/Dockerfile`) is the GitHub Actions runner with JDK 21 (Zulu), `gh` and `fontconfig`.
The runner user has the uid and gid of the host user, and no sudo and no Docker access.

The volumes keep all the downloads between the jobs, also when the container is created again:

| Volume | Content |
|---|---|
| `t3-ci-gradle` | Gradle home: Gradle distribution, dependencies, IDEs, Java 25 toolchain, build cache. External volume: `register.sh` creates it, Compose does not |
| `t3-ci-plugin-verifier` | Plugin Verifier home: the Marketplace plugins of the verified IDEs (1.4 GB) |
| `t3-ci-work` | job workspaces, with the Gradle project caches (`.gradle`, `.intellijPlatform`, `.kotlin`) |
| `t3-ci-runner-state` | runner registration (`.runner`, `.credentials`) |
| `t3-ci-local-gradle` | Gradle home of `ci/local.sh` |
| `t3-ci-local-plugin-verifier` | Plugin Verifier home of `ci/local.sh` |

The Build workflow does not delete the Gradle project caches of the workspace on the self-hosted runner:
the next job reuses the configuration cache. The step "Clean Workspace" deletes all the other untracked and ignored files.

Limits: 12 CPUs, 12 GB of memory (`runner/compose.yaml`).

### Install

Requirements: Docker with Compose, and `gh` with admin access to `bicf/t3-plugin`.

1. Run `ci/runner/register.sh`. The script builds the image, registers the runner and starts the container.
2. Set the variable: `gh variable set CI_RUNS_ON --repo bicf/t3-plugin --body '["self-hosted","linux","t3-ci"]'`.

### Operate

- Logs: `docker logs -f t3-ci-runner`.
- Stop: `docker compose -f ci/runner/compose.yaml stop`. The jobs wait in the queue until the runner starts again.
- Go back to GitHub-hosted runners: `gh variable delete CI_RUNS_ON --repo bicf/t3-plugin`.
- Update the image: `docker compose -f ci/runner/compose.yaml up -d --build`. The registration stays in its volume.
- Remove the runner: stop the container, delete it in the repository settings (Actions → Runners), then `docker volume rm t3-ci-runner-state`.
- Empty the caches: stop the container, then `docker volume rm t3-ci-gradle t3-ci-plugin-verifier t3-ci-work` and `docker volume create t3-ci-gradle`. The next job downloads everything again.

## Local CI

`ci/local.sh` runs `./gradlew buildPlugin check verifyPlugin` in the `t3-ci-runner` image, with the volumes `t3-ci-local-gradle` and `t3-ci-local-plugin-verifier`.

- The script does not use the runner volume `t3-ci-gradle`. Gradle cannot release its locks to a process in another container, so a local run and a runner job would wait for each other.
- A second run waits for the first run to end.
- The build uses the tracked files with their uncommitted changes. Untracked files stay out: add them with `git add` first.
- The snapshot and the reports go to `build/ci-local/src`. Each run replaces the snapshot and its `build` directory, but keeps the Gradle project caches (`.gradle`, `.intellijPlatform`, `.kotlin`).
- Extra arguments go to Gradle, for example `ci/local.sh --no-build-cache`.
