# Beacon end-to-end tests (Playwright)

Separate from the Angular tests: own `package.json`, run against a throwaway stack
(Testcontainers database, invite-only off, so sign-up works).

## Run

1. API on 8081: the `api (testcontainers db)` entry in `.claude/launch.json`
   (`cd backend && set BEACON_INVITE_ONLY=false&& mvnw.cmd spring-boot:test-run -Dspring-boot.run.arguments=--server.port=8081`).
2. Frontend on 4201: `npm --prefix frontend start -- --port 4201 --proxy-config proxy.testcontainers.conf.json`.
3. First time only: `npm --prefix e2e install && npm --prefix e2e run install:browsers`.
4. `npm --prefix e2e test`

`E2E_BASE_URL` overrides the default `http://localhost:4201`. Failed tests keep a trace and
screenshot in `e2e/test-results/` (gitignored); open a trace with `npx playwright show-trace <trace.zip>`.

## How it works

- `tests/fixtures.ts`: a `signUp` helper, and a worker-scoped `account` fixture that signs up a fresh
  user (unique email) and shares its `storageState`, so signed-in tests skip the form.
- Locators are roles and labels (`getByRole`, `getByLabel`); they survive restyling and double as an
  accessibility check. No `data-testid` was needed.
- Each run uses new accounts, so nothing depends on earlier data or on a real database.
