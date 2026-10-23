(ns groundwork-shaped-json.api-test
  "The shaper format, the same on both sides: values keep their types; an id
   is a UUID on the server and text in the browser."
  (:require
   [clojure.test :refer [deftest is testing]]
   [groundwork-shaped-json.api :as shaped-json]
   [groundwork-shaped-json.test-shapers :refer [test-shaper text-decimals-shaper]]
   [groundwork-shaped-json.time :as time]
   [groundwork-shaped-json.big :as big])
  (:import (goog.date Date UtcDateTime)))

(def shape
  [:map [:job/id :uuid] [:job/due :time/local-date] [:job/at :time/instant]
   [:job/area :decimal] [:job/stage :keyword] [:job/cents :int]])

(def id #uuid "7f3c1e2a-0000-4000-8000-000000000001")

(defn- turn-shaped-data-into-json-data
  "The way out's JSON data; nil when it doesn't fit."
  [shape shaped-data]
  (:json-data (shaped-json/turn-shaped-data-into-json-data-or-errors test-shaper shape shaped-data)))

(defn- decimal [text] ((:read big/big) text))
(defn- decimal-text [d] ((:text big/big) d))

(deftest shaped-data-travels-as-text-and-comes-back
  (let [shaped-data {:job/id    id
                     :job/due   (time/parse-date "2026-11-15")
                     :job/at    (time/parse-instant "2026-10-12T10:00:00Z")
                     :job/area  (decimal "12.30")
                     :job/stage :curing
                     :job/cents 4200}
        text        (shaped-json/turn-data-into-json-text test-shaper (turn-shaped-data-into-json-data shape shaped-data))
        parsed      (shaped-json/turn-json-text-into-data test-shaper text)]
    (testing "on the groundwork-shaped-json: plain JSON, keys with their namespace"
      (is (= {:job/id "7f3c1e2a-0000-4000-8000-000000000001" :job/due "2026-11-15"
              :job/at "2026-10-12T10:00:00Z" :job/area "12.3" :job/stage "curing" :job/cents 4200}
             parsed)))
    (testing "back by the shape: the same values"
      (let [{back :shaped-data} (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper shape parsed)]
        (is (= (str id) (:job/id back))
            "an id: a UUID on the server, its text in the browser")
        (is (= "2026-11-15" (str (:job/due back))) "a day stays that day")
        (is (= "2026-10-12T10:00:00Z" (str (:job/at back))) "a moment prints as the text it came as")
        (is (= (:job/at shaped-data) (:job/at back)) "and equals the one sent")
        (is (= "12.3" (decimal-text (:job/area back)))
            "exact; the browser doesn't keep trailing zeros")
        (is (= :curing (:job/stage back)))
        (is (= 4200 (:job/cents back)))))
    (testing "what doesn't fit says why"
      (is (contains? (:errors (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper shape (assoc parsed :job/id "nope"))) :job/id)))))

(deftest an-id-is-text-in-the-browser
     (let [text "7f3c1e2a-0000-4000-8000-000000000001"]
       (testing "text that is an id fits, anything else doesn't"
         (is (nil? (shaped-json/find-errors-in-data-for-shape test-shaper :uuid text)))
         (is (some? (shaped-json/find-errors-in-data-for-shape test-shaper :uuid "nope")))
         (is (some? (shaped-json/find-errors-in-data-for-shape test-shaper :uuid id)) "a UUID value isn't what the browser keeps"))
       (testing "a UUID value given goes out, and is read, as its text"
         (is (= {:shop/id text} (turn-shaped-data-into-json-data [:map [:shop/id :uuid]] {:shop/id id})))
         (is (= {:shaped-data [text]} (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper [:vector :uuid] [id]))))))

(deftest a-moment-is-an-instant-built-on-goog-date
     (testing "the decoder: ISO text becomes an Instant, a goog.date.UtcDateTime"
       (let [{at :shaped-data} (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper :time/instant "2026-10-12T10:00:00Z")]
         (is (instance? UtcDateTime at))
         (is (= (js/Date.parse "2026-10-12T10:00:00Z") (.getTime at)))
         (is (= 10 (.getUTCHours at)))))
     (testing "it prints, and goes out, as java.time prints it"
       (doseq [text ["2026-10-12T10:00:00Z" "2026-10-12T10:00:00.120Z"
                     "2026-10-12T10:00:00.123456Z" "2026-10-12T10:00:00.123456789Z"
                     "1969-12-31T23:59:59.999Z"]]
         (let [at (time/parse-instant text)]
           (is (= text (str at)))
           (is (= text (turn-shaped-data-into-json-data :time/instant at)))
           (is (= (str "{\"at\":\"" text "\"}") (shaped-json/turn-data-into-json-text test-shaper {:at at})) "JSON text too"))))
     (testing "the microseconds JavaScript's date drops are kept"
       (let [at (time/parse-instant "2026-10-12T10:00:00.123456Z")]
         (is (= (js/Date.parse "2026-10-12T10:00:00.123Z") (.getTime at)))
         (is (not= at (time/parse-instant "2026-10-12T10:00:00.123Z")))))
     (testing "an offset is read as the moment it names"
       (is (= "2026-10-12T10:00:00Z" (str (time/parse-instant "2026-10-12T12:00:00+02:00"))))
       (is (= "2026-10-12T10:30:00Z" (str (time/parse-instant "2026-10-12T09:00:00-01:30")))))
     (testing "= and compare, and a clone stays an Instant"
       (let [a (time/parse-instant "2026-10-12T10:00:00Z")
             b (time/parse-instant "2026-10-12T10:00:00.000001Z")]
         (is (= a (time/parse-instant "2026-10-12T10:00:00Z")))
         (is (= (hash a) (hash (time/parse-instant "2026-10-12T10:00:00Z"))))
         (is (= [a b] (sort [b a])))
         (is (= a (.clone a)))))
     (testing "a js/Date given as a param goes out as the same text"
       (is (= "2026-10-12T10:00:00Z" (turn-shaped-data-into-json-data :time/instant (js/Date. "2026-10-12T10:00:00Z")))))
     (testing "text that isn't a moment stays text and fails the shape"
       (doseq [text ["2026-10-12" "2026-02-30T10:00:00Z" "2026-10-12T24:00:00Z" "2026-10-12T10:00:00" "nope"]]
         (is (nil? (time/read-instant text)) text)
         (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper [:map [:at :time/instant]] {:at text}) :errors) text))
       (is (thrown? js/Error (time/parse-instant "nope")))))

