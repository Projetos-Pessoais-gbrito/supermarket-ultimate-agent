import { API_BASE_URL } from '../config';
import type { ProblemDetail } from './types';

/** status 0 means the backend could not be reached at all. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail?: string,
  ) {
    super(detail ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  token?: string | null;
};

export async function apiRequest<T>(path: string, { method = 'GET', body, token }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0);
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblemDetail(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function readProblemDetail(response: Response): Promise<string | undefined> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    return problem.detail ?? problem.title;
  } catch {
    return undefined;
  }
}
