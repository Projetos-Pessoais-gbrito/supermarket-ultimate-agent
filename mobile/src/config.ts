import Constants from 'expo-constants';

const API_PORT = 8080;

/** The API on Render (docs/DEPLOY.md). */
export const DEPLOYED_API_URL = 'https://supermarket-agent-api.onrender.com';

/**
 * Where the backend lives.
 *
 * 1. `EXPO_PUBLIC_API_URL` when set (required for `expo start --tunnel` or a deployed API).
 * 2. Otherwise the PC running the Expo dev server: Expo Go already knows its LAN address
 *    (`hostUri`, e.g. `192.168.0.10:8081`), and the backend runs on the same machine.
 * 3. No dev server means an installed app. Over-the-air updates are bundled without the build
 *    profile's env, so they land here and must still reach the deployed API.
 */
export function resolveApiBaseUrl(envUrl: string | undefined, hostUri: string | null | undefined): string {
  if (envUrl) {
    return envUrl.replace(/\/+$/, '');
  }
  const host = hostUri?.split(':')[0];
  return host ? `http://${host}:${API_PORT}` : DEPLOYED_API_URL;
}

export const API_BASE_URL = resolveApiBaseUrl(process.env.EXPO_PUBLIC_API_URL, Constants.expoConfig?.hostUri);
