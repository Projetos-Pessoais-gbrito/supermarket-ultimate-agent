export const MIN_PASSWORD_LENGTH = 8;

/** Same rules as the backend, checked before calling it. Returns a pt-BR message or null. */
export function validateCredentials(email: string, password: string, mode: 'login' | 'register'): string | null {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
    return 'Digite um e-mail válido.';
  }
  if (mode === 'register' && password.length < MIN_PASSWORD_LENGTH) {
    return `A senha precisa ter pelo menos ${MIN_PASSWORD_LENGTH} caracteres.`;
  }
  if (password.length === 0) {
    return 'Digite sua senha.';
  }
  return null;
}
