"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  clearAccessToken,
  getAccessToken,
  inviteUser,
  listUsers,
  me,
  type UserResponse,
} from "@/lib/api";

const ROLES = ["ORG_ADMIN", "CLINICIAN", "REVIEWER", "AUDITOR"] as const;

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [tenantName, setTenantName] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [needsLogin, setNeedsLogin] = useState(false);

  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [role, setRole] = useState<string>("CLINICIAN");
  const [temporaryPassword, setTemporaryPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    const token = getAccessToken();
    if (!token) {
      setNeedsLogin(true);
      setLoading(false);
      return;
    }
    setError(null);
    try {
      const profile = await me(token);
      if (profile.user.role !== "ORG_ADMIN") {
        setForbidden(true);
        setLoading(false);
        return;
      }
      setTenantName(profile.tenant.name);
      const list = await listUsers(token);
      setUsers(list);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load users";
      if (message.toLowerCase().includes("unauthorized")) {
        clearAccessToken();
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
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onInvite(e: FormEvent) {
    e.preventDefault();
    const token = getAccessToken();
    if (!token) {
      setNeedsLogin(true);
      return;
    }
    setError(null);
    setStatus(null);
    setSubmitting(true);
    try {
      const created = await inviteUser(token, {
        email,
        fullName,
        role,
        temporaryPassword,
      });
      setStatus(
        `Invited ${created.fullName} (${created.email}) as ${created.role}. Share the temporary password out of band.`,
      );
      setEmail("");
      setFullName("");
      setRole("CLINICIAN");
      setTemporaryPassword("");
      const list = await listUsers(token);
      setUsers(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invite failed");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 text-slate-600">
        Loading users…
      </main>
    );
  }

  if (needsLogin) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6 text-slate-900">
        <p className="text-sm text-slate-600">Sign in as ORG_ADMIN to manage users.</p>
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
          Only ORG_ADMIN can manage users. If you were invited with a temporary
          password, change it first via the API.
        </p>
        <Link href="/" className="text-sm font-medium text-teal-700 hover:underline">
          Back home
        </Link>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-50 px-6 py-10 text-slate-900">
      <div className="mx-auto max-w-4xl">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-medium uppercase tracking-widest text-teal-700">
              CareBridge
            </p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight">Users</h1>
            <p className="mt-1 text-sm text-slate-600">
              {tenantName ? `${tenantName} · ` : ""}
              Invite Users with a temporary password (no email in v1).
            </p>
          </div>
          <Link href="/" className="text-sm font-medium text-teal-700 hover:underline">
            Home
          </Link>
        </div>

        {error && (
          <p className="mt-6 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}
        {status && (
          <p className="mt-6 rounded-md border border-teal-200 bg-teal-50 px-3 py-2 text-sm text-teal-800">
            {status}
          </p>
        )}

        <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold">Invite user</h2>
          <form onSubmit={onInvite} className="mt-4 grid gap-4 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="font-medium text-slate-700">Full name</span>
              <input
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
              />
            </label>
            <label className="block text-sm">
              <span className="font-medium text-slate-700">Email</span>
              <input
                type="email"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </label>
            <label className="block text-sm">
              <span className="font-medium text-slate-700">Role</span>
              <select
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                value={role}
                onChange={(e) => setRole(e.target.value)}
              >
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm">
              <span className="font-medium text-slate-700">Temporary password</span>
              <input
                type="password"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none ring-teal-600 focus:ring-2"
                value={temporaryPassword}
                onChange={(e) => setTemporaryPassword(e.target.value)}
                minLength={8}
                required
              />
            </label>
            <div className="sm:col-span-2">
              <button
                type="submit"
                disabled={submitting}
                className="rounded-md bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60"
              >
                {submitting ? "Inviting…" : "Invite user"}
              </button>
            </div>
          </form>
        </section>

        <section className="mt-8 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 px-6 py-4">
            <h2 className="text-lg font-semibold">Tenant users</h2>
          </div>
          {users.length === 0 ? (
            <p className="px-6 py-8 text-sm text-slate-600">No users yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-6 py-3 font-medium">Name</th>
                    <th className="px-6 py-3 font-medium">Email</th>
                    <th className="px-6 py-3 font-medium">Role</th>
                    <th className="px-6 py-3 font-medium">Active</th>
                    <th className="px-6 py-3 font-medium">Must change password</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {users.map((u) => (
                    <tr key={u.id}>
                      <td className="px-6 py-3 font-medium text-slate-900">{u.fullName}</td>
                      <td className="px-6 py-3 text-slate-700">{u.email}</td>
                      <td className="px-6 py-3 text-slate-700">{u.role}</td>
                      <td className="px-6 py-3 text-slate-700">{u.active ? "yes" : "no"}</td>
                      <td className="px-6 py-3 text-slate-700">
                        {u.mustChangePassword ? "yes" : "no"}
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
