(ns wire.time
  "Times in the browser. A moment is an Instant, a goog.date.UtcDateTime; a
   calendar day is a Day, a goog.date.Date (no time, no zone: its year, month
   and day are what the wire said, wherever the browser is). Both print, and
   go to JSON, as the ISO text they came as: \"2026-10-12T10:00:00Z\",
   \"2026-11-15\" (an instant's fraction in 3, 6 or 9 digits, as java.time
   and js-joda print it). JavaScript keeps milliseconds; an Instant keeps the
   finer part of its second too, so a moment goes back exactly as it came.
   They compare with = and compare, and goog.date's methods work on them (a
   copy from clone stays an Instant or a Day).

   Both are read by the same grammar as on the JVM, so both sides take and
   refuse the same text:

     moment  2026-10-12T10:00:00Z, 2026-10-12T12:00:00.5+02:00
             seconds always; a fraction of 1 to 9 digits; Z or an offset up
             to ±18:00; upper-case T and Z; years 0000 to 9999; no leap
             second
     day     2026-11-15, years 0000 to 9999"
  (:require
   [malli.core :as m]
   [malli.transform :as mt])
  (:import (goog.date Date UtcDateTime)))

(defn- pad [n width]
  (.padStart (str n) width "0"))

(defn- fraction
  "A second's fraction in nanoseconds, as java.time prints it: none, or
   3, 6 or 9 digits."
  [nanos]
  (cond
    (zero? nanos)                   ""
    (zero? (rem nanos 1000000))     (str "." (pad (quot nanos 1000000) 3))
    (zero? (rem nanos 1000))        (str "." (pad (quot nanos 1000) 6))
    :else                           (str "." (pad nanos 9))))

(defn ^{:jsdoc ["@constructor" "@extends {goog.date.UtcDateTime}"]} Instant
  "A moment: `ms` since the epoch and `sub-nanos`, the nanoseconds within
   its last millisecond (0–999999). Made by parse-instant and the decoder."
  [ms sub-nanos]
  (this-as ^js this
    (.call UtcDateTime this (js/Date. ms))
    (set! (.-subNanos this) sub-nanos)
    this))

(defn- sub-nanos
  "The nanoseconds within an Instant's last millisecond."
  [^js i]
  (.-subNanos i))

(goog/inherits Instant UtcDateTime)

(defn- instant-text [^js i]
  (let [d (js/Date. (.getTime i))]
    (str (pad (.getUTCFullYear d) 4) "-" (pad (inc (.getUTCMonth d)) 2) "-" (pad (.getUTCDate d) 2)
         "T" (pad (.getUTCHours d) 2) ":" (pad (.getUTCMinutes d) 2) ":" (pad (.getUTCSeconds d) 2)
         (fraction (+ (* 1000000 (.getUTCMilliseconds d)) (sub-nanos i)))
         "Z")))

(extend-type Instant
  Object
  (toString [this] (instant-text this))
  (toJSON [this] (instant-text this))
  (clone [this] (Instant. (.getTime this) (sub-nanos this)))
  IEquiv
  (-equiv [this other]
    (and (instance? Instant other)
         (== (.getTime this) (.getTime other))
         (== (sub-nanos this) (sub-nanos other))))
  IHash
  (-hash [this] (hash (instant-text this)))
  IComparable
  (-compare [this other]
    (if (instance? Instant other)
      (let [by-ms (compare (.getTime this) (.getTime other))]
        (if (zero? by-ms) (compare (sub-nanos this) (sub-nanos other)) by-ms))
      (throw (js/Error. (str "Cannot compare " this " to " other))))))

(defn ^{:jsdoc ["@constructor" "@extends {goog.date.Date}"]} Day
  "A calendar day: `year`, `month` (1–12), `day`. Made by parse-date and
   the decoder."
  [year month day]
  (this-as ^js this
    (.call Date this year (dec month) day)
    ;; goog.date.Date takes years 0–99 as 1900–1999 and lets a day run
    ;; over (0000-02-29 became 0000-03-01): set each part once more
    (when (< year 100)
      (doto this (.setDate 1) (.setFullYear year) (.setMonth (dec month)) (.setDate day)))
    this))

