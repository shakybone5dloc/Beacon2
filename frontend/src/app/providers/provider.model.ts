import { ApiKeyResponse, ProviderFieldResponse, ProviderResponse } from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Provider = ProviderResponse;
export type ProviderField = ProviderFieldResponse;
export type ApiKeyStatus = ApiKeyResponse;

export type ProviderKind = Provider['kind'];
export type ProviderCost = Provider['cost'];

/** Short label for what a provider costs and needs, e.g. "Free · key needed". */
export function accessLabel(p: Provider): string {
  if (p.cost === 'PAID') return 'Paid';
  if (p.needsKey) return 'Free · key needed';
  return p.fields.length ? 'Free · key optional' : 'Free · no key';
}
