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
        <p className="mt-8 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-500">
          Platform shell is up. Auth, cases, and the board arrive in later
          milestones.
        </p>
      </div>
    </main>
  );
}
