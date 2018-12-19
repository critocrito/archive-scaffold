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
 * Construct a query for Elasticsearch.
 */
const queryBuilder = (from, till, videos, channels) => {
  let dateQ;
  if (from != null || till != null) {
    const range = Object.assign(
      {},
      from != null ? {gte: from} : {},
      till != null ? {lte: till} : {},
    );
    dateQ = {range: {"cid.upload_date": {format: "yyyy-MM-dd", ...range}}};
  }
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

  return {
    _source: ["$sc_id_hash", "$sc_content_hash", "cid"],
    query: {
      bool: {
        must: []
          .concat(videoQ)
          .concat(channelQ)
          .concat(dateQ)
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
      const from =
        rows[indexes.from][index] == null || rows[indexes.from][index] === ""
          ? null
          : rows[indexes.from][index];
      const till =
        rows[indexes.till][index] == null || rows[indexes.till][index] === ""
          ? null
          : rows[indexes.till][index];
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
      if (entries.length === 0 && from == null && till == null) {
        log.error(
          `No query selection for column ${column}. Did you speify the right column?`,
        );
        return memo;
      }
      // We have a query that is valid;
      const fromInfo = from == null ? "" : `from ${from}`;
      const tillInfo = till == null ? "" : `till ${till}`;
      const entriesInfo =
        entries.length === 0
          ? ""
          : `for ${entries.length} entries of type ${type}`;
      log.info(
        `Column '${column}': ${fromInfo} ${tillInfo} ${entriesInfo}`.replace(
          /\s\s+/,
          " ",
        ),
      );

      return memo.concat({entries, type, from, till});
    }, [])
    .map(({type, from, till, entries}) => {
      let videos = [];
      let channels = [];
      switch (type) {
        case "youtube_video":
          videos = entries.map(parseVideoQuery);
          break;
        case "youtube_channel":
          channels = entries.map(parseChannelQuery);
          break;
        default:
          break;
      }

      return {
        type: "elastic_query",
        term: JSON.stringify(queryBuilder(from, till, videos, channels)),
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

module.exports.plugins = {
  query_column: queryPlugin,
};
