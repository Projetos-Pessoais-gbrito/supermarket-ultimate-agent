import { ActivityIndicator, Pressable, Text, TextInput, View, type TextInputProps } from 'react-native';

import { makeStyles, spacing, useColors } from './theme';

type ButtonProps = {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
};

export function Button({ title, onPress, loading = false, disabled = false, variant = 'primary' }: ButtonProps) {
  const colors = useColors();
  const styles = useStyles();
  const primary = variant !== 'secondary';
  const danger = variant === 'danger';
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: disabled || loading, busy: loading }}
      disabled={disabled || loading}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        primary ? styles.buttonPrimary : styles.buttonSecondary,
        danger && styles.buttonDanger,
        pressed && primary && !danger && { backgroundColor: colors.primaryPressed },
        (disabled || loading) && styles.buttonDisabled,
      ]}>
      {loading ? (
        <ActivityIndicator color={danger ? colors.onDanger : primary ? colors.onPrimary : colors.primary} />
      ) : (
        <Text
          style={[
            styles.buttonText,
            danger && { color: colors.onDanger },
            !primary && { color: colors.primary },
          ]}>
          {title}
        </Text>
      )}
    </Pressable>
  );
}

export function TextField({ label, ...props }: TextInputProps & { label: string }) {
  const colors = useColors();
  const styles = useStyles();
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        placeholderTextColor={colors.textMuted}
        style={styles.input}
        accessibilityLabel={label}
        {...props}
      />
    </View>
  );
}

export function ErrorBanner({ message }: { message: string | null }) {
  const styles = useStyles();
  if (!message) {
    return null;
  }
  return (
    <View accessibilityRole="alert" style={styles.error}>
      <Text style={styles.errorText}>{message}</Text>
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  // minHeight (not height) and vertical padding let buttons grow with the system font size
  button: {
    minHeight: 48,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  buttonPrimary: { backgroundColor: colors.primary },
  buttonSecondary: { backgroundColor: 'transparent', borderWidth: 1, borderColor: colors.primary },
  buttonDanger: { backgroundColor: colors.danger },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: colors.onPrimary, fontSize: 16, fontWeight: '600', textAlign: 'center' },
  field: { marginBottom: spacing.md },
  label: { fontSize: 14, color: colors.textMuted, marginBottom: spacing.xs },
  input: {
    minHeight: 48,
    paddingVertical: spacing.sm,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: spacing.md,
    fontSize: 16,
    color: colors.text,
    backgroundColor: colors.background,
  },
  error: {
    backgroundColor: colors.dangerBackground,
    borderRadius: 10,
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  errorText: { color: colors.danger, fontSize: 14 },
}));
