#!/bin/bash
# by Claude - Make authenticated API calls to the backend
#
# Usage: ./testing/api.sh <method> <path> [json-body]
#
# Examples:
#   ./testing/api.sh GET /meta/health
#   ./testing/api.sh GET /auth/session/self
#   ./testing/api.sh POST /users/query '{"condition":{"Always":true}}'
#
# Environment variables:
#   API_URL   - Override the API URL (default: http://localhost:$BACKEND_PORT)
#   API_TOKEN - Override the token (default: read from .admin-token)
#   VERBOSE   - Set to 1 for verbose curl output

set -e

# Initialize paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export LK_TESTING_DIR="$SCRIPT_DIR"
source "$SCRIPT_DIR/lib.sh"

API_URL="${API_URL:-http://localhost:$BACKEND_PORT}"

# Get method and path
METHOD="${1:-GET}"
PATH_ARG="${2:-/meta/health}"
BODY="$3"

# Ensure path starts with /
if [[ ! "$PATH_ARG" == /* ]]; then
    PATH_ARG="/$PATH_ARG"
fi

# Get token
if [[ -z "$API_TOKEN" ]]; then
    if [[ -f "$ADMIN_TOKEN_FILE" ]]; then
        API_TOKEN=$(cat "$ADMIN_TOKEN_FILE")
    else
        echo "WARNING: No token found. Making unauthenticated request." >&2
        echo "Run ./testing/setup.sh to start servers and get a token." >&2
    fi
fi

# Build curl command
CURL_ARGS=(-s)

if [[ "$VERBOSE" == "1" ]]; then
    CURL_ARGS=(-v)
fi

CURL_ARGS+=(-X "$METHOD")
CURL_ARGS+=("${API_URL}${PATH_ARG}")

if [[ -n "$API_TOKEN" ]]; then
    CURL_ARGS+=(-H "Authorization: Bearer $API_TOKEN")
fi

CURL_ARGS+=(-H "Content-Type: application/json")

if [[ -n "$BODY" ]]; then
    CURL_ARGS+=(-d "$BODY")
fi

# Execute
curl "${CURL_ARGS[@]}"
echo ""
