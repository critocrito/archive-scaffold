(ns archive.scripts.fix-collection
  (:require [clojure.core.reducers :as r]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [archive.core :as core]
            [archive.elastic :as elastic]
            [archive.mongodb :as mongodb]
            [archive.sugar :as sugar]))

(def duplicate-videos-query
  "Query for Elasticsearch to determine how many duplicate videos there are."
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}
      {:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]}}
   :size 1000
   :_source {:include ["$sc_id_hash" "$sc_downloads" "cid"]}})

(def youtube-video-downloads-query
  "Query for Elasticsearch to determine how many videos use the outdated youtube_video download type."
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "youtube_video"}}}}]
     :must_not
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}]}}
   :size 1000
   :_source {:include ["$sc_id_hash"]}})

(def images-outside-data-collection-query
  "Query to fetch images that are stored outside of the data collection."
  {:query
   {:bool
    {:must
     [{:nested
       {:path "$sc_downloads"
        :query
        {:bool
         {:must [{:term {:$sc_downloads.type "image"}}
                 {:match {:$sc_downloads.location "/var/www"}}]}}}}]}}
   :size 1000
   :_source {:include ["$sc_id_hash"]}})

(def videos-outside-data-collection-query
  "Query for Elasticsearch to determine how many downloads have a file path outside of the regular download directory."
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads"
                :query
                {:bool
                 {:must [{:term {:$sc_downloads.type "video"}}
                         {:match {:$sc_downloads.location "/var/www"}}]}}}}]}}
   :size 1000
   :_source {:include ["$sc_id_hash"]}})

(def cid-filename-outside-data-collection-query
  "Query units that have cid.filename set to outside of the collection."
  {:query
   {:match {:cid.filename "/var/www"}}
   :size 1000
   :_source {:include ["$sc_id_hash"]}})

(def videos-without-filename-query
  "Merge videos without cid.filename set, Elasticsearch version."
  {:query
   {:bool
    {:must
     [{:nested {:path "$sc_downloads" :query {:term {:$sc_downloads.type "video"}}}}]
     :must_not
     [{:exists {:field "cid.filename"}}]}}
   :size 1000
   :_source {:include ["$sc_id_hash"]}})

(defn merge-safe-duplicate-videos-mongo
  "Merge the duplicate video downloads that are safe to merge, MongoDB version."
  [db id]
  (let [unit (mongodb/find-one-by-id db id)
        {:keys [_sc_id_hash _sc_downloads cid]} unit
        video (sugar/find-download "video" _sc_downloads)
        stripped-downloads (->> _sc_downloads
                                (sugar/exclude-from-downloads "youtube_video")
                                (sugar/exclude-from-downloads "video"))
        location (sugar/fmt-location-video _sc_id_hash (:location video))
        downloads (vec (conj stripped-downloads (merge video {:location location})))
        cid (merge cid {:filename (if (nil? location) (:filename cid) location) :md5_hash (:md5 video)})
        mongo-unit (merge unit {:cid cid :_sc_downloads downloads})]
    (mongodb/update-one-by-id db (:_id mongo-unit) (dissoc mongo-unit :_id))
    {:id _sc_id_hash :old_location (:location video) :new_location location :verified (:verified cid)}))

(defn merge-safe-duplicate-videos-elastic
  "Merge the duplicate video downloads that are safe to merge, Elasticsearch version."
  [url id]
  (let [unit (elastic/find-one-by-id url id)
        {:keys [$sc_id_hash $sc_downloads cid]} unit
        video (sugar/find-download "video" $sc_downloads)
        stripped-downloads (->> $sc_downloads
                                (sugar/exclude-from-downloads "youtube_video")
                                (sugar/exclude-from-downloads "video"))
        location (sugar/fmt-location-video $sc_id_hash (:location video))
        downloads (vec (conj stripped-downloads (merge video {:location location})))
        cid (merge cid {:filename (if (nil? location) (:filename cid) location) :md5_hash (:md5 video)})
        es-unit (merge unit {:cid cid :$sc_downloads downloads})]
    (elastic/update-document url $sc_id_hash es-unit)
    {:id $sc_id_hash :old_location (:location video) :new_location location :verified (:verified cid)}))

