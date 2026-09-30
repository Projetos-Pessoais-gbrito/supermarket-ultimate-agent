import Ionicons from '@expo/vector-icons/Ionicons';
import { Tabs } from 'expo-router';
import { Pressable, StyleSheet, Text } from 'react-native';

import { useAuth } from '../../../auth/AuthProvider';
import { colors } from '../../../ui/theme';

export default function TabsLayout() {
  const { signOut } = useAuth();

  return (
    <Tabs
      screenOptions={{
        headerTitleAlign: 'center',
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        headerRight: () => (
          <Pressable accessibilityRole="button" onPress={() => void signOut()} hitSlop={8} style={styles.signOut}>
            <Text style={styles.signOutText}>Sair</Text>
          </Pressable>
        ),
      }}>
      <Tabs.Screen
        name="index"
        options={{
          title: 'Início',
          headerTitle: 'Resumo',
          tabBarIcon: ({ color, size }) => <Ionicons name="stats-chart" color={color} size={size} />,
        }}
      />
      <Tabs.Screen
        name="receipts"
        options={{
          title: 'Notas',
          headerTitle: 'Minhas notas',
          tabBarIcon: ({ color, size }) => <Ionicons name="receipt-outline" color={color} size={size} />,
        }}
      />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  signOut: { marginRight: 16 },
  signOutText: { color: colors.primary, fontSize: 16 },
});
