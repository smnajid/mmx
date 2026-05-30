import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { InstitutionSettingsApiService } from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { OrderExecutionFormComponent } from './order-execution-form.component';

describe('OrderExecutionFormComponent', () => {
  let fixture: ComponentFixture<OrderExecutionFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderExecutionFormComponent],
      providers: [
        provideRouter([]),
        {
          provide: InstitutionSettingsApiService,
          useValue: {
            list: vi.fn().mockReturnValue(
              of([{ institutionCode: 'HSBC-01', displayName: 'HSBC', active: true }])
            ),
          },
        },
        {
          provide: TraderContextService,
          useValue: { traderId: signal('t1'), setTraderId: (): void => {} },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OrderExecutionFormComponent);
    fixture.detectChanges();
  });

  it('disables submit when catalog empty', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [OrderExecutionFormComponent],
      providers: [
        provideRouter([]),
        {
          provide: InstitutionSettingsApiService,
          useValue: { list: vi.fn().mockReturnValue(of([])) },
        },
        {
          provide: TraderContextService,
          useValue: { traderId: signal('t1'), setTraderId: (): void => {} },
        },
      ],
    }).compileComponents();
    const emptyFixture = TestBed.createComponent(OrderExecutionFormComponent);
    emptyFixture.detectChanges();
    expect(emptyFixture.nativeElement.querySelector('form')).toBeNull();
    expect(emptyFixture.nativeElement.textContent).toContain('Onboard in Settings');
  });

  it('emits institutionCode on submit', () => {
    const emitted = vi.fn();
    fixture.componentInstance.submitExecute.subscribe(emitted);
    fixture.componentInstance.rateModel = '3.5';
    fixture.componentInstance.pickerLabel = 'HSBC';
    fixture.componentInstance.onSubmit();
    expect(emitted).toHaveBeenCalledWith({ executedRate: 3.5, institutionCode: 'HSBC-01' });
  });
});
