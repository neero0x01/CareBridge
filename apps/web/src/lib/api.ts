const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type TokenResponse = {
  accessToken: string;
  refreshToken: string | null;
  expiresIn: number;
};

export type TenantResponse = {
  id: string;
  name: string;
  slug: string;
  createdAt: string;
};

export type UserResponse = {
  id: string;
  tenantId: string;
  email: string;
  fullName: string;
  role: string;
  active: boolean;
  mustChangePassword: boolean;
  createdAt: string;
};

export type RegisterTenantResponse = {
  tenant: TenantResponse;
  user: UserResponse;
  tokens: {
    accessToken: string;
    refreshToken: string | null;
    expiresIn: number;
  };
};

export type MeResponse = {
  user: UserResponse;
  tenant: TenantResponse;
};

export type CaseType =
  | "REFERRAL"
  | "PRESCRIPTION_REVIEW"
  | "DISCHARGE"
  | "LAB_FOLLOWUP"
  | "OTHER";

export type CasePriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export type CaseStatus =
  | "TO_DO"
  | "IN_REVIEW"
  | "NEEDS_INFO"
  | "APPROVED"
  | "REJECTED";

export type CaseResponse = {
  id: string;
  tenantId: string;
  caseNumber: string;
  title: string;
  type: CaseType;
  priority: CasePriority;
  status: CaseStatus;
  patientDisplayName: string;
  patientRef: string;
  description: string | null;
  createdBy: string;
  assigneeId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type CasePageResponse = {
  content: CaseResponse[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CreateCaseInput = {
  title: string;
  type: CaseType;
  priority: CasePriority;
  patientDisplayName: string;
  patientRef: string;
  description?: string;
};

export type ApiErrorBody = {
  code?: string;
  message?: string;
};

async function parseError(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as ApiErrorBody;
    if (body.code && body.message) {
      return `${body.code}: ${body.message}`;
    }
    return body.message ?? body.code ?? res.statusText;
  } catch {
    return res.statusText || "Request failed";
  }
}

export async function login(input: {
  tenantSlug: string;
  email: string;
  password: string;
}): Promise<TokenResponse> {
  const res = await fetch(`${API_URL}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<TokenResponse>;
}

export async function registerTenant(input: {
  tenantName: string;
  slug: string;
  adminEmail: string;
  adminPassword: string;
  adminFullName: string;
}): Promise<RegisterTenantResponse> {
  const res = await fetch(`${API_URL}/api/v1/auth/register-tenant`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<RegisterTenantResponse>;
}

export async function me(accessToken?: string): Promise<MeResponse> {
  const res = accessToken
    ? await fetch(`${API_URL}/api/v1/me`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    : await apiFetch("/api/v1/me");
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<MeResponse>;
}

export async function listUsers(accessToken?: string): Promise<UserResponse[]> {
  const res = accessToken
    ? await fetch(`${API_URL}/api/v1/users`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    : await apiFetch("/api/v1/users");
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<UserResponse[]>;
}

export async function inviteUser(
  input: {
    email: string;
    fullName: string;
    role: string;
    temporaryPassword: string;
  },
  accessToken?: string,
): Promise<UserResponse> {
  const body = JSON.stringify(input);
  const res = accessToken
    ? await fetch(`${API_URL}/api/v1/users`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body,
      })
    : await apiFetch("/api/v1/users", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
      });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<UserResponse>;
}

export async function patchUser(
  userId: string,
  input: { role?: string; active?: boolean },
  accessToken?: string,
): Promise<UserResponse> {
  const body = JSON.stringify(input);
  const res = accessToken
    ? await fetch(`${API_URL}/api/v1/users/${userId}`, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body,
      })
    : await apiFetch(`/api/v1/users/${userId}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body,
      });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<UserResponse>;
}

export async function changePassword(
  input: { currentPassword: string; newPassword: string },
  accessToken?: string,
): Promise<UserResponse> {
  const body = JSON.stringify(input);
  const res = accessToken
    ? await fetch(`${API_URL}/api/v1/auth/change-password`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body,
      })
    : await apiFetch("/api/v1/auth/change-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
      });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<UserResponse>;
}

const ACCESS_TOKEN_KEY = "carebridge_access_token";
const REFRESH_TOKEN_KEY = "carebridge_refresh_token";

