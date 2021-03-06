const {isString, omit, map, pick} = require("lodash/fp");
const {queries, envelope: env} = require("@sugarcube/core");
const {parseDms} = require("dms-conversion");

// Ensure date type for download timestamp.
const downloadTimeStamps = (unit) => {
  if (unit._sc_downloads == null) return unit;
  const downloads = unit._sc_downloads.map((download) => {
    const {timestamp, ...rest} = download;
    if (timestamp === "failed") return rest;
    return download;
  });

  return Object.assign({}, unit, {_sc_downloads: downloads});
};

// Merge deprecated _sc_links into _sc_media.
const scLinksToMedia = (unit) => {
  if (unit._sc_links == null) return unit;
  const links = unit._sc_links;
  const media = unit._sc_media == null ? [] : unit._sc_media;

  links.forEach((link) => {
    if (link._sc_id_hash == null) {
      media.push(link);
      return;
    }
    const existing = media.find((m) => m._sc_id_hash === link._sc_id_hash);
    // Media takes precedence
    media.push(Object.assign({}, link, existing));
  });

  // eslint-disable-next-line no-param-reassign
  delete unit._sc_links;

  // We filter out duplicates.
  return Object.assign({}, unit, {
    _sc_media: media.reduce((memo, m) => {
      const existing = memo.find((m2) => m._sc_id_hash === m2._sc_id_hash);
      if (existing == null) return memo.concat(m);
      const existingIndex = memo.find((m2) => m._sc_id_hash === m2._sc_id_hash);
      // eslint-disable-next-line no-param-reassign
      memo[existingIndex] = Object.assign({}, existing, m);
      return memo;
    }, []),
  });
};

// Make _sc lists unique.
const scUniqueLists = (unit) => {
  const fields = [
    "_sc_downloads",
    "_sc_media",
    "_sc_relations",
    "_sc_queries",
    "_sc_locations",
    "_sc_relations",
  ];
  const lists = fields.reduce((memo, field) => {
    if (unit[field] == null) return Object.assign(memo, {[field]: []});

    const list = unit[field];
    const data = [];

    // eslint-disable-next-line no-plusplus
    for (let i = 0; i < list.length; i++) {
      let item = list[i];

      // Hash the item if there is no hash existing.
      if (item._sc_id_hash == null) {
        item = queries.hashOne(item);
      }

      // See if we already have an occurence and merge, otherwise simply add
      // the item.
      const index = list.findIndex((l) => item._sc_id_hash === l._sc_id_hash);
      if (index >= 0) {
        data[index] = Object.assign({}, data[index], item);
        // eslint-disable-next-line no-continue
        continue;
      }
      data.push(item);
    }

    return Object.assign(memo, {[field]: data});
  }, {});

  return Object.assign({}, unit, lists);
};

// Use true booleans for cid fields that are stored as string
const cidBooleans = (unit) => {
  if (unit.cid == null) return unit;
  const fields = [
    "relevant",
    "verified",
    "public",
    "generation",
    "verified",
    "creator_willing",
    "graphic_content",
    "online",
    "edited",
  ];

  const cid = fields.reduce((memo, field) => {
    const value = memo[field];
    return Object.assign(memo, {
      [field]:
        value == null || value === true || value === false
          ? value
          : value.trim() === "TRUE",
    });
  }, Object.assign({}, unit.cid));

  const violations =
    unit.cid.type_of_violation == null
      ? unit.cid.type_of_violation
      : Object.keys(cid.type_of_violation).reduce((memo, field) => {
          const value = unit.cid.type_of_violation[field];
          return Object.assign({}, memo, {
            [field.toLowerCase()]:
              value == null || value === true || value === false
                ? value
                : value.trim() === "TRUE",
          });
        }, {});
  return Object.assign({}, unit, {
    cid: Object.assign({}, cid, {type_of_violation: violations}),
  });
};

// change empty string to null
const cidEmptyStrings = (unit) => {
  if (unit.cid == null) return unit;
  const fields = Object.keys(unit.cid);
  const cid = fields.reduce((memo, key) => {
    const value = unit.cid[key];
    if (value === "") return Object.assign(memo, {[key]: null});
    return Object.assign(memo, {[key]: value});
  }, {});
  return Object.assign({}, unit, {cid});
};

// Store dates in cid as a real date.
const cidDates = (unit) => {
  if (unit.cid == null) return unit;
  const fields = ["upload_date", "date_of_acquisition"];
  const dates = fields.reduce((memo, field) => {
    const value = unit.cid[field];
    if (value == null) return Object.assign(memo, {[field]: value});
    return Object.assign(memo, {[field]: new Date(value)});
  }, {});

  return Object.assign({}, unit, {
    cid: Object.assign({}, unit.cid, {...dates}),
  });
};

