const {basename} = require("path");
const {URL} = require("url");
const {envelope: env, utils} = require("@sugarcube/core");
const {SheetsDo} = require("@sugarcube/plugin-googlesheets");

/**
 * Convert a column name such as "A" or "BC" to the index position in the rows
 * matrix.
 */
const colToIndex = (column) => {
  const len = column.length;
  return (
    [...Array(len).keys()].reduce((memo, pos) => {
      const exp = len - pos - 1;
      return memo + (column.charCodeAt(pos) - 64) * 26 ** exp;
    }, 0) - 1
  );
};

/**
 * Map header entries in column[0] to column indexes.
 */
const headerIndexes = (rows) => {
  const mapping = {
    "Search Term": "term",
    From: "from",
    Till: "till",
    "Incident Codes": "incidentCodes",
    Comment: "comment",
    "Entry Type": "type",
    Entries: "entries",
  };
  return rows
    .map((row) => mapping[row[0]])
    .filter((cell) => cell != null && cell !== "")
    .reduce((memo, name, index) => Object.assign(memo, {[name]: index}), {});
};

/**
 * Parse the different versions of a Youtube video id.
 */
const parseVideoQuery = (query) => {
  let videoId = query;
  if (query.startsWith("http")) {
    const u = new URL(query);
    // Accept youtube urls in the form of https://youtu.be and https://www.youtube.com
    videoId = u.hostname.startsWith("youtu.be")
      ? u.pathname.split("/").filter((segment) => segment.length > 0)[0]
      : u.searchParams.get("v");
  }
  return `https://www.youtube.com/watch?v=${videoId}`;
};

/**
 * Parse the different versions of a Youtube channel id.
 */
