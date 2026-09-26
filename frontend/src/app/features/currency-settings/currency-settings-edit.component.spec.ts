import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CurrencySettingsEditComponent } from './currency-settings-edit.component';
import { TraderContextService } from '../../core/trader/trader-context.service';

describe('CurrencySettingsEditComponent', () => {
  let fixture: ComponentFixture<CurrencySettingsEditComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CurrencySettingsEditComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'code' ? 'EUR' : null),
              },
            },
          },
        },
        {
          provide: TraderContextService,
          useValue: {
            traderId: signal('trader-a'),
            userId: signal('trader-a'),
            isTrader: () => true,
            isClientRepresentative: () => false,
          },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CurrencySettingsEditComponent);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows Reactivate when currency is inactive', async () => {
    fixture.detectChanges();
    const load = httpMock.expectOne('/api/v1/settings/currencies/EUR');
    load.flush({
      code: 'EUR',
      active: false,
      minSubscriptionAmount: 1000000,
      minIncreaseDecreaseAmount: 250000,
      enabledTenors: ['3M'],
      enabledNoticePeriods: ['24H'],
    });
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('button.activate')?.textContent?.trim()).toBe('Reactivate');
    expect(el.querySelector('button.warn')).toBeNull();
  });

  it('blocks last tenor toggle only when OnCall workspace is empty', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/settings/currencies/EUR').flush({
      code: 'EUR',
      active: true,
      minSubscriptionAmount: 1000000,
      minIncreaseDecreaseAmount: 250000,
      enabledTenors: ['3M'],
      enabledNoticePeriods: [],
    });
    await fixture.whenStable();
    const component = fixture.componentInstance;
    fixture.detectChanges();
    expect(component.tenorDisableBlocked('3M')).toBe(true);
    expect(component.tenorDisableBlocked('1M')).toBe(false);
  });

  it('allows clearing all notices when tenors remain', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/settings/currencies/EUR').flush({
      code: 'EUR',
      active: true,
      minSubscriptionAmount: 1000000,
      minIncreaseDecreaseAmount: 250000,
      enabledTenors: ['3M'],
      enabledNoticePeriods: ['24H', '48H'],
    });
    await fixture.whenStable();
    const component = fixture.componentInstance;
    fixture.detectChanges();
    expect(component.noticeDisableBlocked('24H')).toBe(false);
  });
});

describe('CurrencySettingsEditComponent ClientRepresentative', () => {
  it('renders read-only without save controls for ClientRepresentative', async () => {
    await TestBed.configureTestingModule({
      imports: [CurrencySettingsEditComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'code' ? 'EUR' : null),
              },
            },
          },
        },
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
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CurrencySettingsEditComponent);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/settings/currencies/EUR').flush({
      code: 'EUR',
      active: true,
      minSubscriptionAmount: 1000000,
      minIncreaseDecreaseAmount: 250000,
      enabledTenors: ['3M'],
      enabledNoticePeriods: ['24H'],
    });
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Read-only');
    expect(el.querySelector('button[type="submit"]')).toBeNull();
    const checkbox = el.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
    expect(checkbox?.disabled).toBe(true);
    httpMock.verify();
  });
});
