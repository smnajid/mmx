import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { DelegatedGrantsListComponent } from './delegated-grants-list.component';

describe('DelegatedGrantsListComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<DelegatedGrantsListComponent>;

  function setupTrader(): void {
    TestBed.configureTestingModule({
      imports: [DelegatedGrantsListComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('demo-trader'),
            isTrader: () => true,
            isClientRepresentative: () => false,
          },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DelegatedGrantsListComponent);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders hub grants for Trader', async () => {
    setupTrader();
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/settings/delegated-grants');
    req.flush([
      {
        hubInstitutionCode: 'BI-01',
        clientLegalEntityCode: 'PAR',
        currency: 'EUR',
        enabledTenors: ['3M'],
        enabledNoticePeriods: [],
        active: true,
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('BI-01');
    expect(el.textContent).toContain('PAR');
    expect(el.querySelector('[data-testid="create-grant"]')).toBeTruthy();
  });

  it('shows Trader-only message for ClientRepresentative without calling hub list', () => {
    TestBed.configureTestingModule({
      imports: [DelegatedGrantsListComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('demo-trader'),
            isTrader: () => false,
            isClientRepresentative: () => true,
          },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DelegatedGrantsListComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="trader-only-message"]')).toBeTruthy();
    httpMock.expectNone('/api/v1/settings/delegated-grants');
  });
});
