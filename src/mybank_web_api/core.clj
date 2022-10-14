(ns mybank-web-api.core
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test-http]
            [io.pedestal.interceptor :as i]
            [clojure.pprint :as pp])
  (:gen-class))

(defonce server (atom nil))

(defonce contas (atom {:1 {:saldo 100}
                       :2 {:saldo 200}
                       :3 {:saldo 300}}))

(defn add-contas-atom [context]
  (update context :request assoc :contas contas))

(def contas-interceptor
  {:name  :contas-interceptor
   :enter add-contas-atom})

(defn is-a-valid-number? [string]
  (double? (parse-double string)))

(defn positive? [string]
  (-> string bigdec (> 0)))

(defn sanitizer [string]
  "Determinates if a string passed as input is a positive number and case it is
  returns its content as a BigDecimal, case it's not "
  (if (and (is-a-valid-number? string) (positive? string))
     (bigdec string)))

(defn get-saldo [request]
  (let [id-conta (-> request :path-params :id keyword)]
    {:status  200
     :headers {"Content-Type" "text/plain"}
     :body    (id-conta @contas "conta inválida!")}))

(defn make-deposit [request]
  (let [id-conta (-> request :path-params :id keyword)
        valor-deposito (:valor request)
        SIDE-EFFECT! (swap! contas (fn [m] (update-in m [id-conta :saldo] #(-> % (+ valor-deposito) bigdec))))]
    {:status  200
     :headers {"Content-Type" "text/plain"}
     :body    {:id-conta   id-conta
               :novo-saldo (id-conta @contas)}}))

(defn saldo-suficiente?
  [conta valor]
  (let [valor-atual (:saldo conta)]
    (>= valor-atual valor)))

(defn make-withdraw [request]
  (let [conta (:conta request)
        contas (:contas request)
        ;id-conta (-> request :path-params :id keyword) -- funciona sem o merge
        ;id-conta (:id (:conta request)) -- funciona se o merge for feito
        ;id-conta (-> request :conta :id) -- funcioa se o merge for feito
        ;conta (get @contas id-conta) ; toda vez que for necessário utilizar conta, terá esse código se o merge não for feito
        valor (:valor request)
        pode-sacar? (saldo-suficiente? conta valor)
        operacao (fn [mapa] (update-in mapa [(:id conta) :saldo]  #(-> % (- valor) bigdec)))]
        ;SIDE-EFFECT! (swap! contas update-in [(:id conta) :saldo] - valor)]
        (if pode-sacar?
          (do
            (swap! contas operacao)
            {:status  200
             :headers {"Content-Type" "text/plain"}
             :body    {:id-conta   (:id conta)
                       :novo-saldo ((:id conta) @contas)}})
          {:status  400
           :headers {"Content-Type" "text/plain"}
           :body    {:erro   "saldo-insuficiente"}})))

(def validate-conta-existe
  {:name  ::validate-conta-existe
   :enter (fn [context]
            (let [id (-> (:request context) :path-params :id keyword)
                  contas (-> context :request :contas)
                  conta (get @contas id)]
              (if conta                                     ;; { :saldo nil } mesmo que saldo nil, retorna true
                (update context :request assoc :conta (merge conta {:id id}))
                (assoc context :response {:status  400
                                          :headers {"Content-Type" "text/plain"}
                                          :body    {:erro  "conta não existe"
                                                    :conta id}}))))})

(def validate-value
  {:name  ::validate-value
   :enter (fn [context]
            (let [value (-> context :request :body slurp)
                  value-sanitizado (sanitizer value)]
              (if value-sanitizado
                (update context :request assoc :valor value-sanitizado)
                (assoc context :response {:status  400 ;;else
                                          :headers {"Content-Type" "text/plain"}
                                          :body    {:erro  "Valor inválido."
                                                    :valor value}}))))})

(def routes
  (route/expand-routes
    #{["/saldo/:id" :get get-saldo :route-name :saldo]
      ["/deposito/:id" :post [validate-conta-existe validate-value make-deposit] :route-name :deposito]
      ["/saque/:id" :post [validate-conta-existe validate-value make-withdraw] :route-name :saque]}))


(def service-map-simple {::http/routes routes
                         ::http/port   9999
                         ::http/type   :jetty
                         ::http/join?  false})

(def service-map (-> service-map-simple
                     (http/default-interceptors)
                     (update ::http/interceptors conj (i/interceptor contas-interceptor))))

(defn create-server []
  (http/create-server
    service-map))

(defn start []
  (reset! server (http/start (create-server))))

(defn reset []
  (try (do
         (http/stop @server)
         (start))
       (catch Exception _ (start))))

(defn test-request [server verb url]
  (test-http/response-for (::http/service-fn @server) verb url))
(defn test-post [server verb url body]
  (test-http/response-for (::http/service-fn @server) verb url :body body))

(comment
  (reset)
  (start)
  (http/stop @server)


  (test-request server :get "/saldo/1")
  (test-request server :get "/saldo/2")
  (test-request server :get "/saldo/3")
  (test-request server :get "/saldo/4")
  (test-post server :post "/deposito/1" "100.00")
  (test-post server :post "/deposito/4" "325.99")
  (test-post server :post "/saque/1" "1")

  ;curl http://localhost:9999/saldo/1
  ;curl -d "199.99" -X POST http://localhost:9999/deposito/1
  )
