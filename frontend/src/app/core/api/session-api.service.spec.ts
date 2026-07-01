import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SessionApiService } from './session-api.service';

describe('SessionApiService', () => {
  let service: SessionApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('reScope posts to /api/v1/session/scope with X-User-Id', () => {
    service
      .reScope('user-1', { legalEntityCode: 'PAR', role: 'CLIENT_REPRESENTATIVE' })
      .subscribe();

    const req = httpMock.expectOne('/api/v1/session/scope');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('X-User-Id')).toBe('user-1');
    expect(req.request.body).toEqual({
      legalEntityCode: 'PAR',
      role: 'CLIENT_REPRESENTATIVE',
    });
    req.flush({ legalEntityCode: 'PAR', role: 'CLIENT_REPRESENTATIVE' });
  });
});
