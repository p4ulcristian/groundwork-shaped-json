# A database's JSON is [:json shape]; a decimal says its digits

> 2026-10-23, for cellar's [A column's type states Postgres's limit, in malli's words](https://github.com/p4ulcristian/cellar/blob/tidy-up/decisions/2026-10-23-a-columns-type-states-postgres-limit-in-malli-words.md), from Iron Rainbow's port (98 jsonb columns, 24 numeric(p,s)). Built on tidy-up, not released.

**Decision:** two words, the same on both sides:

```clojure
[:appointment-proposal/slots [:json [:vector [:map [:slot/day :time/local-date] [:slot/cents :int]]]]]   ; jsonb
[:inventory/min-qty          [:decimal {:precision 10 :scale 2}]]                                        ; numeric(10,2)
```

- **`[:json shape]`** is a value a database keeps as JSON, shaped as its one child says: it is that child, both ways.
- **`[:decimal {:precision p :scale s}]`** holds at most `s` digits after the point and `p - s` before it, as Postgres's `numeric(p,s)`: `"should be a decimal with at most 8 digits before the point and 2 after"`.

**Why:** a table's row shape (cellar's `turn-table-into-row-shape`) is checked at the app's edge: with the column's limit in its type, a value Postgres would refuse is refused as the shape's error, not answered as a 500 from the database.

**Instead of:**
- **`:any` for JSON columns**: cellar couldn't tell a jsonb column from another, and the shape would check nothing in it
- **cellar's own properties beside wire's words** (`{:postgres-type "numeric(10,2)"}`): the limit known to Postgres alone

**Settled while building:**

1. **`[:json shape]` closes its maps as anywhere**: `[:json [:vector :map]]` takes only empty maps; `[:json :any]` takes any JSON.
2. **A decimal with more places than `:scale` is refused**, where Postgres would round it: nothing is changed on its way in.
3. **`:precision` and `:scale` come together**, whole numbers, `0 <= scale <= precision`, precision at least 1; anything else, or another property on a `:decimal`, throws `:wire/bad-shape` when the shape is first used.
4. **Leading zeros before the point and trailing zeros after it don't count**, as in Postgres: `"00012.300"` fits `numeric(10,2)`.