(defn merge-safe-duplicate-videos
  "Merge duplicate video downloads that are safe to merge."
  [url db id]
  (merge-safe-duplicate-videos-mongo db id)
  (merge-safe-duplicate-videos-elastic url id))

(defn merge-unsafe-duplicate-videos-mongo
  "Merge the duplicate video downloads that are safe to merge, MongoDB version."
  [db id]
  (let [unit (mongodb/find-one-by-id db id)
        {:keys [_sc_id_hash _sc_downloads cid]} unit
        video (sugar/find-download "video" _sc_downloads)
        youtube-video (sugar/find-download "youtube_video" _sc_downloads)
        stripped-downloads (->> _sc_downloads
                                (sugar/exclude-from-downloads "youtube_video")
                                (sugar/exclude-from-downloads "video"))
        video-location (sugar/fmt-location-video _sc_id_hash (:location video))
        youtube-video-location (sugar/fmt-location-video _sc_id_hash (:location youtube-video))
        downloads (vec (concat stripped-downloads
                               [(merge video {:type "video" :location video-location})
                                (merge youtube-video {:type "video" :location youtube-video-location})]))
        cid (merge cid {:filename (if (nil? youtube-video-location) (:filename cid) youtube-video-location)
                        :filename2 video-location})
        mongo-unit (merge unit {:cid cid :_sc_downloads downloads})]
    (mongodb/update-one-by-id db (:_id mongo-unit) (dissoc mongo-unit :_id))
    [{:id _sc_id_hash :old_location (:location video) :new_location video-location :verified (:verified cid)}
     {:id _sc_id_hash :old_location (:location video) :new_location youtube-video-location :verified (:verified cid)}]))

(defn merge-unsafe-duplicate-videos-elastic
  "Merge the duplicate video downloads that are safe to merge, Elasticsearch version."
  [url id]
  (let [unit (elastic/find-one-by-id url id)
        {:keys [$sc_id_hash $sc_downloads cid]} unit
        video (sugar/find-download "video" $sc_downloads)
        youtube-video (sugar/find-download "youtube_video" $sc_downloads)
        stripped-downloads (->> $sc_downloads
                                (sugar/exclude-from-downloads "youtube_video")
                                (sugar/exclude-from-downloads "video"))
        video-location (sugar/fmt-location-video $sc_id_hash (:location video))
        youtube-video-location (sugar/fmt-location-video $sc_id_hash (:location youtube-video))
        downloads (vec (concat stripped-downloads
                               [(merge video {:type "video" :location video-location})
                                (merge youtube-video {:type "video" :location youtube-video-location})]))
        cid (merge cid {:filename (if (nil? youtube-video-location) (:filename cid) youtube-video-location)
                        :filename2 video-location})
        es-unit (merge unit {:cid cid :$sc_downloads downloads})]
    (elastic/update-document url $sc_id_hash es-unit)
    [{:id $sc_id_hash :old_location (:location video) :new_location video-location :verified (:verified cid)}
     {:id $sc_id_hash :old_location (:location youtube-video) :new_location youtube-video-location :verified (:verified cid)}]))

(defn merge-unsafe-duplicate-videos
  "Merge duplicate video downloads that are not safe to merge."
  [url db id]
  (merge-unsafe-duplicate-videos-mongo db id)
  (merge-unsafe-duplicate-videos-elastic url id))

(defn rename-youtube-video-download-type-mongo
  "Fix the download type to video, MongoDB version."
  [db id]
  (let [unit (mongodb/find-one-by-id db id)
        {:keys [_sc_id_hash _sc_downloads cid]} unit
        video (sugar/find-download "youtube_video" _sc_downloads)
        stripped-downloads (->> _sc_downloads
                                (sugar/exclude-from-downloads "youtube_video"))
        location (sugar/fmt-location-video _sc_id_hash (:location video))
        downloads (vec (conj stripped-downloads (merge video {:type "video" :location location})))
        cid (merge cid {:filename (if (nil? location) (:filename cid) location)})
        mongo-unit (merge unit {:cid cid :_sc_downloads downloads})]
    (mongodb/update-one-by-id db (:_id mongo-unit) (dissoc mongo-unit :_id))
    {:id _sc_id_hash :old_location (:location video) :new_location location :verified (:verified cid)}))

