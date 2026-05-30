import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { InstitutionSettingsApiService } from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { InstitutionSettingsOnboardComponent } from './institution-settings-onboard.component';

describe('InstitutionSettingsOnboardComponent', () => {
  let fixture: ComponentFixture<InstitutionSettingsOnboardComponent>;
  const onboard = vi.fn();

  beforeEach(async () => {
    onboard.mockReturnValue(
      of({ institutionCode: 'HSBC-01', displayName: 'HSBC', active: true })
    );
    await TestBed.configureTestingModule({
      imports: [InstitutionSettingsOnboardComponent],
      providers: [
        provideRouter([]),
        {
          provide: InstitutionSettingsApiService,
          useValue: { onboard },
        },
        {
          provide: TraderContextService,
          useValue: { traderId: signal('t1'), setTraderId: (): void => {} },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(InstitutionSettingsOnboardComponent);
    fixture.detectChanges();
  });

  it('submits displayName only without institutionCode field', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture.componentInstance.form.patchValue({ displayName: 'HSBC' });
    fixture.componentInstance.save();
    expect(onboard).toHaveBeenCalledWith('t1', { displayName: 'HSBC' });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/settings/institutions', 'HSBC-01']);
  });
});
