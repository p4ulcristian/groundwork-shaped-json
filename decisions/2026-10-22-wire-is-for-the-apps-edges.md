# wire is for the app's edges, in five functions

> Changed in part by [Data that fits goes out without being read back](2026-10-22-data-that-fits-goes-out-without-being-read-back.md) (2026-10-22): only data that doesn't fit is read back.

> Changed in part by [The way in names every key too](2026-10-22-the-way-in-names-every-key-too.md) (2026-10-22): the way in is checked against the closed shape too.

> Changes in part [Shaped data is data that fits its shape](2026-10-22-shaped-data-is-data-that-fits-its-shape.md): `does-data-fit-shape?` is gone, and the way out is `turn-shaped-data-into-json-data-or-errors`, read back and checked against the closed shape.

**Decision:** wire is used where data leaves the app and has to come back as it was: the browser (transport), the job queue (background-jobs), the settings and the page (main). The JSON Groundwork writes for Postgres or reads from it (access's `groundwork.asking`, sync's change messages) is the library's that writes the SQL reading it: that library uses jsonista itself, with the keys as Postgres names them.

wire's api is five functions, the same on both sides:

- `turn-json-text-into-data`, `turn-data-into-json-text`: text and JSON data
- `turn-json-data-into-shaped-data-or-errors`: the way in, `{:shaped-data …}` or `{:errors …}`
- `turn-shaped-data-into-json-data-or-errors`: the way out, `{:json-data …}` or `{:errors …}`; the JSON data is read back as the other side will read it and checked against the shape with every map closed, so a key the shape doesn't name never leaves, and a value that only goes out right passes (a `js/Date` for a moment, in the browser)
- `find-errors-in-data-for-shape`: why data doesn't fit; nil when it fits

**Why:** wire holds one idea: data that crosses an edge comes back as what it was, or says why it can't. access and sync used only its plain JSON text, for formats their own SQL reads and writes; that made wire look needed everywhere. The way out was closed only for answers, through a sixth function transport alone called; closing every way out is one rule, and nothing unnamed leaves through a job or an ask either.

**The price:** a background job's params or a browser ask's params with a key the shape doesn't name, accepted before, are refused. Every message out is read back once more.

**Instead of:**
- database writing and reading that JSON: its rule is for rows (keys shaped entity__field become `:entity/field`, other keys stay text), and would change the keys sync's change messages carry
- closing only answers: today's behaviour, with `turn-shape-into-closed-shape` kept for transport alone
- checking the shaped data before it goes out: a `js/Date` or a UUID value given in the browser, which goes out as the right text, would be refused
- one function per direction from text to shaped data: transport reads one text holding many asks, each with its own shape, so the two stops stay apart
