import path from 'node:path';
import { test as base, expect, type Page } from '@playwright/test';

export const PASSWORD = 'correct horse battery staple';

/** A new address every call, so tests never share an account or touch real data. */
export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`;
}

export async function signUp(page: Page, email: string): Promise<void> {
  await page.goto('/register');
  // The page assumes "invite-only" until /api/auth/registration answers (slow on a cold API), and a
  // click before then fails the invite-code check client-side without ever calling the server.
  // The invite field disappearing is the visible sign that the real setting has arrived.
  await expect(page.getByLabel('Email')).toBeVisible(); // form rendered, so the count below is meaningful
  await expect(page.getByLabel('Invite code')).toHaveCount(0, { timeout: 30_000 });
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('button', { name: `Account menu for ${email}` })).toBeVisible({ timeout: 30_000 });
}

type Account = { email: string; storageState: Awaited<ReturnType<Page['context']>['storageState']> };

/**
 * `account` signs up one fresh user per worker and `storageState` hands its signed-in
 * cookies to every test's context, so signed-in tests skip the sign-up form.
 */
export const test = base.extend<object, { account: Account }>({
  account: [
    async ({ browser }, use, workerInfo) => {
      const context = await browser.newContext({ baseURL: workerInfo.project.use.baseURL });
      const page = await context.newPage();
      const email = uniqueEmail();
      let storageState: Account['storageState'];
      try {
        await signUp(page, email);
        storageState = await context.storageState();
      } catch (error) {
        // Worker fixtures get no per-test screenshot, so keep one here.
        await page
          .screenshot({ path: path.join(workerInfo.project.outputDir, 'account-fixture-failure.png') })
          .catch(() => undefined);
        throw error;
      } finally {
        await context.close();
      }
      await use({ email, storageState });
    },
    // Longer than the 30s waits inside signUp, so those fail first and the catch above runs.
    { scope: 'worker', timeout: 90_000 },
  ],
});

export { expect };
