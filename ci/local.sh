#!/usr/bin/env bash
# Run the CI of the Build workflow (buildPlugin, check, verifyPlugin) in the t3-ci runner image, without GitHub.
# The build uses a snapshot of the tracked files, with their uncommitted changes; untracked files stay out.
# The snapshot and the reports go to build/ci-local/src. Extra arguments go to Gradle.
# The downloads stay in Docker volumes, and the Gradle project caches stay in the snapshot directory.
set -euo pipefail

ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
SRC="$ROOT/build/ci-local/src"
IMAGE=t3-ci-runner:latest
# Not the runner volume t3-ci-gradle: Gradle cannot release its locks to a process in another container,
# so a local run and a runner job on the same Gradle home wait for each other.
VOLUME=t3-ci-local-gradle
# The Plugin Verifier downloads the plugins of the verified IDEs from the Marketplace into its home directory.
VERIFIER_VOLUME=t3-ci-local-plugin-verifier
# The Gradle project caches (configuration cache, IntelliJ Platform files) stay in the snapshot between runs.
KEEP=(.gradle .intellijPlatform .kotlin)

# One local run at a time: a second run waits, because it shares the snapshot directory and the volume.
mkdir -p "$ROOT/build/ci-local"
exec 9>"$ROOT/build/ci-local/.lock"
if ! flock -n 9; then
  echo "Another ci/local.sh run is in progress: waiting for it."
  flock 9
fi

RUNNER_UID="$(id -u)" RUNNER_GID="$(id -g)" \
  docker compose -f "$ROOT/ci/runner/compose.yaml" build --quiet

# "git stash create" records the uncommitted changes of tracked files without touching the working tree.
REV="$(git -C "$ROOT" stash create)"
REV="${REV:-HEAD}"
mkdir -p "$SRC"
# Delete the previous snapshot and its build directory, but not the Gradle project caches.
find "$SRC" -mindepth 1 -maxdepth 1 $(printf '! -name %s ' "${KEEP[@]}") -exec rm -rf {} +
git -C "$ROOT" archive "$REV" | tar -x -C "$SRC"
echo "Snapshot: $(git -C "$ROOT" rev-parse --short HEAD)$([ "$REV" = HEAD ] || echo ' + uncommitted changes')"

START=$SECONDS
STATUS=0
docker run --rm --init --cpus 12 --memory 12g \
  -v "$VOLUME":/home/runner/.gradle \
  -v "$VERIFIER_VOLUME":/home/runner/.pluginVerifier \
  -v "$SRC":/work -w /work \
  "$IMAGE" ./gradlew buildPlugin check verifyPlugin --console=plain "$@" || STATUS=$?

echo "Time: $((SECONDS - START)) s, exit code $STATUS"
echo "Reports: $SRC/build/reports"
exit "$STATUS"
