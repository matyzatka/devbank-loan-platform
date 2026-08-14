import type { ApiProblem } from './types'

/** Typed error that preserves the backend's RFC 9457 payload for contextual UI rendering. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ApiProblem,
  ) {
    super(problem.detail ?? problem.title ?? 'Požadavek se nepodařilo zpracovat.')
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  // Centralizing protocol concerns keeps feature modules focused on domain-specific endpoints.
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    // Error bodies can be empty at proxy/network boundaries; callers still receive a stable error type.
    const problem = (await response.json().catch(() => ({}))) as ApiProblem
    throw new ApiError(response.status, problem)
  }

  return response.json() as Promise<T>
}
