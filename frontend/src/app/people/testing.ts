import { ApplicationPerson, Person } from './person.model';

export function aPerson(overrides: Partial<Person> = {}): Person {
  return {
    id: 'p1',
    name: 'Pat Lee',
    companyId: 'c1',
    company: 'Robert Half',
    title: 'Senior Recruiter',
    role: 'RECRUITER',
    recruiterType: 'AGENCY',
    email: 'pat@roberthalf.example',
    phone: null,
    linkedinUrl: null,
    notes: null,
    version: 0,
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    ...overrides,
  };
}

/** Someone on an application, as its people endpoint lists them. */
export function onIt(id: string, name: string, company: string | null): ApplicationPerson {
  const person = aPerson({ id, name, company, companyId: company ? `c-${id}` : null });
  return { person, role: person.role, addedAt: '2026-10-24T12:00:00Z' };
}
