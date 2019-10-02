#!/usr/bin/env bash

SPREADSHEET_IDS="./queries/import-spreadsheet-ids.txt"

DATE=$(date +%Y-%m-%d)
LOGFILE="./logs/import-collections/$DATE-$ID.log"

mkdir -p "$(dirname "$LOGFILE")"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/import_collections.json \
              -q queries/mail-recipients.json \
              --google.spreadsheet_id "$1" \
              -d
}

echo "Starting the import of collections."

while IFS="" read -r ID
do
  doit "$ID" 2>&1 | tee -a "$LOGFILE"
done < "$SPREADSHEET_IDS"
