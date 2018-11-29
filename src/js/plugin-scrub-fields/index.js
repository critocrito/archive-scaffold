const {envelope: env} = require("@sugarcube/core");

// Remove failed timestamps to avoid mapping violations.
const downloadTimeStamps = (unit) => {
  if (unit._sc_downloads == null) return unit;
  const downloads = unit._sc_downloads.map((download) => {
    const {timestamp, ...rest} = download;
    if (timestamp === "failed") return rest;
    return download;
  });

  return Object.assign({}, unit, {_sc_downloads: downloads});
};

// Use true booleans for cid.relevant that is stored as string.
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

// Store the cid.upload_date as a real date.
const cidUploadDate = (unit) => {
  if (unit.cid == null || unit.cid.upload_date == null) return unit;
  return Object.assign({}, unit, {
    cid: {upload_date: new Date(unit.cid.upload_date)},
  });
};

// change the thumbnail media type to image to allow http_get to download it.
const thumbnailType = (unit) =>
  Object.assign({}, unit, {
    _sc_media: unit._sc_media.map((media) =>
      media.type === "thumbnail"
        ? Object.assign(media, {type: "image"})
        : media,
    ),
  });

// rewrite the pipeline pubdate to use the new name fetch.
const pipelinePubdates = (unit) => {
  const {_sc_pubdates: pubDates, ...observation} = unit;
  const {pipeline, fetch, ...others} = pubDates;
  if (pipeline == null) return unit;
  const fetchDate = fetch == null || fetch >= pipeline ? pipeline : fetch;
  return Object.assign(
    {},
    {...observation},
    {_sc_pubdates: {fetch: fetchDate, ...others}},
  );
};

// Replace the dem content field with cid.
const demContentField = (unit) => {
  const demIndex = unit._sc_content_fields.indexOf("dem");
  if (demIndex === -1) return unit;
  // eslint-disable-next-line no-param-reassign
  unit._sc_content_fields[demIndex] = "cid";
  return unit;
};

const scrubPlugin = (envelope) => {
  const data = envelope.data.map((unit) =>
    [
      downloadTimeStamps,
      cidRelevant,
      cidUploadDate,
      thumbnailType,
      pipelinePubdates,
      demContentField,
    ].reduce((memo, f) => f(memo), unit),
  );

  return env.envelope(data, envelope.queries);
};

module.exports.plugins = {
  scrub_fields: scrubPlugin,
};
