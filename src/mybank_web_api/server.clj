(ns mybank-web-api.server
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.test :as test-http]
            [mybank-web-api.interceptors :as i]
            [io.pedestal.http.content-negotiation :as conneg]))

(defonce server (atom nil))

(defn start-server [service-map]
  (reset! server (http/start (http/create-server service-map))))

(defn stop-server []
  (http/stop @server))

(defn restart-server [service-map]
  (stop-server)
  (start-server service-map))

(defn test-get [server url]
  (test-http/response-for (::http/service-fn @server) :get url))

(defn test-post [server url body headers]
  (test-http/response-for (::http/service-fn @server) :post url :body body :headers headers))

(defrecord Server [routes database]
  component/Lifecycle
  (start [this]
    (let [service-map-simple {::http/routes (:routes routes)
                              ::http/type   :jetty
                              ::http/port   8890
                              ::http/join?  false}

          service-map (-> service-map-simple
                          (http/default-interceptors)
                          (update ::http/interceptors conj
                                  (conneg/negotiate-content ["application/json"])
                                  (i/db-interceptor (:database database))
                                  http/json-body))]

      (try
        (start-server service-map)
        (println "Server Started successfully!")
        (catch Exception e
          (println "Error executing server start: " (.getMessage e))
          (println "Trying server restart..." (.getMessage e))
          (try
            (restart-server service-map)
            (println "Server Restarted successfully!")
            (catch Exception e (println "Error executing server restart: " (.getMessage e))))))

      (assoc this :server @server)))

  (stop [this]
    (dissoc this :server)))


(defn new-web-server []
  (->Server {} {}))

(comment
  (test-get server "/balance/1")
  (test-post server "/deposit/1" "{\"amount\": 19.90}" {"Content-Type" "application/json"
                                                        "Accept" "application/json"})

  (test-get server "/balance/4")
  (test-post server "/deposit/1" "{\"amount\": \"19.90\"}" {"Content-Type" "application/json"
                                                        "Accept" "application/json"})

  )

