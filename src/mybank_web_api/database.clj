(ns mybank-web-api.database
  (:require  [com.stuartsierra.component :as component]))

(defrecord Database []
  component/Lifecycle

  (start [this]
    (let [accounts (atom {:1 {:balance 100}
                        :2 {:balance 200}
                        :3 {:balance 300}})]

      (assoc this :database {:accounts accounts})))

  (stop [this]
    (dissoc this :database)))


(defn new-database []
  (->Database))
