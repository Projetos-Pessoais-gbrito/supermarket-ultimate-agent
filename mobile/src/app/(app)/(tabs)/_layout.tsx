import Ionicons from '@expo/vector-icons/Ionicons';
import { Tabs } from 'expo-router';

import { colors } from '../../../ui/theme';

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerTitleAlign: 'center',
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
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
      <Tabs.Screen
        name="account"
        options={{
          title: 'Conta',
          tabBarIcon: ({ color, size }) => <Ionicons name="person-circle-outline" color={color} size={size} />,
        }}
      />
    </Tabs>
  );
}
