import { ApiError, apiRequest } from './client';
import { errorMessage } from './messages';

jest.mock('../config', () => ({ API_BASE_URL: 'http://api.test' }));

const fetchMock = jest.fn();
globalThis.fetch = fetchMock;

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('apiRequest', () => {
  beforeEach(() => fetchMock.mockReset());

  it('sends JSON with the bearer token and returns the parsed body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, { id: 1 }));

    const result = await apiRequest<{ id: number }>('/api/receipts', {
      method: 'POST',
      body: { qrCodeUrl: 'x' },
      token: 'abc',
    });

    expect(result).toEqual({ id: 1 });
    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/receipts', {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', Authorization: 'Bearer abc' },
      body: '{"qrCodeUrl":"x"}',
      signal: expect.any(AbortSignal),
    });
  });

  it('turns problem details into an ApiError with status and detail', async () => {
    fetchMock.mockResolvedValue(jsonResponse(409, { status: 409, detail: 'This e-mail is already registered' }));

    await expect(apiRequest('/api/auth/register', { method: 'POST', body: {} })).rejects.toEqual(
      new ApiError(409, 'This e-mail is already registered'),
    );
  });

  it('reports an unreachable backend as status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Network request failed'));

    await expect(apiRequest('/api/me')).rejects.toMatchObject({ status: 0 });
  });
});

describe('errorMessage', () => {
  it('explains how to fix an unreachable backend', () => {
    expect(errorMessage(new ApiError(0))).toContain('mesma rede Wi-Fi');
  });

  it('explains that SEFAZ is down', () => {
    expect(errorMessage(new ApiError(503))).toContain('SEFAZ');
  });

  it('has a generic message for unexpected errors', () => {
    expect(errorMessage(new Error('boom'))).toBe('Algo deu errado. Tente novamente.');
  });
});

describe('error codes', () => {
  beforeEach(() => fetchMock.mockReset());

  it('keeps the problem code so screens can explain specific errors', async () => {
    fetchMock.mockResolvedValue(jsonResponse(422, { status: 422, detail: 'key only', code: 'KEY_ONLY_LINK' }));

    await expect(apiRequest('/api/receipts', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 422,
      code: 'KEY_ONLY_LINK',
    });
  });

  it('explains key-only links in Portuguese', () => {
    expect(errorMessage(new ApiError(422, 'key only', 'KEY_ONLY_LINK'))).toContain('captcha');
  });
});

describe('timeouts', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    jest.useFakeTimers();
  });
  afterEach(() => jest.useRealTimers());

  it('gives up on a stalled request instead of waiting forever', async () => {
    fetchMock.mockImplementation(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(new Error('aborted')));
        }),
    );

    const request = apiRequest('/api/insights/spending', { timeoutMs: 1_000 });
    jest.advanceTimersByTime(1_000);

    await expect(request).rejects.toMatchObject({ status: 0, code: 'TIMEOUT' });
  });

  it('explains a timeout in Portuguese', () => {
    expect(errorMessage(new ApiError(0, undefined, 'TIMEOUT'))).toContain('demorou demais');
  });
});

describe('rate limiting', () => {
  it('asks the user to wait after too many attempts', () => {
    expect(errorMessage(new ApiError(429))).toBe('Muitas tentativas. Tente de novo em alguns minutos.');
  });
});
