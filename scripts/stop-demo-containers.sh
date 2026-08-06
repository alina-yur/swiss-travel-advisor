#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v podman >/dev/null 2>&1; then
  echo "podman is required" >&2
  exit 1
fi

if ! command -v podman-compose >/dev/null 2>&1; then
  echo "podman-compose is required" >&2
  exit 1
fi

cd "$project_dir"

echo "Stopping the Swiss Travel Advisor demo containers..."
podman-compose stop oracle phoenix

echo
echo "Stopped Oracle and Phoenix. Containers, images, and named volumes were preserved."
echo "Phoenix traces and local Oracle data will still be available on the next start."
