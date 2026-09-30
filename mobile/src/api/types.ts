// Mirrors the backend DTOs. Money and quantities arrive as JSON numbers.

export type TokenResponse = {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
};

export type Me = {
  id: number;
  email: string;
};

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ReceiptSummary = {
  id: number;
  storeName: string;
  issuedAt: string;
  totalAmount: number;
  itemCount: number;
};

export type ReceiptItem = {
  lineNumber: number;
  code: string;
  description: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  totalPrice: number;
};

export type ReceiptDetails = {
  id: number;
  accessKey: string;
  number: number;
  series: number;
  issuedAt: string;
  store: { id: number; cnpj: string; name: string; address: string };
  totalAmount: number;
  discountAmount: number;
  approximateTaxes: number;
  items: ReceiptItem[];
  payments: { method: string; amount: number }[];
};

/** RFC 9457 problem details returned by the backend on errors. */
export type ProblemDetail = {
  status: number;
  title?: string;
  detail?: string;
};
