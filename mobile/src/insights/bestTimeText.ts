import type { BestTimeFinding, BestTimeInsight, StoreBestTime } from '../api/types';
import { formatPercent } from '../format';

export type FindingKind = 'period' | 'weekday';

const WEEKDAYS_PLURAL: Record<string, string> = {
  MONDAY: 'segundas-feiras',
  TUESDAY: 'terças-feiras',
  WEDNESDAY: 'quartas-feiras',
  THURSDAY: 'quintas-feiras',
  FRIDAY: 'sextas-feiras',
  SATURDAY: 'sábados',
  SUNDAY: 'domingos',
};

/** "dias 1 a 10", "às quartas-feiras", "aos sábados" */
export function whenText(kind: FindingKind, key: string, label: string): string {
  if (kind === 'period') {
    return label.toLowerCase();
  }
  const plural = WEEKDAYS_PLURAL[key] ?? label.toLowerCase();
  return `${key === 'SATURDAY' || key === 'SUNDAY' ? 'aos' : 'às'} ${plural}`;
}

function otherTimes(kind: FindingKind): string {
  return kind === 'period' ? 'no resto do mês' : 'nos outros dias';
}

/** One line per finding, e.g. "Dias 1 a 10: 10,0% mais barato que no resto do mês". */
export function findingText(finding: BestTimeFinding, kind: FindingKind): string {
  if (finding.status === 'PATTERN' && finding.best && finding.percentCheaper !== null) {
    const when = whenText(kind, finding.best.key, finding.best.label);
    return `${capitalize(when)}: ${formatPercent(finding.percentCheaper)} mais barato que ${otherTimes(kind)}`;
  }
  if (finding.status === 'NO_PATTERN') {
    return kind === 'period'
      ? 'Sem padrão: preços parecidos em qualquer época do mês'
      : 'Sem padrão: preços parecidos em qualquer dia da semana';
  }
  return 'Poucas compras ainda para saber';
}

type Strongest = { store: StoreBestTime; kind: FindingKind; finding: BestTimeFinding };

/** The biggest real pattern across stores, if any. */
export function strongestPattern(insight: BestTimeInsight): Strongest | null {
  let strongest: Strongest | null = null;
  for (const store of insight.stores) {
    for (const [kind, finding] of [
      ['period', store.periodOfMonth],
      ['weekday', store.weekday],
    ] as const) {
      if (
        finding.status === 'PATTERN' &&
        finding.percentCheaper !== null &&
        (strongest === null || finding.percentCheaper > (strongest.finding.percentCheaper ?? 0))
      ) {
        strongest = { store, kind, finding };
      }
    }
  }
  return strongest;
}

/** Title and one line for the dashboard tile. */
export function bestTimeTile(insight: BestTimeInsight): { title: string; detail: string } {
  const strongest = strongestPattern(insight);
  const best = strongest?.finding.best;
  const percentCheaper = strongest?.finding.percentCheaper;
  if (strongest && best && percentCheaper != null) {
    return {
      title: `${strongest.store.storeName}: ${whenText(strongest.kind, best.key, best.label)}`,
      detail: `${formatPercent(percentCheaper)} mais barato que ${otherTimes(strongest.kind)}`,
    };
  }
  if (insight.stores.some(store => store.periodOfMonth.status === 'NO_PATTERN')) {
    return {
      title: 'Sem padrão',
      detail: 'Seus preços são parecidos em qualquer época do mês. Compre quando precisar.',
    };
  }
  return { title: 'Ainda sem dados', detail: 'Precisamos de mais notas de um mesmo mercado para descobrir' };
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
