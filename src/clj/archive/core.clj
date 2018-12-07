(ns archive.core
  (:require [clojure.java.io :as io]
            [clojure.walk :refer (keywordize-keys)]
            [clojure.string :as string]
            [cheshire.core :as json]))

(defn json-str->map
  "Convert a JSON string to a map."
  [s]
  (keywordize-keys (json/parse-string s)))

(defn map->json-str
  "Convert a nested map to a json string."
  [coll]
  (json/generate-string coll))

(defn read-txt
  "Read a txt file and return a vector with one element for every line."
  [file]
  (string/split (slurp file) #"\n"))

(defn read-json
  "Read a json file and parse it as EDN."
  [file]
  (->> file
       slurp
       json-str->map
       keywordize-keys))

(defn elastic-url
  "Construct an Elasticsearch URL."
  []
  (let [file "./configs/elasticsearch.json"
        default {:host "localhost" :port 9200}
        custom (:elastic (read-json file))
        cfg (merge default custom)]
    (str "http://" (:host cfg) ":" (:port cfg) "/" (:index cfg))))

(defn elastic-query
  "Read a JSON Elasticsearch query from disk."
  [query-name]
  (let [file (io/file (.getCanonicalPath (io/file "./es-queries")) (str query-name ".json"))]
    (read-json file)))
