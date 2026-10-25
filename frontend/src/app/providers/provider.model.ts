export type ProviderKind = 'JOB_SOURCE' | 'AI';
export type ProviderCost = 'FREE' | 'PAID';

export interface ProviderField {
  name: string;
  label: string;
  /** Typed into a password input; the server never sends it back. */
  secret: boolean;
  required: boolean;
}

export interface Provider {
  id: string;
  name: string;
  kind: ProviderKind;
  cost: ProviderCost;
  /** True when the provider can't be used until its fields are filled in. */
  needsKey: boolean;
  /** True when this server has switched the provider off for everyone; a key won't help. */
  turnedOff: boolean;
  coverage: string;
  signUpUrl: string;
  pricing: string;
  steps: string[];
  /** Empty when the provider takes no key at all. */
  fields: ProviderField[];
}

export interface ApiKeyStatus {
  provider: string;
  saved: boolean;
  /** Last few characters of the saved secret. */
  hint: string | null;
  updatedAt: string | null;
}

/** Short label for what a provider costs and needs, e.g. "Free · key needed". */
export function accessLabel(p: Provider): string {
  if (p.cost === 'PAID') return 'Paid';
  if (p.needsKey) return 'Free · key needed';
  return p.fields.length ? 'Free · key optional' : 'Free · no key';
}
