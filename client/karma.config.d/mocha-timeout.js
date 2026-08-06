// karma-mocha's default per-test timeout is 2000ms, which the heavier fuzz/property tests in this
// module (e.g. CoverageStoreFuzzTest) can legitimately exceed in a browser environment. Raise it.
// Must be set via config.client.mocha, not `testTask { useMocha { timeout = ... } }` - that DSL
// configures the Node.js test runner and would silently switch execution away from the browser.
config.client.mocha = { timeout: 10000 };
