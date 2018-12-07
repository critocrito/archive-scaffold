(ns archive.stats.locations
  (:require [archive.core :as core]
            [archive.http :as http]
            [archive.csv :as csv]))

(defn -main
  "Stats by source type."
  []
  (let [query (core/elastic-query "location-aggs")
        aggs (->> query
                  core/map->json-str
                  (#(assoc {:method :post
                            :url (str (core/elastic-url) "/_search")
                            :headers {"Content-Type" "application/json"}}
                           :body %))
                  http/make-http-call)
        total (get-in aggs [:aggregations :locations :doc_count])
        buckets (get-in aggs [:aggregations :locations :location_type :buckets])]
    (csv/print-csv [:key :doc_count :total] (map #(merge % {:total total}) buckets))))
