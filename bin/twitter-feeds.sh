#!/usr/bin/env bash

SPREADSHEET_IDS="./queries/spreadsheet-ids.txt"

DATE=$(date +%Y-%m-%d)
COUNTER=0
QUERY_COUNT="$(wc -l < "$SPREADSHEET_IDS")"
LOGFILE="./logs/twitter-feeds/$DATE-$ID.log"

mkdir -p "$(dirname "$LOGFILE")"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/twitter_feeds.json \
              -Q sheets_query:TwitterFeeds \
              --google.spreadsheet_id "$1" \
              -d
}

echo "Starting the scrape of Twitter feeds."

while IFS="" read -r ID
do
  if [ $((COUNTER % 10)) -eq 0 ] && [ "$COUNTER" -gt 0 ]
  then
    echo "Processed $COUNTER queries"
  fi

  doit "$ID" 2>&1 | tee -a "$LOGFILE"

  if [ "$QUERY_COUNT" -eq $((COUNTER + 1)) ]
  then
    exit 0
  fi

  WAIT_TIME=$(((RANDOM % 10)  + 10))
  echo "Sleeping for $WAIT_TIME seconds."
  sleep $WAIT_TIME
  COUNTER=$((COUNTER+1))
done < "$SPREADSHEET_IDS"
