(ns wire.json
  "JSON text in the browser, as the JVM writes and reads it. Keyword keys
   keep their namespace: :post/title travels as \"post/title\" and comes back
   as :post/title (the namespace ends at the first slash, as Clojure's
   keyword does). A UUID, as a key or a value, is its text. Data is turned
   into JavaScript's and back in one walk each, without clj->js and
   js->clj, which ask every value about every protocol."
  (:require
   [goog.object :as gobj]))

(defn- key-name [k]
  (cond
    (keyword? k) (if-let [ns (namespace k)] (str ns "/" (name k)) (name k))
    (string? k)  k
    :else        (str k)))

(defn- turn-data-into-js
  "Maps into objects, other collections into arrays, keywords into their
   text with the namespace, UUIDs into their text; the rest as it is (an
   Instant or a Day writes its own text)."
  [x]
  (cond
    (or (string? x) (number? x) (boolean? x) (nil? x)) x
    (map? x)     (reduce-kv (fn [o k v] (unchecked-set o (key-name k) (turn-data-into-js v)) o) #js {} x)
    (keyword? x) (key-name x)
    (uuid? x)    (str x)
    (coll? x)    (reduce (fn [a v] (.push a (turn-data-into-js v)) a) #js [] x)
    (symbol? x)  (str x)
    :else        x))

(defn write-json
  "Data as JSON text."
  [x]
  (js/JSON.stringify (turn-data-into-js x)))

(def ^:private most-keywords-kept
  ;; the keys an app's answers carry are few; text from elsewhere could
  ;; carry any, so the kept ones are forgotten past this many
  10000)

(defonce ^:private keyword-by-key (js/Map.))

(defn- make-keyword
  "A key's keyword, namespace up to the first slash, as Clojure's keyword."
  [k]
  (let [i (.indexOf k "/")]
    (if (or (neg? i) (= "/" k))
      (keyword k)
      (keyword (subs k 0 i) (subs k (inc i))))))

(defn- find-keyword [k]
  (or (.get keyword-by-key k)
      (let [kw (make-keyword k)]
        (when (<= most-keywords-kept (.-size keyword-by-key)) (.clear keyword-by-key))
        (.set keyword-by-key k kw)
        kw)))

(defn- turn-js-into-data
  "Arrays into vectors, objects into maps with keyword keys."
  [x]
  (case (goog/typeOf x)
    "array"  (persistent! (reduce (fn [v item] (conj! v (turn-js-into-data item))) (transient []) x))
    "object" (persistent! (reduce (fn [m k] (assoc! m (find-keyword k) (turn-js-into-data (unchecked-get x k))))
                                  (transient {})
                                  (gobj/getKeys x)))
    x))

(defn read-json
  "JSON text as data."
  [s]
  (turn-js-into-data (js/JSON.parse s)))
