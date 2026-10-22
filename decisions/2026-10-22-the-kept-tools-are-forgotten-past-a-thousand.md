# The kept tools are forgotten past a thousand shapes

> Changed in part by [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md) (2026-10-22, v0.2.0): how many are kept is the setting :most-shapes-kept.

> Changes in part [A shape's tools are made once](2026-10-22-a-shapes-tools-are-made-once.md): the kept tools no longer grow without end.

**Decision:** wire keeps at most 1000 shapes' tools. When a new shape would be the 1001st, all kept tools are forgotten and each is made again the next time it is used.

**Why:** a shape made on every call, one holding a function (`[:fn …]`) or, in the browser, a regex literal, is a new shape each time: 1000 calls kept 1000 entries, without end. An app has tens or hundreds of shapes; past a thousand, one is being made per call.

**The price:** an app that makes a shape per call pays for making its tools on every call (about 220 µs on the JVM), and every 1000 calls the real shapes are made once more.

**Instead of:**
- keeping the most recently used (an LRU): needs order kept on every read, which costs every call to save a rare one
- stopping keeping new shapes when full: the app's real shapes first used after the limit would never be kept
