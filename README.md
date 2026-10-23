# wire

Data that crosses an edge comes back as what it was, on the JVM and in the
browser. JSON knows only text, numbers, true/false, lists and maps; a
[malli](https://github.com/metosin/malli) shape says what every value
really is, so ids, exact decimals, days, moments and whole numbers too big
for JavaScript travel as text and come back as their real types. Both sides
read the same JSON and refuse the same.

```clojure
io.github.p4ulcristian/wire {:git/tag "v0.2.1" :git/sha "900a84d"}   ; the sha: git ls-remote https://github.com/p4ulcristian/wire v0.2.0
```

Needs only malli and jsonista. One namespace for both languages:
`wire.api` is a `.cljc`; `wire.json`, `wire.time` and `wire.types` are a
`.clj` and a `.cljs` each, and each language loads its own.

## The api: a wire, and five functions

An app makes a wire once from its settings, every value written out (see
[the settings](decisions/2026-10-22-every-value-that-sets-behaviour-is-a-setting.md)),
and every function takes it first.

| function | does |
|---|---|
| `make-wire-from-settings` | settings → a wire; settings that don't fit `settings-shape` throw `:wire/bad-settings` |
| `turn-json-text-into-data` | JSON text → JSON data; `"post/title"` becomes `:post/title` |
| `turn-data-into-json-text` | JSON data → JSON text; keys keep their namespace |
| `turn-json-data-into-shaped-data-or-errors` | the way in: `{:shaped-data …}` or `{:errors …}`, checked with every map closed: a key the shape doesn't name never comes in |
| `turn-shaped-data-into-json-data-or-errors` | the way out: `{:json-data …}` or `{:errors …}`, checked with every map closed: a key the shape doesn't name never leaves |
| `find-errors-in-data-for-shape` | why data doesn't fit a shape; nil when it fits |

```clojure
(require '[wire.api :as wire])

(def wire (wire/make-wire-from-settings
           {:most-shapes-kept 1000
            :json-limits      {:max-nesting-depth 1000 :max-number-length 1000 :max-string-length 20000000
                               :max-name-length 50000 :max-document-length nil :max-token-count nil
                               :max-writing-nesting-depth 1000}}))

(def order [:map [:order/id :uuid] [:order/total :decimal] [:order/day :time/local-date]])

(wire/turn-shaped-data-into-json-data-or-errors wire order {:order/id    #uuid "1d000000-0000-4000-8000-000000000001"
                                                      :order/total 120.50M
                                                      :order/day   (java.time.LocalDate/parse "2026-11-15")})
;=> {:json-data #:order{:id "1d000000-0000-4000-8000-000000000001" :total "120.50" :day "2026-11-15"}}

(wire/turn-shaped-data-into-json-data-or-errors wire order {:order/id #uuid "1d000000-0000-4000-8000-000000000001"
                                                      :order/total 120.50M
                                                      :order/day (java.time.LocalDate/parse "2026-11-15")
                                                      :user/password-hash "x"})
;=> {:errors {:user/password-hash ["disallowed key"]}}

(wire/turn-json-data-into-shaped-data-or-errors wire order (wire/turn-json-text-into-data wire json-text))
;=> {:shaped-data #:order{:id #uuid "1d000000-…" :total 120.50M :day #object[java.time.LocalDate "2026-11-15"]}}
```

## Both sides take and refuse the same

- every map is closed both ways, maps a shape keeps in a registry too
  (`[:schema {:registry {::row [:map …]}} [:vector ::row]]`, recursive
  ones at every depth)
- a tagged union on a keyword, `[:multi {:dispatch :payment/kind} …]`,
  reads its text: `"payment/card"` finds `:payment/card`
- a moment is `2026-10-12T10:00:00Z`: seconds always, a fraction of 1 to 9
  digits, `Z` or an offset up to ±18:00, upper-case `T` and `Z`, years
  0000 to 9999, no leap second; a day is `2026-11-15`
- a `:double` is finite: JSON has no NaN or Infinity
- `[:decimal {:precision 10 :scale 2}]` holds what Postgres's
  `numeric(10,2)` does: at most 2 digits after the point and 8 before it
- `[:json shape]` is a value a database keeps as JSON, its shape the
  child's, maps closed as anywhere (`[:json :any]` for any JSON)
  ([why](decisions/2026-10-23-a-databases-json-and-a-decimals-digits-are-shapes.md))
- an error says the same on both sides: `"should be an id"`,
  `"should be at most 10"`, `"should be a moment, like 2026-10-12T10:00:00Z"`
- on the JVM a `java.sql.Timestamp` or `java.util.Date` goes out as its
  moment and a `java.sql.Date` as its day, as JDBC gives them

## In the browser

- an id stays its text, in lower case as the JVM writes it
- a whole number is a number within JavaScript's safe range and its text
  beyond it (to the ends of a JVM long); a number beyond the range may
  have been rounded, so it doesn't fit `:int`
- a moment is a `wire.time/Instant` and a calendar day a `wire.time/Day`,
  built on Closure's `goog.date`; both print as the ISO text they came as
- a decimal is its text (`:exact-decimals nil`), or exact with big.js
  (`:exact-decimals wire.big/big`, and `big.js` in the app's package.json)

## Speed

1000 orders (382 KB of JSON text), the median of 60 rounds:

| | JVM | browser (node, advanced build) |
|---|---|---|
| way out, shaped data → JSON data | 2.7 ms | 5.3 ms |
| JSON data → text | 1.5 ms | 2.9 ms |
| text → JSON data | 3.3 ms | 3.9 ms |
| way in, JSON data → shaped data | 3.2 ms | 6.5 ms |
| the whole round | 11.9 ms | 20.1 ms |
| plain JSON, parse + write | | 3.0 ms |

Each way is one walk that decodes (or encodes) and checks together; data
that fits goes out without being read back; moments and days are read by
position; the browser turns JSON into data and back in one walk. Most of
what is left is building Clojure's maps.

## Tests

```sh
clojure -X:test                                   # the JVM
npm install && npx shadow-cljs compile test       # the browser, on node
```

Why it is the way it is: [`decisions/`](decisions/README.md).

MIT license.
