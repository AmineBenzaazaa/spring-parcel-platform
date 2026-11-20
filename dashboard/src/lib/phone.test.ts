import { formatMoroccanPhone, normalizeMoroccanPhone } from './phone';

describe('normalizeMoroccanPhone', () => {
  it.each([
    ['06 12 34 56 78', '+212612345678'],
    ['0712345678', '+212712345678'],
    ['+212 5 22 33 44 55', '+212522334455'],
    ['00212612345678', '+212612345678'],
    ['212612345678', '+212612345678'],
    ['612345678', '+212612345678'],
  ])('accepts %s', (input, expected) => {
    expect(normalizeMoroccanPhone(input)).toBe(expected);
  });

  it.each(['0812345678', '+33612345678', '06123', '', 'call me'])('rejects %s', (input) => {
    expect(normalizeMoroccanPhone(input)).toBeNull();
  });

  it('formats E.164 for display', () => {
    expect(formatMoroccanPhone('+212612345678')).toBe('+212 6 12 34 56 78');
  });
});
