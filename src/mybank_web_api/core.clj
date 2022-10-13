(ns mybank-web-api.core
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test-http]
            [io.pedestal.interceptor :as i]
            [io.pedestal.interceptor.chain :as chain]
            [clojure.pprint :as pp])
  (:gen-class))

(defn quadrado [x] (* x x))

(defonce server (atom nil))

;1 - Tratamento de conta inválida/inexistente no deposito. Retornar o status http de erro e mensagem no body.
;
;2 - Implementar funcionalidade saque
;
;3 - Criar reset do servidor (tenta stop e tenta start) e demonstrar no mesmo repl antes e depois do tratamento de erro no ex. 1

(defonce contas- (atom {:1 {:saldo 100}
                       :2 {:saldo 200}
                       :3 {:saldo 300}}))

(defn hello [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Hello!"})

(defn add-contas-atom [context]
  (assoc context :contas contas-))

(def contas-interceptor
  {:name  :contas-interceptor
   :enter add-contas-atom})

;(def widget-finder
;  (i/interceptor
;    {:enter (fn [ctx]
;              (assoc ctx :widget {:id 1 :title "foobar"} ))}))

(def widget-finder
  (i/interceptor
    {:enter (fn [ctx]
              (assoc ctx :widget {:id 1 :title (:title "foobar")} ))}))

(def widget-renderer
  (i/interceptor
    {:leave (fn [ctx]
              (if-let [widget (:widget ctx)]
                (assoc ctx :response {:status 200
                                      :body   (format "Widget ID %d, Title '%s'"
                                                      (:id widget)
                                                      (:title widget))})
                (assoc ctx :response {:status 404 :body "Not Found"})))}))

(defn get-saldo [request]
  (let [contas (request :contas)
        id-conta (-> request :path-params :id keyword)]
    (println (str "CONTAS=>=>" @contas ))
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (id-conta @contas "conta inválida!")}))

(defn make-deposit [request]
  (let [contas (request :contas)
        id-conta (-> request :path-params :id keyword)
        valor-deposito (-> request :body slurp parse-double)
        SIDE-EFFECT! (swap! contas (fn [m] (update-in m [id-conta :saldo] #(+ % valor-deposito))))]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body {:id-conta   id-conta
                        :novo-saldo (id-conta @contas)}}))

(defn make-withdraw [request]
  (let [conta (:conta request)
        contas (:contas request)
        ;id-conta (-> request :path-params :id keyword) -- funciona sem o merge
        ;id-conta (:id (:conta request)) -- funciona se o merge for feito
        ;id-conta (-> request :conta :id) -- funcioa se o merge for feito
        ;conta (get @contas id-conta) ; toda vez que for necessário utilizar conta, terá esse código se o merge não for feito
        valor (-> request :body slurp parse-double) ; TODO sanitizar entrada do valor
        ;SIDE-EFFECT! (swap! contas (fn [mapa] (update-in mapa [(:id conta) :saldo] #(- % valor))))]
        SIDE-EFFECT! (swap! contas update-in [(:id conta) :saldo] - valor)]
    {:status  200
     :headers {"Content-Type" "text/plain"}
     :body    {:id-conta   (:id conta)
               :novo-saldo ((:id conta) @contas)}}))

(def validate-conta-existe
  {:name  ::validate-conta-existe
   :enter (fn [context]
            (let [id (-> (:request context) :path-params :id keyword)
                  contas (-> context :request :contas)
                  conta (get @contas id)]
              (if conta ;; { :saldo nil } mesmo que saldo nil, retorna true
                (update context :request assoc :conta (merge conta {:id id}))
                (assoc context :response {:status  400
                                          :headers {"Content-Type" "text/plain"}
                                          :body    {:erro  "conta não existe"
                                                    :conta id}}))))})

(def routes
  (route/expand-routes
    #{
      ["/hello" :get hello :route-name :hello]
      ["/saldo/:id" :get get-saldo :route-name :saldo]
      ["/deposito/:id" :post [validate-conta-existe make-deposit] :route-name :deposito]
      ["/saque/:id" :post [validate-conta-existe make-withdraw] :route-name :saque]}))


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
  (test-post server :post "/deposito/1" "199.93")
  (test-post server :post "/deposito/4" "325.99")
  (test-post server :post "/saque/1" "1")

  ;curl http://localhost:9999/saldo/1
  ;curl -d "199.99" -X POST http://localhost:9999/deposito/1
  )
