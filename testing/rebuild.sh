#!/bin/bash
# by Claude - Rebuild and restart servers
#
# Usage:
#   ./testing/rebuild.sh           # Rebuild both, restart both
#   ./testing/rebuild.sh server    # Rebuild and restart server only
#   ./testing/rebuild.sh frontend  # Rebuild and restart frontend only
#   ./testing/rebuild.sh all       # Same as no args

set -e

# Initialize paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export LK_TESTING_DIR="$SCRIPT_DIR"
source "$SCRIPT_DIR/lib.sh"

TARGET="${1:-all}"

case "$TARGET" in
    server|backend)
        print_header "Rebuilding Server"
        stop_process_on_port $BACKEND_PORT "Backend"

        echo "Building server..."
        (cd "$LK_PROJECT_DIR" && ./gradlew ${BACKEND_BUILD_TASK:-:server:compileKotlin})

        echo "Starting server..."
        ABSOLUTE_SETTINGS_FILE="$(cd "$(dirname "$SETTINGS_FILE")" && pwd)/$(basename "$SETTINGS_FILE")"
        (cd "$LK_PROJECT_DIR" && nohup ./gradlew --no-daemon ${BACKEND_RUN_TASK:-:server:run} --args="--settings $ABSOLUTE_SETTINGS_FILE serve" > "$BACKEND_LOG" 2>&1) &
        BACKEND_PID=$!

        if ! wait_for_process_or_ready $BACKEND_PID "http://localhost:$BACKEND_PORT/meta/health" "Backend" ${BACKEND_STARTUP_TIMEOUT:-90}; then
            echo "Backend startup failed. Last 30 lines:"
            tail -30 "$BACKEND_LOG"
            exit 1
        fi

        if grep -q "Admin token:" "$BACKEND_LOG"; then
            ADMIN_TOKEN=$(grep "Admin token:" "$BACKEND_LOG" | tail -1 | sed 's/.*Admin token: //')
            echo "$ADMIN_TOKEN" > "$ADMIN_TOKEN_FILE"
        fi

        echo ""
        echo "Server rebuilt and restarted on http://localhost:$BACKEND_PORT"
        ;;

    frontend|client)
        print_header "Rebuilding Frontend"
        stop_process_on_port $FRONTEND_PORT "Frontend"

        echo "Building frontend..."
        (cd "$LK_PROJECT_DIR" && ./gradlew ${FRONTEND_BUILD_TASK:-:apps:jsBrowserDevelopmentVitePrepare})

        echo "Starting frontend..."
        export FRONTEND_PORT
        export BACKEND_PORT
        (cd "$LK_PROJECT_DIR" && nohup ./gradlew ${FRONTEND_RUN_TASK:-:apps:jsViteDev} > "$FRONTEND_LOG" 2>&1) &
        FRONTEND_PID=$!

        if ! wait_for_process_or_ready $FRONTEND_PID "http://localhost:$FRONTEND_PORT" "Frontend" ${FRONTEND_STARTUP_TIMEOUT:-120}; then
            echo "Frontend startup failed. Last 30 lines:"
            tail -30 "$FRONTEND_LOG"
            exit 1
        fi

        echo ""
        echo "Frontend rebuilt and restarted on http://localhost:$FRONTEND_PORT"
        echo "(Vite cache cleared - browser will load fresh code)"
        ;;

    all|"")
        print_header "Rebuilding All"

        # Stop both
        stop_process_on_port $FRONTEND_PORT "Frontend"
        stop_process_on_port $BACKEND_PORT "Backend"

        # Build both
        echo "Building server and frontend..."
        (cd "$LK_PROJECT_DIR" && ./gradlew ${BACKEND_BUILD_TASK:-:server:compileKotlin} ${FRONTEND_BUILD_TASK:-:apps:jsBrowserDevelopmentVitePrepare})

        # Start backend
        echo "Starting backend..."
        ABSOLUTE_SETTINGS_FILE="$(cd "$(dirname "$SETTINGS_FILE")" && pwd)/$(basename "$SETTINGS_FILE")"
        (cd "$LK_PROJECT_DIR" && nohup ./gradlew --no-daemon ${BACKEND_RUN_TASK:-:server:run} --args="--settings $ABSOLUTE_SETTINGS_FILE serve" > "$BACKEND_LOG" 2>&1) &
        BACKEND_PID=$!

        if ! wait_for_process_or_ready $BACKEND_PID "http://localhost:$BACKEND_PORT/meta/health" "Backend" ${BACKEND_STARTUP_TIMEOUT:-90}; then
            echo "Backend startup failed. Last 30 lines:"
            tail -30 "$BACKEND_LOG"
            exit 1
        fi

        if grep -q "Admin token:" "$BACKEND_LOG"; then
            ADMIN_TOKEN=$(grep "Admin token:" "$BACKEND_LOG" | tail -1 | sed 's/.*Admin token: //')
            echo "$ADMIN_TOKEN" > "$ADMIN_TOKEN_FILE"
        fi

        # Start frontend
        echo "Starting frontend..."
        export FRONTEND_PORT
        export BACKEND_PORT
        (cd "$LK_PROJECT_DIR" && nohup ./gradlew ${FRONTEND_RUN_TASK:-:apps:jsViteDev} > "$FRONTEND_LOG" 2>&1) &
        FRONTEND_PID=$!

        if ! wait_for_process_or_ready $FRONTEND_PID "http://localhost:$FRONTEND_PORT" "Frontend" ${FRONTEND_STARTUP_TIMEOUT:-120}; then
            echo "Frontend startup failed. Last 30 lines:"
            tail -30 "$FRONTEND_LOG"
            exit 1
        fi

        echo ""
        echo "All rebuilt and restarted:"
        echo "  Backend:  http://localhost:$BACKEND_PORT"
        echo "  Frontend: http://localhost:$FRONTEND_PORT"
        ;;

    *)
        echo "Usage: $0 [server|frontend|all]"
        echo ""
        echo "  server    - Rebuild and restart server only"
        echo "  frontend  - Rebuild and restart frontend only"
        echo "  all       - Rebuild and restart both (default)"
        exit 1
        ;;
esac
