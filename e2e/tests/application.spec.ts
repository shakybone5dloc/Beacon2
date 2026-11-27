import { test as base, expect } from './fixtures';

// Every test in this file starts already signed in as the worker's fresh account.
const test = base.extend({
  storageState: async ({ account }, use) => use(account.storageState),
});

test('add an application and see its card in the Saved column', async ({ page }) => {
  const company = `Acme ${Date.now()}`;
  await page.goto('/applications/new');
  await page.getByLabel('Company').fill(company);
  await page.getByLabel('Role', { exact: true }).fill('Staff Engineer');
  await page.getByRole('button', { name: 'Save' }).click();

  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
  const saved = page.getByRole('region', { name: 'Saved column' });
  await expect(saved.getByRole('article').filter({ hasText: company })).toBeVisible();
});
