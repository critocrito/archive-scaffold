(ns archive.scripts.locations
  (:require [archive.core :as core]
            [archive.elastic :as elastic]))

(defn -main
  "Stats by source type."
  []
  (let [url (core/elastic-url)
        query (core/elastic-query "locations")
        results (elastic/scrolled-post-search url query)]
    (doall
     (println
      (core/map->json-str
       (map
        (fn [{:keys [_source]}]
          (let [location-type (:type (first (:$sc_locations _source)))]
            {:id (:$sc_id_hash _source)
             :lon (get-in (first (:$sc_locations _source)) [:location :lon])
             :lat (get-in (first (:$sc_locations _source)) [:location :lat])
             :type location-type
             :uploaded (get-in _source [:cid :upload_date])
             :desc (get-in _source [:cid :description])
             :lang (get-in _source [:cid :language])}))
        results))))))
