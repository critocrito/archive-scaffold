(ns archive.stats.locations
  (:require [archive.core :as core]
            [archive.http :as http]
            [archive.csv :as csv]))

(def query
  {:query
   {:nested
    {:path "$sc_locations"
     :query
     {:exists
      {:field "$sc_locations.location"}}}}
   :aggs
   {:locations
    {:nested {:path "$sc_locations"}
     :aggs
     {:location_type
      {:terms
       {:field "$sc_locations.type"}}}}}
   :size 0})

(defn -main
  "Stats by source type."
  []
  (let [aggs (->> query
                  core/map->json-str
                  (#(assoc {:method :post
                            :url (str (core/elastic-url) "/_search")
                            :headers {"Content-Type" "application/json"}}
                           :body %))
                  http/make-http-call)
        total (get-in aggs [:aggregations :locations :doc_count])
        buckets (get-in aggs [:aggregations :locations :location_type :buckets])]
    (csv/print-csv [:key :doc_count :total] (map #(merge % {:total total}) buckets))))