(defn rename-youtube-video-download-type-elastic
  "Fix the download type to video, Elasticsearch version."
  [url id]
  (let [unit (elastic/find-one-by-id url id)
        {:keys [$sc_id_hash $sc_downloads cid]} unit
        video (sugar/find-download "youtube_video" $sc_downloads)
        stripped-downloads (->> $sc_downloads
                                (sugar/exclude-from-downloads "youtube_video"))
        location (sugar/fmt-location-video $sc_id_hash (:location video))
        downloads (vec (conj stripped-downloads (merge video {:type "video" :location location})))
        cid (merge cid {:filename (if (nil? location) (:filename cid) location)})
        es-unit (merge unit {:cid cid :$sc_downloads downloads})]
    (elastic/update-document url $sc_id_hash es-unit)
    {:id $sc_id_hash :old_location (:location video) :new_location location :verified (:verified cid)}))

(defn rename-youtube-video-download-type
  "Fix the download type to video."
  [url db id]
  (rename-youtube-video-download-type-mongo db id)
  (rename-youtube-video-download-type-elastic url id))

(defn merge-images-outside-collection-mongo
  "Merge the duplicate video downloads that are safe to merge, MongoDB version."
  [db id]
  (let [unit (mongodb/find-one-by-id db id)
        {:keys [_sc_id_hash _sc_downloads cid]} unit
        images (sugar/find-downloads "image" _sc_downloads)
        stripped-downloads (->> _sc_downloads
                                (sugar/exclude-from-downloads "image"))
        downloads (->> images
                       (map (fn [image]
                              (let [location (sugar/fmt-location-image _sc_id_hash (:location image))]
                                (merge image {:location location}))))
                       (concat stripped-downloads))
        mongo-unit (merge unit {:_sc_downloads downloads})]
    (mongodb/update-one-by-id db (:_id mongo-unit) (dissoc mongo-unit :_id))
    (->> downloads
         (filter #(= (:type %) "image"))
         (map (fn [image]
                (let [old-image (->> images
                                     (filter #(or (= (:_sc_id_hash %) (:_sc_id_hash image))
                                                  (= (:sha256 %) (:sha256 image))
                                                  (= (:md5 %) (:md5 image))))
                                     first)]
                  {:id _sc_id_hash
                   :old_location (:location old-image)
                   :new_location (:location image)
                   :verified (:verified cid)}))))))

(defn merge-images-outside-collection-elastic
  "Merge the duplicate video downloads that are safe to merge, Elasticsearch version."
  [url id]
  (let [unit (elastic/find-one-by-id url id)
        {:keys [$sc_id_hash $sc_downloads cid]} unit
        images (sugar/find-downloads "image" $sc_downloads)
        stripped-downloads (->> $sc_downloads
                                (sugar/exclude-from-downloads "image"))
        downloads (->> images
                       (map (fn [image]
                              (let [location (sugar/fmt-location-image $sc_id_hash (:location image))]
                                (merge image {:location location}))))
                       (concat stripped-downloads))
        es-unit (merge unit {:$sc_downloads downloads})]
    (elastic/update-document url $sc_id_hash es-unit)
    (->> downloads
         (filter #(= (:type %) "image"))
         (map (fn [image]
                (let [old-image (->> images
                                     (filter #(or (= (:$sc_id_hash %) (:$sc_id_hash image))
                                                  (= (:sha256 %) (:sha256 image))
                                                  (= (:md5 %) (:md5 image))))
                                     first)]
                  {:id $sc_id_hash
                   :old_location (:location old-image)
                   :new_location (:location image)
                   :verified (:verified cid)}))))))

