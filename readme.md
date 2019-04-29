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

Also make a copy of the [Archive Queries Exports](https://docs.google.com/spreadsheets/d/1IsogK13dawk-dHGeHxDWW8HEzd8fAYlaU4ZvZcnjg2k/edit#gid=1718726577) and replace the spreadsheet id in [`export-spreadsheet-ids.txt`](./queries/export-spreadsheet-ids.txt).

On the server take the following steps:

Create a new user for the archive.

```
adduser --disabled-password yemen
```

Become the new user to setup SSH and clone the repository. Add the public SSH key as a deploy key to the Github repository.

```
su - yemen

ssh-keygen
cat <<EOF > .ssh/authorized_keys
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDjftAyMQCwRXdPTjpm1Tu5O7nxgyT/TEbWwGxaVqXBcILPdRjhglY6A6WteKtOxEFhFJhXJh38avI2hHnXmDVNI+qyJbE6eFiG2j7NETOpfHt1IOAdpgo9MWWP+hlEpqDVrMvNYqOvcFKYJAslUyRMcvqhgoiJxp/mjBVEA/xBhR/WgNcGR3bbeh5mNcqIx3tM47RZIdo0reCk7nukgwIFpFjJMCOX9IXlQOz1mjFX2d+ZN69Fmh0DpyzZxAqxXRDfIcse/h+UXi6J4bX0T6fnL3RwW2I4vHGh4WsuRPArQcxYLsXuquOl1iuojBvbqruXHQdDcLv9G6rUJwWCClvd crito@xmarksthespot
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCu9PJBoIUjgfG0YM42WRYH6MPt3w0Gdar6Jw/RjWx0xQGMLd9ervqQk8MpQBIobWcP7FbWCaFIm80/amVCjJgH62ETAUxHBiEXfs+2FiaE5orDYDFbQswmF/V7kMbuoxwlXdo8jiKWt8MoBVW/XNbjZ5VR9YGehi9KGfJzB+sP/ZBK4MalNZJp+Z2BbYJJLCllcnzpdzpgNZJWNFbzIt6pB5Gs1v02IkugMpji4DnIX5QTJcxylOHTTseb+WLmvHv4EUujfxZh2B8q6Yw8K63pjqUQ16BkUGHzOAWwRZabU1hi+pllXTp2/7Z4OSadfAuNxtdMt8oMcofJjsVXf4rZ hak@hakvillehost
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDXf1geWijRyH3ebhfIcoeKHyDhSCEJjjkI78Xh4t+PjSL7I9UACvN/bfCgdQKuGnHICpgTcxA7XsV0uOuB4ivJumn16HJpT8J+baJXnAiQ0RCRBY7c6NAwxyMtBEknxBG/WUhJp7L9h3WayNeJ1HFjh8oLOUNtxqjt7e/jLHtVaDWnwSQH2J1JVUZni15aGaIkLJWNNpxvEKbfock5bExdW5itSOeDYdAFAag9iA3eQIkI9obJxNLik/L3PKeDT73EuS9o/Z8BDng9/0Bld/5ksqIE7iHwM7aOOaTRtXia+5diRDWdRjh5stABzFLbtRwJBfobUdFTTwX2cIlPZcR3 hak@mnemonic001
EOF

git clone git@github.com:critocrito/yemen-archive.git
cd yemen-archive
yarn install
```

To set up Elasticsearch, create the index and set the alias.

```
curl -X PUT -H "Content-Type: application/json" localhost:9200/yemen-archive-1 -d @configs/mappings.json
curl -X POST -H "Content-Type: application/json" localhost:9200/_aliases -d @configs/alias.json
```

Prepare the MongoDB database with the right indexes:

```
mongo localhost:27020
use yemen-archive
db.createCollection("units")
db.units.createIndex({_sc_id_hash: 1})
```

Configure all secrets in `./configs/secrets.json`.

Create a GPG key for this account. Use the email address specified in the `secrets.json`. Add the new public GPG key to the project repository.

```
gpg --gen-key
gpg --import keys/*
gpg --edit crito   # Trust the key, repeat for every key imported from ./keys
gpg --export -a archive@dothorse.horse > keys/archive.gpg
```

Enable the timers for the new archive.

```
cd /etc/systemd/system
mkdir twitter-feeds-scrapes@sudan.timer.d
mkdir twitter-tweets-scrapes@sudan.timer.d
mkdir youtube-daily-scrapes@sudan.timer.d

cat <<EOF > twitter-feeds-scrapes@sudan.timer.d/override.conf
[Timer]
OnCalendar=
OnCalendar=*-*-* 02:00:00
EOF

cat <<EOF > twitter-tweets-scrapes@sudan.timer.d/override.conf
[Timer]
OnCalendar=
OnCalendar=*-*-* 02:30:00
EOF

cat <<EOF > youtube-daily-scrapes@sudan.timer.d/override.conf
[Timer]
OnCalendar=
OnCalendar=*-*-* 02:20:00
EOF

systemctl enable twitter-feeds-scrapes@yemen
systemctl enable twitter-feeds-scrapes@yemen.timer

systemctl enable twitter-tweets-scrapes@yemen
systemctl enable twitter-tweets-scrapes@yemen.timer

systemctl enable youtube-daily-scrapes@yemen
systemctl enable youtube-daily-scrapes@yemen.timer

systemctl start twitter-feeds-scrapes@yemen.timer
systemctl start twitter-tweets-scrapes@yemen.timer
systemctl start youtube-daily-scrapes@yemen.timer
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

### Statistics of failed youtube videos

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
