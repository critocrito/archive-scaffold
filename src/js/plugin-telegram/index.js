/* eslint no-await-in-loop: off, no-restricted-syntax: off */
const {get} = require("lodash/fp");
const cheerio = require("cheerio");
const puppeteer = require("puppeteer");
const {flatmapP, retry} = require("dashp");
const {subDays, parse, compareAsc, distanceInWordsToNow} = require("date-fns");
const {envelope: env} = require("@sugarcube/core");

const scrapeChannel = async (page) => {
  const html = await page.$eval(
    "section.tgme_channel_history",
    (e) => e.outerHTML,
  );
  const channelHtml = await page.$eval(
    "section.tgme_right_column",
    (e) => e.outerHTML,
  );
  const $ = cheerio.load(html.toString());
  const $channel = cheerio.load(channelHtml.toString());

  const channelLogo = $channel(".tgme_page_photo_image > img").attr("src");
  const channelTitle = $channel(".tgme_channel_info_header_title").text();
  const channelUsername = $channel(
    ".tgme_channel_info_header_username > a",
  ).text();
  const channelHref = $channel(".tgme_channel_info_header_username > a").attr(
    "href",
  );
  const channelDescription = $(".tgme_channel_info_description").text();

  return $(".tgme_widget_message_wrap")
    .toArray()
    .map((row) => {
      const id = $(".tgme_widget_message", row).data("post");
      const author = $(".tgme_widget_message_owner_name", row).text();
      const authorUrl = $(".tgme_widget_message_owner_name", row).attr("href");
      const imageStyles = $(".tgme_widget_message_photo_wrap", row).attr(
        "style",
      );
      const imageMatch =
        imageStyles == null
          ? null
          : imageStyles.match(/background-image:url\('(.*)'\)/);
      const image = imageMatch == null ? null : imageMatch[1];
      const video = $("video", row).attr("src");
      const description = $(".tgme_widget_message_text", row).text();
      const createdAt = $(".tgme_widget_message_date > time", row).attr(
        "datetime",
      );
      const href = $(".tgme_widget_message_date", row).attr("href");

      return {
        id,
        channelLogo,
        channelTitle,
        channelUsername,
        channelHref,
        channelDescription,
        author,
        authorUrl,
        image,
        video,
        description,
        href,
        createdAt: parse(createdAt),
      };
    });
};

const messageEntity = (querySource, query, post) =>
  Object.assign({}, post, {
    _sc_id_fields: ["id"],
    _sc_content_fields: ["description"],
    _sc_pubdates: {source: post.createdAt},
    _sc_media: []
      .concat(
        post.image == null
          ? []
          : {
              type: "image",
              term: `${post.id.replace("/", "-")}.jpg`,
              href: post.image,
            },
      )
      .concat(
        post.video == null
          ? []
          : {
              type: "video",
              term: `${post.id.replace("/", "-")}.mp4`,
              href: post.video,
            },
      ),
    _sc_queries: [{type: querySource, query}],
  });

const querySource = "telegram_channel";

const channelPlugin = async (envelope, {log, cfg}) => {
  const pastDays = get("telegram.past_days", cfg);
  const queries = env.queriesByType(querySource, envelope);

  const untilDate = pastDays == null ? null : subDays(new Date(), pastDays);

  const browser = await retry(
    puppeteer.launch({
      args: ["--no-sandbox", "--disabled-setuid-sandbox"],
    }),
  );
  const [page] = await browser.pages();

  const units = await flatmapP(async (channel) => {
    let url;
    if (/^@.*/.test(channel)) {
      // Channel username format, e.g. @dimsmirnov175.
      url = `https://t.me/s/${channel.replace(/^@/, "")}`;
    } else if (/^https?:\/\/t.me\/s/.test(channel)) {
      // Channel url in preview format, e.g. https://t.me/s/dimsmirnov175.
      url = channel;
    } else if (/^https?:\/\/t.me\/.*\/?$/.test(channel)) {
      // Channel url in normal format, e.g.  https://t.me/dimsmirnov175.
      const channelMatch = channel.match(/^https?:\/\/t.me\/(.*)\/?$/);
      if (channelMatch == null) return [];
      url = `https://t.me/s/${channelMatch[1]}`;
    } else {
      // Channel name, e.g. dimsmirnov175.
      url = `https://t.me/s/${channel}`;
    }

    log.info(
      untilDate == null
        ? `Historic scrape for ${url} Telegram channel.`
        : `Fetching Telegram messages for ${url} for the past ${distanceInWordsToNow(
            untilDate,
          )}.`,
    );

    await retry(
      page.goto(url, {timeout: 60 * 1000, waitUntil: "domcontentloaded"}),
    );

    // We scroll up until there are no more messages (historic scrape) or we
    // hit the untilDate (if we use the pastDays option).
    let messageCount = 0;
    let delayCount = 0;
    let messages = [];

    for (;;) {
      // Scrape all messages that are currently in the view port. Sort them by
      // created date as well to easily lookup the oldest message.
      messages = await scrapeChannel(page);
      messages.sort((a, b) => compareAsc(a.createdAt, b.createdAt));
      const {createdAt: oldestMsg} = messages[0] || {createdAt: new Date()};

      log.debug(
        `Scraped ${
          messages.length
        } messages for the past ${distanceInWordsToNow(oldestMsg)}.`,
      );

      // We use a delayCount in case no new messages were scraped to allow for
      // the scrolling to settle. This certainly isn't the prettiest, but
      // works in practice.
      if (messages.length <= messageCount) delayCount += 1;
      if (messages.length > messageCount) delayCount = 0;
      if (delayCount >= 5) break;
      if (untilDate != null && oldestMsg <= untilDate) break;

      messageCount = messages.length;

      // Scroll up to scrape more messages.
      // eslint-disable-next-line no-undef, no-loop-func
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.waitFor(5 * 1000);
    }

    return messages.map((unit) => messageEntity(querySource, channel, unit));
  }, queries);

  await browser.close();

  return env.concatData(units, envelope);
};

channelPlugin.desc = "Scrape messages from a Telegram channel.";
channelPlugin.argv = {
  "telegram.past_days": {
    type: "number",
    nargs: 1,
    desc:
      "Fetch messages published in the past x days. Otherwise scrape all messages.",
  },
};

module.exports.plugins = {
  telegram_channel: channelPlugin,
};