(defn merge-images-outside-collection
  "Move images outside the data collection into the collection."
  [url db id]
  (merge-images-outside-collection-mongo db id)
  (merge-images-outside-collection-elastic url id))

(defn merge-videos-outside-collection-mongo
  "Merge video downloads that are outside of the regular collection path, MongoDB version."
  [db id]
  (let [unit (mongodb/find-one-by-id db id)
        {:keys [_sc_id_hash _sc_downloads cid]} unit
        videos (sugar/find-downloads "video" _sc_downloads)
        stripped-downloads (->> _sc_downloads
                                (sugar/exclude-from-downloads "video"))
        downloads (->> videos
                       (map (fn [video]
                              (let [location (sugar/fmt-location-video _sc_id_hash (:location video))]
                                (merge video {:location location}))))
                       (concat stripped-downloads))
        mongo-unit (merge unit {:_sc_downloads downloads})]
    (mongodb/update-one-by-id db (:_id mongo-unit) (dissoc mongo-unit :_id))
    (->> downloads
         (filter #(= (:type %) "video"))
         (map (fn [video]
                (let [old-video (->> videos
                                     (filter #(or (= (:_sc_id_hash %) (:_sc_id_hash video))
                                                  (= (:sha256 %) (:sha256 video))
                                                  (= (:md5 %) (:md5 video))))
                                     first)]
                  {:id _sc_id_hash
                   :old_location (:location old-video)
                   :new_location (:location video)
                   :verified (:verified cid)}))))))

(defn merge-videos-outside-collection-elastic
  "Merge video downloads that are safe to merge, Elasticsearch version."
  [url id]
  (let [unit (elastic/find-one-by-id url id)
        {:keys [$sc_id_hash $sc_downloads cid]} unit
        videos (sugar/find-downloads "video" $sc_downloads)
        stripped-downloads (->> $sc_downloads
                                (sugar/exclude-from-downloads "video"))
        downloads (->> videos
                       (map (fn [video]
                              (let [location (sugar/fmt-location-video $sc_id_hash (:location video))]
                                (merge video {:location location}))))
                       (concat stripped-downloads))
        es-unit (merge unit {:$sc_downloads downloads})]
    (elastic/update-document url $sc_id_hash es-unit)
    (->> downloads
         (filter #(= (:type %) "video"))
         (map (fn [video]
                (let [old-video (->> videos
                                     (filter #(or (= (:$sc_id_hash %) (:$sc_id_hash video))
                                                  (= (:sha256 %) (:sha256 video))
                                                  (= (:md5 %) (:md5 video))))
                                     first)]
                  {:id $sc_id_hash
                   :old_location (:location old-video)
                   :new_location (:location video)
                   :verified (:verified cid)}))))))

(defn merge-videos-outside-collection
  "Move video downloads outside the data collection into the collection."
  [url db id]
  (merge-videos-outside-collection-mongo db id)
  (merge-videos-outside-collection-elastic url id))

(defn merge-cid-filename-outside-collection-mongo
  "Merge video downloads that are outside of the regular collection path, MongoDB version."
  [db id]
  (let [unit (mongodb/find-one-by-id db id)
        {:keys [_sc_id_hash _sc_downloads cid]} unit
        video (->> _sc_downloads
                   (sugar/find-downloads "video")
                   first)
        location (sugar/fmt-location-video _sc_id_hash (:location video))
        new-cid (merge cid {:filename (if (nil? location) (:filename cid) location) :md5_hash (:md5 video)})

        mongo-unit (merge unit {:cid new-cid})]
    (mongodb/update-one-by-id db (:_id mongo-unit) (dissoc mongo-unit :_id))
    {:id _sc_id_hash
     :old_location (:filename cid)
     :new_location (:filename new-cid)
     :verified (:verified cid)}))

