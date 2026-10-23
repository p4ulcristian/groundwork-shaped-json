(ns groundwork-shaped-json.test-shapers
  "The wires the tests use: settings written out, as an app writes them."
  (:require
   [groundwork-shaped-json.api :as shaped-json]
   #?(:cljs [groundwork-shaped-json.big :as big])))

(def settings
  {:most-shapes-kept 1000
   #?@(:clj  [:json-limits {:max-nesting-depth         1000
                            :max-number-length         1000
                            :max-string-length         20000000
                            :max-name-length           50000
                            :max-document-length       nil
                            :max-token-count           nil
                            :max-writing-nesting-depth 1000}]
       :cljs [:most-keys-kept 10000
              :exact-decimals big/big])})

(def test-shaper
  "Exact decimals in the browser."
  (shaped-json/make-shaper-from-settings settings))

#?(:cljs
   (def text-decimals-shaper
     "Decimals as their text in the browser."
     (shaped-json/make-shaper-from-settings (assoc settings :exact-decimals nil))))
