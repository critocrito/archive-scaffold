#!/usr/bin/env bash

. bin/subr.sh

DATE=$(date +%Y-%m-%d)
MONTH=$(date +%B)
YEAR=$(date +%Y)
REPORT_DIR="reports/$YEAR/$MONTH"
RUN_ID=$(make_id)

provision_vps "$RUN_ID" "small"

mkdir -p "$REPORT_DIR"

export NODE_OPTIONS=--max_old_space_size=16384

percent() {
  echo "scale=2; $2*100/$1" | bc -l | sed -e 's/^\.\(.*\)/0\.\1/g'
}

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_youtube_videos.json \
              -q queries/mail-recipients.json \
              --media.youtubedl_cmd "$PWD"/bin/youtube-dl-wrapper-sudo-"$RUN_ID".sh \
              --csv.data_dir "$REPORT_DIR" \
              --csv.label youtube-videos \
              -d
}

echo "Starting a check for failing youtube videos."

ALL_YT_VIDEOS=$(./bin/stats-sources.sh | awk 'BEGIN{FS=","; c=0; } $1~/^youtube/{ c+=$2 } END{print c}')

doit 2>&1 | tee -a "./$REPORT_DIR/youtube-videos-$DATE.log"

destroy_vps "$RUN_ID"

FAILED_STATS=$(find "$REPORT_DIR"  -name "*failed-stats-youtube-video*.csv" -type f -printf '%T+ %p\n' | sort -r | head -n 1 | awk '{print $2}')
FREQUENCIES="$REPORT_DIR/frequencies-youtube-videos.csv"

{
  echo "reason";
  xsv select reason "$FAILED_STATS" \
    | awk 'BEGIN {getline} /^".*[^"]$/{getline x}{print $0 " " x; x=""}' \
    | sed 's/"//g' \
    | awk '{$1=$1};1' \
    | sed 's/\(.*\)/"\1"/g'
} | xsv frequency | xsv fmt -t "#" > "$FREQUENCIES"

TOTAL=$(awk 'BEGIN{FS="#"} {c+=$3} END {print c}' "$FREQUENCIES")

USER_REGEX="removed.*user\|uploader.*closed"
COPYRIGHT_REGEX="copyright"
UNAVAILABLE_REGEX="This video is unavailable\."
TERMINATED_REGEX="video has been terminated"
TOS_REGEX="terms of service"

USER=$(grep -i "$USER_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')
COPYRIGHT=$(grep -i "$COPYRIGHT_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')
UNAVAILABLE=$(grep -i "$UNAVAILABLE_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')
TERMINATED=$(grep -i "$TERMINATED_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')
TOS=$(grep -i "$TOS_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')

REST=$(grep -iv "$USER_REGEX" "$FREQUENCIES" \
         | grep -v "$COPYRIGHT_REGEX" \
         | grep -iv "$UNAVAILABLE_REGEX" \
         | grep -iv "$TERMINATED_REGEX" \
         | grep -iv "$TOS_REGEX" \
         | tail -n+2 \
         | awk 'BEGIN{FS="#"} {print $2 ": " $3}')

USER_PERCENT=$(percent "$TOTAL" "$USER")
COPYRIGHT_PERCENT=$(percent "$TOTAL" "$COPYRIGHT")
UNAVAILABLE_PERCENT=$(percent "$TOTAL" "$UNAVAILABLE")
TERMINATED_PERCENT=$(percent "$TOTAL" "$TERMINATED")
TOS_PERCENT=$(percent "$TOTAL" "$TOS")

rm "$FREQUENCIES"

REPORT="Report: Failing Youtube videos

Verification date: $DATE

In total $(numfmt --grouping "$TOTAL") of $(numfmt --grouping "$ALL_YT_VIDEOS") youtube videos failed to download

Videos deleted after Youtube terminated the account: $(numfmt --grouping "$TERMINATED") ($TERMINATED_PERCENT%)
Videos deleted on ground of Terms of Service violations: $(numfmt --grouping "$TOS") ($TOS_PERCENT%)
Videos deleted on grounds of copyright violations: $(numfmt --grouping "$COPYRIGHT") ($COPYRIGHT_PERCENT%)
Videos failing with no further reasons: $(numfmt --grouping "$UNAVAILABLE") ($UNAVAILABLE_PERCENT%)
Videos removed by the user: $(numfmt --grouping "$USER") ($USER_PERCENT%)

The other counts look like this:

$REST

"

echo "$REPORT" | tee "$REPORT_DIR/report-youtube-videos.txt"
