# Moments and days have one grammar on both sides

> Adds to [Both sides read the same JSON, and refuse the same](2026-10-22-both-sides-read-the-same-json.md).

**Decision:** wire reads a moment and a calendar day by its own grammar, the same on the JVM and in the browser, before java.time or goog.date see the text:

- a moment: `2026-10-12T10:00:00Z` or `2026-10-12T12:00:00.5+02:00`. Seconds always; a fraction of 1 to 9 digits; `Z` or an offset up to ±18:00; upper-case `T` and `Z`; years 0000 to 9999; hours to 23, seconds to 59.
- a day: `2026-11-15`, years 0000 to 9999.

Text outside it is refused on both sides with the same error, and a moment or day outside those years doesn't fit its shape. A time's `:min` and `:max` hold in the browser too, by `compare`. Going out on the JVM, a `java.util.Date` (and so a `java.sql.Timestamp`, as JDBC gives a moment) is its moment and a `java.sql.Date` its day.

**Why:** the JVM read by java.time's parsers and the browser by its own pattern, and they disagreed: the browser took `10:00Z` without seconds and `+18:30`, the JVM took lower-case `t` and `z`, a leap second (`23:59:60Z`, read as `:59`) and years past 9999. java.time and Java print every moment by this grammar, so nothing wire writes falls outside it.

**The price:** text a person or another tool wrote with no seconds or a lower-case `z` is refused. A year past 9999 can't travel.

**Instead of:**
- java.time's parsers on the JVM and a pattern copying them in the browser: their leniencies are many and undocumented; a copy would drift
- Temporal in the browser: not in stable Safari yet (late 2026)
