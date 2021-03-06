(ns archive.elastic
  (:require [cemerick.url :refer [url]]
            [archive.core :refer [map->json-str]]
            [archive.http :as http]))

(defn strip-index
  "Strip the index from a elasticsearch url."
  [elastic-url]
  (-> (url elastic-url)
      ((fn [{:keys [protocol host port]}]
         (str protocol "://" host ":" port)))))

(defn post-search
  "Make a search using a request body."
  [elastic-url query & {:keys [params] :or {params {}}}]
  (->> query
       map->json-str
       (#(assoc {:method :post
                 :timeout 60000
                 :url (str elastic-url "/_search")
                 :query-params params
                 :headers {"Content-Type" "application/json"}}
                :body %))
       http/make-http-call))

(defn update-document
  "Update a unit."
  [elastic-url id unit]
  (->> unit
       map->json-str
       (#(assoc {:method :put
                 :timeout 60000
                 :url (str elastic-url "/_doc/" id)
                 :headers {"Content-Type" "application/json"}}
                :body %))
       http/make-http-call))

(defn scroll-request
  "Drain a scrolled Elastic query."
  [elastic-url scroll-id result-iter]
  (let [query {:scroll "1m" :scroll_id scroll-id}
        [hits id] (->> query
                       map->json-str
                       (#(assoc {:method :post
                                 :timeout 60000
                                 :url (str (strip-index elastic-url) "/_search/scroll")
                                 :headers {"Content-Type" "application/json"}}
                                :body %))
                       http/make-http-call
                       ((fn [resp] [(get-in resp [:hits :hits]) (get-in resp [:_scroll_id])])))]
    (if (not-empty hits)
      (recur elastic-url id (conj result-iter hits))
      (vec (flatten result-iter)))))

(defn scrolled-post-search
  "Make a search and retrieve all results using scrolling."
  [elastic-url query & params]
  (let [resp (post-search elastic-url query :params (merge (or params {}) {:scroll "1m"}))
        [scroll-id hits] [(get-in resp [:_scroll_id]) (get-in resp [:hits :hits])]]
    (scroll-request elastic-url scroll-id hits)))

(defn find-one-by-id
  "Search for a single unit by it's id hash. Returns the raw _source of the first result."
  [elastic-url id]
  (let [resp (post-search elastic-url {:query {:term {:$sc_id_hash id}}})
        hits (get-in resp [:hits :hits])
        source (:_source (first hits))]
    source))
