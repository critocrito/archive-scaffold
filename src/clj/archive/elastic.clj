(ns archive.elastic
  (:require [archive.core :refer [map->json-str]]
            [archive.http :as http]))

(defn post-search
  "Make a search using a request body."
  [url body]
  (->> body
       map->json-str
       (#(assoc {:method :post
                 :url (str url "/_search")
                 :headers {"Content-Type" "application/json"}}
                :body %))
       http/make-http-call))
