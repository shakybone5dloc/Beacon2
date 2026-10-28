# X1 research: OpenAPI + generated Angular client

Read-only inventory of the code as of commit bc76b8a (2026-10-28). Tools in scope: springdoc-openapi 3.x (Spring Boot 4) and ng-openapi-gen 1.1.0.

## Headline findings

- Status-like fields are `String` in the Java records (application status, document kind, provider kind/cost), while the hand-written TypeScript uses string unions. Generated types would lose the unions unless the backend describes them.
- No response field is marked required, so every generated response field would be optional (`?`).
- Default operationIds repeat (`list` x4, `delete` x4, `upload` x2, `download` x2, `save` x2) and would get unstable `_1`/`_2` suffixes. Every endpoint needs an explicit `@Operation(operationId = ...)`, plus `@Tag` per controller.
- `frontend/tsconfig.json` has no `"strict": true`, so null checks are off; nullability in the generated types won't be enforced until strict mode is turned on.

## Backend endpoints

Every handler takes `@AuthenticationPrincipal SignedInUser` (springdoc is expected to hide it; verify).

| Controller | Method + path | Body | Response |
|---|---|---|---|
| ApplicationController | POST /api/applications | CreateApplicationRequest | ApplicationResponse (201) |
| | GET /api/applications | - | List<ApplicationResponse> |
| | GET /api/applications/{id} | - | ApplicationResponse / 404 |
| | PUT /api/applications/{id} | UpdateApplicationRequest | ApplicationResponse |
| | DELETE /api/applications/{id} | - | 204 / 404 |
| | PUT /api/applications/{id}/follow-up | FollowUpRequest | ApplicationResponse |
| | GET /api/applications/{id}/history | - | List<StatusChangeResponse> |
| | PATCH /api/applications/{id}/status | ChangeStatusRequest | ApplicationResponse |
| AuthController | GET /api/auth/csrf | - (CsrfToken arg) | 204 |
| | POST /api/auth/register | RegisterRequest | CurrentUserResponse (201) |
| | POST /api/auth/login | LoginRequest | `ResponseEntity<?>`: 200 CurrentUserResponse or 401 ProblemDetail |
| | GET /api/auth/me | - | CurrentUserResponse |
| (security filter) | POST /api/auth/logout | - | 204, not in the spec (no controller) |
| CoverLetterController | GET /api/cover-letters/status | - | CoverLetterStatusResponse |
| | POST /api/applications/{applicationId}/cover-letter/draft | CoverLetterDraftRequest | CoverLetterDraftResponse |
| | POST /api/applications/{applicationId}/cover-letter | SaveCoverLetterRequest | DocumentResponse (201) |
| ApplicationDocumentsController | GET .../{applicationId}/documents | - | List<DocumentResponse> |
| | POST .../documents (multipart: file, kind String default OTHER) | - | DocumentResponse (201) |
| | POST .../documents/posting-capture | - | CaptureResponse |
| | POST .../documents/posting-summary | - | DocumentResponse (201) |
| | GET .../documents/{documentId}/content | - | byte[] (binary) |
| | DELETE .../documents/{documentId} | - | 204 |
| LibraryDocumentsController | GET /api/documents | - | List<DocumentResponse> |
| | POST /api/documents (multipart) | - | DocumentResponse (201) |
| | GET /api/documents/{documentId}/content | - | byte[] |
| | DELETE /api/documents/{documentId} | - | 204 |
| PostingsController | GET /api/postings/sources | - | List<JobSourceResponse> |
| | GET /api/postings/search (keywords, location, remoteOnly, sources List<String>) | - | PostingSearchResponse |
| ApiKeysController | GET /api/me/api-keys | - | List<ApiKeyResponse> |
| | PUT /api/me/api-keys/{provider} | SaveApiKeyRequest | ApiKeyResponse |
| | DELETE /api/me/api-keys/{provider} | - | 204 |
| ProvidersController | GET /api/providers | - | List<ProviderResponse> |

