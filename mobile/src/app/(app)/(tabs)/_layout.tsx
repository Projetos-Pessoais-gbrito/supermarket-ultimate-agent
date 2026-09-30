import Ionicons from '@expo/vector-icons/Ionicons';
import { Tabs } from 'expo-router';
import type { ComponentProps } from 'react';
import type { ColorValue } from 'react-native';

import { colors } from '../../../ui/theme';

type IconName = ComponentProps<typeof Ionicons>['name'];

/** Tab bar colors can be platform color objects or null; icons need a plain color string. */
function tabIcon(name: IconName) {
  return function TabIcon({ color, size }: { color: ColorValue; size: number }) {
    return <Ionicons name={name} color={typeof color === 'string' ? color : colors.textMuted} size={size} />;
  };
}

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
          tabBarIcon: tabIcon('stats-chart'),
        }}
      />
      <Tabs.Screen
        name="receipts"
        options={{
          title: 'Notas',
          headerTitle: 'Minhas notas',
          tabBarIcon: tabIcon('receipt-outline'),
        }}
      />
      <Tabs.Screen
        name="account"
        options={{
          title: 'Conta',
          tabBarIcon: tabIcon('person-circle-outline'),
        }}
      />
    </Tabs>
  );
}
