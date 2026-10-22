# Every value that sets behaviour is a setting; no hidden defaults

> Changes in part [wire is for the app's edges, in five functions](2026-10-22-wire-is-for-the-apps-edges.md), [The kept tools are forgotten past a thousand shapes](2026-10-22-the-kept-tools-are-forgotten-past-a-thousand.md) and [Browser values are typed, and small](2026-10-19-browser-values-are-typed-small.md). Since v0.2.0.

**Decision:** an app makes a wire from settings, `(make-wire-from-settings settings)`, and every function takes the wire first. The settings are checked by malli against `settings-shape`, closed, every key required; settings that don't fit throw `:wire/bad-settings` with every problem by key. No function fills in a value: no `:or`, no optional key, no shorter arity, no namespace that changes behaviour by being loaded.

```clojure
;; the JVM
{:most-shapes-kept 1000                      ; shapes' tools kept, then all forgotten
 :json-limits      {:max-nesting-depth         1000    ; Jackson's, written out
                    :max-number-length         1000
                    :max-string-length         20000000
                    :max-name-length           50000
                    :max-document-length       nil     ; nil: no limit
                    :max-token-count           nil
                    :max-writing-nesting-depth 1000}}

;; the browser
{:most-shapes-kept 1000
 :most-keys-kept   10000                     ; JSON keys' keywords kept, then all forgotten
 :exact-decimals   wire.big/big}             ; or nil: a decimal stays its text
```

- `:most-shapes-kept` was the fixed 1000 of [The kept tools are forgotten past a thousand shapes](2026-10-22-the-kept-tools-are-forgotten-past-a-thousand.md).
- `:most-keys-kept` was a fixed 10000. The kept keywords make reading 1000 orders take 3.8 ms instead of 9.6.
- `:json-limits` are Jackson's own limits, which wire left in effect unwritten.
- `:exact-decimals` was a switch thrown by loading `wire.big`: an app that required it anywhere got big.js everywhere. Now the app hands `wire.big/big` in, or nil.

The kept tools and keywords belong to the wire, not to the namespace: two wires keep their own.

**What stays fixed, and why:**

- **The time grammar** ([Moments and days have one grammar on both sides](2026-10-22-moments-and-days-have-one-grammar.md)): it is what java.time and wire write, so what the other side reads; a setting could make one side refuse what the other wrote.
- **JavaScript's safe integer range, ±9007199254740991**: a fact of every browser, where a whole number turns into text.
- **The error texts** (`"should be an id"`, `"should be at most 10"`, …): the same on both sides, what wire says; an app shows its own words.
- **Strict JSON**: no comments, no trailing commas, nothing after the value, as the browser's `JSON.parse`; keys as keywords with their namespace.
- **Every map closed both ways**, ids lower case, decimals as plain text: [the decisions](README.md) that make both sides read the same JSON.
- **The browser's JSON.parse limits**: the engine's; there is nothing to set.

**Why:** a value left to a default is a value the app doesn't show: a 20-million-character string accepted, a thousand shapes kept, big.js switched on by a require somewhere else. Each sets behaviour a user feels or an operator needs to read.

**The price:** every call takes a wire, `(turn-json-data-into-shaped-data-or-errors wire shape json-data)`, and an app writes out its settings once per side. Every caller of v0.1 changes.

**Instead of:**
- defaults an app may override: the value in effect stays invisible
- a global `configure!`: settings by a side effect, as `wire.big` was, and one wire per process
- only the values wire chose, leaving Jackson's: the app still couldn't read its string limit
- the error texts as settings: they are what both sides of wire say; the app shows its own
