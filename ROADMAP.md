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
| R2 | Contact log per person and application, duplicate-pitch warning | d5f66c8 |
| I1 | Interviews with time zones, interviewers, prep/debrief, .ics download; board shows the next one | 26f6503 |
| I2 | Application timeline: status changes, people, contacts, interviews and documents in one feed | d6f1be2 |
| J4 | Company watchlist: Greenhouse, Lever and Ashby careers boards, open roles on the company page and in Find jobs | 1b2c5c2 |
| J1 | Resume profiles: labels, a default, saved text, and a short summary per library resume | f80a798 |
| J2 | Fit scoring: AI score, matches, gaps and dealbreakers on applications and Find jobs results; free skill match | after dd177fe |
| X6 | AI usage tracking: tokens and cost per call, this month by feature and model in Settings, optional monthly budget | after d3d5fb7 |
| J3 | Saved searches on a schedule, a Matches page, fit checks for the newest 20 per run within the AI budget | a6b2f37 |
| J0 | Google Jobs (SerpApi) and JSearch as keyed sources, reaching LinkedIn, Indeed and careers sites | ce3b972, 2672b04 |
| B1 | Board search and filters kept in the address, drag-and-drop between columns (Angular CDK) | after ff19ff9 |

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
- Later: optional careers-board id per company (Greenhouse, Lever, Ashby). Those boards need no API key, so Beacon can list a company's open roles directly. Now planned as the company watchlist (J4).
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

### I1. Interviews (done)
- Built as planned below. The start is saved as the wall-clock time and IANA zone it was agreed in, and shown in that zone plus the viewer's own. Interviewers not on the application yet are added to it. Cancelled interviews never count as next or last. A scheduled interview holds off the stale flag for any status; an Interviewing application with none ahead is flagged 7 days after its last one. The .ics file uses UTC times and keeps one UID per interview, so re-importing an edited one updates the event.
- `interviews`: application, date/time + time zone, type (`PHONE_SCREEN`, `TECHNICAL`, `PANEL`, `ONSITE`, `OTHER`), interviewers (people from R1), location or meeting link, prep notes, debrief notes, outcome.
- Board shows upcoming interviews ("Interview tomorrow 2:00").
- `.ics` download per interview so it can go on any calendar.
- Stale flag for `INTERVIEWING` comes from the last or next interview date instead of days in the status (interview processes can run for months).

### I2. Application timeline (done)
- Built as planned below, replacing the History section on the application page. `GET /api/applications/{id}/timeline` merges one small query per source in memory, newest first; each entry reuses the response its own endpoint returns. Upcoming interviews sit at the top. Emails and inbox accepts join it with E2/E4.
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

