#!/usr/bin/env bash

SPREADSHEET_IDS="./queries/export-spreadsheet-ids.txt"
DATE=$(date +%Y-%m-%d)
COLUMNS="$1"
TARGET_SPREADSHEET="$2"
TARGET_SHEET="$3"

help() {
  echo "Usage: ./bin/export-videos.sh <COLUMNS> <TARGET SPREADSHEET> [<TARGET SHEET>]"
  echo ""
  echo "COLUMNS: Specify the columns to import from, e.g. B or B,C,AC"
  echo "TARGET SPREADSHEET: Supply the spreadsheet id of the target."
  echo "TARGET SHEET: This parameter is optional. Specify the name of the sheet to export to. If omitted use the column specification as default."
}

if [ -z "$COLUMNS" ];
then
  echo "Missing COLUMNS."
  help
  exit 1
fi

if [ -z "$TARGET_SPREADSHEET" ];
then
  echo "Missing TARGET SPREADSHEET."
  help
  exit 1
fi

if [ -z "$TARGET_SHEET" ];
then
  TARGET_SHEET="$COLUMNS"
fi

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/export_videos.json \
              --query.spreadsheet_id "$1" \
              --query.export_columns "$2" \
              --google.spreadsheet_id "$3" \
              --google.sheet "$4"
}

echo "Starting export of videos."

while IFS="" read -r ID
do
  doit "$ID" "$COLUMNS" "$TARGET_SPREADSHEET" "$TARGET_SHEET" | tee -a ./logs/export-by-columns-"$DATE".log
done < "$SPREADSHEET_IDS"
