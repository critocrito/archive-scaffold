(ns archive.core
  (:require [clojure.java.io :as io]
            [clojure.walk :refer (keywordize-keys)]
            [clojure.string :as string]
            [cheshire.core :as json]
            [clojure.data.csv :as csv]))

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

(defn mongo-url
  "Fetch the MongoDB connection uri."
  []
  (let [file "./configs/mongodb.json"
        cfg (:mongodb (read-json file))
        uri (:uri cfg)]
    uri))

(defn csv-data->maps
  "Convert csv data to a collection of maps."
  [csv-data]
  (map zipmap
       (->> (first csv-data)
            (map keyword)
            repeat)
       (rest csv-data)))

(defn maps->csv-data
  "Convert a collection of maps to csv."
  [columns coll]
  (mapv #(mapv % columns) coll))

(defn read-csv
  "Read csv data from a file and convert it to a collection of maps."
  [path]
  (with-open [reader (io/reader path)]
    (doall
     (csv-data->maps (csv/read-csv reader)))))

(defn write-csv
  "Convert a collection of maps to csv with columns and writes it to a file."
  [path columns coll]
  (let [headers (map name columns)
        rows (maps->csv-data columns coll)]
    (with-open [file (io/writer path)]
      (csv/write-csv file (cons headers rows)))))

(defn print-csv
  "Convert a collection of maps to csv with columns and writes it to STDOUT."
  [columns coll]
  (let [headers (map name columns)
        rows (maps->csv-data columns coll)]
    (csv/write-csv *out* (cons headers rows))))
