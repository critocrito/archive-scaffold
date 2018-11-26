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

const thumbnailType = (unit) =>
  Object.assign({}, unit, {
    _sc_media: unit._sc_media.map((media) =>
      media.type === "thumbnail"
        ? Object.assign(media, {type: "image"})
        : media,
    ),
  });

const scrubPlugin = (envelope) => {
  const data = envelope.data.map((unit) =>
    [downloadTimeStamps, cidRelevant, cidUploadDate, thumbnailType].reduce(
      (memo, f) => f(memo),
      unit,
    ),
  );

  return env.envelope(data, envelope.queries);
};

module.exports.plugins = {
  scrub_fields: scrubPlugin,
};
