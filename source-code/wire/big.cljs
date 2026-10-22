(ns wire.big
  "Exact decimals in the browser: once this namespace is loaded, a :decimal
   is a big.js value (big.js must be in the project's package.json). Not
   loaded, a decimal stays its text and no decimal library ships."
  (:require
   ["big.js" :default Big]
   [wire.types :as types]))

(def big
  "big.js, as wire.types/use-exact-decimals! takes it."
  {:decimal? #(instance? Big %)
   :read     #(Big. %)
   ;; toFixed with no places: plain digits, never 1e-7 as toString gives
   :text     #(.toFixed ^js %)})

(types/use-exact-decimals! big)
