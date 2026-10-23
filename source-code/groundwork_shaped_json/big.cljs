(ns groundwork-shaped-json.big
  "Exact decimals in the browser: big.js, for the settings'
   :exact-decimals (big.js must be in the project's package.json). An app
   that keeps decimals as text doesn't require this namespace, and no
   decimal library ships."
  (:require
   ["big.js" :default Big]))

(def big
  "big.js, as the settings' :exact-decimals takes it."
  {:decimal? #(instance? Big %)
   :read     #(Big. %)
   ;; toFixed with no places: plain digits, never 1e-7 as toString gives
   :text     #(.toFixed ^js %)})