Poor-type risks: `login` returns `ResponseEntity<?>` (needs `@ApiResponse` for 200 and 401); `CsrfToken` param may need `@Parameter(hidden = true)`; logout is a security-filter endpoint; multipart `kind` is a plain String; byte[] downloads are only used as link URLs in the frontend; 201/204 are documented as 200 unless annotated; `sources` becomes an array param (repeated `sources=a&sources=b`) instead of today's comma-joined string; `keywords` has a default but blank is rejected.

## DTOs (contracts/)

- Nullability: `jakarta.annotation.Nullable` on 6 request files and ApiKeyResponse only; response records unannotated even where null is documented (jobDescription, appliedAt, applicationId, error, fromStatus).
- String fields that are really enums: ApplicationResponse.status, allowedTransitions, StatusChangeResponse.fromStatus/toStatus, ChangeStatusRequest.status, DocumentResponse.kind, multipart `kind`, ProviderResponse.kind/cost. Enums exist: ApplicationStatus, DocumentKind (domain), Provider.Kind/Cost (application/providers).
- Nested `ProviderResponse.FieldResponse` gets the generic schema name `FieldResponse`.
- Jackson 3, default config: ISO dates, nulls written (every property is present in responses).

## Error shape (GlobalExceptionHandler)

All ProblemDetail. 400 validation (`errors: {field: [messages]}`), 409 conflict (optimistic lock, DomainException), 502/422 for posting capture and cover letters, 401 for login. The `errors` property lives in ProblemDetail's properties map, so springdoc won't describe it; keep the hand-written frontend `ProblemDetail` type.

## Security (SecurityConfig)

Permitted today: POST register/login, GET csrf, /actuator/health/**. Need permitAll for `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`. `csrf.spa()` doesn't affect GETs; swagger-ui "Try it out" writes would need `springdoc.swagger-ui.csrf.enabled=true`. Dev proxy only forwards `/api`.

## Frontend

| Service | Used by components | Specs touching its URLs |
|---|---|---|
| applications/applications-api.ts | application-detail, application-form, board, job-search, posting-view/printable-posting | applications-api, application-detail, application-form, board, job-search, posting-view, auth (401) |
| auth/auth-api.ts | auth-state.ts | app, auth, auth-pages |
| documents/cover-letters-api.ts | cover-letter-drafter | cover-letter-drafter, documents-api |
| documents/documents-api.ts (+ `contentUrl()` helper) | application-detail, application-documents, cover-letter-drafter, printable-posting | documents-api, application-documents, cover-letter-drafter, posting-view |
| postings/postings-api.ts (joins `sources` with commas) | job-search | postings-api, job-search |
| providers/providers-api.ts | api-key-editor, api-keys-page, provider-guide | providers |

- Name differences: StatusChange vs StatusChangeResponse, CurrentUser vs CurrentUserResponse, JobSource/JobPosting/SourceResult vs *Response, Provider/ProviderField/ApiKeyStatus vs ProviderResponse/FieldResponse/ApiKeyResponse.
- Client-only exports to keep hand-written: STATUSES, SALARY_PERIODS, DOCUMENT_KINDS, KIND_LABELS, ACCEPTED_FILES, PostingQuery, PostingDetails, ProblemDetail, accessLabel().
- Generated rootUrl must be `''` (not springdoc's `http://localhost:8080`), or every `expectOne('/api/...')`, the dev proxy and `auth.interceptor.ts`'s URL check break.
- `postings-api.spec.ts:40` expects `sources=remotive,jobicy`; breaks if arrays go out as repeated params.
- The six `*-api.spec.ts` files test the wrappers; they get rewritten or deleted.

## Other

- app.config.ts: `provideHttpClient(withFetch(), withInterceptors([authInterceptor]))`; XSRF works with generated functions too.
- No generation script, ng-openapi-gen not installed; no .gitignore entries for generated code; no springdoc in pom.xml.

## Suggested migration split

1. Backend prep (X1 part 1): springdoc, docs paths permitted, operationIds/tags, enum schemas, required markers, login/csrf annotations.
2. Providers / API keys (smallest).
3. Postings / job search (decide `sources` serialization).
4. Applications (largest; keep STATUSES, PostingDetails, ProblemDetail local).
5. Documents + cover letters (multipart; keep `contentUrl` helper).
6. Auth last (csrf/login/logout quirks, interceptor URL check).
