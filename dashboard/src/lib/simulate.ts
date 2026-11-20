import { api, type CreateOrderInput } from './api';
import type { Delivery } from './types';

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Pause between courier steps so the board animation is visible on a screen recording. */
export const STEP_DELAY_MS = 1400;

const FAILURE_REASONS = ['customer absent', 'phone switched off', 'customer refused the parcel'];

/** Dispatch assigns asynchronously (outbox -> Kafka -> dispatch). Poll until the delivery is ASSIGNED. */
export async function waitForAssignment(orderId: string, timeoutMs = 15_000): Promise<Delivery> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const d = await api.delivery(orderId);
      if (d.status === 'ASSIGNED') return d;
    } catch {
      /* 404 until dispatch has consumed OrderCreated */
    }
    await sleep(400);
  }
  throw new Error('Timed out waiting for a courier to be assigned');
}

/** Drives the courier steps the way the courier app would: out for delivery, then delivered. */
export async function simulateDelivery(orderId: string, delay = STEP_DELAY_MS): Promise<Delivery> {
  let d = await api.delivery(orderId);
  if (d.status === 'ASSIGNED' || d.status === 'FAILED') {
    d = await api.outForDelivery(orderId);
    await sleep(delay);
  }
  return api.delivered(orderId);
}

/** Fails every remaining attempt: the last failure makes dispatch emit FAILED then RETURNED (the saga). */
export async function simulateFailure(orderId: string, delay = STEP_DELAY_MS): Promise<Delivery> {
  let d = await api.delivery(orderId);
  let i = 0;
  while (d.status !== 'RETURNED') {
    if (d.status === 'ASSIGNED' || d.status === 'FAILED') {
      d = await api.outForDelivery(orderId);
      await sleep(delay);
    }
    if (d.status === 'OUT_FOR_DELIVERY') {
      d = await api.failed(orderId, FAILURE_REASONS[i++ % FAILURE_REASONS.length]);
      await sleep(delay);
    } else if (d.status !== 'RETURNED') {
      throw new Error(`Cannot fail a delivery that is ${d.status}`);
    }
  }
  return d;
}

// ---- sample data for the create dialog and the one-click demo ---------------------------

export const MERCHANTS = [
  { id: 'M-1001', name: 'Atlas Store' },
  { id: 'M-1002', name: 'Souk Digital' },
];

/** Cities served by a courier zone in dispatch-service (Zones.java). */
export const CITIES = ['Casablanca', 'Mohammedia', 'Rabat', 'Salé', 'Témara', 'Kénitra', 'Marrakech', 'Tanger'];

const FIRST = ['Salma', 'Youssef', 'Fatima Zahra', 'Mehdi', 'Imane', 'Othmane', 'Khadija', 'Anas', 'Hiba', 'Rachid'];
const LAST = ['Idrissi', 'Benjelloun', 'El Fassi', 'Alaoui', 'Berrada', 'Tazi', 'Chraibi', 'Lahlou', 'Bennani', 'Amrani'];
const STREETS = ['Rue Atlas, Maarif', 'Bd Zerktouni', 'Av. Mohammed V', 'Rue Ibn Battouta', 'Hay Riad, Secteur 12',
  'Bd Anfa', 'Rue de Fès, Gueliz', 'Av. Hassan II'];
// Casablanca twice: most COD volume is there, and it has two couriers
const DEMO_CITIES = ['Casablanca', 'Casablanca', 'Rabat', 'Marrakech', 'Tanger', 'Kénitra', 'Mohammedia'];

const pick = <T,>(xs: T[]) => xs[Math.floor(Math.random() * xs.length)];

export function sampleOrder(): CreateOrderInput {
  const phone = `+2126${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`;
  const amount = Math.round((99 + Math.random() * 900) * 2) / 2 - 0.1;
  return {
    merchantId: pick(MERCHANTS).id,
    recipientName: `${pick(FIRST)} ${pick(LAST)}`,
    recipientPhone: phone,
    addressLine: `${Math.floor(1 + Math.random() * 120)} ${pick(STREETS)}`,
    city: pick(DEMO_CITIES),
    codAmount: Number(amount.toFixed(2)),
  };
}
