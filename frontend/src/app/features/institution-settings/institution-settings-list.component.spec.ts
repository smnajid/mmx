import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { InstitutionSettingsListComponent } from './institution-settings-list.component';
import { TraderContextService } from '../../core/trader/trader-context.service';

describe('InstitutionSettingsListComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<InstitutionSettingsListComponent>;

  async function render(clientRepresentative: boolean, rows: unknown[]): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [InstitutionSettingsListComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
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
    fixture = TestBed.createComponent(InstitutionSettingsListComponent);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/settings/institutions').flush(rows);
    await fixture.whenStable();
    fixture.detectChanges();
    httpMock.verify();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists onboarded institutions for ClientRepresentative under one Institutions heading', async () => {
    const el = await render(true, [
      {
        institutionCode: 'BVL-01',
        displayName: 'BankCo via LOC',
        active: true,
        closedToNewBusiness: false,
        termCounterpartyAccount: 'PAR-BNP-T',
        onCallCounterpartyAccount: null,
        hubInstitutionCode: 'BI-01',
        hubLegalEntityCode: 'LOC',
      },
      {
        institutionCode: 'SVL-01',
        displayName: 'SG via LOC',
        active: false,
        closedToNewBusiness: true,
        hubInstitutionCode: 'SG-01',
        hubLegalEntityCode: 'LOC',
      },
    ]);

    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Institutions');
    expect(el.textContent).toContain('BVL-01');
    expect(el.textContent).toContain('PAR-BNP-T');
    expect(el.textContent).toContain('Onboard institution');
    expect(el.textContent?.toLowerCase()).not.toContain('proxy');

    const badges = [...el.querySelectorAll('.status-badge')].map((b) => b.textContent?.trim());
    expect(badges).toEqual(['Open', 'Closed to new business']);
  });

  it('shows counterparty account columns for a Trader', async () => {
    const el = await render(false, [
      {
        institutionCode: 'BNP-01',
        displayName: 'BNP',
        active: true,
        closedToNewBusiness: false,
        termCounterpartyAccount: 'LOC-BNP-T',
        onCallCounterpartyAccount: 'LOC-BNP-OC',
      },
    ]);

    const headers = [...el.querySelectorAll('th')].map((h) => h.textContent?.trim());
    expect(headers).toContain('Term account');
    expect(headers).toContain('OnCall account');
    expect(el.textContent).toContain('LOC-BNP-T');
    expect(el.textContent).toContain('LOC-BNP-OC');
  });

  it('flags a missing counterparty account', async () => {
    const el = await render(false, [
      { institutionCode: 'BNP-01', displayName: 'BNP', active: true, closedToNewBusiness: false },
    ]);

    expect(el.querySelectorAll('[data-testid="account-missing"]').length).toBe(2);
  });
});
