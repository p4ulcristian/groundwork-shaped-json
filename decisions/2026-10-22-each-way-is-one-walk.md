# Each way is one walk, and malli says why

> Adds to [Data that fits goes out without being read back](2026-10-22-data-that-fits-goes-out-without-being-read-back.md).

**Decision:** wire makes each shape into one walk per way. The way in decodes and checks in the same pass; the way out checks and encodes in the same pass. The walk goes through maps (closed or open), vectors, tuples, `:maybe`, tagged unions and refs itself, and leaves every other part (a value, a set, a `:map-of`, an `:or`) to malli's own decoder, encoder and check for that part. When the walk refuses, wire takes malli's whole path as before, so errors are word for word the same, and data the walk wasn't sure of still gets malli's answer.

A test generates data for shapes of every kind, breaks it in six ways (a key too many, a key gone, text for numbers, numbers for text, a list for a vector, a key too many at every depth), and checks that the walk and malli alone answer the same, both ways, on both sides.

**Why:** the way in walked the data twice (malli's decoder, then its check) and the way out twice too. One walk took the browser's whole round of 1000 orders from 26 to 20 ms. On the JVM it changed nothing measurable (about 12 ms): malli's two walks there are already cheap.

**The price:** about 150 lines that must answer exactly as malli does; the generated test is what holds them to it. Making each value of the common types in the walk itself was tried and dropped: 0.3 ms, the rest of the time is building Clojure maps.

**Instead of:**
- the walk also making values (ids, times, decimals) itself: measured, too little to keep
- turning JSON text into shaped data in one step, skipping Clojure's plain maps: the next large saving in the browser (about 2.5 ms of 20), but a sixth function in the api, which [wire is for the app's edges](2026-10-22-wire-is-for-the-apps-edges.md) chose against
