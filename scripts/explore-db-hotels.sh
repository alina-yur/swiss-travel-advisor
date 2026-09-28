#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
row_count="${1:-20}"
exec "$script_dir/explore-db.sh" HOTELS "$row_count"
