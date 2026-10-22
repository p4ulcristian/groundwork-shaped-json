# The way in names every key too

> Changes in part [wire is for the app's edges, in five functions](2026-10-22-wire-is-for-the-apps-edges.md): the way in is checked against the closed shape too.

**Decision:** `turn-json-data-into-shaped-data-or-errors` checks the shaped data against the shape with every map closed, as the way out does. A key the shape doesn't name, at any depth, answers `{:errors {… ["disallowed key"]}}`. A map the shape leaves open by name, `[:map {:closed false} …]`, stays open. `find-errors-in-data-for-shape` stays open: it checks any data, not what crossed an edge.

**Why:** the way in is where data from outside arrives: a browser's ask, a job's params. An extra key passed through, so `{:order/id … :order/shop-id …}` reached a handler with a shop id its shape never named, and a handler that saves the params as they came would save it. A sender that uses wire sends only named keys already, since its way out is closed; only a sender that doesn't, or one that means harm, adds keys.

**The price:** an ask with a key its shape doesn't name, accepted before, is refused. Each shape's tools no longer keep the open check for the way in; the closed one does both ways.

**Instead of:**
- dropping unnamed keys silently (malli's strip-extra-keys): the sender never learns its ask was not what it thought
- leaving the way in open: every handler would have to pick its keys itself
