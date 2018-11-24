const {envelope: env} = require("@sugarcube/core");

const downloadTimeStamps = (unit) => {
  if (unit._sc_downloads == null) return unit;
  const downloads = unit._sc_downloads.map((download) => {
    const {timestamp, ...rest} = download;
    if (timestamp === "failed") return rest;
    return download;
  });

  return Object.assign({}, unit, {_sc_downloads: downloads});
};

const cidRelevant = (unit) => {
  if (
    unit.cid == null ||
    unit.cid.relevant == null ||
    unit.cid.relevant === true ||
    unit.cid.relevant === false
  )
    return unit;
  return Object.assign({}, unit, {
    cid: {relevant: unit.cid.relevant.trim() !== "FALSE"},
  });
};

const cidUploadDate = (unit) => {
  if (unit.cid == null || unit.cid.upload_date == null) return unit;
  return Object.assign({}, unit, {
    cid: {upload_date: new Date(unit.cid.upload_date)},
  });
};

const scrubPlugin = (envelope) => {
  const data = envelope.data.map((unit) =>
    [downloadTimeStamps, cidRelevant, cidUploadDate].reduce(
      (memo, f) => f(memo),
      unit,
    ),
  );

  return env.envelope(data, envelope.queries);
};

module.exports.plugins = {
  scrub_fields: scrubPlugin,
};
