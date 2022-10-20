(ns mybank-web-api.routes
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http.route :as route]
            [mybank-web-api.interceptors :as i]))

(defrecord Routes []
  component/Lifecycle
  (start [this]
    (let [routes (route/expand-routes
                   #{["/balance/:id" :get [i/account-exists-interceptor i/balance-interceptor] :route-name :balance]
                     ["/deposit/:id" :post [i/account-exists-interceptor i/make-deposit-interceptor] :route-name :deposit]})]
      (assoc this :routes routes)))

  (stop [this]
    (dissoc this :routes)))

(defn new-routes []
  (->Routes))
