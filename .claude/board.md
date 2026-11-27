# Beacon Java Board (shared by every agent)

Notion database: Beacon Java Board
- URL: https://app.notion.com/p/422bea683d2981f4959dc57305e4202f
- Data source: collection://422bea68-3d29-81a5-96d8-000bf5d1a002

Use the Notion MCP tools: fetch the data source for the schema, query it to find
tickets, update a page's properties to move a ticket, and add a comment to the
ticket page for every handoff.

## Statuses and who moves them

| From | To | Who |
|------|----|-----|
| (new) | Backlog | Planner, QA Walker, Product Owner, AJ |
| Backlog | Ready | Product Owner (or AJ) after checking the ticket is complete and its "Depends on" tickets are Done |
| Ready | In Progress | Dev |
| In Progress | With AJ | Dev, when blocked on a decision only AJ can make (AJ answers questions; he does no codework, so agents write all the code) |
| With AJ | In Review | AJ, when his TODOs are finished |
| In Progress | In Review | Dev, Agent-codes mode: PR is open and tests pass |
| In Review | QA | Product Owner, when every acceptance criterion is met |
| In Review | Needs Rework | Product Owner, with a numbered list of gaps; add 1 to "Rework count" |
| Needs Rework | In Progress | Dev |
| QA | Ready to Merge | QA Walker, after running the ticket's test plan in the browser |
| QA | Needs Rework | QA Walker, with steps to reproduce and screenshots |
| Ready to Merge | Done | AJ only. Agents never merge to main. |

If "Rework count" reaches 3, set the status to "With AJ" and stop: a person needs to look.

## Ticket page body (Planner writes this; everyone else keeps it)

```
## Why
One or two sentences: the user problem, and the roadmap section it comes from.

## Requirements
- Numbered, testable statements. Name entities, endpoints, migrations, screens.

## Acceptance criteria
- [ ] Given / when / then, one per line. The Product Owner reviews against these.

## Test plan
- Backend: unit and Testcontainers integration tests to add.
- Frontend: component/unit tests to add.
- Manual (QA Walker): click path in the browser, including empty, error and mobile widths.

## Notes for the dev
Files likely touched, existing patterns to copy, gotchas.

## Out of scope
What this ticket deliberately does not do.
```

## Every agent, every time
- Comment on the ticket page: who you are, what you did, what's next. Keep it short.
- Set "Last update" to one line.
- Never read, print or commit `.env` or any secret. Never push to main. Never merge.
- Repo: `ROADMAP.md` gets updated in the same PR as the change it describes.