(goog/inherits Day Date)

(defn- day-text [^js d]
  (str (pad (.getFullYear d) 4) "-" (pad (inc (.getMonth d)) 2) "-" (pad (.getDate d) 2)))

(extend-type Day
  Object
  (toString [this] (day-text this))
  (toJSON [this] (day-text this))
  (clone [this] (Day. (.getFullYear this) (inc (.getMonth this)) (.getDate this)))
  IEquiv
  (-equiv [this other] (and (instance? Day other) (= (day-text this) (day-text other))))
  IHash
  (-hash [this] (hash (day-text this)))
  IComparable
  (-compare [this other]
    (if (instance? Day other)
      (compare (day-text this) (day-text other))
      (throw (js/Error. (str "Cannot compare " this " to " other))))))

(defn- digits-at
  "The whole number in n ASCII digits of text from i, or -1 when one isn't a
   digit."
  [text i n]
  (loop [k i acc 0]
    (if (== k (+ i n))
      acc
      (let [c (.charCodeAt text k)]
        (if (and (<= 48 c) (<= c 57)) (recur (inc k) (+ (* acc 10) (- c 48))) -1)))))

(defn- char-at? [text i c]
  (identical? c (.charAt text i)))

(defn- days-in-month [year month]
  (case month
    2          (if (and (zero? (rem year 4)) (or (pos? (rem year 100)) (zero? (rem year 400)))) 29 28)
    (4 6 9 11) 30
    31))

(defn- epoch-day
  "Days from 1970-01-01 to a day of the proleptic Gregorian calendar, as
   java.time counts them (Howard Hinnant's days_from_civil)."
  [year month day]
  (let [y   (if (<= month 2) (dec year) year)
        era (quot (if (>= y 0) y (- y 399)) 400)
        yoe (- y (* era 400))
        doy (+ (quot (+ (* 153 (if (> month 2) (- month 3) (+ month 9))) 2) 5) (dec day))
        doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)]
    (+ (* era 146097) doe -719468)))

(defn- day-parts-at
  "[year month day] of yyyy-MM-dd at the start of text, or nil when it is
   no such day."
  [text]
  (let [y (digits-at text 0 4) mo (digits-at text 5 2) d (digits-at text 8 2)]
    (when (and (<= 0 y) (char-at? text 4 "-") (char-at? text 7 "-")
               (<= 1 mo 12) (<= 1 d) (<= d (days-in-month y mo)))
      [y mo d])))

(defn- fraction-end
  "Where the digits of a fraction starting at i end."
  [text i]
  (loop [k i]
    (let [c (.charCodeAt text k)]
      (if (and (<= 48 c) (<= c 57)) (recur (inc k)) k))))

(defn- offset-minutes-at
  "The minutes of Z or ±HH:MM, all of what's left of text from i, or nil."
  [text i]
  (let [left (- (.-length text) i)]
    (cond
      (and (== 1 left) (char-at? text i "Z"))
      0

      (and (== 6 left) (or (char-at? text i "+") (char-at? text i "-")) (char-at? text (+ i 3) ":"))
      (let [oh (digits-at text (inc i) 2) om (digits-at text (+ i 4) 2)]
        (when (and (<= 0 oh) (<= 0 om 59) (or (< oh 18) (and (== 18 oh) (zero? om))))
          (* (if (char-at? text i "-") -1 1) (+ (* 60 oh) om)))))))

(defn read-instant
  "An Instant from its text by the grammar, \"2026-10-12T10:00:00Z\", or nil
   when the text isn't one. Read by position, as the grammar is fixed."
  [text]
  (when (and (string? text) (<= 20 (.-length text)))
    (let [h        (digits-at text 11 2) mi (digits-at text 14 2) s (digits-at text 17 2)
          dot?     (char-at? text 19 ".")
          frac-end (if dot? (fraction-end text 20) 19)
          places   (- frac-end 20)]
      (when (and (char-at? text 10 "T") (char-at? text 13 ":") (char-at? text 16 ":")
                 (<= 0 h 23) (<= 0 mi 59) (<= 0 s 59)
                 (or (not dot?) (<= 1 places 9)))
        (when-let [offset (offset-minutes-at text frac-end)]
          (when-let [[y mo d] (day-parts-at text)]
            (let [nanos (if dot? (* (digits-at text 20 places) (js/Math.pow 10 (- 9 places))) 0)
                  ms    (+ (* (epoch-day y mo d) 86400000)
                           (* 1000 (+ (* 3600 h) (* 60 mi) s))
                           (quot nanos 1000000)
                           (* -60000 offset))]
              (Instant. ms (rem nanos 1000000)))))))))

