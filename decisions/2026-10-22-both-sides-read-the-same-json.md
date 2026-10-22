# Both sides read the same JSON, and refuse the same

> Added to by [Moments and days have one grammar on both sides](2026-10-22-moments-and-days-have-one-grammar.md) (2026-10-22).

> Changes in part [JSON and malli on the wire](2026-10-12-json-and-malli-on-the-wire.md): in the browser, a whole number too big for JavaScript stays its text, as an id does.

**Decision:** the JVM and the browser accept the same JSON for a shape, and refuse the same.

- An `:int` travels as a number within JavaScript's safe range (±9007199254740991), and as its text beyond it, to the ends of a JVM long. On the JVM the text becomes a long. In the browser it stays its text, as an id does; `:min` and `:max` hold for it. A number beyond the range is refused on both sides: a browser may already have rounded it. Text for a number within the range, `"42"`, is refused on both sides: such a number travels as a number.
- A `:decimal` travels as plain text, `-?digits(.digits)?`. Text with an exponent, `"1E+3"`, is refused on both sides; the JVM read it before.
- `turn-data-into-json-text` writes a UUID value as its text in the browser too, as jsonista does on the JVM; it wrote ClojureScript's insides.

**Why:** a whole number from Postgres's `bigint` beyond 2^53 went out as text and the browser refused it as "should be an integer", so an answer holding one failed. A shape is one promise; JSON a side accepts but the other refuses breaks it at the first app with both sides.

**The price:** a browser never holds a big whole number as a number; it shows or sends back its text. JSON a JVM sender wrote by hand with `"42"` or `"1E+3"` is refused.

**Instead of:**
- a JavaScript BigInt in the browser: exact, but `=`, `str` and JSON.stringify don't treat it as ClojureScript treats numbers, and every screen would meet a new type
- text for every `:int`: plain numbers, most of them, would read as text in every tool
