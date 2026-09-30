import type { ReceiptItem } from '../api/types';
import { groupReceiptItems } from './groupItems';

function item(overrides: Partial<ReceiptItem>): ReceiptItem {
  return {
    lineNumber: 1,
    code: '241823',
    description: 'FLOCAO DE MILHO DA TERRINHA 400G',
    quantity: 1,
    unit: 'UN',
    unitPrice: 1.99,
    totalPrice: 1.99,
    productId: 7,
    productName: null,
    categoryLabel: 'Mercearia',
    ...overrides,
  };
}

describe('groupReceiptItems', () => {
  it('merges repeated lines of the same product and price', () => {
    const groups = groupReceiptItems([item({ lineNumber: 1 }), item({ lineNumber: 2 })]);

    expect(groups).toHaveLength(1);
    expect(groups[0]).toMatchObject({ count: 2, quantity: 2, totalPrice: 3.98 });
  });

  it('keeps different prices of the same product apart', () => {
    const groups = groupReceiptItems([item({}), item({ lineNumber: 2, unitPrice: 2.49, totalPrice: 2.49 })]);

    expect(groups).toHaveLength(2);
  });

  it('keeps the order of first appearance', () => {
    const bread = item({ code: '94794', description: 'PAO FRANCES', unit: 'KG', quantity: 0.136, unitPrice: 17.99 });
    const groups = groupReceiptItems([bread, item({ lineNumber: 2 }), { ...bread, lineNumber: 3, quantity: 0.2 }]);

    expect(groups.map(group => group.item.description)).toEqual(['PAO FRANCES', 'FLOCAO DE MILHO DA TERRINHA 400G']);
    expect(groups[0].quantity).toBe(0.336);
  });
});
