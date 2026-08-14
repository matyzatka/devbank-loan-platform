import type { ApiProblem } from './types'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ApiProblem,
  ) {
    super(problem.detail ?? problem.title ?? 'Požadavek se nepodařilo zpracovat.')
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ApiProblem
    throw new ApiError(response.status, problem)
  }

  return response.json() as Promise<T>
}
