---
description: Run one pass of the Beacon Java Board — product owner triage and review, then one dev ticket, then QA.
argument-hint: "[optional: max dev tickets, default 1]"
---

Run one pass of the Beacon Java Board. Read `.claude/board.md` first.
You are the coordinator: you hand work to the subagents and report at the end.
Keep the pass small; usage limits are tight. Max dev tickets this pass: $ARGUMENTS (default 1).

1. Query the board and summarize counts by Status.
2. If anything is In Review or Backlog: run the **product-owner** agent to review and groom.
3. If anything is Needs Rework, or Ready (in that order of preference), run the
   **dev** agent on the single highest-priority ticket. Repeat up to the max.
4. If anything is in QA: run the **qa-walker** agent in Mode A on those tickets.
5. If Backlog + Ready together hold fewer than 3 tickets, tell AJ to run `/plan-next`
   (don't run the planner yourself).

Finish with a short report: what moved, what's waiting on AJ (With AJ, Ready to
Merge), and anything that failed. Never merge anything.
