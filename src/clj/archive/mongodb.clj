(ns archive.mongodb
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [archive.core :as core]))

(defn mongo-connection
  "Get a connection to the MongoDB database."
  []
  (let [uri (core/mongo-url)
        {:keys [conn db]} (mg/connect-via-uri uri)]
    [conn db]))

(defn find-one-by-id
  "Retrieve an individual unit by it's id hash."
  [db id]
  (let [coll "units"]
    (mc/find-one-as-map db coll {:_sc_id_hash id})))

(defn find-many-by-ids
  "Retrieve multiple units by the id hash."
  [db ids]
  (let [coll "units"]
    (vec (mc/find-maps db coll {:_sc_id_hash {:$in ids}}))))

(defn update-one-by-id
  "Update a unit by it's ObjectID."
  [db oid unit]
  (let [coll "units"]
    (mc/update-by-id db coll oid unit)))

(defn find-by-query
  "Find units by query."
  [db query]
  (let [coll "units"]
    (mc/find-maps db coll query)))

(defn count-by-query
  "Find units by query."
  [db query]
  (let [coll "units"]
    (mc/count db coll query)))
