(ns wire.types
  "Decimals, whole numbers and ids in the browser. A decimal is its text,
   \"12.30\", or an exact value once use-exact-decimals! is given one
   (wire.big gives big.js). A whole number is a number within JavaScript's
   safe range and its text beyond it. An id is its text."
  (:require
   [malli.core :as m]
   [malli.transform :as mt]))

(defonce ^:private exact
  ;; the exact decimal type, once one is given: {:decimal? :read :text}
  ;; (wire.big); nil keeps a decimal as its text
  (volatile! nil))

(defn use-exact-decimals!
  "How the browser holds a :decimal: {:decimal? pred, :read text → value,
   :text value → text}, or nil for its text. wire.big
   calls it with big.js when it loads."
  [kind]
  (vreset! exact kind))

(def ^:private decimal-text-pattern #"^-?\d+(\.\d+)?$")

(defn decimal-value?
  "Whether x is a decimal as the browser holds it."
  [x]
  (if-let [{:keys [decimal?]} @exact]
    (decimal? x)
    (and (string? x) (some? (re-matches decimal-text-pattern x)))))

(defn decimal
  "An exact decimal from its text, \"12.30\". With an exact value given (big.js)
   it stays exact, but trailing zeros aren't kept: it reads 12.3; without one,
   it is the text."
  [s]
  (if-let [{:keys [read]} @exact] (read s) s))

(defn decimal-text
  "An exact decimal as text."
  [d]
  (if-let [{:keys [decimal? text]} @exact] (if (decimal? d) (text d) (str d)) (str d)))

(defn decimal-from-wire [x]
  (let [{:keys [read]} @exact]
    (if (and read (string? x) (re-matches decimal-text-pattern x))
      (read x)
      x)))

(defn decimal-to-wire [x]
  (let [{:keys [decimal? text]} @exact]
    (if (and decimal? (decimal? x)) (text x) x)))

(defn example-decimal
  "A whole number as a decimal, for generated values (tests)."
  [n]
  (if-let [{:keys [read]} @exact] (read (str n)) (str n)))

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
