import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { InstitutionSettingsDetailComponent } from './institution-settings-detail.component';
import { TraderContextService } from '../../core/trader/trader-context.service';

describe('InstitutionSettingsDetailComponent grant intake', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<InstitutionSettingsDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InstitutionSettingsDetailComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: () => 'BVL-01' } },
          },
        },
        {
          provide: TraderContextService,
          useValue: {
            userId: signal('demo-trader'),
            isTrader: () => false,
            isClientRepresentative: () => true,
          },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InstitutionSettingsDetailComponent);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows only granted tenor toggles enabled on proxy detail', async () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/v1/settings/institutions/BVL-01')
      .flush({
        institutionCode: 'BVL-01',
        displayName: 'BankCo via LOC',
        active: true,
        hubInstitutionCode: 'BI-01',
        hubLegalEntityCode: 'LOC',
      });
    httpMock.expectOne('/api/v1/settings/delegated-grants/client').flush([
      {
        hubInstitutionCode: 'BI-01',
        clientLegalEntityCode: 'PAR',
        currency: 'EUR',
        enabledTenors: ['1M', '3M'],
        enabledNoticePeriods: [],
        active: true,
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('[data-testid="grant-intake-panel"]');
    expect(panel).toBeTruthy();
    const checkboxes = panel!.querySelectorAll('input[type="checkbox"]') as NodeListOf<HTMLInputElement>;
    const checked = [...checkboxes].filter((c) => c.checked);
    expect(checked.length).toBe(2);
    expect([...checkboxes].every((c) => c.disabled)).toBe(true);
  });
});
