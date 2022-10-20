(ns mybank-web-api.bank)

(defn account [accounts id]
  (get accounts id))

(defn account-balance [accounts id]
  (get-in accounts [id :balance]))

(defn make-deposit [accounts id amount]
  (update-in accounts [id :balance] #(+ % amount)))