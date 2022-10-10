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

(defn add-id-conta [context]
  (let [id-conta (-> context :request :path-params :id keyword)]
    (cond
      (nil? id-conta) context
      (get @contas id-conta) (update context :request assoc :id-conta id-conta)
      :else (assoc context :response {:status  400
                                      :headers {"Content-Type" "text/plain"}
                                      :body    "conta nao existe!"}))))

(def contas-interceptor
  {:name  :contas-interceptor
   :enter add-contas-atom})

(def id-conta-interceptor
  {:name :id-conta-interceptor
   :enter add-id-conta})

(defn get-saldo [request]
  (let [id-conta (-> request :id-conta)
        contas (-> request :contas)]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (id-conta @contas "conta inválida!")}))

(defn make-deposit [request]
  (let [id-conta (-> request :id-conta)
        valor-deposito (-> request :body slurp parse-double)
        contas (-> request :contas)
        SIDE-EFFECT! (swap! contas (fn [m] (update-in m [id-conta :saldo] #(+ % valor-deposito))))]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body {:id-conta   id-conta
            :novo-saldo (id-conta @contas)}}))

(defn make-withdrawal [request]
  (let [id-conta (-> request :id-conta)
        valor-saque (-> request :body slurp parse-double)
        contas (-> request :contas)
        SIDE-EFFECT! (swap! contas update-in [id-conta :saldo] - valor-saque)]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body {:id-conta   id-conta
            :novo-saldo (id-conta @contas)}}))

(def routes
  (route/expand-routes
    #{["/saldo/:id" :get get-saldo :route-name :saldo]
      ["/deposito/:id" :post make-deposit :route-name :deposito]
      ["/saque/:id" :post make-withdrawal :route-name :saque]}))


(def service-map-simple {::http/routes routes
                         ::http/port   9999
                         ::http/type   :jetty
                         ::http/join?  false})

(def service-map (-> service-map-simple
                     (http/default-interceptors)
                     (update ::http/interceptors conj
                             (i/interceptor contas-interceptor)
                             (i/interceptor id-conta-interceptor))))

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
  (deref server)

  (test-request server :get "/saldo/1")
  (test-request server :get "/saldo/2")
  (test-request server :get "/saldo/3")
  (test-request server :get "/saldo/4")

  (test-post server :post "/deposito/1" "199.93")
  (test-post server :post "/deposito/4" "325.99")

  (test-post server :post "/saque/1" "100.00")
  (test-post server :post "/saque/4" "50.00")

  ;curl http://localhost:9999/saldo/1
  ;curl -d "199.99" -X POST http://localhost:9999/deposito/1
  )