(defn merge-cid-filename-outside-collection-elastic
  "Merge video downloads that are safe to merge, Elasticsearch version."
  [url id]
  (let [unit (elastic/find-one-by-id url id)
        {:keys [$sc_id_hash $sc_downloads cid]} unit
        video (->> $sc_downloads
                   (sugar/find-downloads "video")
                   first)
        location (sugar/fmt-location-video $sc_id_hash (:location video))
        new-cid (merge cid {:filename (if (nil? location) (:filename cid) location) :md5_hash (:md5 video)})
        es-unit (merge unit {:cid new-cid})]
    (println video location new-cid)
    (elastic/update-document url $sc_id_hash es-unit)
    {:id $sc_id_hash
     :old_location (:filename cid)
     :new_location (:filename new-cid)
     :verified (:verified cid)}))

(defn merge-cid-filename-outside-collection
  "Move video downloads outside the data collection into the collection."
  [url db id]
  (merge-cid-filename-outside-collection-mongo db id)
  (merge-cid-filename-outside-collection-elastic url id))

(defn merge-videos-without-filename-mongo
  "Merge videos without cid.filename set, MongoDB version."
  [db id]
  (let [unit (mongodb/find-one-by-id db id)
        {:keys [_sc_id_hash _sc_downloads cid]} unit
        video (->> _sc_downloads
                   (sugar/find-downloads "video")
                   first)
        location (sugar/fmt-location-video _sc_id_hash (:location video))
        new-cid (merge cid {:filename (if (nil? location) (:filename cid) location) :md5_hash (:md5 video)})
        mongo-unit (merge unit {:cid new-cid})]
    (mongodb/update-one-by-id db (:_id mongo-unit) (dissoc mongo-unit :_id))
    {:id _sc_id_hash
     :old_location (:location video)
     :new_location (:location video)
     :verified (:verified cid)}))

(defn merge-videos-without-filename-elastic
  "Merge videos without cid.filename set, Elasticsearch version."
  [url id]
  (let [unit (elastic/find-one-by-id url id)
        {:keys [$sc_id_hash $sc_downloads cid]} unit
        video (->> $sc_downloads
                   (sugar/find-downloads "video")
                   first)
        location (sugar/fmt-location-video $sc_id_hash (:location video))
        new-cid (merge cid {:filename (if (nil? location) (:filename cid) location) :md5_hash (:md5 video)})
        es-unit (merge unit {:cid new-cid})]
    (elastic/update-document url $sc_id_hash es-unit)
    {:id $sc_id_hash
     :old_location (:location video)
     :new_location (:location video)
     :verified (:verified cid)}))

(defn merge-videos-without-filename-collection
  "Merge videos without cid.filename set."
  [url db id]
  (merge-videos-without-filename-mongo db id)
  (merge-videos-without-filename-elastic url id))

