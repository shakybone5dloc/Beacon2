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

## Next: Recruiters

### R1. Recruiters
- New `recruiters` table, owned per user (`owner_id`, same as applications and documents).
- Fields: name, company (agency, or employer for in-house), email, phone, LinkedIn URL, notes, timestamps.
- `RecruiterType`: `IN_HOUSE`, `AGENCY`, `RPO`, `INDEPENDENT`.
- On `job_applications`:
  - nullable `recruiter_id` (who submitted you): JPA `@ManyToOne`
  - `employment_type`: `FULL_TIME`, `CONTRACT`, `CONTRACT_TO_HIRE`, `C2C`, `PART_TIME`
    (contract/consultant describes the role, not the recruiter)
  - `client_company`: agencies often hide the end client at first
- CRUD API, recruiter list/detail pages, recruiter picker on the application form.
- Learning focus: JPA relationships, avoiding N+1 queries when the board loads recruiters (fetch join / `@EntityGraph`).

### R2. Recruiter contacts (outreach log)
- `recruiter_contacts`: recruiter, channel (`EMAIL`, `SMS`, `LINKEDIN`, `PHONE`, `OTHER`), received date, raw text, optional link to the application it led to.
- Paste-in form for LinkedIn messages and texts (LinkedIn has no personal messaging API; texts have no general API). Manual fields for now; AI extraction arrives in E3.
- Quick-add for phone calls.
- Duplicate-pitch warning: a second recruiter pitching what looks like the same role (same client + title within a few weeks). Double submission can disqualify a candidate.

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
  type (`STATUS_UPDATE`, `JOB_POSTING`, `RECRUITER_OUTREACH`, `IRRELEVANT`), company, role, suggested status, posting URL, recruiter details, confidence.
- Recruiter outreach: match the recruiter by sender address or propose a new one. The R2 paste-in form gets the same extractor.
- Status updates: match to an application by company name + sender domain.
- A folder of saved sample emails as an evaluation set to measure accuracy when prompts change.

### E4. Inbox queue
- `inbox_items`: type, state (`PENDING`, `ACCEPTED`, `DISMISSED`), extracted fields, link to source email or contact.
- Angular Inbox page: accept (opens the application form pre-filled, links the recruiter) or dismiss.
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

## Backlog (from the original plan)

- Semantic search over documents with pgvector embeddings (the database image already includes pgvector).
- "Ask Beacon": RAG answers with citations, streamed over SSE.
- Resilience: retries / circuit breaker on AI and job-source calls (Resilience4j), rate limiting, readiness probes.
- Containerize API and frontend (nginx), full compose stack, CI pipeline.
