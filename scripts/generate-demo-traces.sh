#!/usr/bin/env bash
set -euo pipefail

base_url="${DEMO_BASE_URL:-http://localhost:8080}"
rounds="${DEMO_ROUNDS:-1}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

post_chat() {
  local message="$1"
  local conversation_id="${2:-}"
  local payload

  if [[ -n "$conversation_id" ]]; then
    payload="$(jq -cn --arg message "$message" --arg conversationId "$conversation_id" \
      '{message: $message, conversationId: $conversationId}')"
  else
    payload="$(jq -cn --arg message "$message" '{message: $message}')"
  fi

  curl --fail --silent --show-error \
    -X POST "$base_url/api/chat" \
    -H 'Content-Type: application/json' \
    -d "$payload"
}

echo "Generating conference traces against $base_url ($rounds round(s))"

for ((round = 1; round <= rounds; round++)); do
  echo "Round $round/$rounds"

  first="$(post_chat 'Find a quiet lakeside hotel near Lucerne under CHF 250.')"
  conversation_id="$(jq -r '.conversationId' <<<"$first")"
  echo "  hotel search: $(jq -r '.message' <<<"$first" | head -n 1)"

  saved="$(post_chat 'Save the first hotel from those results to my wishlist.' "$conversation_id")"
  echo "  wishlist save: $(jq -r '.message' <<<"$saved" | head -n 1)"

  wishlist="$(post_chat 'What is currently on my wishlist?' "$conversation_id")"
  echo "  wishlist read: $(jq -r '.message' <<<"$wishlist" | head -n 1)"

  post_chat 'Show scenic activities within 40 km of Interlaken.' >/dev/null
  post_chat 'Recommend ski destinations near Zermatt.' >/dev/null
  post_chat 'Find boutique hotels near Bern under CHF 220.' >/dev/null
done

echo "Done. Wait a few seconds for OTLP export and CODE annotations, then open Phoenix:"
echo "  http://localhost:6006"
echo "For a denser metrics dashboard, run: DEMO_ROUNDS=3 $0"
