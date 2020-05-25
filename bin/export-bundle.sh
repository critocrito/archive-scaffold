#!/usr/bin/env bash
set -euo pipefail

trap cleanup 0 1 2 3 6 15

. bin/subr.sh

SPREADSHEET_ID="$1"

PIPELINE_CFG="./pipelines/export_bundle.json"
DATE="$(date +%Y-%m-%d)"
RUN_ID="$(make_id)"
EXPORT_DIR="exports/$RUN_ID"
EXPORT_ZIP="exports/$RUN_ID.zip"
EXPORT_JSON="$EXPORT_DIR/$SPREADSHEET_ID.json"
LOGFILE="./logs/export-bundle/$DATE-$RUN_ID.log"

## Remove any temporary files.
cleanup() {
    [ -d "$EXPORT_DIR" ] && rm -rf "$EXPORT_DIR"
}

mkdir -p "$(dirname "$LOGFILE")"
mkdir -p "$EXPORT_DIR"

export NODE_OPTIONS=--max_old_space_size=16384

"$(npm bin)"/sugarcube \
      -c "$PIPELINE_CFG" \
      --google.spreadsheet_id "$SPREADSHEET_ID" \
      --google.sheet Collection \
      --tap.filename "$EXPORT_JSON" \
      -d 2>&1 | tee -a "$LOGFILE"

TOTAL_EXPORTS="$(jq '. | length' "$EXPORT_JSON")"
SUCCESS=0
ERROR=0

while read -r UNIT
do
    ID_HASH=$(echo -E "$UNIT" | jq -r '._sc_id_hash')
    
    while read -r DOWNLOAD
    do
        DOWNLOAD_ID_HASH=$(echo -E "$DOWNLOAD" | jq -r '._sc_id_hash')
        DOWNLOAD_TYPE=$(echo -E "$DOWNLOAD" | jq -r '.type')
        DOWNLOAD_TERM=$(echo -E "$DOWNLOAD" | jq -r '.term')
        LOCATION=$(echo -E "$DOWNLOAD" | jq -r '.location')

        if [ "$LOCATION" = "null" ];
        then
            ERROR=$((ERROR+1))
            echo "$ID_HASH/$DOWNLOAD_ID_HASH ($DOWNLOAD_TYPE/$DOWNLOAD_TERM) lacks a location field." | tee -a "$LOGFILE"
        elif ((((echo "$LOCATION"; echo $? >&3) | grep -v "^data" >&4) 3>&1) | (read xs; exit $xs)) 4>&1
        then
            ERROR=$((ERROR+1))
            echo "$ID_HASH/$DOWNLOAD_ID_HASH ($DOWNLOAD_TYPE/$DOWNLOAD_TERM) not in data directory." | tee -a "$LOGFILE"
        else
            TARGET=$(echo "$LOCATION" | sed -e "s/^data\(.*\)/exports\/$RUN_ID\\1/")

            mkdir -p "$(dirname "$TARGET")"

            if ! $(cp "$LOCATION" "$TARGET" 2>/dev/null);
            then
                echo "$ID_HASH/$DOWNLOAD_ID_HASH ($DOWNLOAD_TYPE/$DOWNLOAD_TERM) failed to copy." | tee -a "$LOGFILE"
                ERROR=$((ERROR+1))
            else
                SUCCESS=$((SUCCESS+1))
            fi
        fi
    done < <(echo -E "$UNIT" | jq -c '._sc_downloads[]')
done < <(jq -c '.[]' "$EXPORT_JSON")

cd exports
zip -r "$RUN_ID".zip "$RUN_ID"
cd -

echo "Bundling up $TOTAL_EXPORTS units with $SUCCESS downloads. $ERROR downloads failed to export."
echo "Download: scp syria@weird.syrianarchive.org:$PWD/$EXPORT_ZIP ."