(defn read-day
  "A Day from its text by the grammar, \"2026-11-15\", or nil when the text
   isn't one."
  [text]
  (when (and (string? text) (== 10 (.-length text)))
    (when-let [[y mo d] (day-parts-at text)]
      (Day. y mo d))))

(def ^:private first-ms -62167219200000)  ; 0000-01-01T00:00:00Z
(def ^:private last-ms 253402300799999)    ; 9999-12-31T23:59:59.999Z

(defn instant-value?
  "Whether x is a moment as the browser holds it, in the years the grammar
   writes: 0000 to 9999."
  [x]
  (and (instance? Instant x) (<= first-ms (.getTime ^js x) last-ms)))

(defn day-value?
  "Whether x is a calendar day as the browser holds it, in the years 0000
   to 9999."
  [x]
  (and (instance? Day x) (<= 0 (.getFullYear ^js x) 9999)))

(defn- day-of-epoch-day
  "The Day n days after 1970-01-01."
  [n]
  (let [d (js/Date. (* n 86400000))]
    (Day. (.getUTCFullYear d) (inc (.getUTCMonth d)) (.getUTCDate d))))

(defn- min-max-by-compare
  "A time's :min and :max, held by compare, as on the JVM."
  [{:keys [min max]}]
  (when (or min max)
    (fn [x]
      (and (or (nil? min) (<= (compare min x) 0))
           (or (nil? max) (<= (compare x max) 0))))))

(def schemas
  "The :time/instant and :time/local-date schemas, as the browser holds them."
  {:time/instant    (m/-simple-schema {:type            :time/instant
                                       :pred            instant-value?
                                       :property-pred   min-max-by-compare
                                       :type-properties {:gen/schema [:tuple [:int {:min first-ms :max last-ms}] [:int {:min 0 :max 999999}]]
                                                         :gen/fmap   (fn [[ms sub-nanos]] (Instant. ms sub-nanos))}})
   :time/local-date (m/-simple-schema {:type            :time/local-date
                                       :pred            day-value?
                                       :property-pred   min-max-by-compare
                                       :type-properties {:gen/schema [:int {:min -719528 :max 2932896}]
                                                         :gen/fmap   day-of-epoch-day}})})

(def transformer
  "The decoder for times in the browser: ISO text becomes an
   Instant or a Day; text that isn't one stays, and fails its shape. Going
   out, an Instant or a Day is its text; a js/Date or another goog.date
   value given as a param becomes the same text."
  (mt/transformer
   {:name     :time
    :decoders {:time/instant    #(or (read-instant %) %)
               :time/local-date #(or (read-day %) %)}
    :encoders {:time/instant    (fn [x] (cond (instance? Instant x)  (instant-text x)
                                              (instance? js/Date x)  (instant-text (Instant. (.getTime x) 0))
                                              (instance? Date x)     (instant-text (Instant. (.getTime x) 0))
                                              :else                  x))
               :time/local-date (fn [x] (if (instance? Date x) (day-text x) x))}}))

(defn parse-date
  "A Day from its ISO text, \"2026-11-15\". Text that isn't one throws."
  [s]
  (or (read-day s) (throw (ex-info (str "Not a day: " s) {:type :wire/not-a-day :text s}))))

(defn parse-instant
  "An Instant from its ISO text, \"2026-10-12T10:00:00Z\". Text that isn't one
   throws."
  [s]
  (or (read-instant s) (throw (ex-info (str "Not a moment: " s) {:type :wire/not-a-moment :text s}))))
