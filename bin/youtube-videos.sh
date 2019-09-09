#!/usr/bin/env bash

. bin/subr.sh

SPREADSHEET_IDS="./queries/spreadsheet-ids.txt"

DATE=$(date +%Y-%m-%d)
COUNTER=0
QUERY_COUNT="$(wc -l < "$SPREADSHEET_IDS")"
RUN_ID=$(make_id)

provision_vps "$RUN_ID" "small"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/youtube_videos.json \
              -q queries/mail-recipients.json \
              -Q sheets_query:YoutubeVideosIncoming \
              --media.youtubedl_cmd "$PWD"/bin/youtube-dl-wrapper-sudo-"$RUN_ID".sh \
              --google.spreadsheet_id "$1" \
              --google.to_spreadsheet_id "$1" \
              --google.to_sheet YoutubeVideosDone \
              -d
}

echo "Starting the incoming scrape of youtube videos."

while IFS="" read -r ID
do
  if [ $((COUNTER % 10)) -eq 0 ] && [ "$COUNTER" -gt 0 ]
  then
    echo "Processed $COUNTER queries"
  fi

  doit "$ID" 2>&1 | tee -a ./logs/youtube-videos-"$ID"-"$DATE".log

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
