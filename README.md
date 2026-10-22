# wire

Data that crosses an edge comes back as what it was, on the JVM and in the
browser. JSON knows only text, numbers, true/false, lists and maps; a
[malli](https://github.com/metosin/malli) shape says what every value
really is, so ids, exact decimals, days, moments and whole numbers too big
for JavaScript travel as text and come back as their real types. Both sides
read the same JSON and refuse the same.

```clojure
io.github.p4ulcristian/wire {:git/tag "v0.1.2" :git/sha "…"}   ; the sha: git ls-remote https://github.com/p4ulcristian/wire v0.1.2
```

Needs only malli and jsonista. One namespace for both languages:
`wire.api` is a `.cljc`; `wire.json`, `wire.time` and `wire.types` are a
`.clj` and a `.cljs` each, and each language loads its own.

## The api: five functions

| function | does |
|---|---|
| `turn-json-text-into-data` | JSON text → JSON data; `"post/title"` becomes `:post/title` |
| `turn-data-into-json-text` | JSON data → JSON text; keys keep their namespace |
| `turn-json-data-into-shaped-data-or-errors` | the way in: `{:shaped-data …}` or `{:errors …}`, checked with every map closed: a key the shape doesn't name never comes in |
| `turn-shaped-data-into-json-data-or-errors` | the way out: `{:json-data …}` or `{:errors …}`, read back as the other side will and checked with every map closed: a key the shape doesn't name never leaves |
| `find-errors-in-data-for-shape` | why data doesn't fit a shape; nil when it fits |

```clojure
(require '[wire.api :as wire])

(def order [:map [:order/id :uuid] [:order/total :decimal] [:order/day :time/local-date]])

(wire/turn-shaped-data-into-json-data-or-errors order {:order/id    #uuid "1d000000-0000-4000-8000-000000000001"
                                                      :order/total 120.50M
                                                      :order/day   (java.time.LocalDate/parse "2026-11-15")})
;=> {:json-data #:order{:id "1d000000-0000-4000-8000-000000000001" :total "120.50" :day "2026-11-15"}}

(wire/turn-shaped-data-into-json-data-or-errors order {:order/id #uuid "1d000000-0000-4000-8000-000000000001"
                                                      :order/total 120.50M
                                                      :order/day (java.time.LocalDate/parse "2026-11-15")
                                                      :user/password-hash "x"})
;=> {:errors {:user/password-hash ["disallowed key"]}}

(wire/turn-json-data-into-shaped-data-or-errors order (wire/turn-json-text-into-data json-text))
;=> {:shaped-data #:order{:id #uuid "1d000000-…" :total 120.50M :day #object[java.time.LocalDate "2026-11-15"]}}
```

## In the browser

- an id stays its text
- a whole number is a number within JavaScript's safe range and its text
  beyond it (to the ends of a JVM long); a number beyond the range may
  have been rounded, so it doesn't fit `:int`
- a moment is a `wire.time/Instant` and a calendar day a `wire.time/Day`,
  built on Closure's `goog.date`; both print as the ISO text they came as
- a decimal is its text, or exact with big.js: require `wire.big` (and add
  `big.js` to the app's package.json)

## Tests

```sh
clojure -X:test                                   # the JVM
npm install && npx shadow-cljs compile test       # the browser, on node
```

Why it is the way it is: [`decisions/`](decisions/README.md).

MIT license.
