(ns wire.api
  "How shaped data travels as text and comes back as what it was, the same
   rules on the JVM and in the browser.

   Shaped data: data whose every value is what its shape (malli) names.
   JSON knows text, numbers, true/false, lists and maps, so on the way out
   the shape turns the rest into text, and on the way in turns it back.
   Keys like :post/title travel as \"post/title\". A date, a decimal and a
   number too big for JavaScript travel as text and come back as what they
   are: on the JVM java.time, BigDecimal and real UUIDs; in the browser an
   Instant and a Day (wire.time), a decimal's text or big.js (wire.big),
   an id as text, and a whole number beyond JavaScript's safe range as its
   text. Money travels as whole cents, an :int. Both ways check the shape
   with every map closed, maps a shape keeps in a registry too: a key it
   doesn't name neither leaves nor comes in.

   One file for both languages: wire.json, wire.time and wire.types are a
   .clj and a .cljs each, and each language loads its own; nothing here
   differs between the two."
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [malli.registry :as mr]
   [malli.transform :as mt]
   [malli.util :as mu]
   [wire.json :as json]
   [wire.time :as time]
   [wire.types :as types]
   [wire.walk :as walk]))

(def ^:private decimal-schema
  (m/-simple-schema {:type            :decimal
                     :pred            types/decimal-value?
                     :type-properties {:gen/schema :int
                                       :gen/fmap   types/example-decimal}}))

(def ^:private registry
  ;; the language's own :int, :double and (in the browser) :uuid, malli's
  ;; own schemas, the language's :time/… schemas, and :decimal
  (mr/composite-registry
   types/schemas
   (m/default-schemas)
   time/schemas
   {:decimal decimal-schema}))

(def ^:private transformer
  (apply mt/transformer
         (remove nil?
                 [(mt/json-transformer {::mt/map-of-key-decoders (assoc (mt/-string-decoders) :int types/int-key-from-wire)})
                  time/transformer
                  (mt/transformer {:name     :decimal
                                   :decoders {:decimal types/decimal-from-wire}
                                   :encoders {:decimal types/decimal-to-wire}})
                  (mt/transformer {:name     :big-integer
                                   :decoders {:int types/int-from-wire}
                                   :encoders {:int types/int-to-wire}})
                  types/id-transformer])))

;; ---------------------------------------------------------------------------
;; Errors: the same text on both sides

(defn- range-text
  "Why a value of the right kind is out of :min/:max, as malli says it."
  [{:keys [min max]}]
  (cond
    (and min max (= min max)) (str "should be " min)
    (and min max)             (str "should be between " min " and " max)
    min                       (str "should be at least " min)
    max                       (str "should be at most " max)))

(defn- error-text-by-kind
  "An :error/fn: the kind's text when the value isn't of the kind, the range's
   when it is but out of :min/:max."
  [kind? kind-text]
  {:error/fn {:en (fn [{:keys [schema value]} _]
                    (or (when (kind? value) (range-text (m/properties schema)))
                        kind-text))}})

(def ^:private error-texts
  (merge me/default-errors
         {:int             (error-text-by-kind types/int-value? "should be an integer")
          :double          (error-text-by-kind types/double-value? "should be a finite number")
          :decimal         {:error/message {:en "should be a decimal"}}
          :uuid            {:error/message {:en "should be an id"}}
          :wire/id-text    {:error/message {:en "should be an id"}}
          :time/instant    (error-text-by-kind time/instant-value? "should be a moment, like 2026-10-12T10:00:00Z")
          :time/local-date (error-text-by-kind time/day-value? "should be a day, like 2026-11-15")}))

(defn- humanize [explanation]
  (me/humanize explanation {:errors error-texts}))

;; ---------------------------------------------------------------------------
;; A shape made ready: maps closed, registries too; tagged unions read keywords

(defn- keyword-multi?
  "A :multi that dispatches on a key, every dispatch value a keyword: JSON
   brings the value as text, which must find its keyword."
  [schema]
  (and (= :multi (m/type schema))
       (keyword? (:dispatch (m/properties schema)))
       (every? (fn [[value]] (or (keyword? value) (= ::m/default value))) (m/children schema))))

(defn- dispatch-on-keyword-of [k]
  (fn [x]
    (let [value (get x k)]
      (if (string? value) (keyword value) value))))

(declare make-ready)

