#!/bin/bash
# by Claude - Shared functions for lk-testing-scripts
# This file is sourced by other scripts to provide common utilities.

# Load project-specific configuration
# Expects LK_TESTING_DIR to be set by the calling script
if [[ -z "$LK_TESTING_DIR" ]]; then
    echo "ERROR: LK_TESTING_DIR not set" >&2
    exit 1
fi

if [[ -f "$LK_TESTING_DIR/config.env" ]]; then
    source "$LK_TESTING_DIR/config.env"
else
    echo "ERROR: $LK_TESTING_DIR/config.env not found" >&2
    exit 1
fi

# Derive paths from config
LK_PROJECT_DIR="${LK_PROJECT_DIR:-$(cd "$LK_TESTING_DIR/.." && pwd)}"
BACKEND_LOG="${BACKEND_LOG:-$LK_TESTING_DIR/.backend.log}"
FRONTEND_LOG="${FRONTEND_LOG:-$LK_TESTING_DIR/.frontend.log}"
ADMIN_TOKEN_FILE="${ADMIN_TOKEN_FILE:-$LK_TESTING_DIR/.admin-token}"
SETTINGS_FILE="${SETTINGS_FILE:-$LK_TESTING_DIR/settings.json}"

# Get the PID of a process listening on a specific port
get_pid_on_port() {
    local port=$1
    lsof -ti :$port -sTCP:LISTEN 2>/dev/null | head -1
}

# Check if a port is available (not in use)
is_port_available() {
    local port=$1
    ! lsof -i :$port -sTCP:LISTEN > /dev/null 2>&1
}

# Stop a process on a specific port with grace period
stop_process_on_port() {
    local port=$1
    local name=${2:-"process"}
    local pid=$(get_pid_on_port $port)

    if [[ -z "$pid" ]]; then
        echo "$name: not running on port $port"
        return 0
    fi

    echo "$name: stopping PID $pid on port $port..."

    # Try graceful shutdown first
    kill $pid 2>/dev/null

    # Wait up to 5 seconds for graceful shutdown
    local count=0
    while [[ $count -lt 50 ]]; do
        if is_port_available $port; then
            echo "$name: stopped"
            return 0
        fi
        sleep 0.1
        ((count++))
    done

    # Force kill if still running
    pid=$(get_pid_on_port $port)
    if [[ -n "$pid" ]]; then
        echo "$name: force killing PID $pid..."
        kill -9 $pid 2>/dev/null
        sleep 0.5
    fi

    if is_port_available $port; then
        echo "$name: stopped (forced)"
        return 0
    else
        echo "$name: WARNING - port $port still in use"
        return 1
    fi
}

# Wait for a service to become available
wait_for_service() {
    local url=$1
    local name=$2
    local timeout=${3:-60}

    echo "$name: waiting for $url (timeout: ${timeout}s)..."

    local count=0
    while [[ $count -lt $timeout ]]; do
        if curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null | grep -q "^[23]"; then
            echo "$name: ready"
            return 0
        fi
        sleep 1
        ((count++))

        # Show progress every 10 seconds
        if [[ $((count % 10)) -eq 0 ]]; then
            echo "$name: still waiting... (${count}s)"
        fi
    done

    echo "$name: TIMEOUT after ${timeout}s"
    return 1
}

# Wait for a background process to be ready or fail
# Monitors both the process and a health endpoint
wait_for_process_or_ready() {
    local pid=$1
    local url=$2
    local name=$3
    local timeout=${4:-60}

    echo "$name: waiting for startup (PID: $pid, timeout: ${timeout}s)..."

    local count=0
    while [[ $count -lt $timeout ]]; do
        # Check if process died
        if ! kill -0 $pid 2>/dev/null; then
            echo "$name: process died unexpectedly"
            return 1
        fi

        # Check if service is ready
        if curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null | grep -q "^[23]"; then
            echo "$name: ready"
            return 0
        fi

        sleep 1
        ((count++))

        if [[ $((count % 15)) -eq 0 ]]; then
            echo "$name: still starting... (${count}s)"
        fi
    done

    echo "$name: TIMEOUT after ${timeout}s"
    return 1
}

# Print a section header
print_header() {
    echo ""
    echo "========================================"
    echo "$1"
    echo "========================================"
}
