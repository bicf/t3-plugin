#!/usr/bin/env bash
# Entrypoint of the t3-ci runner image.
#   register  configure the runner with RUNNER_TOKEN and keep its credentials in /runner-state
#   run       restore the credentials from /runner-state and start the runner (default)
#   <other>   run the command as is (ci/local.sh)
set -euo pipefail

STATE_DIR=/runner-state
STATE_FILES=(.runner .credentials .credentials_rsaparams)

case "${1:-run}" in
  register)
    cd /home/runner
    : "${RUNNER_TOKEN:?RUNNER_TOKEN is required}"
    : "${RUNNER_URL:?RUNNER_URL is required}"
    ./config.sh --unattended --replace \
      --url "$RUNNER_URL" \
      --token "$RUNNER_TOKEN" \
      --name "${RUNNER_NAME:-t3-ci}" \
      --labels "${RUNNER_LABELS:-t3-ci}" \
      --work _work
    for f in "${STATE_FILES[@]}"; do cp -p "$f" "$STATE_DIR/"; done
    echo "The runner is registered. Start it with: docker compose up -d"
    ;;
  run)
    cd /home/runner
    for f in "${STATE_FILES[@]}"; do
      if [ ! -f "$STATE_DIR/$f" ]; then
        echo "The runner is not registered: run ci/runner/register.sh first." >&2
        exit 1
      fi
      cp -p "$STATE_DIR/$f" .
    done
    exec ./run.sh
    ;;
  *)
    exec "$@"
    ;;
esac
