(ns archive.stats.sources
  (:require [archive.core :as core]
            [archive.elastic :as elastic]
            [archive.csv :as csv]))

(defn -main
  "Stats by source type."
  []
  (let [query (core/elastic-query "source-aggs")
        aggs (elastic/post-search (core/elastic-url) query)
        buckets (get-in aggs [:aggregations :sources :buckets])]
    (csv/print-csv [:key :doc_count] buckets)))
