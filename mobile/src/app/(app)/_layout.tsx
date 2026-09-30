import { Stack } from 'expo-router';

export default function SignedInLayout() {
  return (
    <Stack screenOptions={{ headerTitleAlign: 'center', headerBackTitle: 'Voltar' }}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
    </Stack>
  );
}
