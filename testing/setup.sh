#!/bin/bash
# by Claude - Main setup script for testing environment
#
# Usage:
#   ./testing/setup.sh              # Stop existing, start fresh
#   ./testing/setup.sh --rebuild    # Rebuild everything, then start
#   ./testing/setup.sh --backend    # Start backend only
#   ./testing/setup.sh --frontend   # Start frontend only

set -e

# Initialize paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export LK_TESTING_DIR="$SCRIPT_DIR"
source "$SCRIPT_DIR/lib.sh"

# Parse arguments
REBUILD=false
BACKEND_ONLY=false
FRONTEND_ONLY=false

for arg in "$@"; do
    case $arg in
        --rebuild)
            REBUILD=true
            ;;
        --backend)
            BACKEND_ONLY=true
            ;;
        --frontend)
            FRONTEND_ONLY=true
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --rebuild    Rebuild before starting"
            echo "  --backend    Start backend only"
            echo "  --frontend   Start frontend only"
            echo "  --help       Show this help"
            exit 0
            ;;
    esac
done

# Determine what to start
START_BACKEND=true
START_FRONTEND=true

if [[ "$BACKEND_ONLY" == "true" ]]; then
    START_FRONTEND=false
fi
if [[ "$FRONTEND_ONLY" == "true" ]]; then
    START_BACKEND=false
fi

print_header "Test Environment Setup"
echo "Project: $LK_PROJECT_DIR"
echo "Backend port: $BACKEND_PORT"
echo "Frontend port: $FRONTEND_PORT"

# Always stop existing servers first
print_header "Stopping Existing Servers"

if [[ "$START_BACKEND" == "true" ]]; then
    stop_process_on_port $BACKEND_PORT "Backend"
fi
if [[ "$START_FRONTEND" == "true" ]]; then
    stop_process_on_port $FRONTEND_PORT "Frontend"
fi

# Rebuild if requested
if [[ "$REBUILD" == "true" ]]; then
    print_header "Rebuilding"

    if [[ "$START_BACKEND" == "true" && "$START_FRONTEND" == "true" ]]; then
        echo "Building server and frontend..."
        (cd "$LK_PROJECT_DIR" && ./gradlew ${BACKEND_BUILD_TASK:-:server:compileKotlin} ${FRONTEND_BUILD_TASK:-:apps:jsBrowserDevelopmentVitePrepare})
    elif [[ "$START_BACKEND" == "true" ]]; then
        echo "Building server..."
        (cd "$LK_PROJECT_DIR" && ./gradlew ${BACKEND_BUILD_TASK:-:server:compileKotlin})
    elif [[ "$START_FRONTEND" == "true" ]]; then
        echo "Building frontend..."
        (cd "$LK_PROJECT_DIR" && ./gradlew ${FRONTEND_BUILD_TASK:-:apps:jsBrowserDevelopmentVitePrepare})
    fi
fi

# Start servers
print_header "Starting Servers"

# Start backend
if [[ "$START_BACKEND" == "true" ]]; then
    echo "Starting backend on port $BACKEND_PORT..."

    # Ensure settings file exists
    if [[ ! -f "$SETTINGS_FILE" ]]; then
        echo "ERROR: Settings file not found: $SETTINGS_FILE"
        echo "Create one based on the template or your project's settings.json"
        exit 1
    fi

    # Use absolute path for settings file
    ABSOLUTE_SETTINGS_FILE="$(cd "$(dirname "$SETTINGS_FILE")" && pwd)/$(basename "$SETTINGS_FILE")"

    # Start backend in background with settings file argument
    (cd "$LK_PROJECT_DIR" && nohup ./gradlew --no-daemon ${BACKEND_RUN_TASK:-:server:run} --args="--settings $ABSOLUTE_SETTINGS_FILE serve" > "$BACKEND_LOG" 2>&1) &
    BACKEND_PID=$!

    # Wait for backend to be ready
    if ! wait_for_process_or_ready $BACKEND_PID "http://localhost:$BACKEND_PORT/meta/health" "Backend" ${BACKEND_STARTUP_TIMEOUT:-90}; then
        echo ""
        echo "Backend startup failed. Last 30 lines of log:"
        tail -30 "$BACKEND_LOG"
        exit 1
    fi

    # Extract admin token if debug mode creates one
    if grep -q "Admin token:" "$BACKEND_LOG"; then
        ADMIN_TOKEN=$(grep "Admin token:" "$BACKEND_LOG" | tail -1 | sed 's/.*Admin token: //')
        echo "$ADMIN_TOKEN" > "$ADMIN_TOKEN_FILE"
        echo "Admin token saved to $ADMIN_TOKEN_FILE"
    fi
fi

# Start frontend
if [[ "$START_FRONTEND" == "true" ]]; then
    echo "Starting frontend on port $FRONTEND_PORT..."

    # Export ports for vite.config.mjs
    export FRONTEND_PORT
    export BACKEND_PORT

    # Start frontend in background
    (cd "$LK_PROJECT_DIR" && nohup ./gradlew ${FRONTEND_RUN_TASK:-:apps:jsViteDev} > "$FRONTEND_LOG" 2>&1) &
    FRONTEND_PID=$!

    # Wait for frontend to be ready
    if ! wait_for_process_or_ready $FRONTEND_PID "http://localhost:$FRONTEND_PORT" "Frontend" ${FRONTEND_STARTUP_TIMEOUT:-120}; then
        echo ""
        echo "Frontend startup failed. Last 30 lines of log:"
        tail -30 "$FRONTEND_LOG"
        exit 1
    fi
fi

# Summary
print_header "Ready!"

if [[ "$START_BACKEND" == "true" ]]; then
    echo "Backend:  http://localhost:$BACKEND_PORT"
fi
if [[ "$START_FRONTEND" == "true" ]]; then
    echo "Frontend: http://localhost:$FRONTEND_PORT"
fi

if [[ -f "$ADMIN_TOKEN_FILE" ]]; then
    echo ""
    echo "Admin token: $(cat "$ADMIN_TOKEN_FILE")"
fi

echo ""
echo "Commands:"
echo "  ./testing/stop.sh           - Stop all servers"
echo "  ./testing/rebuild.sh        - Rebuild and restart"
echo "  ./testing/api.sh GET /path  - Make API calls"
