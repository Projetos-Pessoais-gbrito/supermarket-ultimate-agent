// Mirrors the backend DTOs. Money and quantities arrive as JSON numbers.

export type TokenResponse = {
  accessToken: string;
  tokenType: 'Bearer';
  /** seconds */
  expiresIn: number;
  refreshToken: string;
  /** seconds */
  refreshExpiresIn: number;
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
  /** As printed on the receipt */
  description: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  totalPrice: number;
  /** Canonical product; null until matched */
  productId: number | null;
  /** Friendly name written by the AI; null until available */
  productName: string | null;
  categoryLabel: string | null;
};

export type PricePoint = { issuedAt: string; storeId: number; storeName: string; unitPrice: number; unit: string };

export type PriceHistory = { productId: number; name: string; prices: PricePoint[] };

export type ReceiptDetails = {
  id: number;
  accessKey: string;
  number: number;
  series: number;
  issuedAt: string;
  /** name is the brand (e.g. ASSAI); legalName is what SEFAZ prints (e.g. SENDAS DISTRIBUIDORA S/A) */
  store: { id: number; cnpj: string; name: string; legalName: string; address: string };
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
  /** Machine-readable reason for errors the app explains specifically, e.g. KEY_ONLY_LINK */
  code?: string;
};

// Insights (see backend /api/insights)

export type MonthTotal = { month: string; total: number; receiptCount: number };

export type SpendingInsight = {
  currentMonth: MonthTotal;
  previousMonth: MonthTotal;
  /** Previous month from day 1 to comparedUntilDay: the base of changePercent */
  previousMonthToDate: MonthTotal;
  comparedUntilDay: number;
  changePercent: number | null;
  monthly: MonthTotal[];
  byStore: { storeId: number; storeName: string; total: number; receiptCount: number }[];
  byCategory: { category: string | null; label: string; total: number }[];
};

export type SavingsInsight = {
  months: number;
  /** Purchases are compared with the best price within this many days of them */
  comparisonWindowDays: number;
  potentialSavings: number;
  comparedSpending: number;
  products: {
    productId: number;
    name: string;
    timesBought: number;
    totalPaid: number;
    bestUnitPrice: number;
    bestStoreName: string;
    extraPaid: number;
  }[];
};

export type BestDayGroup = { key: string; label: string; percentVsAverage: number; samples: number };

export type BestDayInsight = {
  comparableItems: number;
  bestPeriod: BestDayGroup | null;
  byPeriodOfMonth: BestDayGroup[];
  bestWeekday: BestDayGroup | null;
  byWeekday: BestDayGroup[];
};

export type InflationInsight = {
  monthly: { month: string; changePercent: number | null; productsCompared: number }[];
  changes: { productId: number; name: string; previousPrice: number; currentPrice: number; changePercent: number }[];
};

export type InsightSummary = { available: boolean; tips: string[] };

export type CategoryProducts = {
  category: string;
  label: string;
  total: number;
  products: {
    productId: number | null;
    name: string;
    timesBought: number;
    totalSpent: number;
    lastUnitPrice: number;
    lastStoreName: string;
    lastBoughtAt: string;
  }[];
};

export type MonthlyTotal = { month: string; total: number; receiptCount: number };

/** GET /api/receipts: a page plus totals per month of everything the filters matched. */
export type ReceiptListPage = Page<ReceiptSummary> & {
  monthlyTotals: MonthlyTotal[];
  /** Filter options, taken from all of the user's receipts */
  stores: string[];
  months: string[];
};

export type ReceiptFilters = { store?: string; month?: string; q?: string };

export type BudgetCategoryLimit = { category: string; label: string; limit: number };

export type BudgetSettings = { overall: number | null; categories: BudgetCategoryLimit[] };

export type BudgetCategoryOption = { category: string; label: string };

export type BudgetState = 'OK' | 'WARNING' | 'OVER';

export type BudgetLine = {
  category: string | null;
  label: string;
  limit: number;
  spent: number;
  percentUsed: number;
  /** Spending at the current pace by the end of the month */
  projected: number;
  projectedOver: boolean;
  state: BudgetState;
};

export type BudgetStatus = {
  month: string;
  daysElapsed: number;
  daysInMonth: number;
  overall: BudgetLine | null;
  categories: BudgetLine[];
};
