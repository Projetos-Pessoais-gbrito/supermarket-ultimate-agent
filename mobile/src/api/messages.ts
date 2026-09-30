import { ApiError } from './client';

export const KEY_ONLY_LINK_MESSAGE =
  'Esse link só tem a chave de acesso, e a SEFAZ pede um captcha para mostrar a nota. Toque em ' +
  '"Abrir na SEFAZ" para resolver o captcha aqui no app, ou escaneie o QR code do cupom.';

/** User-facing (pt-BR) message for a failed request. Screens can override specific statuses. */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Algo deu errado. Tente novamente.';
  }
  if (error.code === 'KEY_ONLY_LINK') {
    return KEY_ONLY_LINK_MESSAGE;
  }
  if (error.code === 'TIMEOUT') {
    return 'A consulta demorou demais para responder. Verifique a conexão e tente novamente.';
  }
  switch (error.status) {
    case 0:
      return 'Sem conexão com o servidor. Verifique sua internet e tente novamente.';
    case 400:
      return 'Esse QR code não é de uma nota fiscal válida.';
    case 401:
      return 'Sua sessão expirou. Entre novamente.';
    case 403:
      return 'Senha incorreta.';
    case 404:
      return 'Nota fiscal não encontrada.';
    case 409:
      return 'Este e-mail já está cadastrado.';
    case 429:
      return 'Muitas tentativas. Tente de novo em alguns minutos.';
    case 422:
      return 'Não foi possível ler essa nota na SEFAZ. Por enquanto só notas de São Paulo são suportadas.';
    case 503:
      return 'A SEFAZ não está respondendo agora. Tente novamente em alguns minutos.';
    default:
      return 'Algo deu errado. Tente novamente.';
  }
}
