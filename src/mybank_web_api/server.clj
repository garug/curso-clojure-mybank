(ns mybank-web-api.server
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as i]
            [io.pedestal.test :as test-http]))

(defonce server (atom nil))

(defn start-server [service-map]
  (reset! server (http/start (http/create-server service-map))))

(defn stop-server []
  (http/stop @server))

(defn restart-server [service-map]
  (stop-server)
  (start-server service-map))

(defn test-request [server verb url]
  (test-http/response-for (::http/service-fn @server) verb url))

(defn test-post [server verb url body]
  (test-http/response-for (::http/service-fn @server) verb url :body body))

(defrecord Server [routes database]
  component/Lifecycle
  (start [this]
    (let [add-db (fn [context] (assoc context :database (:database database)))

          db-interceptor (i/interceptor {:name :db-interceptor
                                         :enter add-db})

          service-map-simple {::http/routes (:routes routes)
                              ::http/type   :jetty
                              ::http/port   8890
                              ::http/join?  false}

          service-map (-> service-map-simple
                          (http/default-interceptors)
                          (update ::http/interceptors conj (i/interceptor db-interceptor)))]


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
  (test-request server :get "/balance/1")
  (test-post server :post "/deposit/1" "19.90")

  )
