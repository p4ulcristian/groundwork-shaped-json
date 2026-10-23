(ns groundwork-shaped-json.time
  "Times on the JVM: java.time, through malli's time schemas (every :time/…
   schema of malli's). A moment and a calendar day are read by the same
   grammar as in the browser, so both sides take and refuse the same text:

     moment  2026-10-12T10:00:00Z, 2026-10-12T12:00:00.5+02:00
             seconds always; a fraction of 1 to 9 digits; Z or an offset up
             to ±18:00; upper-case T and Z; years 0000 to 9999; no leap
             second
     day     2026-11-15, years 0000 to 9999

   Going out, a java.util.Date (a java.sql.Timestamp, as JDBC gives a
   moment) is its moment, and a java.sql.Date is its day."
  (:require
   [malli.core :as m]
   [malli.experimental.time :as time]
   [malli.experimental.time.transform :as time-transform]
   [malli.transform :as mt])
  (:import
   (java.time Instant LocalDate LocalDateTime ZoneOffset)))

(set! *warn-on-reflection* true)


(defn- digits-at
  "The whole number in n ASCII digits of text from i, or -1 when one isn't a
   digit."
  ^long [^String text ^long i ^long n]
  (loop [k i acc 0]
    (if (= k (+ i n))
      acc
      (let [c (int (.charAt text k))]
        (if (<= 48 c 57) (recur (inc k) (+ (* acc 10) (- c 48))) -1)))))

(defn- char-at? [^String text ^long i ^Character c]
  (= c (.charAt text i)))

(defn- day-at
  "The LocalDate of yyyy-MM-dd at the start of text, or nil."
  ^LocalDate [^String text]
  (let [y (digits-at text 0 4) mo (digits-at text 5 2) d (digits-at text 8 2)]
    (when (and (<= 0 y) (<= 0 mo) (<= 0 d) (char-at? text 4 \-) (char-at? text 7 \-))
      (try (LocalDate/of (int y) (int mo) (int d)) (catch java.time.DateTimeException _ nil)))))

(defn read-day
  "A LocalDate from its text by the grammar, or nil when the text isn't one."
  [text]
  (when (and (string? text) (= 10 (count text)))
    (day-at text)))

(defn- fraction-end
  "Where the digits of a fraction starting at i end."
  ^long [^String text ^long i]
  (loop [k i]
    (if (and (< k (count text)) (<= 48 (int (.charAt text k)) 57)) (recur (inc k)) k)))

(defn- offset-at
  "The ZoneOffset of Z or ±HH:MM, all of what's left of text from i, or nil."
  ^ZoneOffset [^String text ^long i]
  (let [left (- (count text) i)]
    (cond
      (and (= 1 left) (char-at? text i \Z))
      ZoneOffset/UTC

      (and (= 6 left) (or (char-at? text i \+) (char-at? text i \-)) (char-at? text (+ i 3) \:))
      (let [oh (digits-at text (inc i) 2) om (digits-at text (+ i 4) 2)]
        (when (and (<= 0 oh) (<= 0 om 59) (or (< oh 18) (and (= 18 oh) (zero? om))))
          (if (char-at? text i \-)
            (ZoneOffset/ofHoursMinutes (- oh) (- om))
            (ZoneOffset/ofHoursMinutes oh om)))))))

(defn read-instant
  "An Instant from its text by the grammar, or nil when the text isn't one.
   Read by position, as the grammar is fixed: java.time's text parser, and
   a regex, are several times slower."
  [text]
  (when (and (string? text) (<= 20 (count text)))
    (let [^String text text
          h  (digits-at text 11 2) mi (digits-at text 14 2) s (digits-at text 17 2)
          dot?     (char-at? text 19 \.)
          frac-end (if dot? (fraction-end text 20) 19)
          places   (- frac-end 20)]
      (when (and (char-at? text 10 \T) (char-at? text 13 \:) (char-at? text 16 \:)
                 (<= 0 h 23) (<= 0 mi 59) (<= 0 s 59)
                 (or (not dot?) (<= 1 places 9)))
        (when-let [offset (offset-at text frac-end)]
          (when-let [day (day-at text)]
            (let [nanos (if dot? (* (digits-at text 20 places) (long (Math/pow 10 (- 9 places)))) 0)]
              (.toInstant (.atTime day (int h) (int mi) (int s) (int nanos)) offset))))))))

