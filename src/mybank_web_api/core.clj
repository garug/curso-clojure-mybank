(ns mybank-web-api.core
  (:require [mybank-web-api.routes :as r]
            [mybank-web-api.database :as db]
            [mybank-web-api.server :as web-server]
            [com.stuartsierra.component :as component])
  (:gen-class))

(def new-sys
  (component/system-map
    :routes (r/new-routes)
    :database (db/new-database)
    :web-server (component/using
                  (web-server/new-web-server)
                  [:database :routes])))

(def sys (atom nil))
(defn main [] (reset! sys (component/start new-sys)))

(comment
  (main)
)

