(ns groundwork-shaped-json.json
  "JSON text in the browser, as the JVM writes and reads it. Keyword keys
   keep their namespace: :post/title travels as \"post/title\" and comes back
   as :post/title (the namespace ends at the first slash, as Clojure's
   keyword does). A UUID, as a key or a value, is its text. Data is turned
   into JavaScript's and back in one walk each, without clj->js and
   js->clj, which ask every value about every protocol.

   Each key's keyword is made once and kept: reading 1000 orders took 9.6
   ms making them anew and 3.8 ms with them kept. Text from elsewhere could
   carry any keys, so past the settings' :most-keys-kept they are all
   forgotten and made again as read.

   The browser's JSON.parse has no limits to set: what it takes is the
   engine's."
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
  [_json x]
  (js/JSON.stringify (turn-data-into-js x)))

(defn make-json
  "The kept keywords, at most the settings' :most-keys-kept."
  [{:keys [most-keys-kept]}]
  {:most-keys-kept most-keys-kept
   :keyword-by-key (js/Map.)})

(defn- make-keyword
  "A key's keyword, namespace up to the first slash, as Clojure's keyword."
  [k]
  (let [i (.indexOf k "/")]
    (if (or (neg? i) (= "/" k))
      (keyword k)
      (keyword (subs k 0 i) (subs k (inc i))))))

(defn- find-keyword [^js keyword-by-key most-keys-kept k]
  (or (.get keyword-by-key k)
      (let [kw (make-keyword k)]
        (when (<= most-keys-kept (.-size keyword-by-key)) (.clear keyword-by-key))
        (.set keyword-by-key k kw)
        kw)))

(defn- turn-js-into-data
  "Arrays into vectors, objects into maps with keyword keys."
  [keyword-by-key most-keys-kept x]
  (case (goog/typeOf x)
    "array"  (persistent! (reduce (fn [v item] (conj! v (turn-js-into-data keyword-by-key most-keys-kept item)))
                                  (transient [])
                                  x))
    "object" (persistent! (reduce (fn [m k]
                                    (assoc! m
                                            (find-keyword keyword-by-key most-keys-kept k)
                                            (turn-js-into-data keyword-by-key most-keys-kept (unchecked-get x k))))
                                  (transient {})
                                  (gobj/getKeys x)))
    x))

(defn read-json
  "JSON text as data."
  [{:keys [keyword-by-key most-keys-kept]} s]
  (turn-js-into-data keyword-by-key most-keys-kept (js/JSON.parse s)))
