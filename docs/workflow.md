# Workflow

## Rebuilding full database

This is needed when I want to cleanup incosistencies in MongoDB as well as Elasticsearch.

1) Create a copy of the mongodb to act as a source. In the mongo shell.

```
db.copyDatabase("archive-scaffold", "archive-scaffold-source", "localhost")
use archive-scaffold
db.dropDatabase()

# Recreate the collection and create an index. Performance is otherwise terrible.
use archive-scaffold
db.createCollection("units")
db.units.createIndex({_sc_id_hash: 1})
```

2) Create a new mapping for the elasticsearch index.

Use `curl localhost:9200/_cat/indices` and `curl localhost:9200/_cat/aliases?v` to determine the correct index.

```
curl -X PUT -H "Content-Type: application/json" localhost:9200/archive-scaffold-X -d@configs/mappings.json
curl -X POST -H "Content-Type: application/json" localhost:9200/_aliases -d @configs/alias.json
```

3) Migrate the archive.

```
./scripts/migrate-the-archive.sh
```

4) Clean up.

```
curl -X DELETE http://localhost:9200/archive-scaffold-X
```

In the mongo shell.

```
use archive-scaffold-source
db.dropDatabase()
```

## Statistics of failed youtube videos

To generate a statistics count I ran the following shell command:

```
xsv select reason failed-stats-8251ee34b053b62f9cb03ca9e45f5166290013f4.csv \  # Select the right column of the CSV
  | awk 'BEGIN {getline} /^".*[^"]$/{getline x}{print $0 " " x; x=""}' \       # Merge reasons that stretch across two lines into one
  | sed 's/"//g' \          # Remove any quotes
  | awk '{$1=$1};1' \       # Remove any leading and trailing whitespace
  | sort \                  # Sort
  | uniq -c \               # Count unique occurences
  | sort -n \               # Sort by leading numeric value
  | tac > failed-stats-count-reason.txt      # Reverse and output the results
```

To count the number of copyright violations I use this little snippet.

```
grep -i copyright failed-stats-count-reason.txt | awk '{c+=$1} END {print c}'
```