(deftest a-day-is-a-day-built-on-goog-date
     (testing "the decoder: ISO text becomes a Day, a goog.date.Date"
       (let [{due :shaped-data} (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper :time/local-date "2026-11-15")]
         (is (instance? Date due))
         (is (= [2026 10 15] [(.getFullYear due) (.getMonth due) (.getDate due)])
             "its year, month and day are the shaper's, wherever the browser is")
         (is (= "2026-11-15" (str due) (turn-shaped-data-into-json-data :time/local-date due) (.toJSON due)))))
     (testing "= and compare, and a clone stays a Day"
       (let [a (time/parse-date "2026-11-15") b (time/parse-date "2026-12-01")]
         (is (= a (time/parse-date "2026-11-15")))
         (is (= [a b] (sort [b a])))
         (is (= "2026-11-15" (str (.clone a))))
         (is (= a (.clone a)))))
     (testing "text that isn't a day stays text and fails the shape"
       (doseq [text ["2026-02-30" "2026-13-01" "2026-11-15T00:00:00Z" "15/11/2026"]]
         (is (nil? (time/read-day text)) text)
         (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper :time/local-date text) :errors) text))
       (is (time/read-day "2028-02-29") "a leap day")))

(deftest the-way-out-names-every-key
  (is (contains? (:errors (shaped-json/turn-shaped-data-into-json-data-or-errors test-shaper [:map [:shop/id :uuid]] {:shop/id id :shop/secret "x"}))
                 :shop/secret)
      "a key the shape doesn't name never leaves"))

