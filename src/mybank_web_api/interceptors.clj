(ns mybank-web-api.interceptors
  (:require [mybank-web-api.bank :as b]
            [io.pedestal.interceptor :as i]))

(def balance-interceptor
  (i/interceptor {:name  :balance-interceptor
                  :enter (fn [context]
                           (let [;_ (println (str "DBG: " context))
                                 accounts (-> context :database :accounts)
                                 account-id (-> context :request :path-params :id keyword)
                                 balance (b/account-balance @accounts account-id)]

                             (assoc context :response {:status 200 :body {:balance balance}})))}))


(def make-deposit-interceptor
  (i/interceptor {:name  :make-deposit-interceptor
                  :enter (fn [context]
                           (let [accounts (-> context :database :accounts)
                                 account-id (-> context :request :path-params :id keyword)
                                 deposit-amount (-> context :request :body slurp parse-double)
                                 _ (swap! accounts b/make-deposit account-id deposit-amount)
                                 new-balance (b/account-balance @accounts account-id)]

                             (assoc context :response {:status 200 :body {:account-id   account-id
                                                                          :new-balance new-balance}})))}))

(def account-exists-interceptor
  (i/interceptor {:name  :account-exists-interceptor
                  :enter (fn [context]
                           (let [accounts (-> context :database :accounts)
                                 account-id (-> context :request :path-params :id keyword)]
                             (if (b/account @accounts account-id)
                               context
                               (assoc context :response {:status 400 :body {:error "account does not exist!"}}))))}))