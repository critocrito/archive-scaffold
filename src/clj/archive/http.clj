(ns archive.http
  (:require [org.httpkit.client :as http]
            [archive.core :refer [json-str->map map->json-str]]))

(defn make-http-call
  "Handle a single HTTP and parse inputs and outputs to and from JSON."
  [{:keys [method url] :as request}]
  (let [{:keys [error body]} @(http/request request)]
    (if error
      (throw (Exception. (str method " request to " url " failed: " error)))
      (json-str->map body))))

(defn make-http-calls
  "Take a vector of request maps and fire off the http calls. Collect all responses."
  [requests]
  (let [promises (doall (map http/request requests))
        results (doall (map deref promises))]
    (map (fn [{:keys [headers body error] :as resp}]
           (if error
             (throw (Exception. (str headers " || " error)))
             (json-str->map body)))
         results)))

(defn chunk-and-call
  "Divide the input requests into smaller chunks and make the requests."
  [count requests]
  (let [chunks (partition-all count requests)]
    (doall (mapcat make-http-calls chunks))))
