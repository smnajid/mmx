import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { InstitutionSettingsDetailComponent } from './institution-settings-detail.component';
import { TraderContextService } from '../../core/trader/trader-context.service';

const BASE = '/api/v1/settings/institutions';

const CLIENT_INSTITUTION = {
  institutionCode: 'BVL-01',
  displayName: 'BankCo via LOC',
  active: true,
  closedToNewBusiness: false,
  termCounterpartyAccount: 'PAR-BNP-T',
  onCallCounterpartyAccount: null,
  hubInstitutionCode: 'BI-01',
  hubLegalEntityCode: 'LOC',
  enablements: [
    {
      currency: 'EUR',
      grantedTenors: ['1M', '3M'],
      grantedNoticePeriods: [],
      enabledTenors: ['3M', '6M'],
      enabledNoticePeriods: [],
    },
  ],
};

const HUB_INSTITUTION = {
  institutionCode: 'BNP-01',
  displayName: 'BNP',
  active: true,
  closedToNewBusiness: false,
  termCounterpartyAccount: null,
  onCallCounterpartyAccount: 'LOC-BNP-OC',
};

describe('InstitutionSettingsDetailComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<InstitutionSettingsDetailComponent>;
  let el: HTMLElement;

  async function render(clientRepresentative: boolean, institution: object): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [InstitutionSettingsDetailComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: { get: () => (institution as { institutionCode: string }).institutionCode },
            },
          },
        },
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('demo-trader'),
            isTrader: () => !clientRepresentative,
            isClientRepresentative: () => clientRepresentative,
          },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InstitutionSettingsDetailComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${BASE}/${(institution as { institutionCode: string }).institutionCode}`)
      .flush(institution);
    await settle();
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    el = fixture.nativeElement as HTMLElement;
  }

  function checkbox(panel: Element, code: string): HTMLInputElement {
    return panel.querySelector(`input[data-code="${code}"]`) as HTMLInputElement;
  }

  function click(label: string): void {
    const button = [...el.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === label,
    ) as HTMLButtonElement;
    expect(button).toBeTruthy();
    button.click();
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('bounds client enablement toggles by the grant and flags enabled-not-granted', async () => {
    await render(true, CLIENT_INSTITUTION);

    const panel = el.querySelector('[data-testid="enablement-EUR"]')!;
    expect(panel).toBeTruthy();
    expect(checkbox(panel, '1M').disabled).toBe(false);
    expect(checkbox(panel, '1M').checked).toBe(false);
    expect(checkbox(panel, '3M').checked).toBe(true);
    expect(checkbox(panel, '1W').disabled).toBe(true);
    expect(checkbox(panel, '6M').checked).toBe(true);
    expect(panel.querySelector('[data-testid="not-granted-6M"]')?.textContent).toContain(
      'enabled, not granted',
    );
    expect(panel.querySelector('[data-testid="not-granted-3M"]')).toBeNull();
    expect(el.textContent?.toLowerCase()).not.toContain('proxy');
  });

  it('saves the full client enablement replacement for a currency', async () => {
    await render(true, CLIENT_INSTITUTION);
    const panel = el.querySelector('[data-testid="enablement-EUR"]')!;

    checkbox(panel, '1M').click();
    await settle();
    click('Save EUR');

    const req = httpMock.expectOne(`${BASE}/BVL-01/enablement/EUR`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      enabledTenors: ['1M', '3M', '6M'],
      enabledNoticePeriods: [],
    });
    req.flush(CLIENT_INSTITUTION);
  });

  it('disables switching on an OrderType whose counterparty account is missing', async () => {
    await render(true, {
      ...CLIENT_INSTITUTION,
      enablements: [
        {
          currency: 'EUR',
          grantedTenors: ['3M'],
          grantedNoticePeriods: ['24H'],
          enabledTenors: [],
          enabledNoticePeriods: [],
        },
      ],
    });
    const panel = el.querySelector('[data-testid="enablement-EUR"]')!;

    expect(checkbox(panel, '3M').disabled).toBe(false);
    expect(checkbox(panel, '24H').disabled).toBe(true);
  });

  it('saves counterparty accounts for a Trader, sending null for a cleared account', async () => {
    await render(false, HUB_INSTITUTION);
    expect(el.querySelector('[data-testid^="enablement-"]')).toBeNull();

    fixture.componentInstance.accountsForm.patchValue({
      termCounterpartyAccount: ' LOC-BNP-T ',
      onCallCounterpartyAccount: '',
    });
    click('Save accounts');

    const req = httpMock.expectOne(`${BASE}/BNP-01/counterparty-accounts`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      termCounterpartyAccount: 'LOC-BNP-T',
      onCallCounterpartyAccount: null,
    });
    req.flush({
      ...HUB_INSTITUTION,
      termCounterpartyAccount: 'LOC-BNP-T',
      onCallCounterpartyAccount: null,
    });
    await settle();
    expect(el.textContent).toContain('Counterparty accounts saved');
  });

  it('surfaces a refused account change', async () => {
    await render(true, CLIENT_INSTITUTION);

    fixture.componentInstance.accountsForm.patchValue({ termCounterpartyAccount: '' });
    click('Save accounts');
    httpMock
      .expectOne(`${BASE}/BVL-01/counterparty-accounts`)
      .flush(
        { error: 'COUNTERPARTY_ACCOUNT_IN_USE', message: 'Term tenors are still enabled' },
        { status: 409, statusText: 'Conflict' },
      );
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Term tenors are still enabled',
    );
  });

  it('lets a ClientRepresentative offboard and re-onboard', async () => {
    await render(true, CLIENT_INSTITUTION);
    expect(el.textContent).not.toContain('Deactivate');

    click('Offboard');
    httpMock
      .expectOne(`${BASE}/BVL-01/deactivate`)
      .flush({ ...CLIENT_INSTITUTION, active: false, closedToNewBusiness: true });
    await settle();
    expect(el.textContent).toContain('Closed to new business');

    click('Re-onboard');
    httpMock.expectOne(`${BASE}/BVL-01/activate`).flush(CLIENT_INSTITUTION);
    await settle();
    expect(el.querySelector('.status-badge')?.textContent?.trim()).toBe('Open');
  });

  it('keeps Deactivate for a Trader', async () => {
    await render(false, HUB_INSTITUTION);

    expect(el.textContent).not.toContain('Offboard');
    click('Deactivate');
    httpMock
      .expectOne(`${BASE}/BNP-01/deactivate`)
      .flush({ ...HUB_INSTITUTION, active: false, closedToNewBusiness: true });
    await settle();
    expect(el.textContent).toContain('Reactivate');
  });
});
