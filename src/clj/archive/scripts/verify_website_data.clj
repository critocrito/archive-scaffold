(ns archive.scripts.verify-website-data
  (:require [org.httpkit.client :as http]
            [archive.core :as core]))

(defn nil-or-empty?
  [v]
  (or (nil? v)
      (= "" v)))

(defn valid-location?
  [{:keys [location] :as data}]
  (let [is-valid (and (not (nil? location))
                      (not (nil-or-empty? (:lat location)))
                      (not (nil-or-empty? (:lon location)))
                      (not (nil-or-empty? (:name location))))]
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
                 :href (:href o)
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

(defn link-checking
  [observations]
  (->> observations
       (filter (fn [o]
                 (if (nil? (:href o))
                   true
                   (let [request {:method :head :url (:href o)}
                         {:keys [error status]} @(http/request request)]
                     (if (or error (= status 404))
                       true
                       false)))))))

(defn missing-summary
  [observations]
  (let [fn (fn [memo observation]
            (if (nil-or-empty? (:summary observation))
              (conj memo observation)
              memo))]
    (->> observations
         (reduce fn [])
         map-observations)))

(def columns
  [:id :location_name :location_lat :location_lng :href :title :summary :lang :incident_date_time])

(defn -main
  [& args]
  (let [op (first args)
        file (second args)]
    (cond
      (and (= op "missing-location")
           (not (nil? file))) (core/print-csv columns (->> (core/read-json file)
                                                           :data
                                                           missing-location))
      (and (= op "check-video-links")
           (not (nil? file))) (core/print-csv columns (->> (core/read-json file)
                                                           :data
                                                           link-checking))
      (and (= op "missing-summary")
           (not (nil? file))) (core/print-csv columns (->> (core/read-json file)
                                                           :data
                                                           missing-summary))
      :else (println op "unknown"))))
