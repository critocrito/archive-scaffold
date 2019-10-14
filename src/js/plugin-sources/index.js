const {flow, merge, get, getOr} = require("lodash/fp");
const {flowP, flatmapP, tapP, delayP} = require("dashp");
const {envelope: env, utils: u} = require("@sugarcube/core");
const {
  rowsToQueries,
  queriesToRows,
  concatQueriesRows,
  coerceSelectionLists,
  SheetsDo,
} = require("@sugarcube/plugin-googlesheets");
const {
  normalizeTwitterTweetUrl,
  parseTwitterUser,
  isTwitterTweet,
  isTwitterFeed,
} = require("@sugarcube/plugin-twitter");
const {
  isYoutubeChannel,
  isYoutubeVideo,
  normalizeYoutubeVideoUrl,
  normalizeYoutubeChannelUrl,
} = require("@sugarcube/plugin-youtube");
const {
  isFacebookPostUrl: isFacebookPost,
  normalizeFacebookPostUrl,
} = require("sugarcube-plugin-facebook");

const querySource = "sources_sheet";

const plugin = (envelope, {log, cfg, cache, stats}) => {
  const client = get("google.client_id", cfg);
  const secret = get("google.client_secret", cfg);
  const id = get("google.spreadsheet_id", cfg);
  const queryFields = u.sToA(",", getOr([], "google.query_fields", cfg));
  const sheetNameRegex = get("sources.sheet_name_regex", cfg);
  const sourceMappings = get("sources.mappings", cfg);

  const queries = env.queriesByType(querySource, envelope);
  const re = new RegExp(sheetNameRegex, "i");
  const credentials = {client, secret, tokens: cache.get("sheets.tokens")};
  let tokens;

  log.info(
    `Fetching queries from ${queries.length} sheet${
      queries.length > 1 ? "s" : ""
    } matching /${sheetNameRegex}/i.`,
  );

  /**
   * Fetch all links from the original links sheet. It returns a list of URL's.
   */
  const querySheet = async (query) => {
    const [sheets, t] = await SheetsDo(function* fetchQueries({getAllSheets}) {
      return yield getAllSheets(query);
    }, credentials);

    tokens = t;

    log.info(`Fetched ${sheets.length} sheets for ${query}.`);

    const urls = await sheets.reduce(
      (memo, sheet) =>
        memo.then(async (ys) => {
          const [xs] = await SheetsDo(function* fetchQueries({getRows}) {
            let sources = [];

            const [header, ...rows] = yield getRows(
              query,
              `${sheet.title}!1:1000`,
            );
            if (header == null) return sources;

            const indices = header.reduce((acc, cell, i) => {
              if (re.test(cell)) return acc.concat(i);
              return acc;
            }, []);

            rows.forEach((row) => {
              indices.forEach((i) => {
                const cell = row[i];
                if (cell == null || cell === "") return;
                sources = sources.concat(
                  cell
                    // Break cell contents on new line.
                    .split("\n")
                    // Some lines are multiple links separated by a comma.
                    .reduce((acc, elem) => acc.concat(elem.split(",")), [])
                    .map((x) =>
                      x
                        // A line can contain links and text separated by a
                        // space. Only extract URL's.
                        .split(" ")
                        .map((y) => y.trim())
                        .filter((z) => z.startsWith("http")),
                    )
                    // Remove any empty links arrays.
                    .filter((as) => as.length > 0)
                    // Flatten URL's.
                    .reduce((acc, as) => acc.concat(as), []),
                );
              });
            });

            log.info(
              `Fetched ${sources.length} links from ${query}/${sheet.title}.`,
            );

            stats.count("total", sources.length);

            return sources;
          }, credentials);

          await delayP(2 * 1000, Promise.resolve());

          return ys.concat(xs);
        }),
      Promise.resolve([]),
    );

    return urls;
  };

  /**
   * Convert a list or urls into a list of known queries.
   */
  const toQueries = (urls) =>
    urls.reduce((memo, url) => {
      if (isYoutubeVideo(url))
        return memo.concat([
          {
            type: "youtube_video",
            term: normalizeYoutubeVideoUrl(url),
          },
        ]);

      if (isYoutubeChannel(url))
        return memo.concat([
          {
            type: "youtube_channel",
            term: normalizeYoutubeChannelUrl(url),
          },
        ]);

      if (isTwitterTweet(url))
        return memo.concat([
          {
            type: "twitter_tweet",
            term: normalizeTwitterTweetUrl(url),
          },
        ]);

      if (isTwitterFeed(url))
        return memo.concat([
          {
            type: "twitter_user",
            term: parseTwitterUser(url),
          },
        ]);

      if (isFacebookPost(url))
        return memo.concat([
          {
            type: "facebook_post",
            term: normalizeFacebookPostUrl(url),
          },
        ]);
      return memo;
    }, []);

  /**
   * Fetch a list of existing queries from the archive's own queries sheet..
   */
  const existingQueries = async (defaultType, sheet) => {
    const [qs] = await SheetsDo(function* fetchDoneQueries({getRows}) {
      const rows = yield getRows(id, sheet);

      if (rows.length < 2) return [];

      const expanded = rowsToQueries(defaultType, ["type", "term"], rows);
      return expanded;
    }, credentials);

    return qs;
  };

  /**
   * Safely export a list of queries to a sheet containing queries already.
   */
  const exportQueries = async (defaultType, sheet, selectionList, qs) => {
    await SheetsDo(function* exportMergeQueries({
      getRows,
      replaceRows,
      safeReplaceRows,
      setSelections,
    }) {
      const rows = yield getRows(id, sheet);

      const mergedRows = flow([
        queriesToRows(queryFields),
        concatQueriesRows(defaultType, rows),
        get("queries"),
        queriesToRows(queryFields),
      ])(qs);

      log.info(
        `Replacing on ${sheet}: ${rows.length -
          1} existing units with ${mergedRows.length -
          1} updated units of which ${qs.length} are new.`,
      );

      // No need to safely update data if the sheet is empty.
      if (rows.length === 0) {
        yield replaceRows(id, sheet, mergedRows);
      } else {
        const [, e] = yield safeReplaceRows(id, sheet, mergedRows);
        if (e) {
          log.error(`Atomic data replace of target failed.`);
          log.error(`Backup sheet ${e.sheet} is located at ${e.sheetUrl}.`);
          throw e;
        }
      }
      // Set selections on the target sheet.
      yield setSelections(id, sheet, coerceSelectionLists(selectionList));
    },
    credentials);
  };

  /**
   * Merge queries matching a mapping configuration to the queries sheet of
   * the Archive.
   */
  const mergeNewQueries = async (
    {
      default_type: defaultType,
      from_sheet: fromSheet,
      to_sheet: toSheet,
      selection_list: selectionList,
    },
    qs,
  ) => {
    const existing = await existingQueries(defaultType, fromSheet);
    const newQueries = qs.filter(
      ({type, term}) =>
        type === defaultType && !existing.find((q) => q.term === term),
    );

    stats.count("new", newQueries.length);

    await exportQueries(defaultType, toSheet, selectionList, newQueries);
    await delayP(2 * 1000, Promise.resolve());
  };

  /**
   * These are the steps of the plugin.
   * 1) Fetch all links from the links sources sheet.
   * 2) Convert the links into a list of valid queries (with type).
   * 3) Export and merge the new queries to the archive's query sheet.
   */
  return flowP(
    [
      flatmapP(querySheet),
      toQueries,
      async (qs) => {
        await sourceMappings.reduce(
          (memo, mapping) => memo.then(() => mergeNewQueries(mapping, qs)),
          Promise.resolve(),
        );
        return qs;
      },
      tapP((qs) => {
        log.info(`Fetched ${qs.length} sources.`);
        if (tokens != null) cache.update("sheets.tokens", merge(tokens));
      }),
      (qs) => env.envelopeQueries(qs),
    ],
    queries,
  );
};

plugin.desc =
  "Move sources from a column based sheet to the archive's row based queries sheet.";
plugin.argv = {
  "sources.sheet_name_regex": {
    type: "string",
    nargs: 1,
    default: "^link",
    desc: "Extract links from columns that match this regular expression.",
  },
  "sources.mappings": {
    type: "array",
    nargs: 1,
    desc: "Map queries to move to target sheets.",
  },
};

module.exports.plugins = {
  sources_columns_move: plugin,
};
