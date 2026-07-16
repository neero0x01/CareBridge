export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 bg-slate-50 px-6 text-slate-900">
      <div className="max-w-lg text-center">
        <p className="text-sm font-medium uppercase tracking-widest text-teal-700">
          CareBridge
        </p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">
          Clinical workflow platform
        </h1>
        <p className="mt-4 text-base leading-relaxed text-slate-600">
          Multi-tenant case review for synthetic demo data only. Not a medical
          device, not real PHI, not production clinical software.
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <a
            href="/login"
            className="rounded-md bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800"
          >
            Sign in
          </a>
          <a
            href="/register"
            className="rounded-md border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
          >
            Register tenant
          </a>
          <a
            href="/admin/users"
            className="rounded-md border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
          >
            Admin users
          </a>
        </div>
        <p className="mt-6 text-sm text-slate-500">
          Cases and board UI arrive in later milestones.
        </p>
      </div>
    </main>
  );
}
