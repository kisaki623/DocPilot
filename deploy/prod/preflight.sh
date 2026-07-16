#!/usr/bin/env sh
set -eu

ENV_FILE="${1:-deploy/prod/.env.prod}"

fail() {
  printf '%s\n' "ERROR: $*" >&2
  exit 1
}

warn() {
  printf '%s\n' "WARN: $*" >&2
}

get_env() {
  key="$1"
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  if [ -z "$line" ]; then
    printf ''
    return
  fi
  printf '%s' "${line#*=}"
}

require_non_empty() {
  key="$1"
  value="$(get_env "$key")"
  [ -n "$value" ] || fail "$key is required in $ENV_FILE"
}

require_equals() {
  key="$1"
  expected="$2"
  value="$(get_env "$key")"
  [ "$value" = "$expected" ] || fail "$key must be '$expected'"
}

[ -f "$ENV_FILE" ] || fail "$ENV_FILE does not exist. Copy deploy/prod/.env.prod.example first."

if grep -nE 'CHANGE_ME|<YOUR_|docpilot_rag_demo|docpilot_rag_prod' "$ENV_FILE" >/dev/null; then
  fail "$ENV_FILE still contains placeholder or unsafe demo/prod collection defaults."
fi

required_keys='
DOCPILOT_APP_NETWORK
DOCPILOT_MIDDLEWARE_NETWORK
MYSQL_HOST
MYSQL_DATABASE
MYSQL_DB
MYSQL_USERNAME
MYSQL_PASSWORD
REDIS_HOST
ROCKETMQ_NAME_SERVER
ROCKETMQ_PRODUCER_GROUP
ROCKETMQ_CONSUMER_GROUP
DOCPILOT_PARSE_TOPIC
FILE_STORAGE_MODE
MINIO_ENDPOINT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
MINIO_BUCKET
MINIO_BASE_PATH
RAG_VECTOR_STORE_PROVIDER
RAG_QDRANT_HOST
RAG_QDRANT_PORT
RAG_QDRANT_COLLECTION
RAG_QDRANT_DIMENSION
RAG_QDRANT_DISTANCE
RAG_QDRANT_COLLECTION_INIT_ENABLED
AI_MODE
AI_REAL_PROVIDER
AI_REAL_BASE_URL
AI_REAL_API_KEY
APP_RAG_EMBEDDING_ENABLED
APP_RAG_EMBEDDING_PROVIDER
APP_RAG_EMBEDDING_BASE_URL
APP_RAG_EMBEDDING_API_KEY
APP_RAG_EMBEDDING_MODEL
APP_RAG_EMBEDDING_DIMENSION
'

for key in $required_keys; do
  require_non_empty "$key"
done

require_equals "RAG_VECTOR_STORE_PROVIDER" "qdrant"
require_equals "RAG_QDRANT_COLLECTION_INIT_ENABLED" "false"
require_equals "AI_MODE" "real"
require_equals "APP_RAG_EMBEDDING_ENABLED" "true"

qdrant_dimension="$(get_env RAG_QDRANT_DIMENSION)"
embedding_dimension="$(get_env APP_RAG_EMBEDDING_DIMENSION)"
[ "$qdrant_dimension" = "$embedding_dimension" ] || fail "RAG_QDRANT_DIMENSION must equal APP_RAG_EMBEDDING_DIMENSION"

case "$qdrant_dimension" in
  ''|*[!0-9]*) fail "RAG_QDRANT_DIMENSION must be a positive integer" ;;
esac
[ "$qdrant_dimension" -gt 0 ] || fail "RAG_QDRANT_DIMENSION must be a positive integer"

if [ "$(get_env APP_RAG_RERANK_ENABLED)" = "true" ]; then
  require_non_empty "APP_RAG_RERANK_PROVIDER"
  require_non_empty "APP_RAG_RERANK_BASE_URL"
  require_non_empty "APP_RAG_RERANK_API_KEY"
  require_non_empty "APP_RAG_RERANK_MODEL"
fi

if [ "$(get_env APP_QUALITY_CONSOLE_ENABLED)" = "true" ]; then
  warn "APP_QUALITY_CONSOLE_ENABLED=true; make sure only internal admins can access /quality."
fi

if command -v docker >/dev/null 2>&1; then
  app_network="$(get_env DOCPILOT_APP_NETWORK)"
  middleware_network="$(get_env DOCPILOT_MIDDLEWARE_NETWORK)"
  docker network inspect "$app_network" >/dev/null 2>&1 || fail "Docker network '$app_network' does not exist"
  docker network inspect "$middleware_network" >/dev/null 2>&1 || fail "Docker network '$middleware_network' does not exist"
else
  warn "docker command not found; skipped Docker network existence checks."
fi

printf '%s\n' "DocPilot production env preflight PASS."
