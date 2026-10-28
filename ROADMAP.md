# Beacon (Java) Roadmap

A job-search assistant: Spring Boot 4 + Postgres backend, Angular 22 frontend.
Rebuilt from the .NET Beacon, then extended.

## Done

| # | What | Commit |
|---|------|--------|
| 1 | Scaffold: Spring Boot, Postgres (pgvector image), Flyway, `JobApplication` entity, Testcontainers | ac1d0ee |
| 2 | Applications API: create/get/list, records, Bean Validation, ProblemDetail errors | fac5290 |
| 3 | Status transitions (`EnumMap` state machine) + optimistic locking (`@Version` -> 409) | 4369285 |
| 4 | Angular frontend: read-only kanban board, signals, dev proxy | caffe5b |
| 5 | Move buttons with conflict handling, new-application form, server validation errors | e6b25db, dffd777 |
| - | Offer outcomes, editing, deleting, application detail page | ef4a128 |
| - | Status history (`application_status_changes`) shown on the detail page | 42ce88a |
| - | Stale-application flags, apply-by and follow-up dates | a8eddf8, 7114188 |
| - | Job posting search across public job APIs (Adzuna, USAJOBS, Remotive, RemoteOK, Jobicy, Himalayas) | 9ce4f46, 6f84160, dd171ac |
| - | Posting details, documents, posting capture, printable posting / PDF | df3de91, 7b823e8, 8926cf6, 943fa38 |
| - | AI cover letters (Claude and OpenAI) | df3de91, 81da3bc |
| - | User accounts (Spring Security), per-user ownership, per-user encrypted API keys, provider guide | 3c31d80, d35c5b5, 59eb080 |
| X1 | OpenAPI spec with springdoc, generated Angular client (ng-openapi-gen) behind the existing `*Api` services | 9ebee81 onward |
| R0 | Companies per user: V7 backfill, find-or-create by normalized name, undisclosed employers, type-ahead, company pages | caa8455 |
| R1 | People with a role per application, recruiter agencies ("via"), employment type, people pages | 819c7e6 |
| - | Stitch redesign (Tailwind v4 design system on every screen) and dark mode | 42f58e5, 85c7236, 7c039cc |
| R2 | Contact log per person and application, duplicate-pitch warning | after 7c039cc |

## Next: Companies and people

### R0. Companies (done, except the "Later" items)
- Built as planned below. Pre-account rows with no owner were deleted by V7; job search results without a company save as undisclosed. R1 adds the "via <agency>" part and people/contacts on the company page.
- New `companies` table, owned per user: name, normalized name (lowercased, "Inc"/"LLC"/"Corp" stripped), website, email domains, notes.
- `CompanyType`: `EMPLOYER`, `AGENCY`, `BOTH` (some firms place contractors and hire directly).
- `job_applications.company_id` (the employer) replaces the free-text `company` column.
  - Nullable for an undisclosed client; the board shows "Undisclosed client (via <recruiter's agency>)".
- Flyway migration backfills companies from the distinct `company` strings already in `job_applications`, then drops the old column.
- Find-or-create by normalized name; job search results and posting capture create companies automatically.
- Application form gets a type-ahead company picker instead of a text box.
- Company page: every application, person and contact for that company.
- Later: "merge these two companies" for duplicates.
- Later: optional careers-board id per company (Greenhouse, Lever, Ashby). Those boards need no API key, so Beacon can list a company's open roles directly (a keyless job source per company).
- Why: people, email domain matching (E3) and the duplicate-pitch warning (R2) all need one company id instead of free-text names that drift apart.

### R1. People (recruiters, hiring managers, interviewers, referrals) (done)
- Built as planned below. A person also has a usual role, which a new link starts with. A company first named by an agency or RPO recruiter is created as an agency. Undisclosed roles show "via <agency>" from a linked recruiter.
- New `people` table, owned per user (`owner_id`, same as applications and documents), instead of a recruiters-only table.
- Fields: name, `company_id`, title, email, phone, LinkedIn URL, notes, timestamps.
- `PersonRole`: `RECRUITER`, `HIRING_MANAGER`, `INTERVIEWER`, `REFERRAL`, `OTHER`.
- Recruiter-only field: `RecruiterType` (`IN_HOUSE`, `AGENCY`, `RPO`, `INDEPENDENT`), null for other roles.
- `application_people` join table (application, person, role on that application), so one person can be the recruiter on one application and a referral on another.
- On `job_applications`:
  - `employment_type`: `FULL_TIME`, `CONTRACT`, `CONTRACT_TO_HIRE`, `C2C`, `PART_TIME`
    (contract/consultant describes the role, not the recruiter)
- CRUD API, people list/detail pages, "add person" on the application detail page.
- Learning focus: JPA relationships (`@ManyToOne`, join entity for many-to-many with extra columns), avoiding N+1 queries (fetch join / `@EntityGraph`).

