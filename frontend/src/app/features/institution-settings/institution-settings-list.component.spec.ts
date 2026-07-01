import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { InstitutionSettingsListComponent } from './institution-settings-list.component';
import { TraderContextService } from '../../core/trader/trader-context.service';

describe('InstitutionSettingsListComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<InstitutionSettingsListComponent>;

  it('lists proxies for ClientRepresentative', async () => {
    await TestBed.configureTestingModule({
      imports: [InstitutionSettingsListComponent, HttpClientTestingModule],
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
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InstitutionSettingsListComponent);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/settings/institutions');
    req.flush([
      {
        institutionCode: 'BVL-01',
        displayName: 'BankCo via LOC',
        active: true,
        hubInstitutionCode: 'BI-01',
        hubLegalEntityCode: 'LOC',
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Proxy institutions');
    expect(el.textContent).toContain('BVL-01');
    expect(el.textContent).not.toContain('Onboard institution');
    httpMock.verify();
  });
});
