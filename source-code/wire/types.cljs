(ns wire.types
  "Decimals, whole numbers and ids in the browser. A decimal is its text,
   \"12.30\", or an exact value when the settings give an exact type
   (wire.big's big.js). A whole number is a number within JavaScript's safe
   range and its text beyond it. An id is its text."
  (:require
   [malli.core :as m]
   [malli.transform :as mt]))

(def ^:private decimal-text-pattern #"^-?\d+(\.\d+)?$")

(defn- decimal-text? [x]
  (and (string? x) (some? (re-matches decimal-text-pattern x))))

(defn find-precision-and-scale
  "A :decimal's {:precision :scale}, both whole numbers, 0 <= scale <=
   precision, precision at least 1; nil when it has neither. One without
   the other, or another property, throws :wire/bad-shape."
  [properties]
  (let [{:keys [precision scale]} properties]
    (cond
      (empty? properties) nil
      (and (= #{:precision :scale} (set (keys properties))) (int? precision) (int? scale) (<= 0 scale precision) (pos? precision))
      {:precision precision :scale scale}
      :else (throw (ex-info (str "A :decimal's properties are {:precision p :scale s}, 0 <= s <= p: " (pr-str properties))
                            {:type :wire/bad-shape :properties properties})))))

(defn- fits-digits?
  "Whether decimal text has at most scale digits after its point (trailing
   zeros left out) and precision - scale before it (leading zeros left
   out)."
  [{:keys [precision scale]} text]
  (let [[_ whole places] (re-matches #"^-?(\d+)(?:\.(\d+))?$" text)
        whole            (.replace whole (js/RegExp. "^0+") "")
        places           (.replace (or places "") (js/RegExp. "0+$") "")]
    (and (<= (count places) scale) (<= (count whole) (- precision scale)))))

(defn make-decimal-schema
  "The browser's :decimal, by the settings' :exact-decimals: its text, or,
   given an exact type ({:decimal? :read :text}, wire.big's big.js), that
   type's value. With {:precision p :scale s}, as Postgres's numeric(p,s):
   at most s digits after the point and p - s before it."
  [{:keys [exact-decimals]}]
  (let [{:keys [decimal? read text]} exact-decimals]
    (m/-simple-schema {:type            :decimal
                       :pred            (if exact-decimals decimal? decimal-text?)
                       :property-pred   (fn [properties]
                                          (when-let [digits (find-precision-and-scale properties)]
                                            #(fits-digits? digits (if exact-decimals (text %) %))))
                       :type-properties {:gen/schema :int
                                         :gen/fmap   (if exact-decimals #(read (str %)) str)}})))

(defn make-decimal-transformer
  "Decimal text read into the exact type and written from it; with no exact
   type, a decimal stays its text both ways."
  [{:keys [exact-decimals]}]
  (let [{:keys [decimal? read text]} exact-decimals]
    (mt/transformer {:name     :decimal
                     :decoders {:decimal (if exact-decimals #(if (decimal-text? %) (read %) %) identity)}
                     :encoders {:decimal (if exact-decimals #(if (decimal? %) (text %) %) identity)}})))

(defn int-from-wire
  "Whole numbers stay as they came: one beyond JavaScript's safe range is
   text."
  [x]
  x)

(defn int-to-wire [x] x)

(def ^:private integer-text-pattern #"^-?(0|[1-9]\d*)$")

(defn int-key-from-wire
  "A :map-of's :int key, text as every JSON key: a whole number within the
   safe range becomes it; beyond the range it stays its text, as a value
   does. Text that isn't a whole number, \"042\" too, stays and fails."
  [x]
  (if (and (string? x) (re-matches integer-text-pattern x))
    (let [n (js/Number x)]
      (if (js/Number.isSafeInteger n) n x))
    x))

(def ^:private min-safe-integer (js/BigInt js/Number.MIN_SAFE_INTEGER))
(def ^:private max-safe-integer (js/BigInt js/Number.MAX_SAFE_INTEGER))
(def ^:private min-long (js/BigInt "-9223372036854775808"))
(def ^:private max-long (js/BigInt "9223372036854775807"))

(defn- big-integer-text?
  "Whether x is the text of a whole number beyond JavaScript's safe range
   that a JVM long holds."
  [x]
  (and (string? x)
       (some? (re-matches integer-text-pattern x))
       (let [n (js/BigInt x)]
         (and (or (< n min-safe-integer) (< max-safe-integer n))
              (<= min-long n max-long)))))

(defn int-value?
  "Whether x is a whole number as the browser holds it: a number within the
   safe range, or the text of one beyond it that a JVM long holds."
  [x]
  (or (js/Number.isSafeInteger x) (big-integer-text? x)))

(def int-schema
  "An :int in the browser: a number within JavaScript's safe range, or the
   text of a whole number beyond it that a JVM long holds. A number beyond
   the range may have been rounded, so it doesn't fit. :min and :max hold
   for both."
  (m/-simple-schema {:type          :int
                     :pred          int-value?
                     :property-pred (m/-min-max-pred #(if (string? %) (js/BigInt %) %))}))

(defn double-value?
  "Whether x is a finite number: JSON has no NaN or Infinity."
  [x]
  (and (number? x) (js/isFinite x)))

(def double-schema
  "A :double in the browser: a finite number."
  (m/-simple-schema {:type :double :pred double-value? :property-pred (m/-min-max-pred nil)}))

(defn- id-text?
  "Whether x is text shaped like a UUID, 8-4-4-4-12 hex digits; checked by
   position, several times faster than a regex."
  [x]
  (and (string? x)
       (== 36 (.-length x))
       (loop [i 0]
         (or (== i 36)
             (let [c (.charCodeAt x i)]
               (and (if (or (== i 8) (== i 13) (== i 18) (== i 23))
                      (== c 45)
                      (or (<= 48 c 57) (<= 97 c 102) (<= 65 c 70)))
                    (recur (inc i))))))))

(def id-text-schema
  "An id in the browser: text shaped like a UUID. Its type isn't :uuid, so
   malli's JSON transformer leaves the text alone."
  (m/-simple-schema {:type            :wire/id-text
                     :pred            id-text?
                     :type-properties {:gen/schema [:vector {:min 32 :max 32} [:int {:min 0 :max 15}]]
                                       :gen/fmap   (fn [digits]
                                                     (let [hex (apply str (map #(.toString % 16) digits))]
                                                       (str (subs hex 0 8) "-" (subs hex 8 12) "-" (subs hex 12 16) "-"
                                                            (subs hex 16 20) "-" (subs hex 20))))}}))

(def schemas
  "In the browser :uuid is an id as text, :int a whole number as int-schema
   says, and :double a finite number."
  {:uuid         id-text-schema
   :wire/id-text id-text-schema   ; the form :uuid is made into
   :int          int-schema
   :double       double-schema})

(def id-transformer
  "A UUID value given in the browser becomes its text, both ways, and an
   id's text is lower case, as the JVM writes it, so equal ids are =."
  (let [text (fn [x]
               (cond
                 (uuid? x)                                         (str x)
                 (id-text? x)                                      (.toLowerCase x)
                 :else                                             x))]
    (mt/transformer {:name     :id-text
                     :decoders {:wire/id-text text}
                     :encoders {:wire/id-text text}})))
