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

export type ApiErrorBody = {
  code?: string;
  message?: string;
};

async function parseError(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as ApiErrorBody;
    return body.message ?? res.statusText;
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

export async function me(accessToken: string): Promise<MeResponse> {
  const res = await fetch(`${API_URL}/api/v1/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) {
    throw new Error(await parseError(res));
  }
  return res.json() as Promise<MeResponse>;
}

const TOKEN_KEY = "carebridge_access_token";

export function storeAccessToken(token: string): void {
  if (typeof window !== "undefined") {
    localStorage.setItem(TOKEN_KEY, token);
  }
}

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function clearAccessToken(): void {
  if (typeof window !== "undefined") {
    localStorage.removeItem(TOKEN_KEY);
  }
}
