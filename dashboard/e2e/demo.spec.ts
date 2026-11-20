import { expect, test } from '@playwright/test';

/**
 * The 30-second story, end to end through nginx, the gateway, Kafka and three services:
 * log in as ops, create a COD order, watch dispatch assign it, drive the courier steps,
 * and see the card land in DELIVERED, pushed by the live stream.
 */
test('ops creates an order, simulates the delivery and sees it DELIVERED', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel('Username').fill('ops');
  await page.getByLabel('Password').fill('Passw0rd!');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByTestId('live-indicator')).toHaveAttribute('data-status', 'connected');

  await page.getByTestId('open-create-order').click();
  await page.getByTestId('fill-sample').click();
  await page.getByLabel('City').selectOption('Casablanca');
  const created = page.waitForResponse((r) => r.url().endsWith('/api/orders') && r.request().method() === 'POST');
  await page.getByTestId('submit-order').click();
  const order = await (await created).json();
  expect(order.status).toBe('CREATED');

  // outbox -> Kafka -> dispatch assigns a Casablanca courier -> SSE moves the card
  const card = page.locator(`[data-testid="order-card"][data-tracking="${order.trackingCode}"]`);
  await expect(card).toHaveAttribute('data-status', 'ASSIGNED');
  await expect(page.getByTestId('column-ASSIGNED').locator(`[data-tracking="${order.trackingCode}"]`)).toBeVisible();

  await card.click();
  const drawer = page.getByTestId('order-drawer');
  await expect(drawer).toContainText(order.trackingCode);
  await drawer.getByTestId('simulate-delivery').click();

  await expect(drawer.getByTestId('status-badge')).toHaveText('Delivered', { timeout: 20_000 });
  await expect(drawer.getByTestId('timeline')).toContainText('Out for delivery');
  await expect(drawer.getByTestId('timeline').locator('[data-kind="notification"]').first()).toBeVisible();

  await page.keyboard.press('Escape');
  await expect(page.getByTestId('column-DELIVERED').locator(`[data-tracking="${order.trackingCode}"]`)).toBeVisible();
});
