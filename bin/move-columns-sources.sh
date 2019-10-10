#!/usr/bin/env bash

SPREADSHEET_IDS="./queries/spreadsheet-ids.txt"
DATE=$(date +%Y-%m-%d)
LOGFILE="./logs/move-columns-sources/$DATE.log"

mkdir -p "$(dirname "$LOGFILE")"

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/move-columns-sources.json \
              -q queries/columns-sources-queries.json \
              --google.spreadsheet_id "$1" \
              -d
}

echo "Starting to move columns based sources."

while IFS="" read -r ID
do
  doit "$ID" 2>&1 | tee -a "$LOGFILE"
done < "$SPREADSHEET_IDS"
