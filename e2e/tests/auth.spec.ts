import { test, expect } from '@playwright/test';
import { PASSWORD, signUp, uniqueEmail } from './fixtures';

test('sign up, sign out, sign in again', async ({ page }) => {
  const email = uniqueEmail();
  await signUp(page, email);

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();

  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: `Account menu for ${email}` })).toBeVisible();
});
