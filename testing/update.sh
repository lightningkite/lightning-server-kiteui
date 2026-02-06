#!/bin/bash
# by Claude - Update testing scripts from source
#
# This script checks for updates from the original source location
# and updates the scripts while preserving config.env and settings.json

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ ! -f "$SCRIPT_DIR/.scripts-source" ]]; then
    echo "ERROR: No source path found at $SCRIPT_DIR/.scripts-source"
    echo "Was this installed with install.sh?"
    exit 1
fi

SOURCE_DIR=$(cat "$SCRIPT_DIR/.scripts-source")

if [[ ! -d "$SOURCE_DIR" ]]; then
    echo "ERROR: Source not found at $SOURCE_DIR"
    echo "Has it been moved or deleted?"
    exit 1
fi

echo "Updating from $SOURCE_DIR..."
exec "$SOURCE_DIR/install.sh" "$SCRIPT_DIR/.."