(deftest decimals-are-the-apps-choice
  (testing "nil: a decimal is its text, exactly as it came"
    (is (= {:shaped-data {:area "12.30"}} (shaped-json/turn-json-data-into-shaped-data-or-errors text-decimals-shaper [:map [:area :decimal]] {:area "12.30"})))
    (is (= {:json-data {:area "12.30"}} (shaped-json/turn-shaped-data-into-json-data-or-errors text-decimals-shaper [:map [:area :decimal]] {:area "12.30"})))
    (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors text-decimals-shaper :decimal "twelve") :errors)))
  (testing "big.js: exact"
    (let [{^js area :shaped-data} (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper :decimal "0.10")]
      (is ((:decimal? big/big) area))
      (is (= "0.3" (decimal-text (.plus area (decimal "0.2")))))
      (is (= "0.1" (turn-shaped-data-into-json-data :decimal area))))))

(deftest the-way-in-names-every-key
  (let [order [:map [:order/id :uuid] [:order/lines [:vector [:map [:line/cents :int]]]]]]
    (is (contains? (:errors (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper
                             order {:order/id (str id) :order/lines [] :order/shop-id "x"}))
                   :order/shop-id)
        "a key the shape doesn't name never comes in")
    (is (= {:order/lines [{:line/secret ["disallowed key"]}]}
           (:errors (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper
                     order {:order/id (str id) :order/lines [{:line/cents 1 :line/secret 2}]})))
        "nor inside")
    (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper
                    [:map {:closed false} [:order/id :uuid]] {:order/id (str id) :order/note "x"})
                   :shaped-data)
        "a map the shape leaves open by name stays open")))

(deftest a-whole-number-beyond-javascripts-range-is-its-text
  (let [n [:map [:n :int]]]
    (testing "within the safe range: a number"
      (is (= {:shaped-data {:n 9007199254740991}} (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper n {:n 9007199254740991})))
      (is (= {:n -9007199254740991} (turn-shaped-data-into-json-data n {:n -9007199254740991}))))
    (testing "beyond it: its text, both ways, to the ends of a long"
      (doseq [text ["9007199254740992" "-9007199254740992" "9223372036854775807" "-9223372036854775808"]]
        (is (= {:shaped-data {:n text}} (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper n {:n text})) text)
        (is (= {:n text} (turn-shaped-data-into-json-data n {:n text})) text)))
    (testing "the server's text, read from JSON text"
      (is (= {:shaped-data {:n "9007199254740993"}}
             (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper n (shaped-json/turn-json-text-into-data test-shaper "{\"n\":\"9007199254740993\"}")))))
    (testing "refused: a number beyond the range, maybe rounded already"
      (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper n (shaped-json/turn-json-text-into-data test-shaper "{\"n\":9007199254740993}")) :errors))
      (is (nil? (turn-shaped-data-into-json-data n {:n 9007199254740992}))))
    (testing "refused: text a number travels as a number, or no whole number a long holds"
      (doseq [text ["42" "042" "-0" "1.0" "9223372036854775808" "nope"]]
        (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper n {:n text}) :errors) text)))
    (testing ":min and :max hold for the text too"
      (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper [:int {:max 10}] "9007199254740993") :errors))
      (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper [:int {:min 0}] "9007199254740993") :shaped-data))
      (is (contains? (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper [:int {:min 0}] -1) :errors)))))

(deftest a-uuid-value-is-its-text-in-json-text
  (is (= "{\"shop/id\":\"7f3c1e2a-0000-4000-8000-000000000001\"}" (shaped-json/turn-data-into-json-text test-shaper {:shop/id id}))))

(deftest a-decimal-of-any-size-leaves-the-browser
  (doseq [text ["0.0000001" "1000000000000000000000" "-0.5"]]
    (is (= {:json-data text} (shaped-json/turn-shaped-data-into-json-data-or-errors test-shaper :decimal (decimal text))) text)))

(deftest an-id-is-lower-case-text
  (is (= {:shaped-data "7f3c1e2a-0000-4000-8000-00000000000a"}
         (shaped-json/turn-json-data-into-shaped-data-or-errors test-shaper :uuid "7F3C1E2A-0000-4000-8000-00000000000A"))))

(deftest the-kept-keys-are-the-setting
  (let [one-key (shaped-json/make-shaper-from-settings (assoc groundwork-shaped-json.test-shapers/settings :most-keys-kept 1))]
    (is (= [{:a 1 :b 2} {:a 3 :b 4}] (shaped-json/turn-json-text-into-data one-key "[{\"a\":1,\"b\":2},{\"a\":3,\"b\":4}]"))
        "past the setting, keys are forgotten and made again, and read the same")))
