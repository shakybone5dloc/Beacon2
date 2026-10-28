# R0 research: Companies impact inventory

Read-only inventory of the code as of commit bc76b8a (2026-10-28). See ROADMAP.md section R0 for the goal.

## Size and suggested split

About 15 backend source files, 16 backend test files and 6 frontend specs change. Suggested split:
- **R0a**: V7 migration + backend (entity, service, API, find-or-create, fixes in cover letters / posting PDFs).
- **R0b**: Angular type-ahead picker, company page, routes.

## Decisions to make

1. Legacy rows with `owner_id IS NULL` (pre-accounts, hidden by design) can't get a per-user company.
2. Job search saves `'Unknown company'` when a posting has no company (`job-search.ts` L130): map to undisclosed (null `company_id`)?
3. Where find-or-create happens: request carries `companyId`, a company name, or both.
4. Undisclosed client wording for cover-letter prompts and PDF titles.
5. Service must reject a `companyId` owned by another user (new ownership rule + test).

## Database

- Migrations V1-V6; **next is V7**. `ddl-auto: validate`, `open-in-view: false`.
- `job_applications.company varchar(200) NOT NULL` (V1); no CHECK constraint, no index on company.
- `owner_id` nullable (V5), FK users ON DELETE CASCADE, index `(owner_id, created_at)`.

## Backend code touching company

| File | Detail |
|---|---|
| domain/applications/JobApplication.java | `company` field (L24-25), `create(ownerId, company, ...)` L91, `updateDetails` L104, `applyDetails` L109-119 (blank check, strip), private `trimToNull` L168, getter L173. Owner is a plain UUID, not `@ManyToOne`. |
| contracts/applications/Create/UpdateApplicationRequest.java | `@NotBlank @Size(max=200) String company` |
| contracts/applications/ApplicationResponse.java | `String company` |
| application/applications/ApplicationMappings.java | `getCompany()` in `toResponse` (L174) |
| application/applications/ApplicationService.java | `request.company()` in create (L41) and update (L103); `list` needs a fetch join / `@EntityGraph` once company is `@ManyToOne` |
| infrastructure/persistence/JobApplicationRepository.java | owner-scoped derived queries only |
| application/coverletters/CoverLetterService.java | company into `CoverLetterInput` (L88), filename slug (L116), duplicate `slug()` (L140) |
| application/coverletters/CoverLetterWriter.java | `record CoverLetterInput(String company, ...)` |
| infrastructure/ai/CoverLetterPrompt.java | `"Company: " + input.company()` (L30) |
| application/documents/PostingCaptureService.java | filename slug (L62); **`slug()` L79 calls `toLowerCase` and throws on null** |
| application/documents/PostingSummaryService.java | filename slug (L53); company in PDF title and `<p class="company">` (L101-111) |
| application/postings/* and infrastructure/postings/*Source.java | posting company strings; read-only, dedupe key `company|title` (PostingSearchService L171) |

Saving a search result as an application happens only in the frontend (`job-search.ts` L124-150 POSTs /api/applications).

## Backend tests that break

JobApplicationTests, JobApplicationStatusTests, JobApplicationRepositoryTests, MigrationTests (template for a V7 backfill test), PostingSummaryServiceTests, ApplicationsApiTests, ApplicationEditApiTests, ApplicationDatesApiTests, ApplicationPostingDetailsApiTests, ApplicationStatusApiTests, AuthApiTests, DocumentsApiTests, PostingCaptureAndCoverLetterApiTests (filename `posting-acme-corp-`), OwnershipApiTests (native insert with `company` column, L91), ClaudeCoverLetterWriterTests, DocumentTestDoubles.

## Frontend touching company

application.model.ts (L38, L65), applications/testing.ts (fixture), application-form .ts/.html (control, submit, fill, server errors; picker goes at L22-26), board.html (L19, L46), application-detail .html (h1) / .ts (delete confirm), posting-view.html, job-search.ts (create request), app.routes.ts (new routes).
Specs: application-form, board, application-detail, applications-api, posting-view, job-search.

## Ownership pattern to follow

Controllers take `@AuthenticationPrincipal SignedInUser` and pass `user.id()`; services take `ownerId` first and return `Optional.empty()` for other users' rows (-> 404); repositories only expose owner-scoped finders; entities hold `ownerId` UUID (`updatable=false`); OwnershipApiTests + SignedInApiTests for cross-user tests.

## V7 migration risks

- Null-owner rows (decision 1).
- Blank names: guard with `NULLIF(btrim(company), '')`.
- `'Unknown company'` placeholders (decision 2).
- Case/punctuation duplicates ("Acme Inc." vs "ACME"): SQL normalization must match the Java normalizer exactly; prove it in MigrationTests.
- Same name, different owners: separate companies; unique index `(owner_id, normalized_name)`.
- Display name: pick one variant per group (e.g. earliest created_at).
- Order in one migration: add company_id, insert companies, update, drop company; index company_id.
- Reusable helpers today: private `trimToNull` (JobApplication, PostingDetails), `UserAccount.normalizeEmail` pattern, duplicated `slug()` (PostingCaptureService, CoverLetterService). No shared normalizer yet.

## Patterns

Static factories with `Instant now` from `Clock`, `@Version Long version`, DomainException -> 409, RequestValidationException -> 400 `errors.{field}`, records in contracts/ with Jakarta validation, `*Mappings` classes. Tests: AssertJ unit tests, `SignedInApiTests` + `MockMvcTester`, `@SpringBootTest` + Testcontainers repository tests, Flyway `target` migration tests, Vitest + HttpTestingController + `anApplication()` fixtures.
