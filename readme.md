# The Archive of Archives

> The source of all archives.

## Installation

## Bootstrapping a new Archive

```
export archive="yemen-archive"
git clone git@github.com:critocrito/archive-scaffold.git $archive
cd $archive
git remote rm origin
git remote add scaffold git@github.com:critocrito/archive-scaffold.git
git remote add origin git@github.com:critocrito/$archive.git
git push -u origin master
find . -type f -name "*.json" -exec sed -i -e 's/archive-scaffold/yemen-archive/g' {} \;
```

Make a new copy of the [Archive Queries Template](https://docs.google.com/spreadsheets/d/1Be0ZoDQkPQI8kUyHl-TkWcX0heP1aU300x5X5ECoymk/edit#gid=703301831) and replace the spreadsheet id in [`spreadsheet-ids.txt`](./queries/spreadsheet-ids.txt).

```
echo "1mYAuNtKu12ZXaj6Mbh4aoFN8onV-p5AG7lvGJWqtj18" > queries/spreadsheet-ids.txt
```

To set up Elasticsearch, create the index and set the alias.

```
curl -X PUT -H "Content-Type: application/json" localhost:9200/$archive-01 -d @configs/mappings.json
curl -X POST -H "Content-Type: application/json" localhost:9200/_aliases -d @configs/alias.json
```

## Workflow

### Rebuilding full database

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
