(ns archive.vps.vultr
  (:require [archive.http :as http]))

(def endpoint "https://api.vultr.com")

(defn list-regions
  []
  (let [url (str endpoint "/v1/regions/list")
        request {:method :get
                 :url url
                 :query-params {:availability "yes"}}]
    (Thread/sleep 500)
    (try
      (http/make-http-call-vultr request)
      (catch Exception e (println (.getMessage e))))))

(defn list-ssh-keys
  [api-key]
  (let [url (str endpoint "/v1/sshkey/list")
        request {:method :get
                 :url url
                 :headers {"API-Key" api-key}}]
    (Thread/sleep 500)
    (try
      (http/make-http-call-vultr request)
      (catch Exception e (println (.getMessage e))))))

(defn list-scripts
  [api-key]
  (let [url (str endpoint "/v1/startupscript/list")
        request {:method :get
                 :url url
                 :headers {"API-Key" api-key}}]
    (Thread/sleep 500)
    (try
      (http/make-http-call-vultr request)
      (catch Exception e (println (.getMessage e))))))

(defn list-servers
  ([api-key] (let [url (str endpoint "/v1/server/list")
                   request {:method :get
                            :url url
                            :headers {"API-Key" api-key}}]
               (Thread/sleep 500)
               (try
                 (http/make-http-call-vultr request)
                 (catch Exception e (println (.getMessage e))))))
  ([api-key subid] (let [url (str endpoint "/v1/server/list")
                         request {:method :get
                                  :url url
                                  :headers {"API-Key" api-key}
                                  :query-params {:SUBID subid}}]
                     (Thread/sleep 500)
                     (try
                       (http/make-http-call-vultr request)
                       (catch Exception e (println (.getMessage e)))))))

(defn poll-server
  [api-key subid]
  (letfn [(poll []
            (let [server (list-servers api-key subid)]
              (if (and server
                       (= (:status server) "active")
                       (= (:power_status server) "running"))
                server
                (do
                  (Thread/sleep 5000)
                  (recur)))))]
    (poll)))

(defn create-server
  [api-key body]
  (let [url (str endpoint "/v1/server/create")
        request {:method :post
                 :url url
                 :headers {"API-Key" api-key
                           "Content-Type"  "application/x-www-form-urlencoded"}
                 :form-params body}]
    (Thread/sleep 500)
    (http/make-http-call-vultr request)))

(defn destroy-server
  [api-key body]
  (let [url (str endpoint "/v1/server/destroy")
        request {:method :post
                 :url url
                 :headers {"API-Key" api-key
                           "Content-Type"  "application/x-www-form-urlencoded"}
                 :form-params body}]
    (Thread/sleep 500)
    (http/make-http-call-vultr request)))

(defn random-region
  [regions]
  (let [region (rand-nth (keys regions))]
    ((keyword region) regions)))

(defn select-ssh-key
  [ssh-key-name ssh-keys]
  (reduce-kv (fn [m k v] (if (= (:name v) ssh-key-name) v m)) nil ssh-keys))

(defn select-script
  [script-name scripts]
  (reduce-kv (fn [m k v] (if (= (:name v) script-name) v m)) nil scripts))

(defn select-server
  [server-id servers]
  (reduce-kv (fn [m k v] (if (= (:SUBID v) server-id) v m)) nil servers))

(defn select-servers-for-ids
  [servers ids]
  (reduce (fn [m k] (if (select-server k servers) (conj m k) m)) [] ids))

(defn create-request-body
  [region-id ssh-key-id script-id tag]
  {:DCID region-id
   :VPSPLANID 201 ;; 1GB RAM, 1VCPU, 25GB SSD
   :OSID 352      ;; Debian Buster x64
   :SSHKEYID ssh-key-id
   :SCRIPTID script-id
   :tag tag})

(defn create
  [api-key region ssh-key script tag]
  (try
    (let [body (create-request-body (:DCID region) (:SSHKEYID ssh-key) (:SCRIPTID script) tag)
          instance (create-server api-key body)
          server (deref (future (poll-server api-key (:SUBID instance))))]
      {:id (:SUBID server) :ip (:main_ip server) :provider :vultr})
    (catch Exception e (println (format "Failed to create instance: %s" (.getMessage e))))))

(defn destroy
  [api-key id]
  (let [body {:SUBID id}]
    (destroy-server api-key body)))
