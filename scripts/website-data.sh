#!/bin/sh

DATE=$(date +%Y-%m-%d)
PROJECT="$(basename "$(pwd)")"
PROJECT_DIR="/var/www/$PROJECT"
OBSERVATIONS="$PROJECT_DIR/observations-$DATE.json"

mkdir -p "$PROJECT_DIR"

clj -A:website-data > "$OBSERVATIONS"

COUNT_MISSING_LOCATIONS=$(./scripts/verify-website-data.sh "missing-location" "$OBSERVATIONS" | tail -n+2 | grep . -c)

if [ "$COUNT_MISSING_LOCATIONS" != "0" ]
then
  echo "Validation error: Found $COUNT_MISSING_LOCATIONS observations with missing location in $OBSERVATIONS."
  exit 1
fi

ln -sf "$OBSERVATIONS" "$PROJECT_DIR/observations.json"
