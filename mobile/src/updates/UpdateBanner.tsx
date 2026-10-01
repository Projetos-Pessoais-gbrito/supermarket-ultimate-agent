import * as Updates from 'expo-updates';
import { useEffect, useState } from 'react';
import { AppState, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { makeStyles, spacing } from '../ui/theme';

/** Above the tab bar, so the banner never hides the tabs. */
const TAB_BAR_CLEARANCE = 64;

/**
 * Over-the-air updates (`eas update`, docs/DEPLOY.md): the app downloads a new version in the
 * background and this banner offers a one-tap restart into it. Nothing shows in Expo Go or in
 * development, where updates are disabled.
 */
export function UpdateBanner() {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const { isUpdatePending } = Updates.useUpdates();
  const [dismissed, setDismissed] = useState(false);

  // The app checks at launch; people rarely close it, so also check when it comes back
  useEffect(() => {
    if (!Updates.isEnabled) {
      return;
    }
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {
        void downloadUpdate();
      }
    });
    return () => subscription.remove();
  }, []);

  if (!isUpdatePending || dismissed) {
    return null;
  }
  return (
    <View style={[styles.banner, { bottom: insets.bottom + TAB_BAR_CLEARANCE }]} accessibilityLiveRegion="polite">
      <Text style={styles.text}>Nova versão do app pronta.</Text>
      <Pressable accessibilityRole="button" onPress={() => setDismissed(true)} hitSlop={8}>
        <Text style={styles.later}>Depois</Text>
      </Pressable>
      <Pressable accessibilityRole="button" onPress={() => void Updates.reloadAsync()} hitSlop={8}>
        <Text style={styles.action}>Atualizar</Text>
      </Pressable>
    </View>
  );
}

async function downloadUpdate() {
  try {
    const check = await Updates.checkForUpdateAsync();
    if (check.isAvailable) {
      await Updates.fetchUpdateAsync();
    }
  } catch {
    // Offline or the update server is unreachable: try again next time
  }
}

const useStyles = makeStyles(colors => ({
  banner: {
    position: 'absolute',
    left: spacing.md,
    right: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    padding: spacing.md,
    borderRadius: 12,
    backgroundColor: colors.primary,
    elevation: 6,
  },
  text: { flex: 1, fontSize: 15, color: colors.onPrimary },
  later: { fontSize: 15, color: colors.onPrimary, opacity: 0.8 },
  action: { fontSize: 15, fontWeight: '700', color: colors.onPrimary },
}));
