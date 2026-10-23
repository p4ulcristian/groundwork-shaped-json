(ns wire.types
  "Decimals, whole numbers and ids on the JVM. A decimal is a BigDecimal and
   travels as its plain text, \"12.30\". A whole number travels as a number
   within JavaScript's safe range and as text beyond it. An id is a real
   UUID. The JVM reads the same JSON the browser reads, and refuses the
   same."
  (:require
   [malli.core :as m]
   [malli.transform :as mt]))

(set! *warn-on-reflection* true)

(def ^:private decimal-text-pattern #"^-?\d+(\.\d+)?$")

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

(defn- decimal-from-wire
  "Plain decimal text, \"12.30\", becomes a BigDecimal; anything else, an
   exponent's \"1E+3\" too, stays as it came and fails :decimal."
  [x]
  (if (and (string? x) (re-matches decimal-text-pattern x))
    (BigDecimal. ^String x)
    x))

(defn- decimal-to-wire [x]
  (if (decimal? x) (.toPlainString ^BigDecimal x) x))

(defn- count-whole-digits
  "How many digits a decimal has before its point, leading zeros left out."
  [^BigDecimal x]
  (let [whole (.abs (.toBigInteger x))]
    (if (zero? (.signum whole)) 0 (count (str whole)))))

(defn- count-places
  "How many digits a decimal has after its point, trailing zeros left out."
  [^BigDecimal x]
  (max 0 (.scale (.stripTrailingZeros x))))

(defn make-decimal-schema
  "The JVM's :decimal: a BigDecimal. With {:precision p :scale s}, as
   Postgres's numeric(p,s): at most s digits after the point and p - s
   before it. The settings have nothing for it."
  [_settings]
  (m/-simple-schema {:type            :decimal
                     :pred            decimal?
                     :property-pred   (fn [properties]
                                        (when-let [{:keys [precision scale]} (find-precision-and-scale properties)]
                                          #(and (<= (count-places %) scale) (<= (count-whole-digits %) (- precision scale)))))
                     :type-properties {:gen/schema :int
                                       :gen/fmap   #(BigDecimal. (str % ".00"))}}))

(defn make-decimal-transformer
  "A BigDecimal travels as its plain text, \"12.30\", and comes back."
  [_settings]
  (mt/transformer {:name     :decimal
                   :decoders {:decimal decimal-from-wire}
                   :encoders {:decimal decimal-to-wire}}))

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
