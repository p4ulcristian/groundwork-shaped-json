# Shaped data is data that fits its shape

> **Changed in part** by [The family and its composer are groundwork](https://github.com/p4ulcristian/groundwork/blob/Starting-from-the-bottom/decisions/2026-10-23-the-family-and-its-composer-are-groundwork.md) (2026-10-23): manor, the family, and house, the composer, are both named groundwork now.

> **Changed in part** by [wire is for the app's edges, in five functions](2026-10-22-wire-is-for-the-apps-edges.md) (2026-10-22): `does-data-fit-shape?` is gone; the way out is `turn-shaped-data-into-json-data-or-errors`, read back and checked against the closed shape.

> Changes in part house's decision "A name is a sentence that starts with a verb": wire's names say "shaped data" and "data", not "value".

**Decision:** wire names the three forms data takes on its way, and its api says them:

- **json text**: one string, what travels over HTTP: `{"order/total":"120.50"}`
- **json data**: that text read into Clojure maps and lists; ids, decimals and dates are still text: `{:order/total "120.50"}`
- **shaped data**: data whose every value is what its shape names: a UUID, a BigDecimal, a LocalDate (in the browser: an id's text, big.js, an Instant or a Day): `{:order/total 120.50M}`

On the way in, `turn-json-text-into-data` then `turn-json-data-into-shaped-data-or-errors` (it answers `{:shaped-data …}` or `{:errors …}`). On the way out, `turn-shaped-data-into-json-data` then `turn-data-into-json-text`. The two checks take any data: `does-data-fit-shape?`, `find-errors-in-data-for-shape`.

**Why:** the name says the one thing that is true of the result and not of json data: it fits the shape, every value is what the shape names. Shaped data is Clojure data in memory, not a text format like EDN.

**Instead of:**
- value: says nothing about which form
- typed data: json data has types too (its strings are a type)
- clojure data: json data is Clojure data too
- decoded data: says what happened to it, not what it is
- original data: true only when the sender used wire too
