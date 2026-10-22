(ns wire.both-sides-test
  "One file, run on the JVM and in the browser: both sides take, refuse and
   say the same."
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.walk]
   [malli.core :as m]
   [malli.generator :as mg]
   [wire.api :as wire]
   [wire.time :as time]))

(def id #uuid "7f3c1e2a-0000-4000-8000-000000000001")

(defn- way-out [shape shaped-data] (wire/turn-shaped-data-into-json-data-or-errors shape shaped-data))
(defn- way-in [shape json-data] (wire/turn-json-data-into-shaped-data-or-errors shape json-data))
(defn- way-in-from-text [shape text] (way-in shape (wire/turn-json-text-into-data text)))

(deftest maps-a-registry-keeps-are-closed-too
  (let [rows [:schema {:registry {::row [:map [:shop/name :string]]}} [:vector ::row]]]
    (is (= {:errors [{:user/password-hash ["disallowed key"]}]}
           (way-out rows [{:shop/name "b" :user/password-hash "x"}]))
        "a key a reused row doesn't name never leaves")
    (is (= {:errors [{:user/password-hash ["disallowed key"]}]}
           (way-in rows [{:shop/name "b" :user/password-hash "x"}]))
        "nor comes in"))
  (let [tree [:schema {:registry {::node [:map [:part/name :string] [:part/parts [:vector [:ref ::node]]]]}} ::node]]
    (is (= {:shaped-data {:part/name "a" :part/parts [{:part/name "b" :part/parts []}]}}
           (way-in tree {:part/name "a" :part/parts [{:part/name "b" :part/parts []}]})))
    (is (= {:errors {:part/parts [{:part/parts [{:part/secret ["disallowed key"]}]}]}}
           (way-in tree {:part/name "a" :part/parts [{:part/name "b" :part/parts [{:part/name "c" :part/parts [] :part/secret 1}]}]}))
        "a recursive shape is closed at every depth")))

(deftest a-tagged-union-crosses-both-ways
  (let [payment [:multi {:dispatch :payment/kind}
                 [:payment/card [:map [:payment/kind [:= :payment/card]] [:card/last-four :string]]]
                 [:payment/cash [:map [:payment/kind [:= :payment/cash]] [:cash/cents :int]]]]]
    (is (= {:json-data {:payment/kind "payment/card" :card/last-four "4242"}}
           (way-out payment {:payment/kind :payment/card :card/last-four "4242"})))
    (is (= {:shaped-data {:payment/kind :payment/cash :cash/cents 500}}
           (way-in-from-text payment "{\"payment/kind\":\"payment/cash\",\"cash/cents\":500}")))
    (is (contains? (way-in payment {:payment/kind "payment/cheque"}) :errors))
    (is (contains? (way-in payment {:payment/kind "payment/cash" :cash/cents 500 :card/last-four "1"}) :errors)
        "closed in each branch")))

(deftest one-grammar-for-moments-and-days
  (testing "moments both sides take"
    (doseq [text ["2026-10-12T10:00:00Z" "2026-10-12T10:00:00.5Z" "2026-10-12T10:00:00.123456789Z"
                  "2026-10-12T12:00:00+02:00" "2026-10-12T10:00:00+18:00" "2026-10-12T10:00:00-18:00"
                  "0000-01-01T00:00:00Z" "9999-12-31T23:59:59Z"]]
      (is (contains? (way-in :time/instant text) :shaped-data) text)))
  (testing "moments both sides refuse"
    (doseq [text ["2026-10-12T10:00Z" "2026-10-12t10:00:00z" "2026-10-12T10:00:00z" "2026-10-12T10:00:00+18:30"
                  "2026-10-12T10:00:00+19:00" "2026-12-31T23:59:60Z" "2026-10-12T24:00:00Z" "2026-02-30T10:00:00Z"
                  "+10000-01-01T00:00:00Z" "2026-10-12T10:00:00" "2026-10-12T10:00:00.Z" "2026-10-12T10:00:00.1234567890Z"]]
      (is (= {:errors ["should be a moment, like 2026-10-12T10:00:00Z"]} (way-in :time/instant text)) text)))
  (testing "days"
    (doseq [text ["2026-11-15" "0000-02-29" "2028-02-29" "9999-12-31"]]
      (is (= text (str (:shaped-data (way-in :time/local-date text))) (:json-data (way-out :time/local-date (time/parse-date text)))) text))
    (doseq [text ["2026-02-29" "2026-13-01" "+10000-01-01" "2026-11-15T00:00:00Z" "15/11/2026"]]
      (is (= {:errors ["should be a day, like 2026-11-15"]} (way-in :time/local-date text)) text)))
  (testing "an offset is read as the moment it names"
    (is (= "2026-10-12T10:00:00Z" (str (:shaped-data (way-in :time/instant "2026-10-12T12:00:00+02:00")))))))

(deftest a-times-min-and-max-hold
  (let [after-2030 [:time/instant {:min (time/parse-instant "2030-01-01T00:00:00Z")}]]
    (is (= {:errors ["should be at least 2030-01-01T00:00:00Z"]} (way-in after-2030 "2026-10-12T10:00:00Z")))
    (is (contains? (way-in after-2030 "2031-01-01T00:00:00Z") :shaped-data)))
  (let [in-2026 [:time/local-date {:min (time/parse-date "2026-01-01") :max (time/parse-date "2026-12-31")}]]
    (is (= {:errors ["should be between 2026-01-01 and 2026-12-31"]} (way-in in-2026 "2027-01-01")))))

(deftest a-double-is-finite
  (doseq [x [##NaN ##Inf ##-Inf]]
    (is (= {:errors ["should be a finite number"]} (way-out :double x)) (str x)))
  (is (= {:json-data 1.5} (way-out :double 1.5))))

(deftest the-same-error-texts
  (is (= {:errors {:shop/id ["should be an id"]
                   :order/total ["should be a decimal"]
                   :order/cents ["should be an integer"]
                   :order/lines ["should be at most 10"]}}
         (way-in [:map [:shop/id :uuid] [:order/total :decimal] [:order/cents :int] [:order/lines [:int {:max 10}]]]
                 {:shop/id "nope" :order/total "1E+3" :order/cents "42" :order/lines "9007199254740993"}))))

(deftest map-keys-that-are-whole-numbers
  (let [shape [:map-of :int :string]]
    (is (= {:shaped-data {1 "a"}} (way-in-from-text shape "{\"1\":\"a\"}")))
    (is (= {:shaped-data {#?(:clj 9007199254740993 :cljs "9007199254740993") "a"}}
           (way-in-from-text shape "{\"9007199254740993\":\"a\"}"))
        "a key is text, never rounded: beyond the safe range it is a long on the JVM, its text in the browser")
    (is (contains? (way-in-from-text shape "{\"042\":\"a\"}") :errors))
    (is (contains? (way-in-from-text shape "{\"9223372036854775808\":\"a\"}") :errors))))

(deftest json-text-the-same-on-both-sides
  (is (= "{\"7f3c1e2a-0000-4000-8000-000000000001\":1}" (wire/turn-data-into-json-text {id 1}))
      "a UUID key is its text")
  (is (= ["a" "b/c"] ((juxt namespace name) (key (first (wire/turn-json-text-into-data "{\"a/b/c\":1}")))))
      "the namespace ends at the first slash")
  (is (= {:order/lines [1 2] :order/tags ["a"] :order/kind "order/rush" :order/note nil}
         (wire/turn-json-text-into-data (wire/turn-data-into-json-text {:order/lines '(1 2) :order/tags #{"a"} :order/kind :order/rush :order/note nil}))))
  (doseq [text ["{\"a\":1}{\"a\":2}" "[1,2] garbage"]]
    (is (thrown? #?(:clj Exception :cljs js/Error) (wire/turn-json-text-into-data text)) "text after the JSON is refused")))

(def ^:private generated-shapes
  [[:map [:order/id :uuid] [:order/total :decimal] [:order/day :time/local-date] [:order/at :time/instant]
    [:order/cents :int] [:order/status [:enum :order/new :order/paid]] [:order/note {:optional true} :string]
    [:order/lines [:vector [:map [:line/sku :string] [:line/qty :int]]]]]
   [:map-of :int :string]
   [:map-of :uuid :time/local-date]
   [:set :keyword]
   [:tuple :double [:maybe :time/instant] [:or :int :string]]
   [:multi {:dispatch :payment/kind}
    [:payment/card [:map [:payment/kind [:= :payment/card]] [:card/last-four :string]]]
    [:payment/cash [:map [:payment/kind [:= :payment/cash]] [:cash/cents :int]]]]
   [:schema {:registry {::part [:map [:part/name :string] [:part/parts [:vector [:ref ::part]]]]}} ::part]])

(deftest shaped-data-that-fits-reads-back-as-it-went
  ;; the way out sends data that fits without reading it back: generated
  ;; data of every kind must read back fitting, and write the same text again
  (doseq [shape generated-shapes
          :let  [{:keys [encode decode fits-closed?]} (#'wire.api/find-tools-for-shape shape)]
          value (mg/sample (m/schema shape {:registry @#'wire.api/registry}) {:size #?(:clj 200 :cljs 50) :seed 42})]
    (is (fits-closed? value) (pr-str shape))
    (let [json-data (encode value)
          back      (decode (wire/turn-json-text-into-data (wire/turn-data-into-json-text json-data)))]
      (is (fits-closed? back) (pr-str value))
      (is (= (wire/turn-data-into-json-text json-data) (wire/turn-data-into-json-text (encode back))) (pr-str value)))))

(defn- malli-way-in
  "The way in by malli alone, as before the walk."
  [{:keys [decode fits-closed? explain-closed]} json-data]
  (let [shaped-data (decode json-data)]
    (if (fits-closed? shaped-data)
      {:shaped-data shaped-data}
      {:errors (#'wire.api/humanize (explain-closed shaped-data))})))

(defn- malli-way-out
  "The way out by malli alone, as before the walk."
  [{:keys [encode decode fits-closed? explain-closed]} shaped-data]
  (let [json-data (encode shaped-data)]
    (if (or (fits-closed? shaped-data) (fits-closed? (decode json-data)))
      {:json-data json-data}
      {:errors (#'wire.api/humanize (explain-closed (decode json-data)))})))

(defn- break
  "Ways JSON data or shaped data can be wrong: a key too many, a key gone,
   text where numbers are, numbers where text is, a list for a vector."
  [x]
  (let [everywhere (fn [f] (clojure.walk/postwalk f x))]
    [(if (map? x) (assoc x :wire/extra 1) [x])
     (if (and (map? x) (seq x)) (dissoc x (first (keys x))) nil)
     (everywhere #(if (number? %) "7" %))
     (everywhere #(if (string? %) 7 %))
     (everywhere #(if (and (vector? %) (not (map-entry? %))) (seq %) %))
     (everywhere #(if (map? %) (assoc % :wire/extra 1) %))]))

(deftest the-one-walk-answers-as-malli-does
  (doseq [shape generated-shapes
          :let  [tools (#'wire.api/find-tools-for-shape shape)]
          value (mg/sample (m/schema shape {:registry @#'wire.api/registry}) {:size #?(:clj 100 :cljs 30) :seed 7})
          data  (cons value (break value))]
    (is (= (malli-way-out tools data) (way-out shape data)) (pr-str [:out shape data]))
    (let [json-data ((:encode tools) data)
          ;; shaped data compared by the text it writes: big.js values are
          ;; = only to themselves
          comparable (fn [{:keys [shaped-data errors]}]
                       (if errors errors (wire/turn-data-into-json-text ((:encode tools) shaped-data))))]
      (doseq [json (cons json-data (break json-data))]
        (is (= (comparable (malli-way-in tools json)) (comparable (way-in shape json))) (pr-str [:in shape json]))))))
