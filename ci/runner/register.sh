#!/usr/bin/env bash
# Register the self-hosted runner "t3-ci" for bicf/t3-plugin and start it.
# Requires: docker with compose, gh authenticated with admin access to the repository.
set -euo pipefail

cd "$(dirname "$0")"
REPO=bicf/t3-plugin

export RUNNER_UID="$(id -u)" RUNNER_GID="$(id -g)"
# compose.yaml declares the Gradle volume as external: Compose does not create it. The command keeps an existing volume.
docker volume create t3-ci-gradle >/dev/null
docker compose build

# The registration token expires after one hour and is used only here.
RUNNER_TOKEN="$(gh api -X POST "repos/$REPO/actions/runners/registration-token" -q .token)"
docker compose run --rm -e RUNNER_TOKEN="$RUNNER_TOKEN" runner register

docker compose up -d
gh api "repos/$REPO/actions/runners" -q '.runners[] | "\(.name) \(.status) \([.labels[].name] | join(","))"'
