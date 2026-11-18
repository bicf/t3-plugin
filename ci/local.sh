#!/usr/bin/env bash
# Run the CI of the Build workflow (buildPlugin, check, verifyPlugin) in the t3-ci runner image, without GitHub.
# The build uses a snapshot of the tracked files, with their uncommitted changes; untracked files stay out.
# The snapshot and the reports go to build/ci-local/src. Extra arguments go to Gradle.
set -euo pipefail

ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
SRC="$ROOT/build/ci-local/src"
IMAGE=t3-ci-runner:latest
# Not the runner volume t3-ci-gradle: Gradle cannot release its locks to a process in another container,
# so a local run and a runner job on the same Gradle home wait for each other.
VOLUME=t3-ci-local-gradle

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
rm -rf "$SRC"
mkdir -p "$SRC"
git -C "$ROOT" archive "$REV" | tar -x -C "$SRC"
echo "Snapshot: $(git -C "$ROOT" rev-parse --short HEAD)$([ "$REV" = HEAD ] || echo ' + uncommitted changes')"

START=$SECONDS
STATUS=0
docker run --rm --init --cpus 12 --memory 12g \
  -v "$VOLUME":/home/runner/.gradle \
  -v "$SRC":/work -w /work \
  "$IMAGE" ./gradlew buildPlugin check verifyPlugin --console=plain "$@" || STATUS=$?

echo "Time: $((SECONDS - START)) s, exit code $STATUS"
echo "Reports: $SRC/build/reports"
exit "$STATUS"
