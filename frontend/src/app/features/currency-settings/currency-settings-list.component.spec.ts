import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CurrencySettingsListComponent } from './currency-settings-list.component';
import { TraderContextService } from '../../core/trader/trader-context.service';

describe('CurrencySettingsListComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<CurrencySettingsListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CurrencySettingsListComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: TraderContextService,
          useValue: { traderId: signal('trader-a'), userId: signal('trader-a'), setTraderId: (): void => {}, isTrader: () => true, isClientRepresentative: () => false },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CurrencySettingsListComponent);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders currencies from API with status badge and rules summary', async () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/settings/currencies');
    expect(req.request.headers.get('X-User-Id')).toBe('trader-a');
    req.flush([
      {
        code: 'EUR',
        active: true,
        minSubscriptionAmount: 1000000,
        minIncreaseDecreaseAmount: 250000,
        enabledTenors: ['1M', '3M'],
        enabledNoticePeriods: ['24H'],
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('EUR');
    expect(el.querySelector('.status-badge--active')).toBeTruthy();
    expect(el.textContent).toContain('Active');
    expect(el.textContent).toContain('1M');
    expect(el.textContent).toContain('3M');
    expect(el.textContent).toContain('24H');
    expect(el.textContent).toContain('Term:');
    expect(el.textContent).toContain('OnCall:');
  });

  it('shows inactive badge for deactivated currency', async () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/settings/currencies');
    req.flush([
      {
        code: 'USD',
        active: false,
        minSubscriptionAmount: 500000,
        minIncreaseDecreaseAmount: 100000,
        enabledTenors: ['3M'],
        enabledNoticePeriods: ['48H'],
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.status-badge--inactive')).toBeTruthy();
    expect(el.textContent).toContain('Inactive');
  });

  it('hides onboard button for ClientRepresentative', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CurrencySettingsListComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('trader-a'),
            traderId: signal('trader-a'),
            isTrader: () => false,
            isClientRepresentative: () => true,
          },
        },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CurrencySettingsListComponent);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/settings/currencies').flush([
      {
        code: 'EUR',
        active: true,
        minSubscriptionAmount: 1000000,
        minIncreaseDecreaseAmount: 250000,
        enabledTenors: ['3M'],
        enabledNoticePeriods: ['24H'],
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).not.toContain('Onboard currency');
    expect(el.textContent).toContain('View');
  });
});
