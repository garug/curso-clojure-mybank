(ns mybank-web-api.schemas
  (:require [schema.core :as s :include-macros true]))


(defn valid? [schema value]
  (try (do
         (s/validate schema value)
         true)
       (catch Exception _ false)))

(defn number-and-positive? [value]
  (and (number? value) (pos? value)))

(def NumberAndPositive (s/pred number-and-positive?))

(def MakeDepositSchema
  {:amount NumberAndPositive})

