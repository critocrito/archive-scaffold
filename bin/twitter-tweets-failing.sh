#!/usr/bin/env bash

DATE=$(date +%Y-%m-%d)
MONTH=$(date +%B)
YEAR=$(date +%Y)
REPORT_DIR="reports/$YEAR/$MONTH"
LOGFILE="./$REPORT_DIR/twitter-tweets-$DATE.log"

mkdir -p "$REPORT_DIR"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_twitter_tweets.json \
              --csv.data_dir "$REPORT_DIR" \
              --csv.label twitter-tweets \
              -d
}

echo "Starting a check for failing twitter tweets."

ALL_TWEETS=$(./bin/stats-sources.sh | awk 'BEGIN{FS=","; c=0; } $1~/^twitter/{ c+=$2 } END{print c}')

doit 2>&1 | tee -a "$LOGFILE"

FAILED_STATS=$(find "$REPORT_DIR"  -name "*failed-stats-twitter-tweets*.csv" -type f -printf '%T+ %p\n' | sort -r | head -n 1 | awk '{print $2}')
if [ -n "$FAILED_STATS" ] && [ "$FAILED_STATS" != " " ]
then
  TOTAL=$(xsv count "$FAILED_STATS")
else
  TOTAL="0"
fi

REPORT="Report: Failing Twitter tweets

Verification Date: $DATE

In total $(numfmt --grouping "$TOTAL") of $(numfmt --grouping "$ALL_TWEETS") twitter tweets failed.

"

echo "$REPORT" | tee "$REPORT_DIR/report-twitter-tweets-$DATE.txt"
