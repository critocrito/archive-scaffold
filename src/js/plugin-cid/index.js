const parse = require("date-fns/parse");
const {envelope: env} = require("@sugarcube/core");

const youtubeAnnotations = (obs) => {
  const {snippet, statistics, contentDetails} = obs;
  const video = obs._sc_media.find(({type}) => type === "video") || {};
  const file = obs._sc_downloads.find(({term}) => term === video.term) || {};
  const {location} = obs._sc_locations.find(
    ({type}) => type === "youtube_recording",
  ) || {location: {}};

  return {
    language: obs._sc_language,
    online_title: snippet.title,
    online_title_ar: snippet.title,
    online_title_en: snippet.title,
    online_link: video.term,
    description: snippet.description,
    channel_id: snippet.channelId,
    creator: snippet.channelTitle,
    acquired_from: snippet.channelTitle,
    rights_owner: snippet.channelTitle,
    view_count: statistics == null ? null : statistics.viewCount,
    duration: contentDetails.duration,
    filename: file.location,
    md5_hash: file.location,
    sha256_hash: file.sha256,
    longitude: location.lon,
    latitude: location.lat,
  };
};

const twitterAnnotations = (obs) => {
  const {user, tweet, tweet_id: tweetId} = obs;
  const video = obs._sc_media.find(({type}) => type === "video") || {};
  const file = obs._sc_downloads.find(({term}) => term === video.term) || {};

  return {
    language: obs._sc_language,
    online_title: tweet,
    online_title_ar: tweet,
    online_title_en: tweet,
    online_link: `https://twitter.com/${user.screen_name}/status/${tweetId}`,
    description: tweet,
    channel_id: user.user_id,
    creator: user.screen_name,
    acquired_from: user.screen_name,
    rights_owner: user.screen_name,
    filename: file.location,
    md5_hash: file.location,
    sha256_hash: file.sha256,
  };
};

const fsAnnotations = (obs) => {
  const video = obs._sc_media.find(({type}) => type === "video") || {};
  const file = obs._sc_downloads.find(({term}) => term === video.term) || {};

  return {
    filename: file.location,
    md5_hash: file.location,
    sha256_hash: file.sha256,
  };
};

const facebookAnnotations = (obs) => {
  const {message, link, from} = obs;
  const video = obs._sc_media.find(({type}) => type === "video") || {};
  const file = obs._sc_downloads.find(({term}) => term === video.term) || {};

  return {
    language: obs._sc_language,
    online_title: message,
    online_title_ar: message,
    online_title_en: message,
    online_link: link,
    description: message,
    channel_id: from.id,
    creator: from.name,
    acquired_from: from.name,
    rights_owner: from.name,
    filename: file.location,
    md5_hash: file.location,
    sha256_hash: file.sha256,
  };
};

const annotate = (obs) => {
  // Don't annotate if annotations already exist.
  if (obs.cid != null) return obs;

  const source = obs._sc_source;
  const code = obs._sc_id_hash.substr(0, 8);
  const contentFields = [...new Set(obs._sc_content_fields).add("cid")];
  const {fetch: fetchDate, source: sourceDate} = obs._sc_pubdates;
  const incidentDate = sourceDate != null ? parse(sourceDate) : null;
  const acquisitionDate = fetchDate != null ? parse(fetchDate) : null;
  const uploadDate = incidentDate;

  const cid = Object.assign(
    {},
    {
      reference_code: code,
      incident_date: incidentDate,
      date_of_acquisition: acquisitionDate,
      upload_date: uploadDate,
      staff_id: "littlefork",
      summary_ar: null,
      summary_en: null,
      location: null,
      latitude: null,
      longitude: null,
      relevant: null,
      verified: null,
      public: null,
      online_title: null,
      online_title_ar: null,
      online_title_en: null,
      online_link: null,
      description: null,
      channel_id: null,
      view_count: null,
      filename: null,
      creator: null,
      generation: null,
      existence_original: null,
      edited: null,
      online: false,
      file_size: null,
      duration: null,
      acquired_from: null,
      chain_of_custody: null,
      date_of_fixity: null,
      md5_hash: null,
      sha256_hash: null,
      content_type: null,
      language: null,
      finding_aids: null,
      graphic_content: null,
      security_restriction_status: null,
      rights_owner: null,
      rights_declaration: null,
      creator_willing: null,
      priority: null,
      keywords: [],
      notes: null,
      device_used: null,
      weapons_used: [],
      landmarks: [],
      collections: [],
      weather: null,
      type_of_violation: {
        massacres_and_other_unlawful_killing: null,
        arbitrary_arrest_and_unlawful_detention: null,
        hostage_taking: null,
        enforced_disappearance: null,
        torture_and_ill_treatment_of_detainees: null,
        sexual_and_gender_based_violence: null,
        violations_of_childrens_rights: null,
        unlawful_attacks: null,
        violations_against_specifically_protected_persons_and_objects: null,
        use_of_illegal_weapons: null,
        sieges_and_violations_of_economic_social_and_cultural_rights: null,
        arbitrary_and_forcible_displacement: null,
      },
      armed_group: null,
    },
    ["youtube_channel", "youtube_video"].includes(source)
      ? youtubeAnnotations(obs)
      : {},
    ["twitter_feed"].includes(source) ? twitterAnnotations(obs) : {},
    ["fs_unfold"].includes(source) ? fsAnnotations(obs) : {},
    ["facebook_api_feed"].includes(source) ? facebookAnnotations(obs) : {},
  );

  return Object.assign({}, obs, {
    _sc_content_fields: contentFields,
    cid,
  });
};

const annotatePlugin = (envelope, {log}) => {
  log.info(`Annotating ${envelope.data.length} observations.`);

  return env.fmapData(annotate, envelope);
};

annotatePlugin.desc = "Annotate unit data with CID meta data.";
annotatePlugin.argv = {};

module.exports.plugins = {
  cid_annotate: annotatePlugin,
};
