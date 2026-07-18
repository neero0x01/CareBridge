"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  clearSession,
  ensureAccessToken,
  listAudit,
  me,
  type AuditLogResponse,
} from "@/lib/api";

const ENTITY_TYPES = ["", "Case", "User"] as const;

function formatJson(value: Record<string, unknown> | null): string {
  if (value == null) return "—";
  try {
    return JSON.stringify(value, null, 0);
  } catch {
    return String(value);
  }
}

function formatWhen(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export default function AdminAuditPage() {
  const [rows, setRows] = useState<AuditLogResponse[]>([]);
  const [tenantName, setTenantName] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [needsLogin, setNeedsLogin] = useState(false);
  const [totalElements, setTotalElements] = useState(0);

  const [entityType, setEntityType] = useState("");
  const [entityId, setEntityId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

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
      const role = profile.user.role;
      if (role !== "ORG_ADMIN" && role !== "AUDITOR") {
        setForbidden(true);
        setLoading(false);
        return;
      }
      setTenantName(profile.tenant.name);

      const page = await listAudit({
        entityType: entityType || undefined,
        entityId: entityId.trim() || undefined,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
        size: 50,
      });
      setRows(page.content);
      setTotalElements(page.totalElements);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load audit log";
      if (message.toLowerCase().includes("unauthorized")) {
        clearSession();
        setNeedsLogin(true);
      } else if (
        message.toLowerCase().includes("forbidden") ||
        message.includes("MUST_CHANGE_PASSWORD")
      ) {
        setForbidden(true);
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  }, [entityType, entityId, from, to]);

  useEffect(() => {
    void load();
  }, [load]);

  function onFilter(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    void load();
  }

  if (loading && rows.length === 0 && !error && !forbidden && !needsLogin) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 text-slate-600">
        Loading audit log…
      </main>
    );
  }

  if (needsLogin) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6 text-slate-900">
        <p className="text-sm text-slate-600">
          Sign in as ORG_ADMIN or AUDITOR to view the audit log.
        </p>
        <Link
          href="/login"
          className="rounded-md bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800"
        >
          Sign in
        </Link>
      </main>
    );
  }

  if (forbidden) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6 text-slate-900">
        <p className="text-sm text-slate-600">
          Only ORG_ADMIN and AUDITOR can view the full audit log.
        </p>
        <Link href="/" className="text-sm font-medium text-teal-700 hover:underline">
          Back home
        </Link>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-50 px-6 py-10 text-slate-900">
      <div className="mx-auto max-w-6xl">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-medium uppercase tracking-widest text-teal-700">
              CareBridge
            </p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight">Audit log</h1>
            <p className="mt-1 text-sm text-slate-600">
              {tenantName ? `${tenantName} · ` : ""}
              Immutable record of Case and User mutations.
            </p>
          </div>
          <div className="flex flex-wrap gap-3 text-sm">
            <Link href="/admin/users" className="font-medium text-teal-700 hover:underline">
              Users
            </Link>
            <Link href="/" className="font-medium text-teal-700 hover:underline">
              Home
            </Link>
          </div>
        </div>

        {error && (
          <p className="mt-6 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold">Filters</h2>
          <form
            onSubmit={onFilter}
            className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
          >
            <label className="block text-sm">
              <span className="font-medium text-slate-700">Entity type</span>
              <select
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                value={entityType}
                onChange={(e) => setEntityType(e.target.value)}
              >
                {ENTITY_TYPES.map((t) => (
                  <option key={t || "all"} value={t}>
                    {t || "All"}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm">
              <span className="font-medium text-slate-700">Entity ID</span>
              <input
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                value={entityId}
                onChange={(e) => setEntityId(e.target.value)}
                placeholder="UUID"
              />
            </label>
            <label className="block text-sm">
              <span className="font-medium text-slate-700">From</span>
              <input
                type="datetime-local"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </label>
            <label className="block text-sm">
              <span className="font-medium text-slate-700">To</span>
              <input
                type="datetime-local"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </label>
            <div className="sm:col-span-2 lg:col-span-4">
              <button
                type="submit"
                className="rounded-md bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800"
              >
                Apply filters
              </button>
            </div>
          </form>
        </section>

        <section className="mt-8 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
            <h2 className="text-lg font-semibold">Entries</h2>
            <p className="text-sm text-slate-500">{totalElements} total</p>
          </div>
          {rows.length === 0 ? (
            <p className="px-6 py-8 text-sm text-slate-600">No audit entries match.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-3 font-medium">When</th>
                    <th className="px-4 py-3 font-medium">Action</th>
                    <th className="px-4 py-3 font-medium">Entity</th>
                    <th className="px-4 py-3 font-medium">Actor</th>
                    <th className="px-4 py-3 font-medium">Before</th>
                    <th className="px-4 py-3 font-medium">After</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((row) => (
                    <tr key={row.id} className="align-top">
                      <td className="whitespace-nowrap px-4 py-3 text-slate-700">
                        {formatWhen(row.createdAt)}
                      </td>
                      <td className="px-4 py-3 font-medium text-slate-900">{row.action}</td>
                      <td className="px-4 py-3 text-slate-700">
                        <div>{row.entityType}</div>
                        <div className="max-w-[10rem] truncate font-mono text-xs text-slate-500">
                          {row.entityId}
                        </div>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-600">
                        {row.actorId ?? "system"}
                      </td>
                      <td className="max-w-xs px-4 py-3 font-mono text-xs text-slate-600">
                        <span className="line-clamp-3 break-all">{formatJson(row.before)}</span>
                      </td>
                      <td className="max-w-xs px-4 py-3 font-mono text-xs text-slate-600">
                        <span className="line-clamp-3 break-all">{formatJson(row.after)}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
