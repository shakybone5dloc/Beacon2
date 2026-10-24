import { TestBed } from '@angular/core/testing';
import { ApplicationsApi } from './applications-api';

describe('ApplicationsApi', () => {
  let service: ApplicationsApi;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ApplicationsApi);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