### R2. Contacts (outreach log) (done)
- Built as planned below. Logging a contact about an application also puts the person on it. Deleting an application keeps its contacts in the person's log; deleting a person deletes their log. The duplicate-pitch warning shows on the application page and matches company + normalized title ("Sr." = "Senior") within 6 weeks; an undisclosed employer can't be matched. "Log a call" is the quick-add.
- `contacts`: person, channel (`EMAIL`, `SMS`, `LINKEDIN`, `PHONE`, `OTHER`), direction (in/out), date, raw text, optional link to the application it relates to.
- Paste-in form for LinkedIn messages and texts (LinkedIn has no personal messaging API; texts have no general API). Manual fields for now; AI extraction arrives in E3.
- Quick-add for phone calls.
- Duplicate-pitch warning: a second recruiter pitching what looks like the same role (same `company_id` + title within a few weeks). Double submission can disqualify a candidate.

## Then: Interviews and timeline

### I1. Interviews
- `interviews`: application, date/time + time zone, type (`PHONE_SCREEN`, `TECHNICAL`, `PANEL`, `ONSITE`, `OTHER`), interviewers (people from R1), location or meeting link, prep notes, debrief notes, outcome.
- Board shows upcoming interviews ("Interview tomorrow 2:00").
- `.ics` download per interview so it can go on any calendar.
- Stale flag for `INTERVIEWING` comes from the last or next interview date instead of days in the status (interview processes can run for months).

### I2. Application timeline
- One chronological feed on the detail page: status changes, notes, documents, contacts (R2), interviews (I1), and later emails (E) and inbox accepts.
- Mostly a combined read model (one query or a union of small ones) plus an Angular view; no new data.

## Then: Email integration

### E1. Status change source + revert
- Extend `application_status_changes` with `source` (`USER`, `EMAIL`) and the source email id.
- `revert(changeId)`: may undo an email-caused change even when the reverse isn't a normal transition (e.g. out of `REJECTED`), but only if it's still the latest change.
- Builds on the existing history table rather than a new one.

### E2. Email ingestion
- `EmailSource` interface; IMAP implementation first (Jakarta Mail), scheduled poll.
- Per-user mailbox settings; credentials encrypted with the existing `KeyCipher`.
- Store Message-ID, sender, subject, trimmed body, received date. Dedupe on Message-ID.
- Store as little content as needed: snippet + reference, not full mailboxes.

### E3. Classification and extraction
- Rules first: known sender domains (Greenhouse, Lever, Workday, LinkedIn/Indeed alerts), subject keywords.
- Then AI with structured output into a Java record, using the user's own provider keys (same path as cover letters):
  type (`STATUS_UPDATE`, `JOB_POSTING`, `RECRUITER_OUTREACH`, `IRRELEVANT`), company, role, suggested status, posting URL, recruiter details, application deadline, confidence.
- An extracted deadline ("applications close Nov 15") fills the application's apply-by date.
- Recruiter outreach: match the person (R1) by sender address or propose a new one with the `RECRUITER` role. The R2 paste-in form gets the same extractor.
- Status updates: match to an application by sender domain against `companies.email_domains`, falling back to company name.
- A folder of saved sample emails as an evaluation set to measure accuracy when prompts change.

### E4. Inbox queue
- `inbox_items`: type, state (`PENDING`, `ACCEPTED`, `DISMISSED`), extracted fields, link to source email or contact.
- Angular Inbox page: accept (opens the application form pre-filled, links the recruiter as a person) or dismiss.
- Built generically: job-search matches (J3) reuse the same queue with their own item type.
- Dedupe postings that arrive from several alert emails.

### E5. Status-update notifications
- Confident match + allowed transition: apply automatically, record as `source = EMAIL`, notify.
- Notification actions: Undo (E1 revert) and Wrong application (revert, then apply to the chosen one).
- Low confidence or disallowed move: never auto-apply; goes to the inbox as a suggestion.
- Delivery over SSE; header bell with unread count.
- Setting to send everything to the inbox instead of auto-applying.

### E6. (Optional) Gmail API
- OAuth sign-in and push via Gmail `watch` + Pub/Sub instead of polling.
- Gmail read scopes are restricted: fine in Google's testing mode for a few users; a public release needs Google verification plus a paid security assessment.

## Then: Daily job search with fit scores

Reuses the inbox queue from E4: matches land there to accept or dismiss, same as email postings.

### J0. More job sources (per-user keys)
- Each new source is a `ProviderCatalog` entry plus a `JobSource` adapter; users add their own key under Settings, and the source then shows up in Find jobs.
- In order: **JSearch** (best paid pick), **SerpApi Google Jobs** (has a free tier), **Jooble** (free key on request).
- Not possible: LinkedIn and Indeed have no job-search API.

