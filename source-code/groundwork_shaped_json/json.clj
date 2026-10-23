(ns groundwork-shaped-json.json
  "JSON text on the JVM, through jsonista. Keyword keys keep their
   namespace: :post/title travels as \"post/title\" and comes back as
   :post/title. Jackson's limits on what it reads and writes are the
   settings' :json-limits, every one written out."
  (:require
   [jsonista.core :as json])
  (:import
   (com.fasterxml.jackson.core StreamReadConstraints StreamWriteConstraints)
   (com.fasterxml.jackson.databind DeserializationFeature ObjectMapper)))

(set! *warn-on-reflection* true)

(def limits-shape
  "Jackson's limits, each written out; nil for none where Jackson allows
   none."
  [:map {:closed true}
   [:max-nesting-depth [:int {:min 1}]]
   [:max-number-length [:int {:min 1}]]
   [:max-string-length [:int {:min 1}]]
   [:max-name-length [:int {:min 1}]]
   [:max-document-length [:maybe [:int {:min 1}]]]
   [:max-token-count [:maybe [:int {:min 1}]]]
   [:max-writing-nesting-depth [:int {:min 1}]]])

(defn- key-name [k]
  (if (keyword? k) (subs (str k) 1) (str k)))

(defn make-json
  "The mapper that reads and writes JSON text by the settings' limits. Text
   after the JSON is refused, as JSON.parse refuses it."
  [{{:keys [max-nesting-depth max-number-length max-string-length max-name-length
            max-document-length max-token-count max-writing-nesting-depth]} :json-limits}]
  (let [^ObjectMapper mapper (json/object-mapper {:decode-key-fn keyword :encode-key-fn key-name})]
    (doto (.getFactory mapper)
      (.setStreamReadConstraints (-> (StreamReadConstraints/builder)
                                     (.maxNestingDepth max-nesting-depth)
                                     (.maxNumberLength max-number-length)
                                     (.maxStringLength max-string-length)
                                     (.maxNameLength max-name-length)
                                     (.maxDocumentLength (long (or max-document-length -1)))
                                     (.maxTokenCount (long (or max-token-count -1)))
                                     (.build)))
      (.setStreamWriteConstraints (-> (StreamWriteConstraints/builder)
                                      (.maxNestingDepth max-writing-nesting-depth)
                                      (.build))))
    (.enable mapper DeserializationFeature/FAIL_ON_TRAILING_TOKENS)))

(defn write-json
  "Data as JSON text."
  [json x]
  (json/write-value-as-string x json))

(defn read-json
  "JSON text as data."
  [json s]
  (json/read-value s json))
