#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
phoenix_url="${PHOENIX_URL:-http://localhost:6006}"
timeout_seconds="${PHOENIX_TIMEOUT_SECONDS:-60}"

if ! command -v podman >/dev/null 2>&1; then
  echo "podman is required" >&2
  exit 1
fi

if ! command -v podman-compose >/dev/null 2>&1; then
  echo "podman-compose is required" >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

cd "$project_dir"

echo "Starting a fresh Phoenix container while preserving its stored traces..."
podman-compose up -d --force-recreate phoenix

echo "Waiting for Phoenix at $phoenix_url ..."
for ((attempt = 1; attempt <= timeout_seconds; attempt++)); do
  if curl --fail --silent --max-time 2 "$phoenix_url/" >/dev/null 2>&1; then
    echo "Phoenix is ready: $phoenix_url"
    exit 0
  fi
  sleep 1
done

echo "Phoenix did not become responsive within ${timeout_seconds}s." >&2
echo "Recent Phoenix logs:" >&2
podman-compose logs --tail=80 phoenix >&2 || true
exit 1
