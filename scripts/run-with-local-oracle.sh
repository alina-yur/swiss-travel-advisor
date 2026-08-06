#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
container_name="${LOCAL_ORACLE_CONTAINER:-swiss-travel-oracle}"
password="${LOCAL_ORACLE_PASSWORD:-LocalDemoPassword1}"
max_attempts="${LOCAL_ORACLE_HEALTH_ATTEMPTS:-60}"
native_executable="${NATIVE_EXECUTABLE:-$project_dir/target/swiss-travel-advisor}"

if [[ ! -x "$native_executable" ]]; then
  echo "Native executable not found: $native_executable" >&2
  echo "Build it before the talk with: ./mvnw clean package -Dpackaging=native-image -DskipTests" >&2
  exit 1
fi

if ! command -v podman >/dev/null 2>&1; then
  echo "podman is required" >&2
  exit 1
fi

if ! command -v podman-compose >/dev/null 2>&1; then
  echo "podman-compose is required" >&2
  exit 1
fi

cd "$project_dir"
export LOCAL_ORACLE_PASSWORD="$password"

echo "Starting the local Oracle fallback (Phoenix is not changed)..."
podman-compose up -d oracle

echo "Waiting for Oracle to become healthy..."
for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  health="$(podman inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' "$container_name" 2>/dev/null || true)"
  if [[ "$health" == "healthy" ]]; then
    export ORACLE_JDBC_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
    export DB_USERNAME='TRAVEL'
    export DB_PASSWORD="$password"
    # Override Micronaut-native datasource variables too, in case the cloud
    # setup used them instead of the shared aliases above.
    export DATASOURCES_DEFAULT_URL="$ORACLE_JDBC_URL"
    export DATASOURCES_DEFAULT_USERNAME="$DB_USERNAME"
    export DATASOURCES_DEFAULT_PASSWORD="$DB_PASSWORD"
    echo "Oracle is healthy. Starting the native executable with the local database."
    exec "$native_executable"
  fi

  if [[ "$health" == "unhealthy" ]]; then
    echo "Oracle reported an unhealthy status." >&2
    echo "Inspect it with: podman-compose logs oracle" >&2
    exit 1
  fi

  if ((attempt < max_attempts)); then
    sleep 5
  fi
done

echo "Oracle did not become healthy after $((max_attempts * 5)) seconds." >&2
echo "Inspect it with: podman-compose logs oracle" >&2
exit 1
