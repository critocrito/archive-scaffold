#!/usr/bin/env bash

SPREADSHEET_IDS="./queries/spreadsheet-ids.txt"

DATE=$(date +%Y-%m-%d)
COUNTER=0
QUERY_COUNT="$(wc -l < "$SPREADSHEET_IDS")"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_youtube_channels.json \
              -q queries/mail-recipients.json \
              --google.spreadsheet_id "$1" \
              -d
}

echo "Starting a check for failing youtube channels."

while IFS="" read -r ID
do
  if [ $((COUNTER % 10)) -eq 0 ] && [ "$COUNTER" -gt 0 ]
  then
    echo "Processed $COUNTER queries"
  fi

  doit "$ID" 2>&1 | tee -a ./logs/failing-youtube-channels-"$ID"-"$DATE".log

  if [ "$QUERY_COUNT" -eq $((COUNTER + 1)) ]
  then
    exit 0
  fi

  WAIT_TIME=$(((RANDOM % 10)  + 10))
  echo "Sleeping for $WAIT_TIME seconds."
  sleep $WAIT_TIME
  COUNTER=$((COUNTER+1))
done < "$SPREADSHEET_IDS"
