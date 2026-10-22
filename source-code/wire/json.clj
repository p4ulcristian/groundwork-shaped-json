(ns wire.json
  "JSON text on the JVM, through jsonista. Keyword keys keep their
   namespace: :post/title travels as \"post/title\" and comes back as
   :post/title."
  (:require
   [jsonista.core :as json]))

(set! *warn-on-reflection* true)

(defn- key-name [k]
  (if (keyword? k) (subs (str k) 1) (str k)))

(def ^:private mapper
  ;; text after the JSON is refused, as JSON.parse refuses it
  (doto ^com.fasterxml.jackson.databind.ObjectMapper
   (json/object-mapper {:decode-key-fn keyword
                        :encode-key-fn key-name})
    (.enable com.fasterxml.jackson.databind.DeserializationFeature/FAIL_ON_TRAILING_TOKENS)))

(defn write-json
  "Data as JSON text."
  [x]
  (json/write-value-as-string x mapper))

(defn read-json
  "JSON text as data."
  [s]
  (json/read-value s mapper))
