(ns mybank-web-api.interceptors
  (:require [mybank-web-api.bank :as b]
            [io.pedestal.interceptor :as i]
            [mybank-web-api.schemas :as s]))

(defn response
  ([status body]
   (response status body {}))
  ([status body headers]
   {:status status :body body :headers headers}))

(def ok (partial response 200))
(def bad-request (partial response 400))
(def unsupported-media-type (partial response 415 "Unsupported Media Type"))

(def content-type-interceptor
  (i/interceptor {:name  :content-type-interceptor
                  :enter (fn [ctx] (let [content-type (get-in ctx [:request :headers "content-type"])]
                                     (if (some #(= % content-type) ["application/json"])
                                       ctx
                                       (assoc ctx :response (unsupported-media-type)))))}))

(def balance-interceptor
  (i/interceptor {:name  :balance-interceptor
                  :enter (fn [context]
                           (let [;_ (println (str "DBG: " context))
                                 accounts (-> context :database :accounts)
                                 account-id (-> context :request :path-params :id keyword)
                                 balance (b/account-balance @accounts account-id)]

                             (assoc context :response (ok {:balance balance}))))}))


(defn json-validator-interceptor
  [schema]
  (i/interceptor {:name  :json-validator-interceptor
                  :enter (fn [context]
                           (if (s/valid? schema (-> context :request :json-params))
                             context
                             (assoc context :response (bad-request "Invalid Payload!"))))}))

(def make-deposit-validator-interceptor
  (json-validator-interceptor s/MakeDepositSchema))

(def make-deposit-interceptor
  (i/interceptor {:name  :make-deposit-interceptor
                  :enter (fn [context]
                           (let [accounts (-> context :database :accounts)
                                 account-id (-> context :request :path-params :id keyword)
                                 deposit-amount (-> context :request :json-params :amount)
                                 _ (swap! accounts b/make-deposit account-id deposit-amount)
                                 new-balance (b/account-balance @accounts account-id)]

                             (assoc context :response (ok {:account-id  account-id
                                                           :new-balance new-balance}))))}))

(def account-exists-interceptor
  (i/interceptor {:name  :account-exists-interceptor
                  :enter (fn [context]
                           (let [accounts (-> context :database :accounts)
                                 account-id (-> context :request :path-params :id keyword)]
                             (if (b/account @accounts account-id)
                               context
                               (assoc context :response (bad-request "Account does not exist!")))))}))

(defn db-interceptor [db]
  (i/interceptor {:name  :db-interceptor
                  :enter (fn [context] (assoc context :database db))}))