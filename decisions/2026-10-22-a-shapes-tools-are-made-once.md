# A shape's tools are made once

**Decision:** the first time a shape is used, wire makes everything it needs from it (the malli schema, its decoder and encoder, its checks, the closed checks of the way out) and keeps them, by the shape, for as long as the app runs. Every later call with an equal shape takes them from there. The five functions stay as they are.

**Why:** a shape never changes while an app runs, but each call built all of it again and threw it away, on every ask, answer and job, on the server and in the browser. Kept, one way out plus one way in of an order with five lines went from 77 to 18 microseconds on the JVM.

**The price:** the kept tools stay in memory: one entry per distinct shape. An app that made a new, different shape on every call would grow it without end; shapes are written once in an app's code, so there are as many entries as shapes it has.

**Instead of:**
- an api to make the tools first and pass them in: every caller keeps them itself, and the api grows
- a cache that forgets: shapes are few, and forgetting one only makes it again