const parseChannelQuery = (query) => {
  if (query.startsWith("http")) {
    const u = new URL(query);
    return u.pathname
      .replace(/^\//, "")
      .replace(/\/$/, "")
      .split("/")[1];
  }
  return query;
};

/**
 * Parse a tweet id either by id or URL.
 */
const parseTweetQuery = (id) => {
  if (id.startsWith("http")) {
    const u = new URL(id);
    return basename(u.pathname);
  }
  return id;
};

/**
 * Construct a query for Elasticsearch.
 */
const queryBuilder = (
  term,
  from,
  till,
  incidentCodes,
  videos,
  channels,
  tweets,
) => {
  let dateQ;
  if (from != null || till != null) {
    const range = Object.assign(
      {},
      from != null ? {gte: from} : {},
      till != null ? {lte: till} : {},
    );
    dateQ = {range: {"cid.upload_date": {format: "yyyy-MM-dd", ...range}}};
  }
  const incidentCodeQ =
    incidentCodes.length > 0
      ? {
          bool: {
            should: incidentCodes.map((code) => {
              if (/\*$/.test(code)) {
                return {
                  match_phrase_prefix: {
                    "cid.incident_code": code.replace(/\*$/, ""),
                  },
                };
              }
              return {match: {"cid.incident_code": code}};
            }),
          },
        }
      : null;

  const videoQ =
    videos.length > 0
      ? {
          nested: {
            path: "$sc_media",
            query: {
              terms: {
                "$sc_media.term": videos,
              },
            },
          },
        }
      : null;
  const channelQ =
    channels.length > 0
      ? {
          terms: {
            "snippet.channelId.keyword": channels,
          },
        }
      : null;
  const tweetQ =
    tweets.length > 0
      ? {
          terms: {
            tweet_id: tweets,
          },
        }
      : null;

  const termQ =
    term != null
      ? {
          simple_query_string: {
            query: term,
            fields: [
              "cid.description",
              "cid.online_title",
              "cid.location",
              "notes",
            ],
            quote_field_suffix: ".exact",
          },
        }
      : null;

  return {
    _source: ["$sc_id_hash", "$sc_content_hash", "cid", "notes"],
    query: {
      bool: {
        must: []
          .concat(videoQ)
          .concat(channelQ)
          .concat(tweetQ)
          .concat(dateQ)
          .concat(incidentCodeQ)
          .concat(termQ)
          .filter((q) => q != null),
      },
    },
  };
};

const queryPlugin = async (envelope, {log, cfg, cache}) => {
  const {spreadsheet_id: id, export_columns: columns, sheet} = cfg.query;
  const {client_id: client, client_secret: secret} = cfg.google;

  const [rows, tokens, history] = await SheetsDo(
    function* fetchColumn({getRows}) {
      yield getRows(id, sheet);
    },
    {client, secret, tokens: cache.get("sheets.tokens")},
  );
  history.forEach(([k, meta]) => log.debug(`${k}: ${JSON.stringify(meta)}.`));
  if (tokens != null) cache.update("sheets.tokens", () => tokens);

  const indexes = headerIndexes(rows);
  const queries = utils
    .sToA(",", columns)
    .map((column) => column.toUpperCase())
    .filter((column) => column !== "A")
    .reduce((memo, column) => {
      const index = colToIndex(column);
      const term =
        rows[indexes.term][index] == null || rows[indexes.term][index] === ""
          ? null
          : rows[indexes.term][index];
      const from =
        rows[indexes.from][index] == null || rows[indexes.from][index] === ""
          ? null
          : rows[indexes.from][index];
      const till =
        rows[indexes.till][index] == null || rows[indexes.till][index] === ""
          ? null
          : rows[indexes.till][index];
      const incidentCodes =
        rows[indexes.incidentCodes][index] == null ||
        rows[indexes.incidentCodes][index] === ""
          ? []
          : rows[indexes.incidentCodes][index]
              .split(",")
              .map((code) => code.trim());
      const type = rows[indexes.type][index];
      const entries = rows
        .slice(indexes.entries)
        .map((r) => r[index])
        .filter((e) => e !== "" && e != null);

      // we have some entries, but don't know the type of the entries.
      if (type == null && entries.length > 0) {
        log.error(
          "Not sure what type of entries you want. Maybe you have to set the 'Entry Type'",
        );
        return memo;
      }
      // No real query was configured.
      if (
        term == null &&
        entries.length === 0 &&
        from == null &&
        till == null &&
        incidentCodes.length === 0
      ) {
        log.error(
          `No query selection for column ${column}. Did you specify the right column?`,
        );
        return memo;
      }
      // We have a query that is valid;
      const fromInfo = from == null ? "" : `from ${from}`;
      const tillInfo = till == null ? "" : `till ${till}`;
      const incidentInfo =
        incidentCodes.length === 0
          ? ""
          : ` For incidents ${incidentCodes.join(",")}`;
      const entriesInfo =
        entries.length === 0
          ? ""
          : `a total of ${entries.length} entries of type ${type}`;
      log.info(
        `Column '${column}':${incidentInfo} ${fromInfo} ${tillInfo} ${entriesInfo}`.replace(
          /\s\s+/,
          " ",
        ),
      );

      return memo.concat({term, entries, type, incidentCodes, from, till});
    }, [])
    .map(({term, type, from, incidentCodes, till, entries}) => {
      let videos = [];
      let channels = [];
      let tweets = [];
      switch (type) {
        case "youtube_video":
          videos = entries.map(parseVideoQuery);
          break;
        case "youtube_channel":
          channels = entries.map(parseChannelQuery);
          break;
        case "twitter_tweet":
          tweets = entries.map(parseTweetQuery);
          break;
        default:
          break;
      }

      return {
        type: "elastic_query",
        term: JSON.stringify(
          queryBuilder(
            term,
            from,
            till,
            incidentCodes,
            videos,
            channels,
            tweets,
          ),
        ),
      };
    });

  return env.concatQueries(queries, envelope);
};
queryPlugin.desc = "Export videos based on columnar query definitons.";
queryPlugin.argv = {
  "query.spreadsheet_id": {
    type: "string",
    nargs: 1,
    desc: "The source spreadsheet.",
  },
  "query.sheet": {
    type: "string",
    nargs: 1,
    desc: "The name of the sheet to import from",
  },
  "query.export_columns": {
    type: "string",
    nargs: 1,
    desc: "Columns to export.",
  },
};

const queryToTweetsPlugin = (envelope, {log}) => {
  const queries = envelope.data.map(({cid}) => ({
    type: "twitter_tweet",
    term: cid.online_link,
  }));
  log.info(`Mapping ${envelope.data.length} units to queries`);
  return env.envelopeQueries(queries);
};

queryToTweetsPlugin.desc = "Map units to twitter tweets queries.";
queryToTweetsPlugin.argv = {};

module.exports.plugins = {
  query_column: queryPlugin,
  query_to_tweets: queryToTweetsPlugin,
};
