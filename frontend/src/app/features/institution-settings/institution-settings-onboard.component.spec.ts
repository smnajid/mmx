import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { InstitutionSettingsApiService } from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { InstitutionSettingsOnboardComponent } from './institution-settings-onboard.component';

describe('InstitutionSettingsOnboardComponent', () => {
  let fixture: ComponentFixture<InstitutionSettingsOnboardComponent>;
  const onboard = vi.fn();

  it('submits displayName only for Trader', async () => {
    onboard.mockReturnValue(
      of({ institutionCode: 'HSBC-01', displayName: 'HSBC', active: true })
    );
    await TestBed.configureTestingModule({
      imports: [InstitutionSettingsOnboardComponent],
      providers: [
        provideRouter([]),
        { provide: InstitutionSettingsApiService, useValue: { onboard } },
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('t1'),
            isTrader: () => true,
            isClientRepresentative: () => false,
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(InstitutionSettingsOnboardComponent);
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.form.patchValue({ displayName: 'HSBC' });
    fixture.componentInstance.save();
    expect(onboard).toHaveBeenCalledWith('t1', { displayName: 'HSBC' });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/settings/institutions', 'HSBC-01']);
  });

  it('submits hubInstitutionCode for ClientRepresentative proxy onboard', async () => {
    onboard.mockReturnValue(
      of({
        institutionCode: 'BVL-01',
        displayName: 'BankCo via LOC',
        active: true,
        hubInstitutionCode: 'BI-01',
        hubLegalEntityCode: 'LOC',
      })
    );
    await TestBed.configureTestingModule({
      imports: [InstitutionSettingsOnboardComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        { provide: InstitutionSettingsApiService, useValue: { onboard } },
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('t1'),
            isTrader: () => false,
            isClientRepresentative: () => true,
          },
        },
      ],
    }).compileComponents();
    const httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InstitutionSettingsOnboardComponent);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/settings/delegated-grants/client').flush([
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

    fixture.componentInstance.form.patchValue({ hubInstitutionCode: 'BI-01' });
    fixture.componentInstance.save();
    expect(onboard).toHaveBeenCalledWith('t1', { hubInstitutionCode: 'BI-01' });
    httpMock.verify();
  });
});
