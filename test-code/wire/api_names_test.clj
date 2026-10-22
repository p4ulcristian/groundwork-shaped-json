(ns wire.api-names-test
  "The public names of wire's api. Adding or removing one fails
   here, so the api changes on purpose."
  (:require
   [clojure.test :refer [deftest is]]
   [wire.api]))

(deftest the-api-names
  (is (= '#{settings-shape
           make-wire-from-settings
           turn-json-data-into-shaped-data-or-errors
           turn-shaped-data-into-json-data-or-errors
           find-errors-in-data-for-shape
           turn-data-into-json-text
           turn-json-text-into-data}
         (set (keys (ns-publics 'wire.api))))))
