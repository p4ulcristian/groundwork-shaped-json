(ns wire.walk
  "One walk per shape and way, inside wire. The way in decodes and checks
   in the same pass; the way out checks and encodes in the same pass. A
   walk goes through maps, vectors, tuples, :maybe, :json, tagged unions
   and refs itself, and leaves every other part of a shape (a value, a set, a
   :map-of, an :or) to malli's own decoder, encoder and check for that
   part, so each value is made exactly as malli makes it.

   A walk answers the value, or `refused` when anything doesn't fit; the
   api then takes malli's whole path, which says why, word for word as
   before. A walk never takes what malli would refuse: it refuses whatever
   it isn't sure of (a list where a vector goes, a key that isn't a
   keyword) and malli decides."
  (:require
   [malli.core :as m]))

(def refused
  "What a walk answers when the data doesn't fit."
  #?(:clj (Object.) :cljs (js-obj)))

(defn refused? [x]
  (identical? refused x))

(defn- make-part-walk
  "Malli's own way for a part of a shape the walk doesn't go through."
  [schema transformer way]
  (let [fits? (m/validator schema)]
    (case way
      :in  (let [decode (m/decoder schema transformer)]
             (fn [x] (let [y (decode x)] (if (fits? y) y refused))))
      :out (let [encode (m/encoder schema transformer)]
             (fn [x] (if (fits? x) (encode x) refused))))))

(declare make-walk)

(defn- make-map-walk
  "A map's keys, each walked by its own; closed, a key it doesn't name
   refuses. Its own keys only, keywords, and no property but :closed:
   anything else is malli's."
  [schema transformer way]
  (let [properties (m/properties schema)
        entries    (mapv (fn [[k entry-properties child]]
                           [k (boolean (:optional entry-properties)) (make-walk child transformer way)])
                         (m/children schema))
        n          (count entries)]
    (if (or (seq (dissoc properties :closed)) (not-every? (comp keyword? first) entries))
      (make-part-walk schema transformer way)
      (if (:closed properties)
        (fn [x]
          (if-not (map? x)
            refused
            (loop [i 0 found 0 out (transient {})]
              (if (== i n)
                (if (== found (count x)) (persistent! out) refused)
                (let [[k optional? walk] (nth entries i)
                      entry              (find x k)]
                  (if entry
                    (let [v (walk (val entry))]
                      (if (refused? v) refused (recur (inc i) (inc found) (assoc! out k v))))
                    (if optional? (recur (inc i) found out) refused)))))))
        (fn [x]
          (if-not (map? x)
            refused
            (loop [i 0 out x]
              (if (== i n)
                out
                (let [[k optional? walk] (nth entries i)
                      entry              (find x k)]
                  (if entry
                    (let [v (walk (val entry))]
                      (if (refused? v) refused (recur (inc i) (assoc out k v))))
                    (if optional? (recur (inc i) out) refused)))))))))))

(defn- make-vector-walk
  "A vector's items, each walked by the one walk; :min and :max by count."
  [schema transformer way]
  (let [{:keys [min max] :as properties} (m/properties schema)
        walk                             (make-walk (first (m/children schema)) transformer way)]
    (if (seq (dissoc properties :min :max))
      (make-part-walk schema transformer way)
      (fn [x]
        (if-not (and (vector? x) (or (nil? min) (<= min (count x))) (or (nil? max) (<= (count x) max)))
          refused
          (let [n (count x)]
            (loop [i 0 out (transient [])]
              (if (== i n)
                (persistent! out)
                (let [v (walk (nth x i))]
                  (if (refused? v) refused (recur (inc i) (conj! out v))))))))))))

(defn- make-tuple-walk
  "A tuple's items, each walked by its own."
  [schema transformer way]
  (let [walks (mapv #(make-walk % transformer way) (m/children schema))
        n     (count walks)]
    (fn [x]
      (if-not (and (vector? x) (== n (count x)))
        refused
        (loop [i 0 out (transient [])]
          (if (== i n)
            (persistent! out)
            (let [v ((nth walks i) (nth x i))]
              (if (refused? v) refused (recur (inc i) (conj! out v))))))))))

(defn- make-maybe-walk [schema transformer way]
  (let [walk (make-walk (first (m/children schema)) transformer way)]
    (fn [x] (if (nil? x) nil (walk x)))))

(defn- make-multi-walk
  "A tagged union: the branch its dispatch names, or :malli.core/default."
  [schema transformer way]
  (let [dispatch    (:dispatch (m/properties schema))
        walks       (into {} (map (fn [[value _ child]] [value (make-walk child transformer way)])) (m/children schema))
        default     (get walks ::m/default)]
    (fn [x]
      (if-let [walk (get walks (dispatch x) default)]
        (walk x)
        refused))))

(defn- make-ref-walk
  "A ref's schema, walked; made when first used, so a recursive one ends."
  [schema transformer way]
  (let [walk (delay (make-walk (m/deref schema) transformer way))]
    (fn [x] (@walk x))))

(defn make-walk
  "The walk of a shape (a malli schema made ready: maps closed, tagged
   unions reading text) for one way, :in or :out."
  [schema transformer way]
  (case (m/type schema)
    :map                 (make-map-walk schema transformer way)
    :vector              (make-vector-walk schema transformer way)
    :tuple               (make-tuple-walk schema transformer way)
    :maybe               (make-maybe-walk schema transformer way)
    :json                (make-walk (first (m/children schema)) transformer way)
    :multi               (make-multi-walk schema transformer way)
    (:ref :schema ::m/schema) (make-ref-walk schema transformer way)
    :any                 identity
    (make-part-walk schema transformer way)))
