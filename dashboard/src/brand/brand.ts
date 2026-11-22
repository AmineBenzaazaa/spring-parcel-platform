/**
 * The product brand, in one place. Renaming the suite is a change to `name` and `suite` here,
 * plus the logo files in /brand. Every screen, page title and the product switcher read from this.
 */
export const BRAND = {
  name: 'MSE',
  suite: 'MSE Transport',
  /** This app. */
  product: 'Dispatch',
} as const;

export type ProductId = 'dispatch' | 'settle' | 'copilot';

const env = import.meta.env as Record<string, string | undefined>;

/** The three apps of the suite. URLs default to the local docker compose ports. */
export const PRODUCTS: Array<{ id: ProductId; name: string; url: string }> = [
  { id: 'dispatch', name: 'Dispatch', url: env.VITE_DISPATCH_URL ?? 'http://localhost:3100' },
  { id: 'settle', name: 'Settle', url: env.VITE_SETTLE_URL ?? 'http://localhost:3200' },
  { id: 'copilot', name: 'Copilot', url: env.VITE_COPILOT_URL ?? 'http://localhost:3300' },
];

export const CURRENT_PRODUCT: ProductId = 'dispatch';

export const productTitle = (product: string = BRAND.product) => `${BRAND.name} ${product}`;
