import { DEPLOYED_API_URL, resolveApiBaseUrl } from './config';

describe('resolveApiBaseUrl', () => {
  it('uses the dev server host so Expo Go on the phone reaches the PC', () => {
    expect(resolveApiBaseUrl(undefined, '192.168.0.10:8081')).toBe('http://192.168.0.10:8080');
  });

  it('prefers EXPO_PUBLIC_API_URL and drops trailing slashes', () => {
    expect(resolveApiBaseUrl('https://api.example.com/', '192.168.0.10:8081')).toBe('https://api.example.com');
  });

  it('uses the deployed API in an installed app (no dev server), e.g. after an over-the-air update', () => {
    expect(resolveApiBaseUrl(undefined, null)).toBe(DEPLOYED_API_URL);
    expect(DEPLOYED_API_URL).toBe('https://supermarket-agent-api.onrender.com');
  });
});