(def ^:private first-instant (Instant/parse "0000-01-01T00:00:00Z"))
(def ^:private last-instant (Instant/parse "9999-12-31T23:59:59.999999999Z"))

(defn instant-value?
  "Whether x is a moment as the JVM holds it, in the years the grammar
   writes: 0000 to 9999."
  [x]
  (and (instance? Instant x)
       (not (.isBefore ^Instant x first-instant))
       (not (.isAfter ^Instant x last-instant))))

(defn day-value?
  "Whether x is a calendar day as the JVM holds it, in the years 0000 to
   9999."
  [x]
  (and (instance? LocalDate x) (<= 0 (.getYear ^LocalDate x) 9999)))

(defn- min-max-by-compare
  "A time's :min and :max, held by compare, as in the browser."
  [{:keys [min max]}]
  (when (or min max)
    (fn [x]
      (and (or (nil? min) (<= (compare min x) 0))
           (or (nil? max) (<= (compare x max) 0))))))

(def schemas
  "The :time/… schemas: malli's, with a moment and a day of groundwork-shaped-json's own,
   in the grammar's years."
  (merge (time/schemas)
         {:time/instant    (m/-simple-schema {:type            :time/instant
                                              :pred            instant-value?
                                              :property-pred   min-max-by-compare
                                              :type-properties {:gen/schema [:tuple [:int {:min -62167219200 :max 253402300799}]
                                                                             [:int {:min 0 :max 999999999}]]
                                                                :gen/fmap   (fn [[seconds nanos]] (Instant/ofEpochSecond seconds nanos))}})
          :time/local-date (m/-simple-schema {:type            :time/local-date
                                              :pred            day-value?
                                              :property-pred   min-max-by-compare
                                              :type-properties {:gen/schema [:int {:min -719528 :max 2932896}]
                                                                :gen/fmap   #(LocalDate/ofEpochDay %)}})}))

(defn- instant-to-wire [x]
  (cond
    (instance? Instant x)        (str x)
    (instance? java.sql.Date x)  x
    (instance? java.util.Date x) (str (.toInstant ^java.util.Date x))
    :else                        x))

(defn- day-to-wire [x]
  (cond
    (instance? LocalDate x)     (str x)
    (instance? java.sql.Date x) (str (.toLocalDate ^java.sql.Date x))
    :else                       x))

(def transformer
  "Text to java.time and back: a moment and a day by the grammar, the other
   :time/… types by malli."
  (let [formats (dissoc time-transform/default-formats :time/instant :time/local-date)]
    (mt/transformer
     {:name     :time
      :decoders (assoc (time-transform/time-decoders formats)
                       :time/instant    #(or (read-instant %) %)
                       :time/local-date #(or (read-day %) %))
      :encoders (assoc (time-transform/time-encoders formats)
                       :time/instant    instant-to-wire
                       :time/local-date day-to-wire)})))

(defn parse-date
  "A LocalDate from its ISO text, \"2026-11-15\". Text that isn't one throws."
  [s]
  (or (read-day s) (throw (ex-info (str "Not a day: " s) {:type :groundwork-shaped-json/not-a-day :text s}))))

(defn parse-instant
  "An Instant from its ISO text, \"2026-10-12T10:00:00Z\". Text that isn't one
   throws."
  [s]
  (or (read-instant s) (throw (ex-info (str "Not a moment: " s) {:type :groundwork-shaped-json/not-a-moment :text s}))))
