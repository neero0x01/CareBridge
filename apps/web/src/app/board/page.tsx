"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  clearSession,
  createCase,
  ensureAccessToken,
  listCases,
  me,
  type CasePriority,
  type CaseResponse,
  type CaseStatus,
  type CaseType,
} from "@/lib/api";

const STATUSES: { key: CaseStatus; label: string }[] = [
  { key: "TO_DO", label: "To do" },
  { key: "IN_REVIEW", label: "In review" },
  { key: "NEEDS_INFO", label: "Needs info" },
  { key: "APPROVED", label: "Approved" },
  { key: "REJECTED", label: "Rejected" },
];

const TYPES: CaseType[] = [
  "REFERRAL",
  "PRESCRIPTION_REVIEW",
  "DISCHARGE",
  "LAB_FOLLOWUP",
  "OTHER",
];

const PRIORITIES: CasePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const PRIORITY_STYLES: Record<CasePriority, string> = {
  LOW: "bg-slate-100 text-slate-700",
  MEDIUM: "bg-sky-100 text-sky-800",
  HIGH: "bg-amber-100 text-amber-900",
  URGENT: "bg-red-100 text-red-800",
};

export default function BoardPage() {
  const [cases, setCases] = useState<CaseResponse[]>([]);
  const [tenantName, setTenantName] = useState("");
  const [role, setRole] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusMsg, setStatusMsg] = useState<string | null>(null);
  const [needsLogin, setNeedsLogin] = useState(false);

  const [title, setTitle] = useState("");
  const [type, setType] = useState<CaseType>("REFERRAL");
  const [priority, setPriority] = useState<CasePriority>("MEDIUM");
  const [patientDisplayName, setPatientDisplayName] = useState("");
  const [patientRef, setPatientRef] = useState("");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [showCreate, setShowCreate] = useState(false);

  const canCreate = role === "ORG_ADMIN" || role === "CLINICIAN";

  const byStatus = useMemo(() => {
    const map = new Map<CaseStatus, CaseResponse[]>();
    for (const col of STATUSES) {
      map.set(col.key, []);
    }
    for (const c of cases) {
      const list = map.get(c.status);
      if (list) {
        list.push(c);
      }
    }
    return map;
  }, [cases]);

  const load = useCallback(async () => {
    const token = await ensureAccessToken();
    if (!token) {
      setNeedsLogin(true);
      setLoading(false);
      return;
    }
    setError(null);
    try {
      const profile = await me();
      setTenantName(profile.tenant.name);
      setRole(profile.user.role);
      const page = await listCases({ size: 100 });
      setCases(page.content);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load board";
      if (message.toLowerCase().includes("unauthorized")) {
        clearSession();
        setNeedsLogin(true);
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    if (!(await ensureAccessToken())) {
      setNeedsLogin(true);
      return;
    }
    setError(null);
    setStatusMsg(null);
    setSubmitting(true);
    try {
      const created = await createCase({
        title,
        type,
        priority,
        patientDisplayName,
        patientRef,
        description: description || undefined,
      });
      setStatusMsg(`Created ${created.caseNumber}: ${created.title}`);
      setTitle("");
      setType("REFERRAL");
      setPriority("MEDIUM");
      setPatientDisplayName("");
      setPatientRef("");
      setDescription("");
      setShowCreate(false);
      const page = await listCases({ size: 100 });
      setCases(page.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Create failed");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 text-slate-600">
        Loading board…
      </main>
    );
  }

  if (needsLogin) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6 text-slate-900">
        <p className="text-sm text-slate-600">Sign in to view the case board.</p>
        <Link
          href="/login"
          className="rounded-md bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800"
        >
          Sign in
        </Link>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-100 px-4 py-8 text-slate-900 sm:px-6">
      <div className="mx-auto max-w-[1400px]">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-medium uppercase tracking-widest text-teal-700">
              CareBridge
            </p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight">Board</h1>
            <p className="mt-1 text-sm text-slate-600">
              {tenantName ? `${tenantName} · ` : ""}
              Cases by status (tenant-wide)
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            {canCreate && (
              <button
                type="button"
                onClick={() => setShowCreate((v) => !v)}
                className="rounded-md bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800"
              >
                {showCreate ? "Hide form" : "New case"}
              </button>
            )}
            <Link
              href="/admin/users"
              className="text-sm font-medium text-teal-700 hover:underline"
            >
              Users
            </Link>
            <Link href="/" className="text-sm font-medium text-teal-700 hover:underline">
              Home
            </Link>
          </div>
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

        {canCreate && showCreate && (
          <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold">Create case</h2>
            <form onSubmit={onCreate} className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="block text-sm sm:col-span-2">
                <span className="font-medium text-slate-700">Title</span>
                <input
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </label>
              <label className="block text-sm">
                <span className="font-medium text-slate-700">Type</span>
                <select
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                  value={type}
                  onChange={(e) => setType(e.target.value as CaseType)}
                >
                  {TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-sm">
                <span className="font-medium text-slate-700">Priority</span>
                <select
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value as CasePriority)}
                >
                  {PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-sm">
                <span className="font-medium text-slate-700">Patient display name</span>
                <input
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                  value={patientDisplayName}
                  onChange={(e) => setPatientDisplayName(e.target.value)}
                  required
                />
              </label>
              <label className="block text-sm">
                <span className="font-medium text-slate-700">Patient ref</span>
                <input
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                  value={patientRef}
                  onChange={(e) => setPatientRef(e.target.value)}
                  placeholder="PAT-001"
                  required
                />
              </label>
              <label className="block text-sm sm:col-span-2">
                <span className="font-medium text-slate-700">Description</span>
                <textarea
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </label>
              <div className="sm:col-span-2">
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-md bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60"
                >
                  {submitting ? "Creating…" : "Create case"}
                </button>
              </div>
            </form>
          </section>
        )}

        <div className="mt-6 flex gap-4 overflow-x-auto pb-4">
          {STATUSES.map((col) => {
            const columnCases = byStatus.get(col.key) ?? [];
            return (
              <section
                key={col.key}
                className="flex w-72 shrink-0 flex-col rounded-xl border border-slate-200 bg-slate-50 shadow-sm"
              >
                <header className="flex items-center justify-between border-b border-slate-200 px-3 py-3">
                  <h2 className="text-sm font-semibold text-slate-800">{col.label}</h2>
                  <span className="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-slate-600 ring-1 ring-slate-200">
                    {columnCases.length}
                  </span>
                </header>
                <div className="flex flex-1 flex-col gap-2 p-2">
                  {columnCases.length === 0 ? (
                    <p className="px-2 py-6 text-center text-xs text-slate-500">No cases</p>
                  ) : (
                    columnCases.map((c) => (
                      <Link
                        key={c.id}
                        href={`/cases/${c.id}`}
                        className="block rounded-lg border border-slate-200 bg-white p-3 shadow-sm transition hover:border-teal-300 hover:shadow"
                      >
                        <div className="flex items-start justify-between gap-2">
                          <p className="text-xs font-medium text-teal-700">{c.caseNumber}</p>
                          <span
                            className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${PRIORITY_STYLES[c.priority]}`}
                          >
                            {c.priority}
                          </span>
                        </div>
                        <h3 className="mt-1 text-sm font-semibold leading-snug text-slate-900">
                          {c.title}
                        </h3>
                        <p className="mt-1 text-xs text-slate-600">
                          {c.patientDisplayName} · {c.patientRef}
                        </p>
                        <p className="mt-1 text-[11px] uppercase tracking-wide text-slate-400">
                          {c.type.replaceAll("_", " ")}
                        </p>
                      </Link>
                    ))
                  )}
                </div>
              </section>
            );
          })}
        </div>
      </div>
    </main>
  );
}
