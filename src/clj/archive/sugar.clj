(ns archive.sugar
  (:require [clojure.java.io :as io]))

(defn find-downloads
  "Find downloads of a particular type in the downloads section."
  [type downloads]
  (filter #(= (:type %) type) downloads))

(defn find-download
  "Find a single download of a particular type in the downloads section."
  [type downloads]
  (->> downloads
       (find-downloads type)
       first))

(defn fmt-location-video
  "Format a new video location."
  [id location]
  ;; some locations are not set. We ognore them for the time being.
  (if (nil? location)
    location
    (format "data/%s/youtubedl/%s"
            id
            (.getName
             (io/file
              ;; Some locations are stored using URL percent encoding
              (java.net.URLDecoder/decode location))))))

(defn fmt-location-image
  "Format a new video location."
  [id location]
  ;; some locations are not set. We ognore them for the time being.
  (if (nil? location)
    location
    (format "data/%s/image/%s"
            id
            (.getName
             (io/file
              ;; Some locations are stored using URL percent encoding
              (java.net.URLDecoder/decode location))))))

(defn exclude-from-downloads
  "Filter downloads of a certain type."
  [type downloads]
  (filter #(not= (:type %) type) downloads))