// Set location from cid.
const cidLocation = (unit) => {
  // Nothing to do here if we don't have a cid attribute.
  if (unit.cid == null) return unit;

  const {cid} = unit;

  // longitude/latitude can be values like null, "", " ", "34.234",
  // "35°48'45\"N" and can be different for each attribute. If any of the
  // location attributes is null, we make sure that both are null since half a
  // location seems useless.
  if (cid.latitude == null || cid.longitude == null)
    return Object.assign({}, unit, {
      cid: Object.assign({}, cid, {latitude: null, longitude: null}),
    });

  const {location} = cid;
  let {longitude, latitude} = cid;

  // Coerce values to well formed strings, otherwise the proper conversion
  // later on can fail.
  longitude = isString(longitude) ? longitude.trim() : longitude.toString();
  latitude = isString(latitude) ? latitude.trim() : latitude.toString();

  // No empty strings allowed for longitude/latitude. Set to null instead.
  if (longitude === "" || latitude === "")
    return Object.assign({}, unit, {
      cid: Object.assign({}, cid, {latitude: null, longitude: null}),
    });

  // Parse and convert the location attributes to a float.
  const lon = parseDms(longitude);
  const lat = parseDms(latitude);

  const item = {
    location: {lon, lat},
    type: "cid",
    term: [lon, lat],
    description: location,
  };

  return Object.assign({}, unit, {
    cid: Object.assign({}, cid, {latitude: lat, longitude: lon}),
    _sc_locations: unit._sc_locations.concat(item),
  });
};

// Rewrite thumbnail media type to image.
const thumbnailType = (unit) =>
  Object.assign({}, unit, {
    _sc_media: unit._sc_media.map((media) =>
      media.type === "thumbnail"
        ? Object.assign(media, {type: "image"})
        : media,
    ),
  });

// Rewrite deprecated pipeline date class to fetch.
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

// Turn recordingDetails.location to a true location.
const recordingLocation = (unit) => {
  const {recordingDetails} = unit;
  if (
    recordingDetails == null ||
    recordingDetails.location == null ||
    recordingDetails.location.longitude == null ||
    recordingDetails.location.latitude == null
  )
    return unit;
  const {longitude, latitude} = recordingDetails.location;
  const item = {
    location: {lon: longitude, lat: latitude},
    type: "youtube_recording",
    term: [longitude, latitude],
    description: recordingDetails.locationDescription,
  };
  return Object.assign({}, unit, {
    _sc_locations: unit._sc_locations.concat(item),
  });
};

// Turn twitter coordinates to a true location.
const twitterLocation = (unit) => {
  const {coordinates} = unit;
  if (coordinates == null || coordinates.coordinates == null) return unit;
  const [lon, lat] = coordinates.coordinates;
  const item = {
    location: {lon, lat},
    type: "tweet_location",
    term: [lon, lat],
  };
  return Object.assign({}, unit, {
    _sc_locations: unit._sc_locations.concat(item),
  });
};

// Turn facebook place to a true location.
const facebookLocation = (unit) => {
  const {place} = unit;
  if (place == null || place.location == null) return unit;
  const {longitude, latitude, name} = place.location;
  const item = {
    location: {lon: longitude, lat: latitude},
    type: "facebook_place",
    description: name,
    term: [longitude, latitude],
  };
  return Object.assign({}, unit, {
    _sc_locations: unit._sc_locations.concat(item),
  });
};

// Replace the dem content field with cid.
const demContentField = (unit) => {
  if (unit._sc_content_fields == null) return unit;
  const demIndex = unit._sc_content_fields.indexOf("dem");
  if (demIndex === -1) return unit;
  // eslint-disable-next-line no-param-reassign
  unit._sc_content_fields[demIndex] = "cid";
  return unit;
};

// Some media.id values are stored as numbers, but should be text.
const twitterMediaId = (unit) => {
  if (unit.medias == null || unit.medias.length === 0) return unit;
  const medias = unit.medias.map((media) => {
    if (typeof media.id === "number")
      return Object.assign({}, media, {id: media.id.toString()});
    return media;
  });
  return Object.assign({}, unit, {medias});
};

const twitterVideoMedia = (unit) => {
  const twitterVideos = unit._sc_media.filter(
    ({type}) => type === "twitter_video",
  );
  if (twitterVideos.length === 0) return unit;
  const medias = unit._sc_media
    .filter(({type}) => type !== "twitter_video")
    .map((m) => {
      const video = twitterVideos.find(({term}) => m.term === term);
      if (video == null) return m;
      return Object.assign({}, video, m, {
        _sc_id_hash_legacy: video._sc_id_hash,
      });
    });
  // lets catch any twitter-videos that we missed.
  const missingMedias = twitterVideos
    .filter(({term}) => {
      if (medias.find((m) => m.term === term) == null) return true;
      return false;
    })
    .map((m) => Object.assign(m, {type: "video"}));
  return Object.assign({}, unit, {_sc_media: medias.concat(missingMedias)});
};

