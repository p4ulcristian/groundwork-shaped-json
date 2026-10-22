(ns wire.api-test
  "The wire format, the same on both sides: values keep their types; an id
   is a UUID on the server and text in the browser."
  (:require
   [clojure.test :refer [deftest is testing]]
   [wire.api :as wire]
   [wire.test-wires :refer [test-wire]]
   [wire.time :as time]))

(def shape
  [:map [:job/id :uuid] [:job/due :time/local-date] [:job/at :time/instant]
   [:job/area :decimal] [:job/stage :keyword] [:job/cents :int]])

(def id #uuid "7f3c1e2a-0000-4000-8000-000000000001")

(deftest shaped-data-travels-as-text-and-comes-back
  (let [shaped-data {:job/id    id
                     :job/due   (time/parse-date "2026-11-15")
                     :job/at    (time/parse-instant "2026-10-12T10:00:00Z")
                     :job/area  12.30M
                     :job/stage :curing
                     :job/cents 4200}
        text        (wire/turn-data-into-json-text test-wire (:json-data (wire/turn-shaped-data-into-json-data-or-errors test-wire shape shaped-data)))
        parsed      (wire/turn-json-text-into-data test-wire text)]
    (testing "on the wire: plain JSON, keys with their namespace"
      (is (= {:job/id "7f3c1e2a-0000-4000-8000-000000000001" :job/due "2026-11-15"
              :job/at "2026-10-12T10:00:00Z" :job/area "12.30" :job/stage "curing" :job/cents 4200}
             parsed)))
    (testing "back by the shape: the same values"
      (let [{back :shaped-data} (wire/turn-json-data-into-shaped-data-or-errors test-wire shape parsed)]
        (is (= id (:job/id back))
            "an id: a UUID on the server, its text in the browser")
        (is (= "2026-11-15" (str (:job/due back))) "a day stays that day")
        (is (= "2026-10-12T10:00:00Z" (str (:job/at back))) "a moment prints as the text it came as")
        (is (= (:job/at shaped-data) (:job/at back)) "and equals the one sent")
        (is (= "12.30" (.toPlainString ^BigDecimal (:job/area back)))
            "exact; the browser doesn't keep trailing zeros")
        (is (= :curing (:job/stage back)))
        (is (= 4200 (:job/cents back)))))
    (testing "what doesn't fit says why"
      (is (contains? (:errors (wire/turn-json-data-into-shaped-data-or-errors test-wire shape (assoc parsed :job/id "nope"))) :job/id)))))

(deftest the-way-out-names-every-key
  (let [order [:map [:order/id :uuid] [:order/total :decimal]]]
    (is (= {:json-data {:order/id "7f3c1e2a-0000-4000-8000-000000000001" :order/total "12.30"}}
           (wire/turn-shaped-data-into-json-data-or-errors test-wire order {:order/id id :order/total 12.30M})))
    (is (contains? (:errors (wire/turn-shaped-data-into-json-data-or-errors test-wire
                             order {:order/id id :order/total 12.30M :user/password-hash "x"}))
                   :user/password-hash)
        "a key the shape doesn't name never leaves")
    (is (contains? (:errors (wire/turn-shaped-data-into-json-data-or-errors test-wire order {:order/id id :order/total "twelve"}))
                   :order/total)
        "a value that doesn't come back as its type")))

(deftest the-way-in-names-every-key
  (let [order [:map [:order/id :uuid] [:order/lines [:vector [:map [:line/cents :int]]]]]]
    (is (contains? (:errors (wire/turn-json-data-into-shaped-data-or-errors test-wire
                             order {:order/id (str id) :order/lines [] :order/shop-id "x"}))
                   :order/shop-id)
        "a key the shape doesn't name never comes in")
    (is (= {:order/lines [{:line/secret ["disallowed key"]}]}
           (:errors (wire/turn-json-data-into-shaped-data-or-errors test-wire
                     order {:order/id (str id) :order/lines [{:line/cents 1 :line/secret 2}]})))
        "nor inside")
    (is (contains? (wire/turn-json-data-into-shaped-data-or-errors test-wire
                    [:map {:closed false} [:order/id :uuid]] {:order/id (str id) :order/note "x"})
                   :shaped-data)
        "a map the shape leaves open by name stays open")))

(deftest a-whole-number-is-a-number-a-browser-holds-exactly
  (let [n [:map [:n :int]]]
    (testing "within JavaScript's safe range: a number"
      (is (= {:json-data {:n 9007199254740991}} (wire/turn-shaped-data-into-json-data-or-errors test-wire n {:n 9007199254740991})))
      (is (= {:shaped-data {:n -9007199254740991}} (wire/turn-json-data-into-shaped-data-or-errors test-wire n {:n -9007199254740991}))))
    (testing "beyond it: text, both ways, to the ends of a long"
      (doseq [x [9007199254740992 -9007199254740992 Long/MAX_VALUE Long/MIN_VALUE]]
        (is (= {:json-data {:n (str x)}} (wire/turn-shaped-data-into-json-data-or-errors test-wire n {:n x})))
        (is (= {:shaped-data {:n x}} (wire/turn-json-data-into-shaped-data-or-errors test-wire n {:n (str x)})))))
    (testing "refused: a number beyond the range, which a browser may have rounded"
      (is (contains? (wire/turn-json-data-into-shaped-data-or-errors test-wire n {:n 9007199254740993}) :errors)))
    (testing "refused: text a number travels as a number, or no whole number a long holds"
      (doseq [text ["42" "042" "-0" "1.0" "9223372036854775808" "nope"]]
        (is (contains? (wire/turn-json-data-into-shaped-data-or-errors test-wire n {:n text}) :errors) text)))))

(deftest a-decimal-is-plain-text
  (is (= {:shaped-data -0.5M} (wire/turn-json-data-into-shaped-data-or-errors test-wire :decimal "-0.5")))
  (doseq [text ["1E+3" "1e3" ".5" "+1" "1." "NaN" " 1"]]
    (is (contains? (wire/turn-json-data-into-shaped-data-or-errors test-wire :decimal text) :errors) text))
  (is (contains? (wire/turn-json-data-into-shaped-data-or-errors test-wire :decimal 12.3) :errors)
      "a JSON number may already be rounded"))

(deftest a-jdbc-time-goes-out-as-its-moment-or-day
  (is (= {:json-data "2026-10-12T10:00:00.123456789Z"}
         (wire/turn-shaped-data-into-json-data-or-errors test-wire
          :time/instant (doto (java.sql.Timestamp/from (java.time.Instant/parse "2026-10-12T10:00:00Z")) (.setNanos 123456789)))))
  (is (= {:json-data "2026-10-12T10:00:00Z"}
         (wire/turn-shaped-data-into-json-data-or-errors test-wire :time/instant (java.util.Date/from (java.time.Instant/parse "2026-10-12T10:00:00Z")))))
  (is (= {:json-data "2026-11-15"}
         (wire/turn-shaped-data-into-json-data-or-errors test-wire :time/local-date (java.sql.Date/valueOf "2026-11-15")))))

(deftest the-kept-tools-stay-few
  (dotimes [i 1100]
    (is (= {:shaped-data i} (wire/turn-json-data-into-shaped-data-or-errors test-wire [:enum i] i))))
  (is (<= (count @(:tools-by-shape test-wire)) 1000)
      "a shape made on every call doesn't grow the kept tools without end"))

(deftest jacksons-limits-are-the-settings
  (let [tight (wire/make-wire-from-settings (assoc-in wire.test-wires/settings [:json-limits :max-nesting-depth] 2))]
    (is (= [[1]] (wire/turn-json-text-into-data tight "[[1]]")))
    (is (thrown? Exception (wire/turn-json-text-into-data tight "[[[1]]]")) "deeper than the setting is refused"))
  (let [short-text (wire/make-wire-from-settings (assoc-in wire.test-wires/settings [:json-limits :max-string-length] 5))]
    (is (thrown? Exception (wire/turn-json-text-into-data short-text "[\"longer than five\"]")))))
