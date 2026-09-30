import { ApiError } from './client';

/** User-facing (pt-BR) message for a failed request. Screens can override specific statuses. */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Algo deu errado. Tente novamente.';
  }
  switch (error.status) {
    case 0:
      return 'Não foi possível conectar ao servidor. Verifique se o backend está rodando e se o celular está na mesma rede Wi-Fi do computador.';
    case 400:
      return 'Esse QR code não é de uma nota fiscal válida.';
    case 401:
      return 'Sua sessão expirou. Entre novamente.';
    case 404:
      return 'Nota fiscal não encontrada.';
    case 409:
      return 'Este e-mail já está cadastrado.';
    case 422:
      return 'Não foi possível ler essa nota na SEFAZ. Por enquanto só notas de São Paulo são suportadas.';
    case 503:
      return 'A SEFAZ não está respondendo agora. Tente novamente em alguns minutos.';
    default:
      return 'Algo deu errado. Tente novamente.';
  }
}
