import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { CurrencySettingsEditComponent } from './currency-settings-edit.component';
import { TraderContextService } from '../../core/trader/trader-context.service';

describe('CurrencySettingsEditComponent', () => {
  let fixture: ComponentFixture<CurrencySettingsEditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CurrencySettingsEditComponent],
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
          useValue: { traderId: signal('trader-a'), setTraderId: (): void => {} },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CurrencySettingsEditComponent);
  });

  it('disables sole enabled tenor toggle', async () => {
    const component = fixture.componentInstance;
    component['selectedTenors'].set(new Set(['3M']));
    fixture.detectChanges();
    expect(component.tenorDisableBlocked('3M')).toBe(true);
    expect(component.tenorDisableBlocked('1M')).toBe(false);
  });
});
