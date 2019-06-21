#!/bin/sh

DATE=$(date +%Y-%m-%d)
PROJECT="$(basename "$(pwd)")"
PROJECT_DIR="/var/www/$PROJECT"
OBSERVATIONS="$PROJECT_DIR/observations-$DATE.json"

mkdir -p "$PROJECT_DIR"

clj -A:website-data > "$OBSERVATIONS"

ln -sf "$OBSERVATIONS" "$PROJECT_DIR/observations.json"
