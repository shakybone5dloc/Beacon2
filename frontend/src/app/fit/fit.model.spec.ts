import { matchingSkills, verdict } from './fit.model';

describe('fit model', () => {
  it('bands scores the way the prompt does', () => {
    expect(verdict(85).label).toBe('Strong fit');
    expect(verdict(84).label).toBe('Good fit');
    expect(verdict(70).pill).toBe('pill-blue');
    expect(verdict(69).label).toBe('Stretch');
    expect(verdict(49).label).toBe('Poor fit');
  });

  it('matches whole skills, ignoring case', () => {
    const text = 'Senior JAVA engineer: Spring Boot, JavaScript, C# and .NET; no Go.';

    expect(matchingSkills(['Java', 'spring boot', 'C#', '.NET', 'Rust', 'Script'], text)).toEqual([
      'Java',
      'spring boot',
      'C#',
      '.NET',
    ]);
  });

  it('does not count a skill hiding inside another word', () => {
    expect(matchingSkills(['Go', 'SQL'], 'Good with PostgreSQL')).toEqual([]);
  });
});
