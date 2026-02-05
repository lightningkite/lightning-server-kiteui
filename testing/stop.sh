#!/bin/bash
# by Claude - Stop all testing servers
# Usage: ./testing/stop.sh

set -e

# Initialize paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export LK_TESTING_DIR="$SCRIPT_DIR"
source "$SCRIPT_DIR/lib.sh"

print_header "Stopping Test Servers"

# Stop frontend first (faster)
stop_process_on_port $FRONTEND_PORT "Frontend"

# Stop backend
stop_process_on_port $BACKEND_PORT "Backend"

echo ""
echo "All servers stopped."
