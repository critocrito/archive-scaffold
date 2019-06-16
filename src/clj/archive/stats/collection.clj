(ns archive.stats.collection
  (:require [archive.core :as core]
            [archive.elastic :as elastic]))

(def duplicate-videos-query
  "Query for Elasticsearch to determine how many duplicate videos there are."
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]}}
   :size 0})

(def duplicate-videos-verified-query
  "Query for Elasticsearch to determine how many duplicate videos there are."
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]}}
   :size 0})

(def no-video-download-query
  "Query for Elasticsearch to determine how many videos use the outdated youtube_video download type."
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
     :must_not
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}]}}
   :size 0})

(def no-video-download-verified-query
  "Query for Elasticsearch to determine how many videos use the outdated youtube_video download type."
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
     :must_not
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}]}}
   :size 0})

(def url-download-type-query
  "Units that contain URL as download type."
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "url"}}}}]}}
   :size 0})

(def missing-cid-query
  "Units that have no meta data."
  {:query
   {:bool
    {:must_not
     [{:exists {:field "cid"}}]}}
   :size 0})

(def video-without-download-query
  "Units that have a video download but no location."
  {:query
   {:bool
    {:should
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
     :must_not
     [{:nested {:path "$sc_downloads" :query {:exists {:field "$sc_downloads.location"}}}}]}}
   :size 0})

(def video-without-download-verified-query
  "Units that have a video download but no location."
  {:query
   {:bool
    {:must
     [{:term {:cid.veriffied true}}]
     :should
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
     :must_not
     [{:nested {:path "$sc_downloads" :query {:exists {:field "$sc_downloads.location"}}}}]}}
   :size 0})

(def youtube-videos-without-a-filename-query
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
     :must_not
     [{:exists {:field "cid.filename"}}]}}
   :size 0})

(def youtube-videos-without-a-filename-verified-query
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
     :must_not
     [{:exists {:field "cid.filename"}}]}}
   :size 0})

(def videos-without-a-filename-query
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}]
     :must_not
     [{:exists {:field "cid.filename"}}]}}
   :size 0})

(def videos-without-a-filename-verified-query
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}]
     :must_not
     [{:exists {:field "cid.filename"}}]}}
   :size 0})

(def filename-in-russia-strikes-query
  {:query
   {:bool
    {:must
     [{:wildcard {:cid.filename "*russia*"}}]}}
   :size 0})

(def filename-in-russia-strikes-verified-query
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:wildcard {:cid.filename "*russia*"}}]}}
   :size 0})

(def filename-in-chemical-weapons-query
  {:query
   {:bool
    {:must
     [{:wildcard {:cid.filename "*chemical*"}}]}}
   :size 0})

(def filename-in-chemical-weapons-verified-query
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:wildcard {:cid.filename "*chemical*"}}]}}
   :size 0})

(def filename-in-videoapi-query
  {:query
   {:bool
    {:must
     [{:wildcard {:cid.filename "*videoapi*"}}]}}
   :size 0})

(def filename-in-videoapi-verified-query
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:wildcard {:cid.filename "*videoapi*"}}]}}
   :size 0})

(def images-outside-collection-query
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:bool {:must [{:term {:$sc_downloads.type "image"}}
                                                            {:wildcard {:$sc_downloads.location "*files*"}}]}}}}]}}
   :size 0})

(def downloads-outside-collection-query
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:bool {:must [{:match {:$sc_downloads.location "/var/www"}}]}}}}]}}
   :size 0})

(def downloads-outside-collection-verified-query
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:nested {:path "$sc_downloads" :query {:bool {:must [{:match {:$sc_downloads.location "/var/www"}}]}}}}]}}
   :size 0})

(def images-that-are-videos-query
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:bool {:must [{:term {:$sc_downloads.type "image"}}
                                                            {:wildcard {:$sc_downloads.location "*mp4"}}]}}}}]}}
   :size 0})

(def images-that-are-videos-verified-query
  {:query
   {:bool
    {:must
     [{:term {:cid.verified true}}
      {:nested {:path "$sc_downloads" :query {:bool {:must [{:term {:$sc_downloads.type "image"}}
                                                            {:wildcard {:$sc_downloads.location "*mp4"}}]}}}}]}}
   :size 0})

