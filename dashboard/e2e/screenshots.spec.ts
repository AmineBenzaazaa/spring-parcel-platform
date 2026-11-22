import { expect, test, type Page } from '@playwright/test';

/**
 * Product screenshots for the README (docs/screenshots, 1440x900). Opt-in; seed the board first:
 *   ./scripts/seed-dashboard.sh && SCREENSHOTS=1 npx playwright test screenshots
 */
test.skip(!process.env.SCREENSHOTS, 'set SCREENSHOTS=1 to refresh the README screenshots');
test.use({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });

const out = (name: string) => `../docs/screenshots/${name}.png`;

async function prefs(page: Page, theme: 'light' | 'dark', lang: 'en' | 'fr') {
  await page.addInitScript(([t, l]) => { localStorage.setItem('parcel.theme', t); localStorage.setItem('parcel.lang', l); }, [theme, lang]);
}

test('README screenshots', async ({ page, browser }) => {
  await prefs(page, 'light', 'en');
  await page.goto('/login');
  await expect(page.getByTestId('login-form')).toBeVisible();
  await page.screenshot({ path: out('login') });

  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByTestId('live-indicator')).toHaveAttribute('data-status', 'connected');
  await expect(page.getByTestId('order-card').first()).toBeVisible();
  await page.mouse.move(5, 890);
  await page.waitForTimeout(1200);
  await page.screenshot({ path: out('board') });

  await page.locator('[data-testid="order-card"][data-status="DELIVERED"]').first().click();
  await expect(page.getByTestId('order-drawer')).toBeVisible();
  await page.waitForTimeout(700);
  await page.screenshot({ path: out('order-timeline') });
  await page.keyboard.press('Escape');

  await page.getByTestId('open-create-order').click();
  await page.getByTestId('fill-sample').click();
  await page.waitForTimeout(500);
  await page.screenshot({ path: out('create-order') });
  await page.keyboard.press('Escape');

  const dark = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  await prefs(dark, 'dark', 'fr');
  await dark.goto('/login');
  await dark.getByRole('button', { name: 'Se connecter' }).click();
  await expect(dark.getByTestId('live-indicator')).toHaveAttribute('data-status', 'connected');
  await expect(dark.getByTestId('order-card').first()).toBeVisible();
  await dark.mouse.move(5, 890);
  await dark.waitForTimeout(1200);
  await dark.screenshot({ path: out('board-dark-fr') });
  await dark.close();
});