(defn duplicate-video-safe-to-merge?
  "Determine if this videos are safe to merge. We exclude any unit that
  references a location in the russian_attacks, chemicalweapons or videpapi
  directories. The video source, md5sum and sha256sum of the download
  directories have to match as well."
  [{:keys [$sc_downloads cid]}]
  (let [video-download (sugar/find-download "video" $sc_downloads)
        youtube-video-download (sugar/find-download "youtube_video" $sc_downloads)]
    (and (= (:sha256 video-download) (:sha256 youtube-video-download))
         (= (:md5 video-download) (:md5 youtube-video-download))
         (= (:term video-download) (:term youtube-video-download))
         (if (nil? (:filename cid)) true (not (re-find #"russia" (:filename cid))))
         (if (nil? (:filename cid)) true (not (re-find #"videoapi" (:filename cid))))
         (if (nil? (:filename cid)) true (not (re-find #"chemical" (:filename cid))))
         (if (nil? (:location youtube-video-download)) false (not (re-find #"russia" (:location youtube-video-download))))
         (if (nil? (:location youtube-video-download)) false (not (re-find #"videoapi" (:location youtube-video-download))))
         (if (nil? (:location youtube-video-download)) false (not (re-find #"chemical" (:location youtube-video-download))))
         (if (nil? (:location video-download)) false (not (re-find #"russia" (:location video-download))))
         (if (nil? (:location video-download)) false (not (re-find #"videoapi" (:location video-download))))
         (if (nil? (:location video-download)) false (not (re-find #"chemical" (:location video-download)))))))

(defn fix-safe-duplicate-videos
  "Remove duplicate video downloads."
  [url db]
  (doall
   (->> (elastic/scrolled-post-search url duplicate-videos-query)
        (r/map :_source)
        (r/filter duplicate-video-safe-to-merge?)
        (into [])
        (partition-all 5)
        (map (fn [as] (map #(merge-safe-duplicate-videos url db (:$sc_id_hash %)) as)))
        flatten)))

(defn fix-unsafe-duplicate-videos
  "Remove duplicate video downloads."
  [url db]
  (doall
   (->> (elastic/scrolled-post-search url duplicate-videos-query)
        (r/map :_source)
        (r/filter (comp not duplicate-video-safe-to-merge?))
        (into [])
        (partition-all 5)
        (map (fn [as] (map #(merge-unsafe-duplicate-videos url db (:$sc_id_hash %)) as)))
        flatten)))

(defn fix-youtube-video-downloads
  "Rename downloads of type youtube_video to video."
  [url db]
  (doall
   (->> (elastic/scrolled-post-search url youtube-video-downloads-query)
        (r/map :_source)
        (into [])
        (partition-all 5)
        (map (fn [as] (map #(rename-youtube-video-download-type url db (:$sc_id_hash %)) as)))
        flatten)))

(defn fix-images-outside-collection
  "Move images back into the collection."
  [url db]
  (doall
   (->> (elastic/scrolled-post-search url images-outside-data-collection-query)
        (r/map :_source)
        (into [])
        (partition-all 5)
        (map (fn [as] (map #(merge-images-outside-collection url db (:$sc_id_hash %)) as)))
        flatten)))

(defn fix-videos-outside-collection
  "Move videos back into the collection."
  [url db]
  (doall
   (->> (elastic/scrolled-post-search url videos-outside-data-collection-query)
        (r/map :_source)
        (into [])
        (partition-all 5)
        (map (fn [as] (map #(merge-videos-outside-collection url db (:$sc_id_hash %)) as)))
        flatten)))

(defn fix-cid-filename-outside-collection
  "Rewrite cid.filename field."
  [url db]
  (doall
   (->> (elastic/scrolled-post-search url cid-filename-outside-data-collection-query)
        (r/map :_source)
        (into [])
        (partition-all 5)
        (map (fn [as] (map #(merge-cid-filename-outside-collection url db (:$sc_id_hash %)) as)))
        flatten)))

(defn fix-videos-without-filename
  "Merge videos without cid.filename set."
  [url db]
  (doall
   (->> (elastic/scrolled-post-search url videos-without-filename-query)
        (r/map :_source)
        (into [])
        (partition-all 5)
        (map (fn [as] (map #(merge-cid-filename-outside-collection url db (:$sc_id_hash %)) as)))
        flatten)))

(def csv-columns [:id :old_location :new_location :verified])

(defn -main
  "Fix inconsistencies in the collection."
  []
  (let [header (map name csv-columns)
        [_ db] (mongodb/mongo-connection)
        url (core/elastic-url)
        safe-duplicate-videos-csv (fix-safe-duplicate-videos url db)
        unsafe-duplicate-videos-csv (fix-unsafe-duplicate-videos url db)
        fixed-youtube-videos-csv (fix-youtube-video-downloads url db)
        images-outside-collection-csv (fix-images-outside-collection url db)
        videos-outside-collection-csv (fix-videos-outside-collection url db)
        cid-filename-outside-collection-csv (fix-cid-filename-outside-collection url db)
        videos-without-filename-csv (fix-videos-without-filename url db)
        rows (pmap #(mapv % csv-columns) (->> []
                                              (cons safe-duplicate-videos-csv)
                                              (cons unsafe-duplicate-videos-csv)
                                              (cons fixed-youtube-videos-csv)
                                              (cons images-outside-collection-csv)
                                              (cons videos-outside-collection-csv)
                                              (cons cid-filename-outside-collection-csv)
                                              (cons videos-without-filename-csv)
                                              flatten))]
    (csv/write-csv *out* (cons header rows))))