(defn- make-registry-ready
  "A schema's local registry with each of its schemas made ready, so a map
   reached through a :ref, recursive ones too, is closed as well."
  [schema close? options]
  (let [local    (:registry (m/properties schema))
        options' (update options :registry #(mr/composite-registry local %))]
    (update-vals local #(m/form (make-ready (m/schema % options') close? options')))))

(defn- make-ready-walked [schema close? options]
  (m/walk schema
          (m/schema-walker
           (fn [s]
             (cond-> s
               (:registry (m/properties s))
               (mu/update-properties assoc :registry (make-registry-ready s close? options))

               (and close? (= :map (m/type s)) (not (contains? (m/properties s) :closed)))
               (mu/update-properties assoc :closed true)

               (keyword-multi? s)
               (mu/update-properties assoc :dispatch (dispatch-on-keyword-of (:dispatch (m/properties s)))))))
          options))

(defn- make-ready
  "A shape ready to use: every map closed when close?, those in a local
   registry too, and every :multi on a keyword reading its text. Made again
   from its form, so a :ref finds its registry's ready schema."
  [schema close? options]
  (m/schema (m/form (make-ready-walked schema close? options)) options))

;; ---------------------------------------------------------------------------
;; A shape's tools, made once

(defn- make-tools-from-shape
  "What a shape is made into: the one walk of each way, the decoder and
   encoder, the open check that says why any data doesn't fit, and the
   checks of both ways, with every map closed."
  [shape]
  (let [options {:registry registry}
        schema  (m/schema shape options)
        open    (make-ready schema false options)
        closed  (make-ready schema true options)]
    {:walk-in        (walk/make-walk closed transformer :in)
     :walk-out       (walk/make-walk closed transformer :out)
     :decode         (m/decoder closed transformer)
     :encode         (m/encoder closed transformer)
     :explain        (m/explainer open)
     :fits-closed?   (m/validator closed)
     :explain-closed (m/explainer closed)}))

(def ^:private most-shapes-kept
  ;; an app has tens or hundreds of shapes; more means one is made on every
  ;; call, and keeping them all would grow without end
  1000)

(def ^:private tools-by-shape
  ;; each shape's tools, made the first time it is used; when there are
  ;; more than an app has, they are all forgotten and made again as used
  (atom {}))

(defn- find-tools-for-shape [shape]
  (or (get @tools-by-shape shape)
      (let [tools (make-tools-from-shape shape)]
        (swap! tools-by-shape #(assoc (if (< (count %) most-shapes-kept) % {}) shape tools))
        tools)))

;; ---------------------------------------------------------------------------
;; The api

(defn turn-json-data-into-shaped-data-or-errors
  "JSON data turned into shaped data: {:shaped-data …} when it fits the
   shape with every map closed, or {:errors …} (readable, by key) when it
   doesn't: a key the shape doesn't name never comes in.

   Decoded and checked in one walk; data the walk refuses goes through
   malli's whole decoder and check, which say why."
  [shape json-data]
  (let [{:keys [walk-in decode fits-closed? explain-closed]} (find-tools-for-shape shape)
        walked                                               (walk-in json-data)]
    (if-not (walk/refused? walked)
      {:shaped-data walked}
      (let [shaped-data (decode json-data)]
        (if (fits-closed? shaped-data)
          {:shaped-data shaped-data}
          {:errors (humanize (explain-closed shaped-data))})))))

(defn turn-shaped-data-into-json-data-or-errors
  "Shaped data turned into JSON data, ids, decimals and dates as text:
   {:json-data …} when it fits the shape with every map closed, or comes
   back fitting it, or {:errors …} (readable, by key) when neither. So a key
   the shape doesn't name never leaves (an extra column, a password hash),
   and a value that only goes out right passes (a js/Date for a moment in
   the browser, a java.sql.Timestamp on the JVM).

   Shaped data that fits is checked and encoded in one walk and goes out
   without being read back: every value of wire's types that fits writes
   text the other side reads as it (a moment and a day fit only in the
   years the grammar writes). Data that doesn't fit is read back as the
   other side will read it, and checked."
  [shape shaped-data]
  (let [{:keys [walk-out encode decode explain-closed fits-closed?]} (find-tools-for-shape shape)
        walked                                                       (walk-out shaped-data)]
    (if-not (walk/refused? walked)
      {:json-data walked}
      (let [json-data (encode shaped-data)]
        (if (or (fits-closed? shaped-data) (fits-closed? (decode json-data)))
          {:json-data json-data}
          {:errors (humanize (explain-closed (decode json-data)))})))))

(defn find-errors-in-data-for-shape
  "Why data doesn't fit a shape, readable, by key; nil when it fits."
  [shape data]
  (humanize ((:explain (find-tools-for-shape shape)) data)))

(defn turn-data-into-json-text
  "Data as JSON text; keyword keys keep their namespace: \"post/title\"."
  [data]
  (json/write-json data))

(defn turn-json-text-into-data
  "JSON text as data; keys become keywords, \"post/title\" → :post/title."
  [json-text]
  (json/read-json json-text))
