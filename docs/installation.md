# Installation

## Requirements

- NodeJS 8 or higher
- MongoDB 3 or higher
- Elasticsearch 6 or higher
- A Google account and a set of API keys for Youtube and Google Sheets
- A Twitter account and a set of API keys for Twitter
- An email account and GPG key for email reports
- OpenJDK 8 and Clojure 1.10 (optional)

## Installation

Make your own private copy of the following spreadsheets:

- [Archive Queries Template](https://docs.google.com/spreadsheets/d/1Be0ZoDQkPQI8kUyHl-TkWcX0heP1aU300x5X5ECoymk/edit?usp=sharing)

  This spreadsheet collects all the sources that should be preserved.

- [Archive Exports Template](https://docs.google.com/spreadsheets/d/1IsogK13dawk-dHGeHxDWW8HEzd8fAYlaU4ZvZcnjg2k/edit?usp=sharing)

  This spreadsheet allows to export data from the databases into a collections spreadsheet.

- [Archive Collections Template](https://docs.google.com/spreadsheets/d/1Q4dBLm98zcYHm6kR3N4ardwVohWX-HPapNlv9Az_Er0/edit#gid=0)

  Whenever a collection is exported, the new collection spreadsheet uses this spreadsheet as a template.

All archives are derived from a [scaffold archive](https://github.com/critocrito/archive-scaffold). Look there to find the latest installation instructions to bootstrap your own archive.

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
