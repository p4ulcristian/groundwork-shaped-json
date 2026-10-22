(ns wire.json
  "JSON text in the browser. Keyword keys keep their namespace: :post/title
   travels as \"post/title\" and comes back as :post/title.")

(defn- key-name [k]
  (if (keyword? k) (subs (str k) 1) (str k)))

(defn- uuid-as-text
  "JSON.stringify's replacer: a UUID value is its text, as on the JVM."
  [_ v]
  (if (uuid? v) (str v) v))

(defn write-json
  "Data as JSON text; a UUID value as its text."
  [x]
  (js/JSON.stringify (clj->js x :keyword-fn key-name) uuid-as-text))

(defn read-json
  "JSON text as data."
  [s]
  (js->clj (js/JSON.parse s) :keywordize-keys true))
