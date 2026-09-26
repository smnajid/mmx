import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { InstitutionSettingsApiService } from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { InstitutionSettingsOnboardComponent } from './institution-settings-onboard.component';

describe('InstitutionSettingsOnboardComponent', () => {
  let fixture: ComponentFixture<InstitutionSettingsOnboardComponent>;
  let onboard: ReturnType<typeof vi.fn>;
  let listGrantedInstitutions: ReturnType<typeof vi.fn>;

  async function setUp(clientRepresentative: boolean): Promise<void> {
    onboard = vi.fn();
    listGrantedInstitutions = vi.fn().mockReturnValue(
      of([
        {
          hubLegalEntityCode: 'LOC',
          hubInstitutionCode: 'BNP-01',
          displayName: 'BNP via LOC',
          currencies: ['EUR'],
          onboardedInstitutionCode: 'BVL-01',
          closedToNewBusiness: false,
        },
        {
          hubLegalEntityCode: 'LOC',
          hubInstitutionCode: 'SG-01',
          displayName: 'SG via LOC',
          currencies: ['USD'],
        },
        {
          hubLegalEntityCode: 'LOC',
          hubInstitutionCode: 'HSBC-01',
          displayName: 'HSBC via LOC',
          currencies: ['GBP'],
          onboardedInstitutionCode: 'HVL-01',
          closedToNewBusiness: true,
        },
      ]),
    );
    await TestBed.configureTestingModule({
      imports: [InstitutionSettingsOnboardComponent],
      providers: [
        provideRouter([]),
        { provide: InstitutionSettingsApiService, useValue: { onboard, listGrantedInstitutions } },
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('t1'),
            isTrader: () => !clientRepresentative,
            isClientRepresentative: () => clientRepresentative,
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(InstitutionSettingsOnboardComponent);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  }

  it('submits displayName only for Trader when no account is entered', async () => {
    await setUp(false);
    onboard.mockReturnValue(
      of({
        institutionCode: 'HSBC-01',
        displayName: 'HSBC',
        active: true,
        closedToNewBusiness: false,
      }),
    );

    fixture.componentInstance.form.patchValue({ displayName: 'HSBC' });
    fixture.componentInstance.save();

    expect(onboard).toHaveBeenCalledWith('t1', { displayName: 'HSBC' });
    expect(listGrantedInstitutions).not.toHaveBeenCalled();
    await fixture.whenStable();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith([
      '/settings/institutions',
      'HSBC-01',
    ]);
  });

  it('submits trimmed optional counterparty accounts for Trader', async () => {
    await setUp(false);
    onboard.mockReturnValue(
      of({
        institutionCode: 'HSBC-01',
        displayName: 'HSBC',
        active: true,
        closedToNewBusiness: false,
      }),
    );

    fixture.componentInstance.form.patchValue({
      displayName: 'HSBC',
      termCounterpartyAccount: ' LOC-HSBC-T ',
      onCallCounterpartyAccount: '',
    });
    fixture.componentInstance.save();

    expect(onboard).toHaveBeenCalledWith('t1', {
      displayName: 'HSBC',
      termCounterpartyAccount: 'LOC-HSBC-T',
    });
  });

  it('offers ClientRepresentative only granted institutions not yet open', async () => {
    await setUp(true);
    const options = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll('select option:not([disabled])'),
    ].map((o) => o.textContent?.trim());

    expect(options).toEqual(['HSBC via LOC (re-onboard)', 'SG via LOC']);
    expect((fixture.nativeElement as HTMLElement).textContent?.toLowerCase()).not.toContain(
      'proxy',
    );
  });

  it('submits hubInstitutionCode with accounts for ClientRepresentative', async () => {
    await setUp(true);
    onboard.mockReturnValue(
      of({
        institutionCode: 'SVL-01',
        displayName: 'SG via LOC',
        active: true,
        closedToNewBusiness: false,
        hubInstitutionCode: 'SG-01',
        hubLegalEntityCode: 'LOC',
      }),
    );

    fixture.componentInstance.form.patchValue({
      hubInstitutionCode: 'SG-01',
      onCallCounterpartyAccount: 'PAR-SG-OC',
    });
    fixture.componentInstance.save();

    expect(onboard).toHaveBeenCalledWith('t1', {
      hubInstitutionCode: 'SG-01',
      onCallCounterpartyAccount: 'PAR-SG-OC',
    });
  });

  it('rejects a whitespace-only Trader display name before submit', async () => {
    await setUp(false);
    fixture.componentInstance.form.patchValue({ displayName: '   ' });

    expect(fixture.componentInstance.form.invalid).toBe(true);
    fixture.componentInstance.save();
    expect(onboard).not.toHaveBeenCalled();
  });

  it('rejects an over-long counterparty account before submit', async () => {
    await setUp(false);
    fixture.componentInstance.form.patchValue({
      displayName: 'HSBC',
      termCounterpartyAccount: 'X'.repeat(35),
    });

    expect(fixture.componentInstance.form.invalid).toBe(true);
    fixture.componentInstance.save();
    expect(onboard).not.toHaveBeenCalled();
  });
});
