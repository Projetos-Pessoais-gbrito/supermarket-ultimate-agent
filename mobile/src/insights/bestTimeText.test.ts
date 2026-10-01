import type { BestTimeFinding, BestTimeInsight, StoreBestTime } from '../api/types';
import { bestTimeTile, findingText, strongestPattern, whenText } from './bestTimeText';

const noData: BestTimeFinding = { status: 'NOT_ENOUGH_DATA', best: null, percentCheaper: null, groups: [] };
const noPattern: BestTimeFinding = { status: 'NO_PATTERN', best: null, percentCheaper: null, groups: [] };

function pattern(key: string, label: string, percentCheaper: number): BestTimeFinding {
  const best = { key, label, percentVsStoreAverage: -percentCheaper, samples: 10 };
  return { status: 'PATTERN', best, percentCheaper, groups: [best] };
}

function store(name: string, periodOfMonth: BestTimeFinding, weekday: BestTimeFinding = noData): StoreBestTime {
  return { storeId: name.length, storeName: name, comparablePurchases: 30, periodOfMonth, weekday };
}

function insight(...stores: StoreBestTime[]): BestTimeInsight {
  return { days: 365, stores };
}

describe('whenText', () => {
  it('uses the period label in lower case', () => {
    expect(whenText('period', 'DAYS_1_10', 'Dias 1 a 10')).toBe('dias 1 a 10');
  });

  it('builds a correct pt-BR phrase for weekdays', () => {
    expect(whenText('weekday', 'WEDNESDAY', 'Quarta-feira')).toBe('às quartas-feiras');
    expect(whenText('weekday', 'SATURDAY', 'Sábado')).toBe('aos sábados');
  });
});

describe('findingText', () => {
  it('describes a pattern against the rest of the month or week', () => {
    expect(findingText(pattern('DAYS_1_10', 'Dias 1 a 10', 10), 'period')).toBe(
      'Dias 1 a 10: 10,0% mais barato que no resto do mês',
    );
    expect(findingText(pattern('WEDNESDAY', 'Quarta-feira', 6.2), 'weekday')).toBe(
      'Às quartas-feiras: 6,2% mais barato que nos outros dias',
    );
  });

  it('says plainly when there is no pattern or too little data', () => {
    expect(findingText(noPattern, 'period')).toBe('Sem padrão: preços parecidos em qualquer época do mês');
    expect(findingText(noPattern, 'weekday')).toBe('Sem padrão: preços parecidos em qualquer dia da semana');
    expect(findingText(noData, 'period')).toBe('Poucas compras ainda para saber');
  });
});

describe('bestTimeTile', () => {
  it('shows the biggest pattern across stores and kinds', () => {
    const data = insight(
      store('Carrefour', pattern('DAYS_21_31', 'Dias 21 a 31', 4)),
      store('Assaí', pattern('DAYS_1_10', 'Dias 1 a 10', 5), pattern('WEDNESDAY', 'Quarta-feira', 8)),
    );

    expect(strongestPattern(data)?.store.storeName).toBe('Assaí');
    expect(bestTimeTile(data)).toEqual({
      title: 'Assaí: às quartas-feiras',
      detail: '8,0% mais barato que nos outros dias',
    });
  });

  it('tells people not to wait when prices do not depend on the time', () => {
    expect(bestTimeTile(insight(store('Assaí', noPattern), store('Dia', noData))).title).toBe('Sem padrão');
  });

  it('asks for more receipts without enough data', () => {
    expect(bestTimeTile(insight()).title).toBe('Ainda sem dados');
    expect(bestTimeTile(insight(store('Dia', noData))).title).toBe('Ainda sem dados');
  });
});
