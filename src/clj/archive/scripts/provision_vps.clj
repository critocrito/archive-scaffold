(ns archive.scripts.provision-vps
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [archive.core :as core]
            [archive.vps.vultr :as vps-vultr])
  (:gen-class))

(def cli-options
  [["-p" "--provider PROVIDER" "VPS provider"
    :default "vultr"
    :default-fn #(keyword (:provider %))
    :parse-fn #(keyword %)]
   ["-s" "--secrets PATH" "Path to the secrets file"
    :default "./configs/secrets.json"
    :default-fn #(core/read-json (:secrets %))
    :parse-fn #(core/read-json %)]
   ["-c" "--count NUM" "Number of instances to create."
    :default 1
    :parse-fn #(Integer/parseInt %)]
   ["-S" "--state PATH" "VPS provisioning state."
    :default "./vps-state.json"]
   ["-P" "--plan PLAN" "VPS plan."
    :default "small"
    :default-fn #(keyword (:plan %))
    :parse-fn #(keyword %)
    :validate [#(contains? #{:small :medium :large} %) "Plan must be either: small, medium or large."]]
   ["-l" "--label LABEL" "A label for this VPS."
    :default "unnamed_pipeline"
    :default-fn #(keyword (:label %))]
   ["-h" "--help"]])

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn usage [options-summary]
  (->> ["Provision VPS for the Syrian Archive."
        ""
        "Usage: ./bin/provision-vps [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  create Create a new VPS"
        "  destroy Destroy a VPS that was previously provisioned."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (and (= 1 (count arguments))
           (#{"create" "destroy"} (first arguments)))
      {:action (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defprotocol IVps
  (create [this count])
  (destroy [this ids]))

(defrecord VultrVps
    [cfg]
  IVps
  (create [this count]
    (let [api-key (:api_key cfg)
          ssh-key-name (:ssh_key cfg)
          plan (:plan cfg)
          label (:label cfg)
          tag (core/project-tag)
          ssh-key (vps-vultr/select-ssh-key ssh-key-name (vps-vultr/list-ssh-keys api-key))]

      (when-not ssh-key
        (throw (ex-info "SSH key not found" {:type ::not-found-error})))

      (println (format "Creating %d instances" count))

      (doall
       (map
        (fn [_]
          (future (do (Thread/sleep 1000)
                      (vps-vultr/create api-key ssh-key plan tag label))))
        (range count)))))

  (destroy [this ids]
    (let [api-key (:api_key cfg)
          servers (vps-vultr/select-servers-for-ids (vps-vultr/list-servers api-key) ids)]
      (doall
       (map
        (fn [id]
          (println (format "Destroying instance: %s" id))
          (future (do (Thread/sleep 1000)
                      (vps-vultr/destroy api-key id))))
        servers)))))

(defn connect
  [provider cfg]
  (cond
    (= :vultr provider) (->VultrVps cfg)
    :else
    (exit 1 "Provider not recognized.")))

(defn -main
  [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (let [provider (:provider options)
            plan (:plan options)
            label (:label options)
            cfg (merge {:plan plan :label label}
                       (get-in options [:secrets (keyword provider)]))
            vps-connection (connect provider cfg)]
        (case action
          "create" (let [instances (remove nil? (map deref (.create vps-connection (:count options))))]
                     (doseq [i instances] (println (format "Creating instance with ip %s" (:ip i))))
                     (core/write-json (:state options) instances)
                     (shutdown-agents))
          "destroy" (let [ids (->> (:state options)
                                   core/read-json
                                   (map #(:id %)))]
                      (map deref (.destroy vps-connection ids))
                      (shutdown-agents)))))))
