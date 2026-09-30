import { API_BASE_URL } from '../config';
import type { ProblemDetail } from './types';

/** status 0 means the backend could not be reached at all. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail?: string,
    readonly code?: string,
  ) {
    super(detail ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
  }
}

/** Without a limit a stalled connection (Wi-Fi drop, sleeping phone) would spin forever. */
export const DEFAULT_TIMEOUT_MS = 20_000;
/** Imports wait for SEFAZ; the backend gives up on SEFAZ after 45 s, so allow a little more. */
export const IMPORT_TIMEOUT_MS = 60_000;

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  token?: string | null;
  timeoutMs?: number;
};

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return (await apiRequestWithStatus<T>(path, options)).data;
}

/** Like {@link apiRequest}, also returning the HTTP status (e.g. 201 created vs 200 already existed). */
export async function apiRequestWithStatus<T>(
  path: string,
  { method = 'GET', body, token, timeoutMs = DEFAULT_TIMEOUT_MS }: RequestOptions = {},
): Promise<{ data: T; status: number }> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    let response: Response;
    try {
      response = await fetch(`${API_BASE_URL}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
    } catch {
      throw new ApiError(0, undefined, controller.signal.aborted ? 'TIMEOUT' : undefined);
    }

    if (!response.ok) {
      const problem = await readProblemDetail(response);
      throw new ApiError(response.status, problem?.detail ?? problem?.title, problem?.code);
    }
    if (response.status === 204) {
      return { data: undefined as T, status: 204 };
    }
    return { data: (await response.json()) as T, status: response.status };
  } finally {
    clearTimeout(timer);
  }
}

async function readProblemDetail(response: Response): Promise<ProblemDetail | undefined> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return undefined;
  }
}
