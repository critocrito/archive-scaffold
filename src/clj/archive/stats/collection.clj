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

(def no-video-download-query
  "Query for Elasticsearch to determine how many videos use the outdated youtube_video download type."
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
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

(def youtube-videos-without-a-filename-query
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
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

(def filename-in-russia-strikes-query
  {:query
   {:bool
    {:must
     [{:wildcard {:cid.filename "*russia*"}}]}}
   :size 0})

(def filename-in-chemical-weapons-query
  {:query
   {:bool
    {:must
     [{:wildcard {:cid.filename "*chemical*"}}]}}
   :size 0})

(def filename-in-videoapi-query
  {:query
   {:bool
    {:must
     [{:wildcard {:cid.filename "*videoapi*"}}]}}
   :size 0})

(def images-outside-collection-query
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "image"}}}}
      {:nested {:path "$sc_downloads" :query {:wildcard {:$sc_downloads.location "*files*"}}}}]}}
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
        no-video-download (run-stats no-video-download-query)
        url-downloads (run-stats url-download-type-query)
        missing-cid (run-stats missing-cid-query)
        missing-video-downloads (run-stats video-without-download-query)
        youtube-videos-missing-filename (run-stats youtube-videos-without-a-filename-query)
        videos-missing-filename (run-stats videos-without-a-filename-query)
        filename-russia (run-stats filename-in-russia-strikes-query)
        filename-chemical (run-stats filename-in-chemical-weapons-query)
        filename-videoapi (run-stats filename-in-videoapi-query)
        images-outside-collection (run-stats images-outside-collection-query)]
    (println (format "Duplicate videos: %s" duplicate-videos))
    (println (format "Outdated video type (youtube_video): %s" no-video-download))
    (println (format "URL Download type: %s" url-downloads))
    (println (format "Missing CID field: %s" missing-cid))
    (println (format "Missing downloads for a video: %s" missing-video-downloads))
    (println (format "Youtube videos without cid.filename: %s" youtube-videos-missing-filename))
    (println (format "Videos without cid.filename: %s" videos-missing-filename))
    (println (format "Filename outside of collection: Russia %s, Chemical Weapons: %s, Videoapi results: %s"
                     filename-russia filename-chemical filename-videoapi))
    (println (format "Images outside of data collection: %s" images-outside-collection))))