export function storeTokens(tokens: {
  accessToken: string;
  refreshToken: string | null;
}): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
  if (tokens.refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

/** Clears both access and refresh tokens from local storage. */
export function clearSession(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

/** @deprecated Use clearSession */
export function clearAccessToken(): void {
  clearSession();
}

function accessTokenExpired(token: string, skewSeconds = 30): boolean {
  try {
    const payload = token.split(".")[1];
    if (!payload) return true;
    const json = JSON.parse(
      atob(payload.replace(/-/g, "+").replace(/_/g, "/")),
    ) as { exp?: number };
    if (typeof json.exp !== "number") return true;
    return json.exp * 1000 <= Date.now() + skewSeconds * 1000;
  } catch {
    return true;
  }
}

export async function refreshSession(
  refreshToken: string,
): Promise<TokenResponse> {
  const res = await fetch(`${API_URL}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  const tokens = (await res.json()) as TokenResponse;
  storeTokens(tokens);
  return tokens;
}

/**
 * Returns a non-expired access token, rotating via refresh when needed.
 */
export async function ensureAccessToken(): Promise<string | null> {
  const access = getAccessToken();
  if (access && !accessTokenExpired(access)) {
    return access;
  }
  const refresh = getRefreshToken();
  if (!refresh) {
    return null;
  }
  try {
    const tokens = await refreshSession(refresh);
    return tokens.accessToken;
  } catch {
    clearSession();
    return null;
  }
}

export async function listCases(params?: {
  status?: CaseStatus;
  assignee?: string;
  priority?: CasePriority;
  q?: string;
  page?: number;
  size?: number;
}): Promise<CasePageResponse> {
  const search = new URLSearchParams();
  if (params?.status) search.set("status", params.status);
  if (params?.assignee) search.set("assignee", params.assignee);
  if (params?.priority) search.set("priority", params.priority);
  if (params?.q) search.set("q", params.q);
  if (params?.page != null) search.set("page", String(params.page));
  if (params?.size != null) search.set("size", String(params.size));
  const qs = search.toString();
  const res = await apiFetch(`/api/v1/cases${qs ? `?${qs}` : ""}`);
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CasePageResponse>;
}

export async function createCase(
  input: CreateCaseInput,
): Promise<CaseResponse> {
  const res = await apiFetch("/api/v1/cases", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CaseResponse>;
}

export async function getCase(caseId: string): Promise<CaseResponse> {
  const res = await apiFetch(`/api/v1/cases/${caseId}`);
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CaseResponse>;
}

export type CaseTransitionResponse = {
  id: string;
  caseId: string;
  fromStatus: CaseStatus;
  toStatus: CaseStatus;
  actorId: string;
  comment: string | null;
  createdAt: string;
};

export async function claimCase(
  caseId: string,
  version: number,
): Promise<CaseResponse> {
  const res = await apiFetch(`/api/v1/cases/${caseId}/claim`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ version }),
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CaseResponse>;
}

export async function assignCase(
  caseId: string,
  assigneeId: string,
  version: number,
): Promise<CaseResponse> {
  const res = await apiFetch(`/api/v1/cases/${caseId}/assign`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ assigneeId, version }),
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CaseResponse>;
}

export async function transitionCase(
  caseId: string,
  input: { toStatus: CaseStatus; comment?: string; version: number },
): Promise<CaseResponse> {
  const res = await apiFetch(`/api/v1/cases/${caseId}/transitions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CaseResponse>;
}

export async function listCaseTransitions(
  caseId: string,
): Promise<CaseTransitionResponse[]> {
  const res = await apiFetch(`/api/v1/cases/${caseId}/transitions`);
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CaseTransitionResponse[]>;
}

export type CaseCommentResponse = {
  id: string;
  caseId: string;
  authorId: string;
  body: string;
  createdAt: string;
};

export async function listCaseComments(
  caseId: string,
): Promise<CaseCommentResponse[]> {
  const res = await apiFetch(`/api/v1/cases/${caseId}/comments`);
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CaseCommentResponse[]>;
}

export async function addCaseComment(
  caseId: string,
  body: string,
): Promise<CaseCommentResponse> {
  const res = await apiFetch(`/api/v1/cases/${caseId}/comments`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ body }),
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<CaseCommentResponse>;
}

export type AuditLogResponse = {
  id: string;
  tenantId: string;
  actorId: string | null;
  action: string;
  entityType: string;
  entityId: string;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
  ip: string | null;
  createdAt: string;
};

export type AuditPageResponse = {
  content: AuditLogResponse[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export async function listAudit(params?: {
  entityType?: string;
  entityId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}): Promise<AuditPageResponse> {
  const search = new URLSearchParams();
  if (params?.entityType) search.set("entityType", params.entityType);
  if (params?.entityId) search.set("entityId", params.entityId);
  if (params?.from) search.set("from", params.from);
  if (params?.to) search.set("to", params.to);
  if (params?.page != null) search.set("page", String(params.page));
  if (params?.size != null) search.set("size", String(params.size));
  const qs = search.toString();
  const res = await apiFetch(`/api/v1/audit${qs ? `?${qs}` : ""}`);
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<AuditPageResponse>;
}

/** Authenticated fetch that refreshes once on 401. */
export async function apiFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  let token = await ensureAccessToken();
  if (!token) {
    throw new Error("UNAUTHORIZED: Not signed in");
  }

  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${token}`);

  const res = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (res.status !== 401) {
    return res;
  }

  const refresh = getRefreshToken();
  if (!refresh) {
    clearSession();
    return res;
  }

  try {
    const tokens = await refreshSession(refresh);
    token = tokens.accessToken;
  } catch {
    clearSession();
    return res;
  }

  headers.set("Authorization", `Bearer ${token}`);
  return fetch(`${API_URL}${path}`, { ...init, headers });
}