const twitterVideoDownloads = (unit) => {
  const twitterVideos = unit._sc_downloads.filter(
    ({type}) => type === "twitter_video",
  );
  if (twitterVideos.length === 0) return unit;
  const downloads = unit._sc_downloads
    .filter(({type}) => type !== "twitter_video")
    .map((m) => {
      const video = twitterVideos.find(({term}) => m.term === term);
      if (video == null) return m;
      return Object.assign({}, video, m, {
        _sc_id_hash_legacy: video._sc_id_hash,
      });
    });
  // lets catch any twitter-videos that we missed.
  const missingDownloads = twitterVideos
    .filter(({term}) => {
      if (downloads.find((m) => m.term === term) == null) return true;
      return false;
    })
    .map((m) => Object.assign(m, {type: "video"}));
  return Object.assign({}, unit, {
    _sc_downloads: downloads.concat(missingDownloads),
  });
};

const scrubPlugin = (envelope) => {
  const scrub = (unit) =>
    [
      downloadTimeStamps,
      scLinksToMedia,
      cidEmptyStrings,
      cidBooleans,
      cidDates,
      cidLocation,
      thumbnailType,
      pipelinePubdates,
      demContentField,
      twitterMediaId,
      twitterVideoMedia,
      twitterVideoDownloads,
      recordingLocation,
      twitterLocation,
      facebookLocation,
      scUniqueLists,
    ].reduce((memo, f) => f(memo), unit);

  return env.fmapData(scrub, envelope);
};

const omitFieldsPlugin = (envelope) =>
  env.fmapData((unit) => {
    const {
      // sugarcube
      _sc_checker, // eslint-disable-line camelcase
      // youtube
      etag,
      contentDetails,
      status,
      statistics,
      topicDetails,
      snippet,
      // twitter
      medias,
      user,
      // facebook
      privacy,
      // this goes in
      ...rest
    } = unit;
    const {
      categoryId,
      thumbnails,
      position,
      resourceId,
      liveBroadcastContent,
      ...realSnippet
    } = snippet == null ? {} : snippet;

    return Object.assign(
      {},
      rest,
      Object.keys(realSnippet).length > 0 ? {snippet: realSnippet} : {},
      // Those fields are required by the CID annotation
      statistics == null ? {} : {statistics: {viewCount: statistics.viewCount}},
      contentDetails == null
        ? {}
        : {contentDetails: {duration: contentDetails.duration}},
      user == null
        ? {}
        : {user: {screen_name: user.screen_name, user_id: user.user_id}},
    );
  }, envelope);

const pickCollection = pick(["type", "term", "_sc_id_hash"]);
const pickDownload = pick([
  "type",
  "term",
  "_sc_id_hash",
  "location",
  "mosaic",
  "md5",
  "sha256",
]);
const pickLocation = pick(["type", "term", "_sc_id_hash", "location"]);

const pickFieldsPlugin = (envelope) =>
  env.fmapData((unit) => {
    const {
      _sc_id_hash, // eslint-disable-line camelcase
      _sc_content_hash, // eslint-disable-line camelcase
      _sc_id_fields, // eslint-disable-line camelcase
      _sc_content_fields, // eslint-disable-line camelcase
      _sc_downloads, // eslint-disable-line camelcase
      _sc_media, // eslint-disable-line camelcase
      _sc_language, // eslint-disable-line camelcase
      _sc_locations, // eslint-disable-line camelcase
      _sc_queries, // eslint-disable-line camelcase
      _sc_relations, // eslint-disable-line camelcase
      _sc_source, // eslint-disable-line camelcase
      _sc_pubdates, // eslint-disable-line camelcase
      cid,
    } = unit;
    return {
      _sc_id_hash,
      _sc_content_hash,
      _sc_id_fields,
      _sc_content_fields,
      _sc_language,
      _sc_source,
      _sc_pubdates,
      _sc_relations: map(pickCollection, _sc_relations),
      _sc_queries: map(pickCollection, _sc_queries),
      _sc_media: map(pickCollection, _sc_media),
      _sc_locations: map(pickLocation, _sc_locations),
      _sc_downloads: map(pickDownload, _sc_downloads),
      cid: omit(["notes"], cid),
    };
  }, envelope);

module.exports.plugins = {
  scrub_fields: scrubPlugin,
  scrub_omit_fields: omitFieldsPlugin,
  scrub_pick_fields: pickFieldsPlugin,
};
