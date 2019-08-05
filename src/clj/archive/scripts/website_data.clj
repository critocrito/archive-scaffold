(ns archive.scripts.website-data
  (:require [clojure.java.io :as io]
            [clojure.string :refer [trim]]
            [clojure.edn :as edn]
            [clj-time.format :as f]
            [me.raynes.fs :as fs]
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
  (let [project-dir (.getName fs/*cwd*)]
    (if (nil? x)
      x
      (str "https://cube.syrianarchive.org/" project-dir "/files/" (.getName (io/file x))))))

(defn website-observation-nested
  [{:keys [$sc_id_hash cid]}]
  (let [date-formatter (f/formatter "yyyy-MM-dd HH:mm:ss")
        incident-date (if (nil? (:incident_time cid))
                        (:incident_date cid)
                        (f/unparse (f/formatters :date-time-no-ms)
                                   (f/parse date-formatter (str (:incident_date cid) " " (:incident_time cid)))))]
    {:id $sc_id_hash
     :annotations
     {:online_title_en (maybe-trim (:online_title_en cid))
      :online_title_ar (maybe-trim (:online_title_ar cid))
      :summary_en (maybe-trim (:summary_en cid))
      :summary_ar (maybe-trim (:summary_ar cid))
      :incident_date_time incident-date
      :location_info
      {:location (maybe-trim (:location cid))
       :lat (if (string? (:latitude cid))
              (edn/read-string (:latitude cid))
              (:latitude cid))
       :lon (if (string? (:longitude cid))
              (edn/read-string (:longitude cid))
              (:longitude cid))}}
     :type_of_violation (:type_of_violation cid)
     :online_link (:online_link cid)
     :href (maybe-href (:filename cid))
     :collections []
     :incidents_code []}))

(defn website-observation-flat
  [{:keys [$sc_id_hash cid]}]
  (let [date-formatter (f/formatter "yyyy-MM-dd HH:mm:ss")
        incident-date (if (nil? (:incident_time cid))
                        (:incident_date cid)
                        (f/unparse (f/formatters :date-time-no-ms)
                                   (f/parse date-formatter (str (:incident_date cid) " " (:incident_time cid)))))
        title_ar (if (nil? (:online_title_ar cid)) (:summary_ar cid) (:online_title_ar cid))
        title_en (if (nil? (:online_title_en cid)) (:summary_en cid) (:online_title_en cid))
        observation {:id $sc_id_hash
                     :incident_date_time incident-date
                     :link (:online_link cid)
                     :location
                     {:name (maybe-trim (:location cid))
                      :lat (if (string? (:latitude cid)) (edn/read-string (:latitude cid)) (:latitude cid))
                      :lon (if (string? (:longitude cid)) (edn/read-string (:longitude cid)) (:longitude cid))}
                     :type_of_violation (:type_of_violation cid)
                     :href (maybe-href (:filename cid))
                     :collections []
                     :incidents_code []}]
    [(conj observation {:lang "ar" :title title_ar :summary (:summary_ar cid)})
     (conj observation {:lang "en" :title title_en :summary (:summary_en cid)})]))

(defn -main
  []
  (let [url (core/elastic-url)
        data (into []
                   (comp
                    (map :_source)
                    (map website-observation-flat))
                   (elastic/scrolled-post-search url verified-data-query))]
    (doall
     (let [xs (reduce into [] data)]
       (println (core/map->json-str {:total (count xs) :data xs}))))))
