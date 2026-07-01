import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { DelegatedGrantsFormComponent } from './delegated-grants-form.component';

describe('DelegatedGrantsFormComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<DelegatedGrantsFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DelegatedGrantsFormComponent, HttpClientTestingModule],
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
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DelegatedGrantsFormComponent);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('bounds tenor toggles to hub managed currency on create', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/settings/institutions').flush([
      { institutionCode: 'BI-01', displayName: 'BankCo', active: true },
    ]);
    httpMock.expectOne('/api/v1/settings/currencies').flush([
      {
        code: 'EUR',
        active: true,
        minSubscriptionAmount: 1_000_000,
        minIncreaseDecreaseAmount: 250_000,
        enabledTenors: ['1M', '3M'],
        enabledNoticePeriods: ['24H'],
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.componentInstance.form.patchValue({ currency: 'EUR' });
    fixture.componentInstance.onCurrencyChange();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const checkboxes = el.querySelectorAll('fieldset input[type="checkbox"]') as NodeListOf<HTMLInputElement>;
    expect(checkboxes.length).toBe(3);
    expect([...checkboxes].every((c) => !c.disabled)).toBe(true);
    expect(el.textContent).not.toContain('6M');
  });

  it('creates a grant via API', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/settings/institutions').flush([
      { institutionCode: 'BI-01', displayName: 'BankCo', active: true },
    ]);
    httpMock.expectOne('/api/v1/settings/currencies').flush([
      {
        code: 'EUR',
        active: true,
        minSubscriptionAmount: 1_000_000,
        minIncreaseDecreaseAmount: 250_000,
        enabledTenors: ['3M'],
        enabledNoticePeriods: ['24H'],
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.componentInstance.form.patchValue({
      hubInstitutionCode: 'BI-01',
      clientLegalEntityCode: 'PAR',
      currency: 'EUR',
    });
    fixture.componentInstance.onCurrencyChange();
    fixture.componentInstance.toggleTenor('3M', true);
    fixture.componentInstance.save();

    const createReq = httpMock.expectOne('/api/v1/settings/delegated-grants');
    expect(createReq.request.method).toBe('POST');
    expect(createReq.request.body.enabledTenors).toEqual(['3M']);
    createReq.flush({
      hubInstitutionCode: 'BI-01',
      clientLegalEntityCode: 'PAR',
      currency: 'EUR',
      enabledTenors: ['3M'],
      enabledNoticePeriods: [],
      active: true,
    });
  });
});
