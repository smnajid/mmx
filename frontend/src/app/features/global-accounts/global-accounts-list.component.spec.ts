import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { GlobalAccountsListComponent } from './global-accounts-list.component';

describe('GlobalAccountsListComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<GlobalAccountsListComponent>;

  function setupTrader(): void {
    TestBed.configureTestingModule({
      imports: [GlobalAccountsListComponent, HttpClientTestingModule],
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
    fixture = TestBed.createComponent(GlobalAccountsListComponent);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders global accounts for Trader', async () => {
    setupTrader();
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/settings/global-accounts');
    req.flush([
      {
        clientLegalEntityCode: 'PAR',
        hubLegalEntityCode: 'LOC',
        currency: 'EUR',
        accountRef: 'PAR-EUR-001',
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('PAR-EUR-001');
    expect(el.querySelector('[data-testid="create-global-account"]')).toBeTruthy();
  });

  it('shows Trader-only message for ClientRepresentative without calling list', () => {
    TestBed.configureTestingModule({
      imports: [GlobalAccountsListComponent, HttpClientTestingModule],
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
    fixture = TestBed.createComponent(GlobalAccountsListComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="trader-only-message"]')).toBeTruthy();
    httpMock.expectNone('/api/v1/settings/global-accounts');
  });
});
