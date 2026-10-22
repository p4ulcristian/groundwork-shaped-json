(ns wire.types
  "Decimals, whole numbers and ids on the JVM. A decimal is a BigDecimal and
   travels as its plain text, \"12.30\". A whole number travels as a number
   within JavaScript's safe range and as text beyond it. An id is a real
   UUID. The JVM reads the same JSON the browser reads, and refuses the
   same."
  (:require
   [malli.core :as m]))

(set! *warn-on-reflection* true)

(def ^:private decimal-text-pattern #"^-?\d+(\.\d+)?$")

(defn decimal-value?
  "Whether x is a decimal as the JVM holds it."
  [x]
  (decimal? x))

(defn decimal
  "An exact decimal from its text, \"12.30\"."
  [s]
  (BigDecimal. ^String s))

(defn decimal-text
  "An exact decimal as text."
  [d]
  (.toPlainString ^BigDecimal d))

(defn decimal-from-wire
  "Plain decimal text, \"12.30\", becomes a BigDecimal; anything else, an
   exponent's \"1E+3\" too, stays as it came and fails :decimal."
  [x]
  (if (and (string? x) (re-matches decimal-text-pattern x))
    (BigDecimal. ^String x)
    x))

(defn decimal-to-wire [x]
  (if (decimal? x) (.toPlainString ^BigDecimal x) x))

(defn example-decimal
  "A whole number as a decimal, for generated values (tests)."
  [n]
  (BigDecimal. (str n ".00")))

(def ^:private max-safe-integer
  ;; JavaScript's Number.MAX_SAFE_INTEGER: the largest whole number a
  ;; browser holds exactly
  9007199254740991)

(defn- safe-integer? [n]
  (<= (- max-safe-integer) n max-safe-integer))

(def ^:private integer-text-pattern #"^-?(0|[1-9]\d*)$")

(defn int-value?
  "Whether x is a whole number as the JVM holds it."
  [x]
  (int? x))

(defn int-from-wire
  "The text of a whole number beyond JavaScript's safe range becomes a long.
   A number beyond that range may have been rounded by a browser, so it
   becomes a double, as a browser read it, and fails :int. The text of a
   number within the range stays text and fails :int: such a number travels
   as a number. A map key comes as a keyword (JSON keys are text, never
   rounded): one naming a whole number a long holds becomes it."
  [x]
  (cond
    (and (string? x) (re-matches integer-text-pattern x))
    (let [n (parse-long x)]
      (if (and n (not (safe-integer? n))) n x))

    (and (integer? x) (not (safe-integer? x)))
    (double x)

    (and (keyword? x) (nil? (namespace x)) (re-matches integer-text-pattern (name x)))
    (or (parse-long (name x)) x)

    :else
    x))

(defn int-key-from-wire
  "A :map-of's :int key is left a keyword for int-from-wire, which tells it
   from a number that may have been rounded."
  [x]
  x)

(defn int-to-wire
  "A whole number beyond JavaScript's safe range goes out as its text."
  [x]
  (if (and (integer? x) (not (safe-integer? x))) (str x) x))

(defn double-value?
  "Whether x is a finite double: JSON has no NaN or Infinity."
  [x]
  (and (double? x) (Double/isFinite x)))

(def schemas
  "On the JVM :uuid and :int are malli's own: a real UUID, a long. A :double
   is finite."
  {:double (m/-simple-schema {:type :double :pred double-value? :property-pred (m/-min-max-pred nil)})})

(def id-transformer
  "On the JVM ids need no transformer of their own."
  nil)
