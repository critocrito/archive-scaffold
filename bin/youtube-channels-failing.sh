#!/usr/bin/env bash

. bin/subr.sh

SPREADSHEET_IDS="./queries/spreadsheet-ids.txt"
DATE=$(date +%Y-%m-%d)
MONTH=$(date +%B)
YEAR=$(date +%Y)
REPORT_DIR="reports/$YEAR/$MONTH"
COUNTER=0
QUERY_COUNT="$(wc -l < "$SPREADSHEET_IDS")"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_youtube_channels.json \
              -q queries/mail-recipients.json \
              --google.spreadsheet_id "$1" \
              --csv.data_dir "$REPORT_DIR" \
              --csv.label youtube-channels \
              -d
}

echo "Starting a check for failing youtube channels."

while IFS="" read -r ID
do
  if [ $((COUNTER % 10)) -eq 0 ] && [ "$COUNTER" -gt 0 ]
  then
    echo "Processed $COUNTER queries"
  fi

  doit "$ID" 2>&1 | tee -a "$REPORT_DIR/youtube-channels-$DATE.log"

  if [ "$QUERY_COUNT" -eq $((COUNTER + 1)) ]
  then
    exit 0
  fi

  WAIT_TIME=$(((RANDOM % 10)  + 10))
  echo "Sleeping for $WAIT_TIME seconds."
  sleep $WAIT_TIME
  COUNTER=$((COUNTER+1))
done < "$SPREADSHEET_IDS"

FAILED_STATS=$(find "$REPORT_DIR"  -name "*failed-stats-youtube-channels*.csv" -type f -printf '%T+ %p\n' | sort -r | head -n 1 | awk '{print $2}')
TOTAL=$(head -n 100 "$REPORT_DIR/youtube-channels-$DATE.log" | sed -n 's/.*Fetched a total of \([0-9]*\) quer\(y\|ies\).$/\1/p')
if [ -n "$FAILED_STATS" ] && [ "$FAILED_STATS" != " " ]
then
  FAILED_COUNT=$(xsv count "$FAILED_STATS")
else
  FAILED_COUNT="0"
fi

REPORT="Report: Failing Youtube channels

Verification Date: $DATE

In total $(numfmt --grouping "$FAILED_COUNT") of $(numfmt --grouping "$TOTAL") Youtube channels failed.

"

echo "$REPORT" | tee "$REPORT_DIR/report-youtube-channels.txt"
