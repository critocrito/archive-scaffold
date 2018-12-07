(ns archive.stats.locations
  (:require [archive.core :as core]
            [archive.elastic :as elastic]
            [archive.csv :as csv]))

(defn -main
  "Stats by source type."
  []
  (let [query (core/elastic-query "location-aggs")
        aggs (elastic/post-search (core/elastic-url) query)
        total (get-in aggs [:aggregations :locations :doc_count])
        buckets (get-in aggs [:aggregations :locations :location_type :buckets])]
    (csv/print-csv [:key :doc_count :total] (map #(merge % {:total total}) buckets))))
