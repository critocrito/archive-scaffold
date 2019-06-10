/* eslint no-await-in-loop: off, no-restricted-syntax: off */
const cheerio = require("cheerio");
const fetch = require("node-fetch");
const {parseDms} = require("dms-conversion");
const puppeteer = require("puppeteer");
const {flatmapP, retry} = require("dashp");
const {envelope: env} = require("@sugarcube/core");
const langs = require("langs");

const scrapePage = async (page, {id, href}) => {
  let lon;
  let lat;
  let video;
  const localized = {};

  await retry(
    page.goto(href, {timeout: 60 * 1000, waitUntil: "domcontentloaded"}),
  );
  const postHtml = await page.$eval("div.popup-box", (e) => e.outerHTML);
  const $post = cheerio.load(postHtml.toString());

  const source = $post("a.source-link").attr("href");
  const coordinates = $post("div.marker-time > a")
    .text()
    .trim();
  const location = $post("div.tagas > strong")
    .text()
    .trim();
  const tags = $post("div.tagas > a")
    .toArray()
    .map((r) =>
      $post(r)
        .text()
        .trim(),
    );
  const description = $post("div.popup-text > h2")
    .text()
    .trim();
  const image = $post("div.popup_imgi > img").attr("src");

  if ($post("div.popup_video").children().length > 0) {
    if (
      $post("div.popup_video")
        .children()
        .first()
        .is("iframe")
    ) {
      const iframeSrc = $post("div.popup_video > iframe").attr("src");
      const iframeHtml = await fetch(iframeSrc).then((resp) => resp.text());
      const $iframe = cheerio.load(iframeHtml);
      if ($iframe("a.ytp-share-panel-link").attr("href") != null) {
        video = $iframe("a.ytp-share-panel-link").attr("href");
      }
    } else {
      video = source;
    }
  }
  if (coordinates != null && coordinates !== "") {
    lat = parseDms(coordinates.split(" ")[0]);
    lon = parseDms(coordinates.split(" ")[1]);
  }
  if ($post("div.alsoLangs").children().length > 0) {
    const languages = $post("div.alsoLangs > a")
      .toArray()
      .reduce((memo, row) => {
        const langHref = $post(row).attr("href");
        const langMatch = langHref.match(/liveuamap\.com\/(\w\w)\//);
        if (langMatch == null) return memo;
        const langInfo = langs.where("1", langMatch[1]);
        if (langInfo == null) return memo;
        return memo.concat({
          name: langInfo.name,
          local: langInfo.local,
          language: langInfo["1"],
          href: langHref,
        });
      }, []);
    for (const lang of languages) {
      await retry(
        page.goto(lang.href, {
          timeout: 60 * 1000,
          waitUntil: "domcontentloaded",
        }),
      );
      const langHtml = await page.$eval("div.popup-box", (e) => e.outerHTML);
      const $lang = cheerio.load(langHtml.toString());
      const langDesc = $lang("div.popup-text > h2")
        .text()
        .trim();
      localized[lang.language] = {
        description: langDesc,
        href: lang.href,
        name: lang.name,
        local: lang.local,
      };
    }
  }

  return {
    id,
    href,
    source,
    description,
    coordinates,
    image,
    video,
    tags,
    location,
    lon,
    lat,
    localized,
  };
};

const querySource = "liveuamap_region";

const regionPlugin = async (envelope, {log}) => {
  const queries = env.queriesByType(querySource, envelope);

  const browser = await retry(
    puppeteer.launch({
      args: ["--no-sandbox", "--disabled-setuid-sandbox"],
    }),
  );
  const [page] = await browser.pages();

  const units = await flatmapP(async (region) => {
    const url = `https://${region}.liveuamap.com`;
    const html = await fetch(url).then((resp) => resp.text());
    const $ = cheerio.load(html);

    const feed = $("#feedler > div.event")
      .toArray()
      .map((row) => {
        const id = $(row).data("id");
        const href = $(row).data("link");
        return {id, href};
      })
      // Filter out commercials.
      .filter(({href}) => href != null);

    const posts = [];

    for (const post of feed) {
      const data = await scrapePage(page, post);
      posts.push(data);
      log.debug(`Scraped post ${post.id}.`);
      await page.waitFor(3 * 1000);
    }

    log.info(`Scraped ${posts.length} posts for ${region}.`);

    return posts.map((post) =>
      Object.assign(post, {
        _sc_id_fields: ["id", "region"],
        _sc_content_fields: ["description"],
        _sc_media: []
          .concat(post.image == null ? [] : {type: "image", term: post.image})
          .concat(post.video == null ? [] : {type: "video", term: post.video}),
        _sc_queries: [{type: querySource, query: region}],
        _sc_locations: [].concat(
          post.lat == null && post.lon == null
            ? []
            : {
                location: {lon: post.lon, lat: post.lat},
                type: "liveuamap_location",
                term: [post.lon, post.lat],
              },
        ),
        _sc_localized: Object.keys(post.localized).map((lang) =>
          Object.assign(post.localized[lang], {type: "iso-639-1", term: lang}),
        ),
        _sc_language: "en",
        region,
      }),
    );
  }, queries);

  await browser.close();

  const additionalQueries = units.reduce((memo, unit) => {
    if (/twitter\.com\/.*\/status/.test(unit.source))
      return memo.concat({type: "twitter_tweet", term: unit.source});
    if (/youtube/.test(unit.video) || /youtu\.be/.test(unit.video))
      return memo.concat({type: "youtube_video", term: unit.video});
    if (/t\.me/.test(unit.source))
      return memo.concat({type: "telegram_post", term: unit.source});
    if (/facebook/.test(unit.source) || /fb/.test(unit.source))
      return memo.concat({type: "facebook_post", term: unit.source});
    return memo;
  }, []);

  return env.concatQueries(additionalQueries, env.concatData(units, envelope));
};

regionPlugin.desc = "Scrape a region from liveuamap.com.";
regionPlugin.argv = {};

module.exports.plugins = {
  liveuamap_region: regionPlugin,
};
