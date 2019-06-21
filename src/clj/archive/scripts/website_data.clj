(ns archive.scripts.website-data
  (:require [clojure.java.io :as io]
            [clojure.string :refer [trim]]
            [clj-time.format :as f]
            [archive.core :as core]
            [archive.elastic :as elastic]))

(def verified-data-query
  "Retrieve the verified observations."
  {:query
   {:term {:cid.verified true}}
   :size 1000
   :_source {:include ["$sc_id_hash" "cid"]}})

(defn maybe-trim
  [x]
  (if (nil? x)
    x
    (trim x)))

(defn maybe-href
  [x]
  (if (nil? x)
    x
    (str "https://cube.syrianarchive.org/syrian-archive/files/" (.getName (io/file x)))))

(defn website-observation
  [{:keys [$sc_id_hash cid]}]
  (let [date-string (str (:incident_date cid) " " (:incident_time cid))
        date-formatter (f/formatter "yyyy-MM-dd HH:mm:ss")]
    {:id $sc_id_hash
     :annotations
     {:online_title_en (maybe-trim (:online_title_en cid))
      :online_title_ar (maybe-trim (:online_title_ar cid))
      :summary_en (maybe-trim (:summary_en cid))
      :summary_ar (maybe-trim (:summary_ar cid))
      :incident_date_time (f/unparse (f/formatters :date-time-no-ms)
                                     (f/parse date-formatter date-string))
      :location_info
      {:location (maybe-trim (:location cid))
       :lat (:latitude cid)
       :lon (:longitude cid)}}
     :type_of_violation (:type_of_violation cid)
     :online_link (:online_link cid)
     :href (maybe-href (:filename cid))
     :collections []
     :incidents_code []}))

(defn -main
  []
  (let [url (core/elastic-url)
        transform #(->> % :_source website-observation)
        data (map transform (elastic/scrolled-post-search url verified-data-query))]
    (doall
     (println (core/map->json-str data)))))
