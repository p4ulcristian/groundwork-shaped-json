# wire is its own library, one namespace for both languages

**Decision:** wire is a library of its own, in its own public repository, under the MIT license; an app or a framework takes it from git by tag. It was house's library `groundwork.server.wire` and `groundwork.browser.wire` (house was named Groundwork then); now it is one namespace, `wire`, for both languages: `wire.api` is a `.cljc` with no reader conditionals, and `wire.json`, `wire.time` and `wire.types` are a `.clj` and a `.cljs` each, so each language loads its own. `wire.big` (big.js) is the browser's only. Its own `deps.edn` names only malli and jsonista, and its tests run with nothing else on the classpath.

**Why:** wire knows nothing of house; it holds one idea any Clojure app with a browser has. The two api files were the same but for their namespace names; one file can't drift. Tests that run with only wire's deps prove it needs nothing more.

**The price:** a change to wire is a new tag, and house moves to it on purpose. house's rule that a namespace says its side, and that no file is `.cljc`, does not hold for wire.

**Instead of:**
- staying a folder in house's tree: no app can take it alone, and nothing proves its deps
- two libraries, a server one and a browser one: the same api written twice
- a name under house's (`groundwork` then): wire isn't house's
