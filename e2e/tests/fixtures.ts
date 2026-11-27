import { test as base, expect, type Page } from '@playwright/test';

export const PASSWORD = 'correct horse battery staple';

/** A new address every call, so tests never share an account or touch real data. */
export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`;
}

export async function signUp(page: Page, email: string): Promise<void> {
  await page.goto('/register');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('button', { name: `Account menu for ${email}` })).toBeVisible();
}

type Account = { email: string; storageState: Awaited<ReturnType<Page['context']>['storageState']> };

/**
 * `account` signs up one fresh user per worker and `storageState` hands its signed-in
 * cookies to every test's context, so signed-in tests skip the sign-up form.
 */
export const test = base.extend<object, { account: Account }>({
  account: [
    async ({ browser }, use) => {
      const context = await browser.newContext({ baseURL: process.env.E2E_BASE_URL ?? "http://localhost:4201" });
      const page = await context.newPage();
      const email = uniqueEmail();
      await signUp(page, email);
      const storageState = await context.storageState();
      await context.close();
      await use({ email, storageState });
    },
    { scope: 'worker' },
  ],
});

export { expect };
