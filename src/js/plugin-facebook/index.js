const puppeteer = require("puppeteer");
const cheerio = require("cheerio");
const {flatmapP, retry} = require("dashp");
const {envelope: env} = require("@sugarcube/core");

const scrapeChannel = async (page) => {
  const html = await page.$eval("body", (e) => e.innerHTML);
  const $ = cheerio.load(html.toString());

  const message = $('[data-testid="post_message"]').text();
  // eslint-disable-next-line no-undef
  const video = await page.$eval("body", () => document.location.href);
  const id = video.replace(/.*\/videos\/(\d*)\/.*/, "$1");
  const user = video.replace(/.*\/(.*)\/videos\/.*/, "$1");

  return {id, message, user, video};
};

const messageEntity = (querySource, post) =>
  Object.assign({}, post, {
    _sc_id_fields: ["id"],
    _sc_content_fields: ["message"],
    _sc_media: [].concat(
      post.video == null
        ? []
        : {
            type: "video",
            term: post.video,
          },
    ),
    _sc_queries: [{type: querySource, term: post.video}],
  });

const querySource = "video_url";

const fbVideosPlugin = async (envelope, {log, stats}) => {
  log.info("Started FB videos plugin");

  const queries = env.queriesByType(querySource, envelope);

  const browser = await retry(
    puppeteer.launch({
      args: ["--no-sandbox", "--disabled-setuid-sandbox"],
    }),
  );
  const [page] = await browser.pages();

  const units = await flatmapP(async (term) => {
    let unit;
    try {
      await retry(
        page.goto(term, {timeout: 60 * 1000, waitUntil: "networkidle0"}),
      );
      await page.waitFor(5 * 1000);
      unit = await scrapeChannel(page);
    } catch (e) {
      const failed = {
        type: "facebook_video",
        plugin: "facebook_video",
        reason: e.message,
        term,
      };
      stats.update("failed", (fails) =>
        Array.isArray(fails) ? fails.concat(failed) : [failed],
      );

      log.warn(`Failed to download video ${term}: ${e.message}`);
    }

    log.debug(`Scraped ${term}`);

    return messageEntity(querySource, unit);
  }, queries);

  await browser.close();

  return env.concatData(units, envelope);
};

fbVideosPlugin.desc = "Scrape videos from Facebook.";
fbVideosPlugin.argv = {};

module.exports.plugins = {
  facebook_video: fbVideosPlugin,
};
