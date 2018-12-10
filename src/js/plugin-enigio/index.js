const fs = require("fs");
const {promisify} = require("util");
const {URL} = require("url");
const fetch = require("node-fetch");
const {envelope: env, plugin: p, utils} = require("@sugarcube/core");

// https://github.com/nodejs/node/issues/21513
require("tls").DEFAULT_ECDH_CURVE = "auto";

const {assertCfg} = utils.assertions;
const stat = promisify(fs.stat);

const assertCredentials = assertCfg([
  "enigio.name",
  "enigio.password",
  "enigio.email",
]);

const enigioPlugin = (envelope, {cfg, log, stats}) => {
  const {name, password, email} = cfg.enigio;

  const timestampDownload = async (download) => {
    if (download.location == null) return download;
    if (download.timestamp != null) return download;

    const {location, sha256, type, term} = download;
    let size;

    try {
      const file = await stat(location);
      // eslint-disable-next-line prefer-destructuring
      size = file.size;
    } catch (e) {
      const fail = {
        type,
        term,
        plugin: "enigio_timestamp",
        reason: `Can't determine size of ${location}: ${e.message}`,
      };
      stats.update("failed", (failures) =>
        Array.isArray(failures) ? failures.concat(fail) : [fail],
      );

      log.error(fail.reason);
      log.error(e);

      return download;
    }

    const url = new URL("https://api.timebeat.com/s/timeStamp/reg");
    url.username = name;
    url.password = password;
    url.searchParams.append("size", size);
    url.searchParams.append("mime", "text/plain");
    url.searchParams.append("fileName", location);
    url.searchParams.append("sha256", sha256);
    url.searchParams.append("c", term);
    url.searchParams.append("userId", email);

    let resp;

    try {
      resp = await fetch(url.toString());
      if (!resp.ok) throw new Error("Enigio Login Failed or resource wrong");
    } catch (e) {
      const fail = {
        type,
        term,
        plugin: "enigio_timestamp",
        reason: `Failed to timestamp download ${term} at ${location}: ${
          e.message
        }`,
      };
      stats.update("failed", (failures) =>
        Array.isArray(failures) ? failures.concat(fail) : [fail],
      );

      log.error(fail.reason);
      log.error(e);

      return download;
    }

    const {v: timestamp, m: msg} = await resp.json();

    if (timestamp == null) {
      log.warn(`${location}: ${msg}`);
      return download;
    }

    return Object.assign({}, {timestamp}, download);
  };

  return env.fmapDataDownloadsAsync(timestampDownload, envelope);
};

enigioPlugin.desc = "Timestamp and store hash with enigio.";
enigioPlugin.argv = {
  "enigio.name": {
    type: "string",
    nargs: 1,
    desc: "Enigio Username",
  },
  "enigio.password": {
    type: "string",
    nargs: 1,
    desc: "Enigio Password",
  },
  "enigio.email": {
    type: "string",
    nargs: 1,
    desc: "Enigio Email",
  },
};

module.exports.plugins = {
  enigio_timestamp: p.liftManyA2([assertCredentials, enigioPlugin]),
};