(def cid-filename-without-download-locations-query
  {:query
   {:bool
    {:must [{:exists {:field "cid.filename"}}]
     :must_not [{:nested {:path "$sc_downloads" :query {:exists {:field "$sc_downloads.location"}}}}]}}
   :size 0})

(def cid-filename-without-download-locations-verified-query
  {:query
   {:bool
    {:must [{:term {:cid.verified true}}
            {:exists {:field "cid.filename"}}]
     :must_not [{:nested {:path "$sc_downloads" :query {:exists {:field "$sc_downloads.location"}}}}]}}
   :size 0})

(defn run-stats
  "Determine duplicate videos in the collection."
  [query]
  (let [url (core/elastic-url)
        results (elastic/post-search url query)]
    (get-in results [:hits :total])))

(defn -main
  []
  (let [duplicate-videos (run-stats duplicate-videos-query)
        duplicate-videos-verified (run-stats duplicate-videos-verified-query)
        no-video-download (run-stats no-video-download-query)
        no-video-download-verified (run-stats no-video-download-verified-query)
        url-downloads (run-stats url-download-type-query)
        missing-cid (run-stats missing-cid-query)
        missing-video-downloads (run-stats video-without-download-query)
        missing-video-downloads-verified (run-stats video-without-download-verified-query)
        youtube-videos-missing-filename (run-stats youtube-videos-without-a-filename-query)
        youtube-videos-missing-filename-verified (run-stats youtube-videos-without-a-filename-verified-query)
        videos-missing-filename (run-stats videos-without-a-filename-query)
        videos-missing-filename-verified (run-stats videos-without-a-filename-verified-query)
        filename-russia (run-stats filename-in-russia-strikes-query)
        filename-chemical (run-stats filename-in-chemical-weapons-query)
        filename-videoapi (run-stats filename-in-videoapi-query)
        filename-russia-verified (run-stats filename-in-russia-strikes-verified-query)
        filename-chemical-verified (run-stats filename-in-chemical-weapons-verified-query)
        filename-videoapi-verified (run-stats filename-in-videoapi-verified-query)
        images-outside-collection (run-stats images-outside-collection-query)
        downloads-outside-collection (run-stats downloads-outside-collection-query)
        downloads-outside-collection-verified (run-stats downloads-outside-collection-verified-query)
        images-that-are-videos (run-stats images-that-are-videos-query)
        images-that-are-videos-verified (run-stats images-that-are-videos-verified-query)
        cid-filename-without-download-locations (run-stats cid-filename-without-download-locations-query)
        cid-filename-without-download-locations-verified (run-stats cid-filename-without-download-locations-verified-query)]
    (println (format "Duplicate videos: %s (%s)" duplicate-videos duplicate-videos-verified))
    (println (format "Outdated video type (youtube_video): %s (%s)" no-video-download no-video-download-verified))
    (println (format "URL Download type: %s" url-downloads))
    (println (format "Missing CID field: %s" missing-cid))
    (println (format "Missing downloads for a video: %s (%s)" missing-video-downloads missing-video-downloads-verified))
    (println (format "Youtube videos without cid.filename: %s (%s)" youtube-videos-missing-filename youtube-videos-missing-filename-verified))
    (println (format "Videos without cid.filename: %s (%s)" videos-missing-filename videos-missing-filename-verified))
    (println (format "Filename outside of collection: Russia %s (%s), Chemical Weapons: %s (%s), Videoapi results: %s (%s)"
                     filename-russia filename-russia-verified filename-chemical filename-chemical-verified filename-videoapi filename-videoapi-verified))
    (println (format "Images outside of data collection: %s" images-outside-collection))
    (println (format "Downloads outside of data collection: %s (%s)" downloads-outside-collection downloads-outside-collection-verified))
    (println (format "Images that are actually a video: %s (%s)" images-that-are-videos images-that-are-videos-verified))
    (println (format "CID filenames without a download location: %s (%s)"
                     cid-filename-without-download-locations
                     cid-filename-without-download-locations-verified))))
