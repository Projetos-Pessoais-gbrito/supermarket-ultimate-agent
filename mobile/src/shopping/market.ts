import * as SecureStore from 'expo-secure-store';

const PREFERENCE_KEY = 'prefs.shoppingMarket';

/** The market the list is priced for, remembered between visits; null when none was chosen. */
export async function loadMarket(): Promise<string | null> {
  try {
    return (await SecureStore.getItemAsync(PREFERENCE_KEY)) || null;
  } catch {
    return null;
  }
}

export function saveMarket(market: string | null): Promise<void> {
  const saving = market ? SecureStore.setItemAsync(PREFERENCE_KEY, market) : SecureStore.deleteItemAsync(PREFERENCE_KEY);
  return saving.catch(() => undefined);
}
