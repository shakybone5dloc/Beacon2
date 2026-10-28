import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme';

describe('ThemeService', () => {
  const root = document.documentElement;

  beforeEach(() => {
    localStorage.removeItem('beacon.theme');
    delete root.dataset['theme'];
  });

  afterEach(() => localStorage.removeItem('beacon.theme'));

  function create(): ThemeService {
    const theme = TestBed.inject(ThemeService);
    TestBed.tick();
    return theme;
  }

  it('follows the system by default', () => {
    const theme = create();

    // The test DOM has no matchMedia, so "system" reads as light.
    expect(theme.choice()).toBe('system');
    expect(root.dataset['theme']).toBe('light');
  });

  it('starts from the remembered choice', () => {
    localStorage.setItem('beacon.theme', 'dark');

    const theme = create();

    expect(theme.choice()).toBe('dark');
    expect(root.dataset['theme']).toBe('dark');
  });

  it('cycles system, light, dark and remembers all but system', () => {
    const theme = create();

    theme.next();
    TestBed.tick();
    expect(theme.choice()).toBe('light');
    expect(root.dataset['theme']).toBe('light');
    expect(localStorage.getItem('beacon.theme')).toBe('light');

    theme.next();
    TestBed.tick();
    expect(root.dataset['theme']).toBe('dark');
    expect(localStorage.getItem('beacon.theme')).toBe('dark');

    theme.next();
    expect(theme.choice()).toBe('system');
    expect(localStorage.getItem('beacon.theme')).toBeNull();
  });
});
