# CareBridge

Multi-tenant clinical workflow platform for synthetic demo data: clinics manage cases through a review workflow with roles, audit, and inbound webhooks.

## Language

**Tenant**:
An organization (clinic) that owns a fully isolated set of users, cases, and configuration.
_Avoid_: Org (except in role name ORG_ADMIN), workspace, account

**User**:
A principal that belongs to exactly one Tenant in v1. Most Users are people with a Role who can log in; a Tenant also has exactly one System Actor User.
_Avoid_: Member, account, staff (unless speaking casually)

**System Actor**:
The non-login User for a Tenant used as Creator or comment author when an inbound lab webhook opens or comments on a Case. Minted with the Tenant; never invited, never listed as staff, never issued tokens.
_Avoid_: System user (ambiguous with OS), service account, bot, first admin

**Invite**:
An ORG_ADMIN action that creates a human User in their Tenant with a role and temporary password (no email delivery in v1). Does not create the System Actor.
_Avoid_: Magic link, invitation email (v2)

**Role**:
One of ORG_ADMIN, CLINICIAN, REVIEWER, or AUDITOR — the permission set attached to a human User. The System Actor does not use Role for authorization.
_Avoid_: Permission, group

**Case**:
A unit of clinical workflow work about a synthetic patient within a Tenant. All Users of that Tenant may read every Case; write and transition rights depend on Role and assignment rules.
_Avoid_: Ticket, task, work item (in domain language — UI may say “board”)

**Assignee**:
The User currently responsible for progressing a Case; may be empty when the Case is unclaimed. In v1 only a REVIEWER may be Assignee.
_Avoid_: Owner (owner is ambiguous with creator)

**Creator**:
The User who opened the Case (`created_by`) — a human or the System Actor. Distinct from Assignee.
_Avoid_: Owner

**Claim**:
A Reviewer action that sets themselves as Assignee and moves a Case from TO_DO to IN_REVIEW in one step. Only a REVIEWER may Claim.
_Avoid_: Take, pick up (except in UI copy)

**Assign**:
An ORG_ADMIN action that sets a Case’s Assignee (and, from TO_DO, moves it to IN_REVIEW). Distinct from Claim.
_Avoid_: Claim

**Open Case**:
A Case in TO_DO, IN_REVIEW, or NEEDS_INFO. Used when deciding whether a lab webhook creates a new Case or comments on an existing LAB_FOLLOWUP for the same patientRef.
_Avoid_: Active case (unless UI copy)

**Terminal status**:
APPROVED or REJECTED — end states of the Case workflow. In v1 a Case in a terminal status is frozen: no field edits, transitions, or comments; read and audit only.
_Avoid_: Closed (unless defining Open Case by contrast)
