# Issue tracker: Linear

Issues and PRDs for this **CareBridge** repo live in **Linear**, under the **CareBridge** project. Use the **Linear MCP** tools for all operations (create, read, update, comment, label, close). Do not use `gh issue` for this repo's issue tracker unless the user explicitly asks.

## Default scope (required)

| Field | Value |
| ----- | ----- |
| Workspace | `neero0x01` |
| **Project** | **CareBridge** (`b7249ec4-dbe8-4d4c-afdf-5207668ca02c`) — [open in Linear](https://linear.app/neero0x01/project/carebridge-e428c77a58bb) |
| Team (for create) | `Muhammad Ahmad` (team key `MUH` — Linear assigns issue IDs from the team key) |

**Always** attach new tickets to project **CareBridge**. When listing or searching, filter with `project: "CareBridge"` (or the project id/slug above).

**Do not** treat the default Linear onboarding issues (e.g. `MUH-1` … `MUH-4` outside this project) as CareBridge work. Only issues on the CareBridge project count.

## Conventions

- **Create an issue**: Linear MCP `save_issue` with:
  - `title` (required)
  - `team`: `Muhammad Ahmad` (required by Linear)
  - `project`: `CareBridge` (**always**)
  - `description` (markdown body)
  - triage labels from `docs/agents/triage-labels.md` when applicable
- **Read an issue**: Linear MCP get issue by identifier or URL; include description and comments when available.
- **List issues**: Linear MCP `list_issues` with `project: "CareBridge"` (plus state/label/assignee filters as needed). Never list the whole workspace as if it were this repo.
- **Comment on an issue**: Linear MCP create comment on the issue.
- **Apply / remove labels**: Linear MCP update issue labels — map triage roles via `docs/agents/triage-labels.md` (create labels in Linear if missing).
- **Close / cancel**: Linear MCP update issue state (e.g. Done / Canceled) with a closing comment when the skill requires one.

Prefer the issue identifier Linear shows in agent output and cross-links. IDs use the team key prefix; the **project** is what scopes CareBridge work, not the prefix alone.

If Linear MCP is unavailable in the session, stop and tell the user to connect Linear MCP rather than inventing a CLI fallback.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub PRs for this repo are not treated as the primary request surface for `/triage`. Work is tracked in Linear under the CareBridge project.

## When a skill says "publish to the issue tracker"

Create a Linear issue via MCP on team **Muhammad Ahmad** with project **CareBridge**.

## When a skill says "fetch the relevant ticket"

Fetch the Linear issue via MCP by identifier or URL the user provided. Confirm it belongs to project **CareBridge** when relevant.

## Wayfinding operations

Used by `/wayfinder`. Adapt Linear to the map + child ticket model — all map and child issues on project **CareBridge**:

- **Map**: a Linear issue on project CareBridge labelled (or titled/identified) as the wayfinder map, holding Notes / Decisions-so-far / Fog in the description.
- **Child ticket**: a Linear issue on project CareBridge related to the map (parent/sub-issue or explicit link in description: `Part of <map-id>`). Labels/types: research / prototype / grilling / task as appropriate.
- **Blocking**: record blockers on the child (Linear relations if available, else a `Blocked by: <id>` line at the top of the description). Unblocked when every blocker is Done/Canceled.
- **Frontier query**: open children of the map with no open blockers and no assignee; first in map order wins.
- **Claim**: assign the issue to the driving user/session before work.
- **Resolve**: comment the answer, set state Done, append a context pointer to the map's Decisions-so-far.
