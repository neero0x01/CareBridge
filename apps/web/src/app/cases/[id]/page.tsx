"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  addCaseComment,
  assignCase,
  claimCase,
  clearSession,
  ensureAccessToken,
  getCase,
  listCaseComments,
  listCaseTransitions,
  listUsers,
  me,
  transitionCase,
  type CaseCommentResponse,
  type CaseResponse,
  type CaseStatus,
  type CaseTransitionResponse,
  type UserResponse,
} from "@/lib/api";

const STATUS_LABELS: Record<CaseStatus, string> = {
  TO_DO: "To do",
  IN_REVIEW: "In review",
  NEEDS_INFO: "Needs info",
  APPROVED: "Approved",
  REJECTED: "Rejected",
};

function isTerminal(status: CaseStatus): boolean {
  return status === "APPROVED" || status === "REJECTED";
}

type Action =
  | { kind: "claim"; label: string }
  | { kind: "transition"; toStatus: CaseStatus; label: string };

function allowedActions(
  c: CaseResponse,
  role: string,
  userId: string,
): Action[] {
  if (isTerminal(c.status)) {
    return [];
  }
  const actions: Action[] = [];
  if (role === "REVIEWER" && c.status === "TO_DO" && !c.assigneeId) {
    actions.push({ kind: "claim", label: "Claim" });
  }
  if (
    (role === "ORG_ADMIN" ||
      (role === "REVIEWER" && c.assigneeId === userId)) &&
    c.status === "IN_REVIEW"
  ) {
    actions.push(
      { kind: "transition", toStatus: "NEEDS_INFO", label: "Request info" },
      { kind: "transition", toStatus: "APPROVED", label: "Approve" },
      { kind: "transition", toStatus: "REJECTED", label: "Reject" },
    );
  }
  if (
    c.status === "NEEDS_INFO" &&
    (role === "ORG_ADMIN" ||
      userId === c.createdBy ||
      userId === c.assigneeId)
  ) {
    actions.push({
      kind: "transition",
      toStatus: "IN_REVIEW",
      label: "Re-submit",
    });
  }
  return actions;
}

function canPostComment(role: string, status: CaseStatus): boolean {
  return role !== "AUDITOR" && !isTerminal(status);
}

