#!/usr/bin/env bash

SPREADSHEET_IDS="./queries/spreadsheet-ids.txt"
DATE=$(date +%Y-%m-%d)
MONTH=$(date +%B)
YEAR=$(date +%Y)
REPORT_DIR="reports/$YEAR/$MONTH"
COUNTER=0
QUERY_COUNT="$(wc -l < "$SPREADSHEET_IDS")"
LOGFILE="$REPORT_DIR/twitter-feeds-$DATE.log"

mkdir -p "$REPORT_DIR"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_twitter_feeds.json \
              -q queries/mail-recipients.json \
              --google.spreadsheet_id "$1" \
              --csv.data_dir "$REPORT_DIR" \
              --csv.label twitter-feeds \
              -d
}

echo "Starting a check for failing twitter feeds."

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

FAILED_STATS=$(find "$REPORT_DIR" -name "*failed-stats-twitter-feeds*.csv" -type f -printf '%T+ %p\n' | sort -r | head -n 1 | awk '{print $2}')
TOTAL=$(head -n 100 "$REPORT_DIR/twitter-feeds-$DATE.log" | sed -n 's/.*Fetched a total of \([0-9]*\) quer\(y\|ies\).$/\1/p')
if [ -n "$FAILED_STATS" ] && [ "$FAILED_STATS" != " " ]
then
  FAILED_COUNT=$(xsv count "$FAILED_STATS")
else
  FAILED_COUNT="0"
fi

REPORT="Report: Failing Twitter feeds

Verification Date: $DATE

In total $(numfmt --grouping "$FAILED_COUNT") of $(numfmt --grouping "$TOTAL") Twitter feeds failed.

"

echo "$REPORT" | tee "$REPORT_DIR/report-twitter-feeds-$DATE.txt"
