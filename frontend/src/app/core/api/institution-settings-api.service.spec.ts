import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InstitutionSettingsApiService } from './institution-settings-api.service';

describe('InstitutionSettingsApiService', () => {
  let service: InstitutionSettingsApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InstitutionSettingsApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listGrantedInstitutions gets /granted with X-User-Id', () => {
    let result: unknown;
    service.listGrantedInstitutions('cr-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/v1/settings/institutions/granted');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('X-User-Id')).toBe('cr-1');
    const body = [
      {
        hubLegalEntityCode: 'LOC',
        hubInstitutionCode: 'SG-01',
        displayName: 'SG via LOC',
        currencies: ['USD'],
      },
    ];
    req.flush(body);
    expect(result).toEqual(body);
  });

  it('updateCounterpartyAccounts puts the full replacement', () => {
    service
      .updateCounterpartyAccounts('t1', 'BNP-01', {
        termCounterpartyAccount: 'LOC-BNP-T',
        onCallCounterpartyAccount: null,
      })
      .subscribe();

    const req = httpMock.expectOne('/api/v1/settings/institutions/BNP-01/counterparty-accounts');
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('X-User-Id')).toBe('t1');
    expect(req.request.body).toEqual({
      termCounterpartyAccount: 'LOC-BNP-T',
      onCallCounterpartyAccount: null,
    });
    req.flush({ institutionCode: 'BNP-01', displayName: 'BNP', active: true });
  });

  it('updateClientEnablement puts the currency tenor/notice sets', () => {
    service
      .updateClientEnablement('cr-1', 'BVL-01', 'EUR', {
        enabledTenors: ['3M'],
        enabledNoticePeriods: ['24H'],
      })
      .subscribe();

    const req = httpMock.expectOne('/api/v1/settings/institutions/BVL-01/enablement/EUR');
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('X-User-Id')).toBe('cr-1');
    expect(req.request.body).toEqual({ enabledTenors: ['3M'], enabledNoticePeriods: ['24H'] });
    req.flush({ institutionCode: 'BVL-01', displayName: 'BankCo via LOC', active: true });
  });
});
