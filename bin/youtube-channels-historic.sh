#!/usr/bin/env bash

. bin/subr.sh

SPREADSHEET_IDS="./queries/spreadsheet-ids.txt"

PIPELINE_CFG="./pipelines/youtube_channels.json"
PIPELINE_NAME=$(pipeline_name "$PIPELINE_CFG")
LABEL=$(snake_case "$PIPELINE_NAME")
DATE=$(date +%Y-%m-%d)
COUNTER=0
QUERY_COUNT="$(wc -l < "$SPREADSHEET_IDS")"
RUN_ID=$(make_id)
RUN_DIR="$PWD/tmp/$RUN_ID"
LOGFILE="./logs/youtube-channels-historic-$ID-$DATE.log"

provision_vps "$RUN_ID" "medium" "$LABEL" | tee -a "$LOGFILE"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c "$PIPELINE_CFG" \
              -q queries/mail-recipients.json \
              -Q sheets_query:YoutubeChannelsHistoric \
              --media.youtubedl_cmd "$RUN_DIR"/youtube-dl-wrapper-sudo.sh \
              --google.spreadsheet_id "$1" \
              --google.to_spreadsheet_id "$1" \
              --google.to_sheet YoutubeChannelsDaily \
              -d
}

echo "Starting historic scrape of youtube channels."

while IFS="" read -r ID
do
  if [ $((COUNTER % 10)) -eq 0 ] && [ "$COUNTER" -gt 0 ]
  then
    echo "Processed $COUNTER queries"
  fi

  doit "$ID" 2>&1 | tee -a "$LOGFILE"

  if [ "$QUERY_COUNT" -eq $((COUNTER + 1)) ]
  then
    destroy_vps "$RUN_ID"
    exit 0
  fi

  WAIT_TIME=$(((RANDOM % 10)  + 10))
  echo "Sleeping for $WAIT_TIME seconds."
  sleep $WAIT_TIME
  COUNTER=$((COUNTER+1))
done < "$SPREADSHEET_IDS"