### J1. Resume profiles
- Builds on the existing library documents (`kind = RESUME`, no application): no new file storage.
- Add a label ("Backend Java", "Test Engineering"...), a default flag, and cached extracted text (`DocumentText`).
- Optional short summary per resume: target titles, key skills, years of experience. AI-suggested from the resume, user-edited.
- Cover letters and fit scoring pick from these resumes.

### J2. Fit scoring
- Compare a posting with a resume and return a structured result, not just a number:
  score (0-100), matched skills, gaps, dealbreakers (location, salary floor, clearance, sponsorship), one-paragraph rationale.
- Uses the user's own AI provider keys, same path as cover letters.
- The same call extracts an application deadline from the description when the job source gave none; it fills apply-by.
- Cost control: cheap pre-filter first (keywords, then pgvector embedding similarity from the backlog item), LLM only on the top candidates.
- Cache scores per (posting fingerprint, resume version) so re-runs don't re-pay.
- Show the score on job search results and on existing applications; suggest which resume fits a posting best.
- Evaluation set: a handful of postings hand-labelled good/okay/poor fit, to check scoring changes.

### J3. Saved searches + daily runs
- `saved_searches` per user: keywords, location, remote, salary minimum, sources, resume to score against, minimum score, active flag.
- Scheduled daily run (Spring `@Scheduled`), in the user's time zone, through the existing job-source integrations.
- Dedupe against existing applications, earlier matches and dismissed postings
  (fingerprint: source + external id, falling back to normalized company + title + location).
- Matches at or above the minimum score go to the inbox queue as `JOB_SEARCH_MATCH`, with the fit breakdown shown on the item.
- Accept creates a `SAVED` application with posting details, company (R0) and the chosen resume attached.
- The posting's close date (USAJOBS and Himalayas give one) becomes the application's apply-by date. Saving a Find jobs result by hand should do the same today.
- Dismissals are remembered (and can feed back into the minimum score later).
- Optional daily summary notification: "6 new matches, best 87".

## Then: AI helpers and insights

### A1. Interview prep
- From the posting, the chosen resume (J1) and fit gaps (J2): likely questions, skills to brush up on, and which experiences fit as STAR stories.
- Saved with the interview (I1) so it can be edited.

### A2. Tailored resume suggestions
- Turn J2's gaps into concrete edit suggestions for one posting ("move the Spring Boot project up", "mention Testcontainers").
- Suggestions only; the resume file is never rewritten automatically.

### A3. Stats
- Funnel by source (applied -> interview -> offer), response rate, average time in each stage, ghosting rate.
- Which resume variant (J1) gets more interviews.
- Learning focus: SQL aggregation, window functions, a chart page in Angular.

### A4. Offer comparison
- Side-by-side total compensation: base, bonus, equity, PTO, benefits, commute vs remote. Builds on the existing offer outcomes.

### A5. Weekly digest
- Email (or in-app) summary: follow-ups due, stale applications, upcoming interviews, new job matches.

## Board (any time)

### B1. Search, filter and drag-and-drop
- Search box and filters on the board (company, role, source, remote, has follow-up due).
- Drag cards between columns with Angular CDK; only allowed transitions accept a drop, with the same 409 conflict handling as the move buttons.

## Engineering (any time; earlier is cheaper)

### X1. OpenAPI + generated Angular client (done)
- Springdoc serves the spec at `/v3/api-docs`; a copy lives in `frontend/openapi.json`, and `OpenApiSpecTests` fails when the two differ.
- `npm run generate:api` (also run before `start`, `build` and `test`) generates `src/app/api/` (git-ignored); the app's `*Api` services call it, and the `*.model.ts` files alias its types.
- After an API change: `./mvnw test -Dtest=OpenApiSpecTests -Dopenapi.update=true`, then rerun the frontend.
- Nullable fields carry `@Nullable` in the Java records. TypeScript 6 has strict mode on by default, so nulls are checked.

### X2. Observability
- Micrometer metrics, structured JSON logs, tracing (OpenTelemetry).
- Prometheus + Grafana in Docker Compose; dashboards for API latency, AI call cost/latency, job-source errors.
- Concepts carry over to Datadog.

### X3. End-to-end tests
- Playwright: log in, add an application, move it, upload a resume, accept an inbox item.
- Runs in CI against the compose stack.

### X4. Deploy to the home server
- Containerize API and frontend (nginx serves Angular and proxies `/api`), full compose stack, CI pipeline building images.
- HTTPS, automated Postgres backups with a tested restore.

### X5. Data export and account deletion
- Export everything a user owns (JSON + documents as a zip); delete account and all data.
- Becomes important once mailbox credentials and email snippets are stored.

## Backlog (from the original plan)

- Semantic search over documents with pgvector embeddings (the database image already includes pgvector). Also the pre-filter for J2.
- "Ask Beacon": RAG answers with citations, streamed over SSE.
- Resilience: retries / circuit breaker on AI and job-source calls (Resilience4j), rate limiting, readiness probes.
