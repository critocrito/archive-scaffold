#!/bin/sh

DIR="$(mktemp -d -p .)"
COUNTER=0
MONGODB_URI="$(jq '.mongodb.uri' < configs/mongodb.json | sed -e 's/^"\(.*\)"$/\1/')"

export NODE_OPTIONS=--max_old_space_size=16384

mongo --quiet \
      --eval 'db.units.find({}, {"_id": 0, "_sc_id_hash": 1}).toArray()' \
      "$MONGODB_URI" \
  | jq -cM '[.[] | {"type": "mongodb_unit", "term": ._sc_id_hash}] | _nwise(50000)' \
  | while read -r line;
do
  echo "$line" > "$DIR/chunk-$COUNTER.json";
  COUNTER=$((COUNTER+1))
done

find "$DIR" -type f -name "chunk*.json" | parallel --delay 5 \
                                                   --eta \
                                                   --progress \
                                                   -j 3 \
                                                   -k \
                                                   --group \
                                                   'echo {}; "$(npm bin)"/sugarcube -c configs/migrate-archive.json -q {} -d'

rm -rf "$DIR"
