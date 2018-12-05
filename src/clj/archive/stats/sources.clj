(ns archive.stats.sources
  (:require [archive.core :as core]
            [archive.http :as http]
            [archive.csv :as csv]))

(def query
  {:aggs
   {:sources
    {:terms
     {:field "$sc_source.keyword"}}}
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
                  http/make-http-call
                  (#(get-in % [:aggregations :sources :buckets])))]
    (csv/print-csv [:key :doc_count] aggs)))
