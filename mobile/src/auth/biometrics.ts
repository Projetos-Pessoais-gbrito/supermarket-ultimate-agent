import * as LocalAuthentication from 'expo-local-authentication';
import * as SecureStore from 'expo-secure-store';

const PREFERENCE_KEY = 'auth.biometricLock';

/** Coming back within this time does not ask again (e.g. switching to the camera or WhatsApp). */
export const LOCK_GRACE_MS = 60_000;

/** The phone has a fingerprint/face sensor and the owner registered at least one. */
export async function isBiometricAvailable(): Promise<boolean> {
  return (await LocalAuthentication.hasHardwareAsync()) && (await LocalAuthentication.isEnrolledAsync());
}

/**
 * Asks for the fingerprint (or face). The phone's own PIN is accepted as a fallback, as banking
 * apps do, so a wet finger never locks the owner out.
 */
export async function authenticate(promptMessage: string): Promise<boolean> {
  const result = await LocalAuthentication.authenticateAsync({
    promptMessage,
    cancelLabel: 'Cancelar',
    fallbackLabel: 'Usar senha do celular',
  });
  return result.success;
}

export async function isBiometricLockEnabled(): Promise<boolean> {
  return (await SecureStore.getItemAsync(PREFERENCE_KEY)) === 'on';
}

export async function setBiometricLockEnabled(enabled: boolean): Promise<void> {
  if (enabled) {
    await SecureStore.setItemAsync(PREFERENCE_KEY, 'on');
  } else {
    await SecureStore.deleteItemAsync(PREFERENCE_KEY);
  }
}

/** Whether returning to the app after {@code backgroundedAt} requires unlocking again. */
export function shouldLockAfterBackground(backgroundedAt: number | null, now = Date.now()): boolean {
  return backgroundedAt !== null && now - backgroundedAt > LOCK_GRACE_MS;
}
