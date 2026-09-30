import { Stack } from 'expo-router';
import { Pressable, StyleSheet, Text } from 'react-native';

import { useAuth } from '../../auth/AuthProvider';
import { colors } from '../../ui/theme';

export default function SignedInLayout() {
  const { signOut } = useAuth();

  return (
    <Stack
      screenOptions={{
        headerTitleAlign: 'center',
        headerRight: () => (
          <Pressable accessibilityRole="button" onPress={() => void signOut()} hitSlop={8}>
            <Text style={styles.signOut}>Sair</Text>
          </Pressable>
        ),
      }}
    />
  );
}

const styles = StyleSheet.create({
  signOut: { color: colors.primary, fontSize: 16 },
});
