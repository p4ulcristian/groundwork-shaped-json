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
   and an id as text. Money travels as whole cents, an :int.

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
   [wire.types :as types]))

(def ^:private decimal-schema
  (m/-simple-schema {:type            :decimal
                     :pred            types/decimal-value?
                     :type-properties {:error/message "should be a decimal"
                                       :gen/schema    :int
                                       :gen/fmap      types/example-decimal}}))

(def ^:private registry
  ;; malli's own schemas, the language's :time/… schemas, :decimal, and in
  ;; the browser :uuid as an id's text
  (mr/composite-registry
   types/id-schemas
   (m/default-schemas)
   time/schemas
   {:decimal decimal-schema}))

(def ^:private transformer
  (apply mt/transformer
         (remove nil?
                 [(mt/json-transformer)
                  time/transformer
                  (mt/transformer {:name     :decimal
                                   :decoders {:decimal types/decimal-from-wire}
                                   :encoders {:decimal types/decimal-to-wire}})
                  (mt/transformer {:name     :big-integer
                                   :decoders {:int types/int-from-wire}
                                   :encoders {:int types/int-to-wire}})
                  types/id-transformer])))

(defn- make-tools-from-shape
  "What a shape is made into: a malli schema with this library's schemas,
   and the decoder, encoder and checks built from it, the way out's checks
   with every map closed."
  [shape]
  (let [schema (m/schema shape {:registry registry})
        closed (mu/closed-schema schema)]
    {:decode         (m/decoder schema transformer)
     :encode         (m/encoder schema transformer)
     :fits?          (m/validator schema)
     :explain        (m/explainer schema)
     :fits-closed?   (m/validator closed)
     :explain-closed (m/explainer closed)}))

(def ^:private tools-by-shape
  ;; each shape's tools, made the first time it is used: shapes are an
  ;; app's few, written once, and never change while it runs
  (atom {}))

(defn- find-tools-for-shape [shape]
  (or (get @tools-by-shape shape)
      (let [tools (make-tools-from-shape shape)]
        (swap! tools-by-shape assoc shape tools)
        tools)))

(defn turn-json-data-into-shaped-data-or-errors
  "JSON data turned into shaped data: {:shaped-data …} when it fits the
   shape, or {:errors …} (readable, by key) when it doesn't."
  [shape json-data]
  (let [{:keys [decode fits? explain]} (find-tools-for-shape shape)
        shaped-data                    (decode json-data)]
    (if (fits? shaped-data)
      {:shaped-data shaped-data}
      {:errors (me/humanize (explain shaped-data))})))

(defn turn-shaped-data-into-json-data-or-errors
  "Shaped data turned into JSON data, ids, decimals and dates as text:
   {:json-data …} when it comes back fitting the shape with every map
   closed, or {:errors …} (readable, by key) when it doesn't. The JSON data
   is read back as the other side will read it, so a key the shape doesn't
   name never leaves (an extra column, a password hash), and a value that
   only goes out right passes (a js/Date for a moment, in the browser)."
  [shape shaped-data]
  (let [{:keys [encode decode fits-closed? explain-closed]} (find-tools-for-shape shape)
        json-data                                           (encode shaped-data)
        back                                                (decode json-data)]
    (if (fits-closed? back)
      {:json-data json-data}
      {:errors (me/humanize (explain-closed back))})))

(defn find-errors-in-data-for-shape
  "Why data doesn't fit a shape, readable, by key; nil when it fits."
  [shape data]
  (me/humanize ((:explain (find-tools-for-shape shape)) data)))

(defn turn-data-into-json-text
  "Data as JSON text; keyword keys keep their namespace: \"post/title\"."
  [data]
  (json/write-json data))

(defn turn-json-text-into-data
  "JSON text as data; keys become keywords, \"post/title\" → :post/title."
  [json-text]
  (json/read-json json-text))