### J0. More job sources (per-user keys) (done, except Jooble)
- Built as two keyed sources: **Google Jobs** through SerpApi (`googlejobs`; free plan 250 searches a month) and **JSearch** on RapidAPI (`jsearch`; free plan 200 requests a month). Both read Google's job listings, so LinkedIn, Indeed, ZipRecruiter and company careers sites now reach Find jobs and saved searches.
- Each search reads one page (Google's 10 most relevant results, one credit) and is cached for six hours, so a weekday saved search uses about 22 a month. Remote and location go into the query as words, since Google's own filters don't work reliably through either API. Google Jobs gives relative dates ("3 days ago") and a salary line, which Beacon turns into a date and numbers when it's in dollars with a period; JSearch returns them parsed, plus an expiry date that becomes apply-by. The board each result came from (LinkedIn, Indeed...) shows as a tag.
- Both wait up to 30 seconds (other sources get 10) and retry once when the connection drops, but not when the source is just slow. A failed source now says why: its own message, the HTTP status, "took too long", or "Could not reach" when the connection failed.
- JSearch takes a key from OpenWeb Ninja (its maker, `api.openwebninja.com/jsearch`, `x-api-key`) or from RapidAPI; Beacon tries OpenWeb Ninja first and falls back to RapidAPI when the key is rejected.
- Both checked live with real keys (SerpApi, and OpenWeb Ninja for JSearch). Still to do: Jooble; more than one page per search (a setting, since each page costs a credit).
- Each new source is a `ProviderCatalog` entry plus a `JobSource` adapter; users add their own key under Settings, and the source then shows up in Find jobs.
- In order: **JSearch** (best paid pick), **SerpApi Google Jobs** (has a free tier), **Jooble** (free key on request).
- Not possible: LinkedIn and Indeed have no job-search API.

### J1. Resume profiles (done)
- Built as planned below, as `resume_profiles` beside each library resume (V12, which also gives resumes uploaded before it a profile, newest as default). The text is read once and kept; Word files and scans are marked unreadable with a hint. Cover letters start on the default and use the kept text. Still to do: the AI-suggested summary (titles, skills, years are entered by hand for now).
- Builds on the existing library documents (`kind = RESUME`, no application): no new file storage.
- Add a label ("Backend Java", "Test Engineering"...), a default flag, and cached extracted text (`DocumentText`).
- Optional short summary per resume: target titles, key skills, years of experience. AI-suggested from the resume, user-edited.
- Cover letters and fit scoring pick from these resumes.

### J2. Fit scoring (done, except the items below)
- Built as a **Fit** section on the application page (pick a resume, default first; "Check again" when the description or resume changed) and a **Check fit** button on each Find jobs result, scored against the default resume. Each result shows the score with a verdict (Strong 85+, Good 70+, Stretch 50+, Poor), what matches, the gaps, anything that could rule you out, and a short rationale.
- Structured output on both providers (OpenAI strict JSON schema, Claude output format), same key choice as cover letters. Scores are kept in `fit_scores` (V13) by a SHA-256 of the posting, resume and its text, and prompt version, so the same inputs never pay twice; saving a checked result to the board reuses its score. `application_fits` points each application at its latest score.
- A deadline the posting states fills an empty apply-by date; it never replaces one you set.
- Free first look: every Find jobs result shows how many of the default resume's skills (J1) it mentions, so you can choose what's worth an AI check. Nothing is scored automatically.
- The OpenAI and Claude calls now go through shared helpers (`OpenAiChat`, `ClaudeMessages`) that cover letters use too, so later AI features get the same error messages.
- Board cards show the latest score as a small bar in the top right ("fit ▬▬▬░ 72", coloured by verdict); the time in status moved to the card's bottom row.
- Still to do: "which resume fits best" (one check per resume); the evaluation set; the embedding pre-filter, which matters once J3 scores in bulk. Checked live only up to the provider call (a real key was not used).
- Compare a posting with a resume and return a structured result, not just a number:
  score (0-100), matched skills, gaps, dealbreakers (location, salary floor, clearance, sponsorship), one-paragraph rationale.
- Uses the user's own AI provider keys, same path as cover letters.
- The same call extracts an application deadline from the description when the job source gave none; it fills apply-by.
- Cost control: cheap pre-filter first (keywords, then pgvector embedding similarity from the backlog item), LLM only on the top candidates.
- Cache scores per (posting fingerprint, resume version) so re-runs don't re-pay.
- Show the score on job search results and on existing applications; suggest which resume fits a posting best.
- Evaluation set: a handful of postings hand-labelled good/okay/poor fit, to check scoring changes.

### J3. Saved searches + daily runs (done, except the items below)
Built as: `saved_searches` (keywords, left-out title words, location, remote, yearly pay floor, sources, posted within N days, resume, minimum score, run days and hour in the browser's time zone), `search_runs` and `job_matches`. A scheduler checks once a minute for due searches; Run now starts one at once. Each run gathers from the job boards and the company watchlist, filters without AI (a posting that doesn't state pay or a date is kept), drops anything whose normalized company and title is already on the board or was found before, and keeps the rest as NEW matches. With a resume picked, it fit-scores up to 20 unscored matches per run, newest first, stopping at the monthly AI budget (X6); a score under the minimum moves the match to BELOW_MIN. The Matches page (with a count on its nav link) saves a match as a SAVED application with its close date as apply-by and its fit linked at no cost, or dismisses it for good (with Restore). Find jobs links to the saved searches page with its current search filled in.
Still to do: the sources J0, E2/E3 and J5 add; showing every link when sources find the same job (a match keeps the first source's); per-source counts of what was new; feeding dismissals back into the minimum score; the inbox (E4) instead of the Matches page; a daily summary notification (E5).

The in-app version of the daily "new or new-to-me postings" job (run by hand in a Claude conversation as of Oct 2026), reaching well past the job-board APIs while keeping AI to the one step that needs it.
- `saved_searches` per user: keywords, location, remote, salary minimum, employment types, sources, resume to score against, minimum score, active flag, run days (default weekdays).
- Scheduled run (Spring `@Scheduled`), in the user's time zone. Each run is a pipeline, and only step 4 uses AI:
  1. **Gather** from every source the search turns on:
     - job-board APIs (today's sources plus J0's),
     - Google Jobs through SerpApi or JSearch (J0), which reaches LinkedIn, Indeed and company sites,
     - the company watchlist (J4),
     - job-alert emails (E2/E3), which cover LinkedIn and Indeed alerts,
     - the optional AI web sweep (J5), for what none of the above reach.
  2. **Dedupe** against existing applications, earlier matches and dismissed postings
     (fingerprint: source + external id, falling back to normalized company + title + location), and across sources, so one job found three ways shows once with all three links.
  3. **Filter** without AI: keywords and exclusions, location and remote, salary floor, employment type, posted within N days.
  4. **Score** only what survives with fit scoring (J2), cheap pre-filter first.
  5. **Deliver** to the inbox and the daily summary.
- Each match records which sources found it, so the run summary can show what each source is worth ("Watchlist: 4 new, Google Jobs: 9, AI sweep: 2 that nothing else found").
- Cost, estimated Oct 30 for weekday runs on gpt-6-sol: about $5/month for scoring ~20 survivors a day, plus any Google Jobs plan beyond the free tier. Doing the whole search with an AI agent instead would be about $15-25/month, which is why J5 is a top-up, not the main path.
- Matches at or above the minimum score go to the inbox queue as `JOB_SEARCH_MATCH`, with the fit breakdown shown on the item.
- Accept creates a `SAVED` application with posting details, company (R0) and the chosen resume attached.
- The posting's close date (USAJOBS and Himalayas give one) becomes the application's apply-by date. Saving a Find jobs result by hand should do the same today.
- Dismissals are remembered (and can feed back into the minimum score later).
- Optional daily summary notification: "6 new matches, best 87".

### J4. Company watchlist (keyless careers boards) (done)
- Built as planned below, except: the board is stored on the company and set by pasting its careers link (checked by reading the board once); the company page shows the newest 10 roles with "Show all"; Watchlist keyword searches also match the full description, since careers boards always include it. Still to do: Lever's full description (`lists[]`, `additionalPlain`), Lever's EU host, SmartRecruiters, and saving a role straight from the company page.
- Follow companies you'd like to work for; Beacon checks their careers boards every run and finds roles the day they're posted, often before they reach the job boards.
- Many companies publish their openings through Greenhouse, Lever, Ashby or SmartRecruiters, which offer public job-list endpoints with no key and no cost. Each is a `JobSource` adapter that takes the company's board id.
- Adding a company: paste its careers page URL and Beacon works out the platform and board id; or pick from companies already in R0. Stored on the company (`careers_platform`, `careers_board_id`), so the company page also lists its open roles.
- **Workday and Oracle Recruiting Cloud (added after J4):** read through the JSON their own careers pages load (not documented APIs, so they may change). Their boards run to thousands of roles, so searches run on the host and only the top 20 results per company get a second call for the full description (four at a time, cached for an hour); the company page lists the newest 20. Board ids are `tenant.wdN/site` and `pod.fa.region/CX_n`. A company's own careers page (careers.oracle.com) is opened once to find the board it loads from or embeds, public addresses only. Checked live against Elevance (Workday) and Oracle. iCIMS and Phenom (Liberty Mutual) are not supported; leave them to J5 or a later adapter.
- Learning focus: several small adapters behind one interface, detecting the platform from a URL, polite polling (conditional requests, one call per company per run).

### J5. AI web sweep (optional top-up)
- An AI agent with web search looks for matching postings that the other sources miss: niche boards, companies hosting their own careers pages, Workday sites. Same idea as the Claude-run daily job, but inside Beacon.
- Off by default; turned on per saved search, and runs weekly unless set to daily.
- It returns structured results (title, company, URL, location, posted date) instead of prose. Beacon then fetches each URL itself to confirm the posting exists and is still open before it reaches dedupe, so made-up or expired links never land in the inbox.
- Uses the user's own AI key and counts against the monthly budget (X6); the sweep is the first thing paused when the budget runs out.
- Estimated Oct 30: about $0.75-1 per run on gpt-6-sol (20-40 searches plus the pages read), so about $3-4/month weekly or $15-25/month on weekdays.
- Learning focus: tool-using AI calls with structured output, verifying AI-found data before trusting it, and cost limits on background work.

## Then: AI helpers and insights

The interview helpers split three ways, by what each one is for:

| | A1 Interview brief | A7 Story bank | A6 Study cards |
|---|---|---|---|
| What it is | The document you prepare from and read before each round | Your reusable STAR stories | Flashcards you drill |
| Belongs to | One application | You, across every application | A deck, usually for one interview |
| Lifetime | Grows round by round until the application closes | Kept and refined for the whole search | Studied until the interview, then archived |
| Example | "Elevance Staff IAM Interview Prep" (Oct 22-29) | "PropKnot multi-tenant authorization" | "OAuth vs OIDC vs SAML?" |

### A1. Interview brief
One living prep document per application that moves to interviews, modeled on the hand-made "Elevance Staff IAM Interview Prep" doc. Opened from the application page, it starts when the first interview is scheduled and gains a section after each round.
- **Sections**, in this order. Each says where its content comes from:
  - **At a glance** (filled from Beacon's own data, not AI): role, req number, location and hybrid days, every round from I1 with the next one in bold, format, interviewers from R1, pay from the posting or what the recruiter said.
  - **The role in one line** and **your pitch in one line** (AI, from the posting and the chosen resume).
  - **Day-of checklist**: a template you can tick off, saved per brief.
  - **About the company and team** (AI with web search): size, brands, leadership, strategy, where the role fits, and why it matters to you. Every fact carries a source link; anything without a source is left out.
  - **Your honest gap, and how to frame it**: a They want / You have / Frame it as table. From J2's fit result once it exists; until then the model writes it from the posting and resume.
  - **Core stories**: two to four picked from the story bank (A7), each with a "Tie to <company>" line written for this application.
  - **Likely questions** with answer outlines, split into background and fit, technical and behavioral.
  - **Technical refresher**: the concepts and vocabulary this role leans on (for Elevance: OAuth/PKCE, OIDC, SAML, JWT, Spring Security, authorization models).
  - **System design practice**: one prompt to practise out loud, a suggested walkthrough, and small coding questions they might ask.
  - **Update after each round**, one section per interview (I1): what you learned (process, pay, what the role really is), the next round's format, and how to shift your answers. Written from that interview's debrief notes; the AI suggests the shifts.
  - **Questions to ask and closing**, then **Sources**.
- **Storage:** `interview_briefs` (one per application) and `brief_sections` (kind, position, markdown, generated_at, edited_by_user). Each section can be regenerated on its own; a section you've edited is never overwritten without asking, so regenerating the research can't wipe your notes.
- **AI:** uses the user's own provider keys, the same path as cover letters. Generation is per section, so one failure doesn't lose the rest, and you can regenerate just the part that went stale.
- **Export:** download as Markdown, and Save as PDF through the existing headless-browser printing.
- **Can start before J1/J2:** pick a library resume by hand and let the model write the gap table; J2's structured result replaces it later.
- **Feeds A6:** "Make study cards" turns the brief's likely questions, refresher terms and stories into draft cards.
- Learning focus: an ordered, section-based document model; prompts per section with structured output; web search with citations; merging regenerated content with user edits.

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

### A6. Study cards (spaced repetition)
- Flashcards to drill before an interview: terms, likely questions and your own STAR stories. Prototyped as the "Interview Recall" page (Oct 29) for the Elevance hiring manager round.
- `study_decks` and `study_cards` per user: question, answer (markdown), optional link to an application, interview (I1) or company (R0).
- Cards come from four places: written by hand, made from an interview brief (A1: its likely questions, refresher terms and stories), drilled from the story bank (A7), or promoted from an interview's prep notes. Generated cards land as drafts to review and edit before they're studied.
- Review flow: show the question, answer out loud, reveal, self-grade (Again / Hard / Good / Easy).
- Scheduling: start with Leitner boxes (1 to 3), then move to SM-2 (ease factor, interval, due date) in a `card_reviews` table. Due cards come first; "Again" brings a card back in the same session.
- "Study for this interview" button on the interview page builds a deck from that application's posting and fit gaps (J2); the board's next-interview chip shows how many cards are due.
- Keyboard shortcuts (Space reveal, 1-4 grade) and a cheat-sheet view listing every answer.
- Learning focus: modeling a scheduling algorithm as a pure, unit-tested Java service (dates in, next due date out), a "due today" query with an index on `(owner_id, due_at)`, and an Angular study screen with signals and keyboard handling.

### A7. Story bank
- Your STAR stories, kept once and reused across applications: title, situation, task, action, result, the numbers that back it up, and tags for what each answers ("auth you've built", "leadership", "hardest problem", "legacy to cloud").
- A brief (A1) picks stories by tag for each application and adds a "Tie to <company>" line; the story itself stays shared, so improving it improves every brief that uses it.
- Gaps get flagged: the Elevance doc's "[how many tenants/roles]" placeholders become a "needs a number" warning on the story until you fill it in.
- AI helps draft a story from your resume or from rough notes, and suggests which stories fit a posting. You write the final version.
- Stories can be drilled as study cards (A6).

## Then: Portfolio (public pages)

A page each user builds in Beacon from their resume and extra details, and shares with recruiters and hiring teams, like a LinkedIn public profile: anyone with the link can open it, no account needed. W1 and W2 work before Beacon is hosted; W3 and W4 need it on the internet (X4).

### W1. Portfolio editor, preview and export
- Pick a resume profile (J1) as the starting point, then add what a resume doesn't hold: a headline and bio, photo, experience highlights, projects (description, tech, screenshots, links), links (GitHub, LinkedIn, personal site) and contact preferences.
- Structured fields, not the raw resume text: `portfolios` (one per user: slug, headline, bio, theme, published, show_email, noindex) plus `portfolio_sections` / `portfolio_items` (kind, position, content). Photos and screenshots reuse document storage.
- A few themes, all built from one server-side template; a live preview inside the app.
- **Export:** download the page as a single HTML file (images inlined) to host on GitHub Pages or Netlify, so it's useful before Beacon is hosted.

### W2. AI drafting
- Suggest the bio, experience highlights and project write-ups from the resume text and target titles, using the user's AI setup (same path as cover letters). Suggestions land in the editor to review; nothing is published unedited.
- Shares the extraction with J1's AI-suggested summary.

### W3. Public hosting at `/p/{slug}`
- A public section of the site: `SecurityConfig` permits `GET /p/**` for anyone, while `/api/**` and the app stay behind sign-in.
- Rendered by Spring as plain HTML (not the Angular app), so visitors load no app code, get no session, and link previews on LinkedIn and Slack work (Open Graph tags).
- Only published portfolios are served; anything else is a plain 404, so nobody can tell whether a name exists. Unpublishing takes effect at once.
- Slugs: unique per user, reserved words blocked (`admin`, `api`, `login`, `p`...); changing one redirects the old link for a while.
- Privacy and abuse: choose what shows (no phone by default; email or a contact form), a "hide from search engines" switch (`noindex`), rate limiting on `/p/**`, a report link and an admin takedown, since registration is open.
- Later: custom domains (`yourname.dev`), with certificates and DNS checks; a likely paid-plan feature (P2).

### W4. Per-application links
- Each application gets its own portfolio link (`/p/{slug}?ref=<token>`); Beacon records when it's opened and shows "Acme opened your portfolio" on that application's timeline (I2), without the visitor signing in.
- Optionally a tailored version per application: projects and skills that match the posting (J2) shown first.
- What a job tracker can do that a plain portfolio site can't; a natural paid-plan feature.

## Board (any time)

### B1. Search, filter and drag-and-drop (done)
- Built as a filter bar above the board: a search box (every word must appear in the employer, role, location, source or notes, ignoring case and accents), a source picker listing the sources on the board, Remote and Needs action. Filters run in the browser over the loaded board, show "Showing N of M" with a Clear button, and live in the address (`?q=&source=&remote=&action=`), so a refresh keeps them and the Board link clears them. The Needs attention strip follows the filters.
- Drag and drop with Angular CDK (`@angular/cdk`): a column only accepts a card if one of its allowed moves lands there, and those columns are outlined while dragging. A card dropped on Closed with more than one way to close (rejected, withdrawn...) asks which. Moves go through the same call and 409 handling as the buttons, which stay for keyboard users. Touch drags start after a short hold so the board still scrolls.
- Still to do: a "has follow-up due" filter separate from Needs action; filtering by company type or employment type.
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
- Public on the internet with a real domain once other people use Beacon; portfolio pages (W3) are the first part anyone can open without an account.

### X5. Data export and account deletion
- Export everything a user owns (JSON + documents as a zip); delete account and all data.
- Becomes important once mailbox credentials and email snippets are stored.

### X6. AI usage tracking (done, except the items below)
Built as: every AI call reports the tokens the provider returned to an `AiUsageMeter` as soon as the response arrives (so refused and unreadable replies are counted too), and `ai_usage` keeps one row per call in its own transaction, priced on the day from `beacon.ai.prices`. A fit score reused from its cache records nothing. Settings shows the month in the browser's time zone, by feature and by model, the last six months, and the budget meter (amber from 80%, red at 100%). `ai_usage.owner_id` has no foreign key (see V14), so X5 must delete its rows.
Still to do: pausing background jobs at 100% of the budget (none exist yet; J3 should check it before scoring), counting web searches once a feature uses them, and pricing Claude cache writes at their real rate if Beacon starts caching prompts.

- Record the token counts the provider returns with every AI response (input, cached input, output including reasoning, web searches) in an `ai_usage` table: user, feature (cover letter, fit score, brief section, email sorting...), provider, model, timestamp.
- Price it with a small, dated price table per model, kept in config, so a price change doesn't rewrite past months.
- Settings shows this month's spend: total, by feature and by model, plus the last few months for comparison.
- Optional monthly budget per user: warn at 80%, and pause the background jobs (daily search scoring, email sorting) at 100% while leaving the ones the user starts by hand working.
- Turns the Oct 30 estimate (about $30/month on gpt-6-sol for 5 applications a day, about $15 with cheaper models for routine jobs) into measured numbers, and shows which features are worth moving to a cheaper model.
- Earlier is cheaper: start logging with the cover-letter writer that exists today, so every AI feature added later reports from day one.
- Learning focus: capturing usage from both OpenAI and Anthropic responses behind one interface, an aggregation query by month and feature, and money math with `BigDecimal`.

## Business (research and decide before opening Beacon to others)

Research items, not code yet. Each ends in a written decision; the answers change what gets built (X4, X6, J0, J3).

**Direction (Nov 3, details still to work out):** two paid plans.

| | All-included | Open |
|---|---|---|
| Who it's for | Job seekers who just want it to work | Tech-savvy users who already have, or don't mind making, their own API accounts |
| Job sources | Beacon supplies the keys for chosen paid sources (likely Google Jobs/JSearch), plus every free source | The free sources, plus any keyed source the user adds a key for (what Beacon does today) |
| AI | Beacon supplies it (fit scores, cover letters, later briefs and email sorting), within a monthly allowance | The user's own OpenAI or Anthropic key, billed to them; no allowance needed |
| Setup | Nothing to sign up for elsewhere | Settings -> API keys, with the provider guide's steps |
| Price | Higher: covers Beacon's API and AI costs plus a margin | Significantly less: Beacon only carries hosting and the app itself |

P1 and P2 below now work out the details of this split instead of choosing between models.

### P1. Who pays for API keys
Today every user brings their own keys (OpenAI or Anthropic for AI; Adzuna, USAJOBS and later JSearch or SerpApi for paid job sources). That's fine for developers, but most job seekers have never heard of an API key, and "go sign up at four other sites first" will turn many of them away before they see what Beacon does.
- **Options to compare:**
  - **Bring your own keys** (today): no cost to Beacon, full control for the user, worst sign-up experience.
  - **Managed keys:** Beacon holds one key per provider and pays for everyone's usage, built into the price. Best experience; Beacon carries the cost and must meter it (X6) and set fair-use limits.
  - **Hybrid:** managed keys by default within each plan's allowance; power users can add their own keys to go past the limits or pick their model.
- **Research:**
  - Each job source's terms: may a commercial app use one key for all its users, or resell access? (Adzuna, USAJOBS, JSearch, SerpApi, Jooble.) Some may need a business agreement or a different plan.
  - Real per-user cost with managed keys, from X6's measured usage rather than the Oct 30 estimate (about $15-30/month for a heavy AI user; about $5/month for job-search scoring alone).
  - Abuse: what stops one account running up a large bill (per-plan monthly caps, rate limits, background jobs paused first).
  - **Anthropic or OpenAI for the All-included plan's AI.** At list price the two match at the tier Beacon uses: Claude Sonnet 5.5 and gpt-6-sol are both $2 per million input tokens and $10 per million output, $0.20 cached (OpenAI's figures come from third-party price sites; confirm on its own pricing page). So the provider alone barely moves the bill; these do:
    - **A cheaper model for routine jobs:** Claude Haiku 5.5 is $0.10 / $0.50 per million (about 20 times less; it counts the same text as roughly 30% more tokens, so nearer 15 times in practice). Fit scoring of saved-search matches and email sorting are the high-volume jobs where that adds up, if J2's evaluation set shows the scores hold up. Cover letters and interview briefs stay on the stronger model.
    - **Batch processing at half price** for work nobody waits on, like the scheduled saved-search runs (J3).
    - **Prompt caching:** a run scores up to 20 postings against the same resume, so caching the resume makes every read after the first cost $0.20 per million instead of $2 (Sonnet).
    - **One provider is simpler** for managed keys (one account, one bill, one set of limits); Beacon already speaks both, so it can switch or split by job.
    - **Decide with data:** score the same postings on each candidate model, compare the scores against hand labels (J2's evaluation set) and the measured cost (X6).
- **Settled (Nov 3):** both, as separate plans: managed keys in All-included, bring-your-own in Open. Still open: whether an All-included user may add their own key to go past the allowance or pick a model (the old "hybrid" idea), and whether Open users get any managed AI at all (for example a small trial allowance).
- **What this means for the code:**
  - A plan on each account (`ALL_INCLUDED`, `OPEN`, maybe a free or trial state), checked wherever credentials are looked up.
  - Credentials resolve per plan: All-included uses Beacon's server-side keys for the supplied providers; Open uses the user's saved keys as now. Job sources and AI writers shouldn't care where the key came from.
  - Settings -> API keys and the provider guide only matter to Open users; All-included users see their allowance instead.
  - X6's usage metering becomes the allowance: count All-included usage against the plan, warn near the limit, pause background jobs first. Open users' usage is still shown, as it is their own bill.
  - Paid job sources usually charge per request, so All-included needs per-user caps on those too (searches a day or a month), not just AI.

### P2. Plans and pricing
- **Settled (Nov 3):** two paid plans, All-included and Open (see the table at the top of this section). The questions below now apply to those two.
- **Questions:**
  - Is there also a free plan or a free trial, and what's in it? (Likely: tracking, board, contacts, interviews, timeline, the free job sources; no AI, or a small taste of it.) A trial of All-included may sell it better than a permanent free plan.
  - What each plan costs: All-included from measured cost per user (AI, paid search requests, hosting) plus a margin; Open well below it, since its main cost is hosting.
  - All-included's allowance: how many AI fit checks, cover letters and paid searches a month, and what happens at the limit (pause, buy more, or add your own key).
  - Which features, if any, are All-included only (for example the AI web sweep J5, or email sorting E3), or whether the plans differ only in who supplies the keys. Differing only in keys is simplest and easiest to explain.
  - Moving between plans: switching from Open to All-included keeps the user's data; switching back keeps their saved keys, if any.
  - Monthly, or a job-search pass (a few months at once)? People leave when they're hired, so monthly churn is built in; a pass may fit better.
  - Price point: what comparable job-search trackers charge, and what each paid plan costs Beacon to run (AI, search APIs, hosting) plus a margin.
- **Research:**
  - Competitors' free and paid plans (Huntr, Teal, Simplify, Careerflow and others): what they give away free, what's paid, at what price.
  - Ask 5-10 job seekers what they'd pay, and for which features (the value test from Oct 30: "would you be upset if it was taken away?").
  - Cost per user per plan from X6 data, including hosting from X4.
- **Decide:** free plan or trial, what each of the two plans includes, their prices, and All-included's allowance.

### P3. Payments and accounts
- Payments through a provider (Stripe most likely): checkout, subscriptions, upgrades and downgrades, cancellations, receipts, failed payments.
- What happens to a user's data when a plan lapses: read-only rather than deleted, export always available (X5).
- Taxes on subscriptions (sales tax / VAT rules by location) and a simple terms of service and privacy policy, especially once mailboxes are connected (E2).
- Decide only after P1 and P2; build alongside X4 (deploy).

### P4. Name and brand
"Beacon" is a working name; a crowded one for a public product. Decide before launch, after P1-P3.
- Ruled out: **Ontraq** (sounds exactly like OnTrac, the parcel carrier, and already has many listings).
- Shortlist to check, from words for job, career or path in other languages (Oct 2026):
  - **Baan** (Dutch: both "job" and "track"; *loopbaan* = career, literally "running track"),
  - **Ura** (Finnish: "career", also "track"),
  - **Rumbo** (Spanish: "heading, direction"),
  - **Parcours** (French: "path"; *parcours professionnel* = career path).
  - Also considered: Waymark, Lodestar, Hirepath, Métier, Kairos.
- Checks for any candidate:
  1. Say it to someone: can they type it right the first time?
  2. Search the name plus "app" and "software": is page one empty or unrelated?
  3. Is the .com or .app domain available at a normal price, with matching social handles?
  4. USPTO trademark search (tmsearch.uspto.gov): nothing close in software or online services.
- Pick 3-4 that pass, then decide; logo and colours follow (the current `flare` icon and blue can stay or change).

## Backlog (from the original plan)

- Semantic search over documents with pgvector embeddings (the database image already includes pgvector). Also the pre-filter for J2.
- "Ask Beacon": RAG answers with citations, streamed over SSE.
- Resilience: retries / circuit breaker on AI and job-source calls (Resilience4j), rate limiting, readiness probes.
