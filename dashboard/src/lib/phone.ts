/**
 * Moroccan mobile/landline numbers in E.164, as order-service validates them: +212 then 9 digits
 * starting with 5, 6 or 7. Users type the local form (06 12 34 56 78) or the international one.
 */
export const MOROCCAN_PHONE = /^\+212[5-7]\d{8}$/;

export function normalizeMoroccanPhone(input: string): string | null {
  let digits = input.replace(/[\s().-]/g, '');
  if (digits.startsWith('00212')) digits = '+' + digits.slice(2);
  if (digits.startsWith('212')) digits = '+' + digits;
  if (/^0[5-7]\d{8}$/.test(digits)) digits = '+212' + digits.slice(1);
  if (/^[5-7]\d{8}$/.test(digits)) digits = '+212' + digits;
  return MOROCCAN_PHONE.test(digits) ? digits : null;
}

/** +212612345678 -> +212 6 12 34 56 78 */
export function formatMoroccanPhone(e164: string): string {
  const m = /^\+212(\d)(\d{2})(\d{2})(\d{2})(\d{2})$/.exec(e164);
  return m ? `+212 ${m[1]} ${m[2]} ${m[3]} ${m[4]} ${m[5]}` : e164;
}
