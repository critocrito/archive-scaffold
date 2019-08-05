(ns archive.scripts.verify-website-data
  (:require [archive.core :as core]))

(defn valid-location?
  [{:keys [location] :as data}]
  (let [is-valid (and (not (nil? location))
                      (not (nil? (:lat location)))
                      (not (nil? (:lon location))))]
    (if is-valid data nil)))

(defn map-observations
  [observations]
  (map (fn [o] (let [loc (:location o)]
                {
                 :id (:id o)
                 :title (:title o)
                 :summary (:summary o)
                 :lang (:lang o)
                 :incident_date_time (:incident_date_time o)
                 :location_name (:name loc)
                 :location_lat (:lat loc)
                 :location_lng (:lon loc)})) observations))

(defn missing-location
  [observations]
  (let [fn (fn
            [memo observation]
            (if
                (valid-location? observation)
              memo
              (conj memo observation)))]
    (->> observations
         (reduce fn [])
         map-observations)))

(def columns
  [:id :location_name :location_lat :location_lng :title :summary :lang :incident_date_time])

(defn -main
  [& args]
  (let [op (first args)
        file (second args)
        data (core/read-json "observations2.json")
        observations (:data data)]
    (cond
      (and (= op "missing-location")
           (not (nil? file))) (core/print-csv columns (->> (core/read-json file)
                                                           :data
                                                           missing-location))
      :else (println op "unknown"))))