export default function CaseDetailPage() {
  const params = useParams();
  const caseId = typeof params.id === "string" ? params.id : "";

  const [c, setCase] = useState<CaseResponse | null>(null);
  const [history, setHistory] = useState<CaseTransitionResponse[]>([]);
  const [comments, setComments] = useState<CaseCommentResponse[]>([]);
  const [role, setRole] = useState("");
  const [userId, setUserId] = useState("");
  const [reviewers, setReviewers] = useState<UserResponse[]>([]);
  const [assigneeId, setAssigneeId] = useState("");
  const [comment, setComment] = useState("");
  const [threadBody, setThreadBody] = useState("");
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusMsg, setStatusMsg] = useState<string | null>(null);
  const [needsLogin, setNeedsLogin] = useState(false);

  const actions = useMemo(
    () => (c ? allowedActions(c, role, userId) : []),
    [c, role, userId],
  );

  const load = useCallback(async () => {
    if (!caseId) return;
    const token = await ensureAccessToken();
    if (!token) {
      setNeedsLogin(true);
      setLoading(false);
      return;
    }
    setError(null);
    try {
      const profile = await me();
      setRole(profile.user.role);
      setUserId(profile.user.id);
      const [detail, transitions, caseComments] = await Promise.all([
        getCase(caseId),
        listCaseTransitions(caseId),
        listCaseComments(caseId),
      ]);
      setCase(detail);
      setHistory(transitions);
      setComments(caseComments);
      if (profile.user.role === "ORG_ADMIN") {
        try {
          const users = await listUsers();
          setReviewers(users.filter((u) => u.role === "REVIEWER" && u.active));
        } catch {
          setReviewers([]);
        }
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load case";
      if (message.toLowerCase().includes("unauthorized")) {
        clearSession();
        setNeedsLogin(true);
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function runAction(action: Action) {
    if (!c) return;
    setError(null);
    setStatusMsg(null);
    setActing(true);
    try {
      let updated: CaseResponse;
      if (action.kind === "claim") {
        updated = await claimCase(c.id, c.version);
        setStatusMsg("Claimed — you are the Assignee.");
      } else {
        updated = await transitionCase(c.id, {
          toStatus: action.toStatus,
          comment: comment || undefined,
          version: c.version,
        });
        setStatusMsg(`Moved to ${STATUS_LABELS[action.toStatus]}.`);
        setComment("");
      }
      setCase(updated);
      setHistory(await listCaseTransitions(c.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action failed");
    } finally {
      setActing(false);
    }
  }

  async function onAssign(e: FormEvent) {
    e.preventDefault();
    if (!c || !assigneeId) return;
    setError(null);
    setStatusMsg(null);
    setActing(true);
    try {
      const updated = await assignCase(c.id, assigneeId, c.version);
      setCase(updated);
      setHistory(await listCaseTransitions(c.id));
      setStatusMsg("Assigned reviewer.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Assign failed");
    } finally {
      setActing(false);
    }
  }

  async function onPostComment(e: FormEvent) {
    e.preventDefault();
    if (!c || !threadBody.trim()) return;
    setError(null);
    setStatusMsg(null);
    setActing(true);
    try {
      await addCaseComment(c.id, threadBody.trim());
      setComments(await listCaseComments(c.id));
      setThreadBody("");
      setStatusMsg("Comment posted.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Comment failed");
    } finally {
      setActing(false);
    }
  }

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 text-slate-600">
        Loading case…
      </main>
    );
  }

  if (needsLogin) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6">
        <p className="text-sm text-slate-600">Sign in to view this case.</p>
        <Link
          href="/login"
          className="rounded-md bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800"
        >
          Sign in
        </Link>
      </main>
    );
  }

  if (!c) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6">
        <p className="text-sm text-slate-600">{error ?? "Case not found."}</p>
        <Link href="/board" className="text-sm font-medium text-teal-700 hover:underline">
          Back to board
        </Link>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8 text-slate-900 sm:px-6">
      <div className="mx-auto max-w-3xl">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm font-medium uppercase tracking-widest text-teal-700">
              CareBridge
            </p>
            <p className="mt-1 text-sm text-teal-700">{c.caseNumber}</p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight">{c.title}</h1>
            <p className="mt-2 text-sm text-slate-600">
              {STATUS_LABELS[c.status]} · {c.priority} · {c.type.replaceAll("_", " ")}
            </p>
          </div>
          <Link href="/board" className="text-sm font-medium text-teal-700 hover:underline">
            Board
          </Link>
        </div>

        {error && (
          <p className="mt-6 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}
        {statusMsg && (
          <p className="mt-6 rounded-md border border-teal-200 bg-teal-50 px-3 py-2 text-sm text-teal-800">
            {statusMsg}
          </p>
        )}

        <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold">Details</h2>
          <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">Patient</dt>
              <dd className="font-medium">
                {c.patientDisplayName} ({c.patientRef})
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Version</dt>
              <dd className="font-medium">{c.version}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Creator</dt>
              <dd className="font-mono text-xs">{c.createdBy}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Assignee</dt>
              <dd className="font-mono text-xs">{c.assigneeId ?? "—"}</dd>
            </div>
          </dl>
          {c.description && (
            <div className="mt-4">
              <p className="text-sm text-slate-500">Description</p>
              <p className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{c.description}</p>
            </div>
          )}
        </section>

        <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold">Actions</h2>
          {isTerminal(c.status) ? (
            <p className="mt-2 text-sm text-slate-600">
              This Case is {STATUS_LABELS[c.status].toLowerCase()} and frozen — no edits,
              transitions, or new comments.
            </p>
          ) : (
            <>
              {actions.length === 0 && role !== "ORG_ADMIN" && (
                <p className="mt-2 text-sm text-slate-600">
                  No actions available for your role.
                </p>
              )}

              {(actions.some((a) => a.kind === "transition") || actions.length > 0) && (
                <div className="mt-4 space-y-3">
                  {actions.some((a) => a.kind === "transition") && (
                    <label className="block text-sm">
                      <span className="font-medium text-slate-700">
                        Transition note (optional)
                      </span>
                      <textarea
                        className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                        rows={2}
                        value={comment}
                        onChange={(e) => setComment(e.target.value)}
                      />
                    </label>
                  )}
                  <div className="flex flex-wrap gap-2">
                    {actions.map((action) => (
                      <button
                        key={action.label}
                        type="button"
                        disabled={acting}
                        onClick={() => void runAction(action)}
                        className="rounded-md bg-teal-700 px-3 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60"
                      >
                        {action.label}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {role === "ORG_ADMIN" && reviewers.length > 0 && (
                <form onSubmit={onAssign} className="mt-6 border-t border-slate-100 pt-4">
                  <h3 className="text-sm font-semibold text-slate-800">Assign reviewer</h3>
                  <div className="mt-2 flex flex-wrap items-end gap-2">
                    <label className="block flex-1 text-sm">
                      <span className="font-medium text-slate-700">Reviewer</span>
                      <select
                        className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                        value={assigneeId}
                        onChange={(e) => setAssigneeId(e.target.value)}
                        required
                      >
                        <option value="">Select…</option>
                        {reviewers.map((r) => (
                          <option key={r.id} value={r.id}>
                            {r.fullName} ({r.email})
                          </option>
                        ))}
                      </select>
                    </label>
                    <button
                      type="submit"
                      disabled={acting || !assigneeId}
                      className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50 disabled:opacity-60"
                    >
                      Assign
                    </button>
                  </div>
                </form>
              )}
            </>
          )}
        </section>

        <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold">Comments</h2>
          {comments.length === 0 ? (
            <p className="mt-2 text-sm text-slate-600">No comments yet.</p>
          ) : (
            <ol className="mt-4 space-y-3">
              {comments.map((item) => (
                <li
                  key={item.id}
                  className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-sm"
                >
                  <p className="whitespace-pre-wrap text-slate-800">{item.body}</p>
                  <p className="mt-1 text-xs text-slate-400">
                    {new Date(item.createdAt).toLocaleString()} ·{" "}
                    <span className="font-mono">{item.authorId.slice(0, 8)}…</span>
                  </p>
                </li>
              ))}
            </ol>
          )}
          {canPostComment(role, c.status) ? (
            <form onSubmit={onPostComment} className="mt-4 border-t border-slate-100 pt-4">
              <label className="block text-sm">
                <span className="font-medium text-slate-700">Add a comment</span>
                <textarea
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                  rows={3}
                  value={threadBody}
                  onChange={(e) => setThreadBody(e.target.value)}
                  required
                />
              </label>
              <button
                type="submit"
                disabled={acting || !threadBody.trim()}
                className="mt-2 rounded-md bg-teal-700 px-3 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60"
              >
                Post comment
              </button>
            </form>
          ) : (
            <p className="mt-4 text-sm text-slate-500">
              {role === "AUDITOR"
                ? "AUDITOR can read comments but cannot post."
                : isTerminal(c.status)
                  ? "Comments are closed on terminal Cases."
                  : null}
            </p>
          )}
        </section>

        <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold">History</h2>
          {history.length === 0 ? (
            <p className="mt-2 text-sm text-slate-600">No transitions yet.</p>
          ) : (
            <ol className="mt-4 space-y-3">
              {history.map((t) => (
                <li
                  key={t.id}
                  className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-sm"
                >
                  <p className="font-medium text-slate-900">
                    {STATUS_LABELS[t.fromStatus]} → {STATUS_LABELS[t.toStatus]}
                  </p>
                  {t.comment && <p className="mt-0.5 text-slate-600">{t.comment}</p>}
                  <p className="mt-1 text-xs text-slate-400">
                    {new Date(t.createdAt).toLocaleString()}
                  </p>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </main>
  );
}
