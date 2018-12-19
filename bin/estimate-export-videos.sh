#!/usr/bin/env bash

SPREADSHEET_IDS="./queries/export-spreadsheet-ids.txt"
DATE=$(date +%Y-%m-%d)
COLUMNS="$1"

help() {
  echo "Usage: ./bin/estimate-export-videos.sh <COLUMNS>"
  echo ""
  echo "COLUMNS: Specify the columns to import from, e.g. B or B,C,AC"
}

if [ -z "$COLUMNS" ];
then
  echo "Missing COLUMNS."
  help
  exit 1
fi

doit() {
  "$(npm bin)"/sugarcube \
              -p query_column,elastic_import \
              -c pipelines/export_videos.json \
              --query.spreadsheet_id "$1" \
              --query.export_columns "$2"
}

echo "Starting estimate export of videos."

while IFS="" read -r ID
do
  doit "$ID" "$COLUMNS"
done < "$SPREADSHEET_IDS"
