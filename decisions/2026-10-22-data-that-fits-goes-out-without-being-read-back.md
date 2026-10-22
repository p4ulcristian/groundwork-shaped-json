# Data that fits goes out without being read back

> Changes in part [wire is for the app's edges, in five functions](2026-10-22-wire-is-for-the-apps-edges.md): the way out reads back only data that doesn't fit.

**Decision:** `turn-shaped-data-into-json-data-or-errors` checks the shaped data against the closed shape first. Data that fits goes out as its JSON data at once. Only data that doesn't fit is read back as the other side will read it, and checked: a value that goes out right (a `js/Date`, a `java.sql.Timestamp`) passes, anything else is refused as before.

For that to hold, every value of wire's types that fits writes text the other side reads as that value. A moment and a day fit only in the years the grammar writes, 0000 to 9999, on both sides. A test generates data for shapes of every kind and checks that what fits reads back fitting and writes the same text again.

**Why:** reading back was the largest part of the way out: of 7.6 ms for 1000 orders on the JVM, the read-back took 4.6. Data a handler built from its own rows fits nearly always.

**The price:** the promise that what leaves reads back rests on wire's types and the generated test, not on reading every message back. A moment past 9999 no longer fits a shape: it never could travel.

**Instead of:**
- reading every message back: a third of the cost of every answer
- checking the JSON data against a second shape made for the text: every malli type would need a twin, and the two could drift
