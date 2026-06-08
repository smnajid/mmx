import { Component, inject, OnInit, output, signal } from '@angular/core';
import { WizardApiService } from '../services/wizard-api.service';
import { WizardStateService } from '../services/wizard-state.service';

@Component({
  selector: 'mmx-step-currency',
  standalone: true,
  template: `
    <section class="step-currency">
      <h2 class="step-title">Choose currency</h2>

      @if (loading()) {
        <p class="step-state" data-testid="currency-loading">Loading currencies…</p>
      } @else if (error()) {
        <div class="step-error" data-testid="currency-error" role="alert">
          <p>{{ error() }}</p>
          <button type="button" data-testid="currency-retry" (click)="load()">Retry</button>
        </div>
      } @else if (currencies().length === 0) {
        <p class="step-state" data-testid="currency-empty">
          No currencies are currently available for order creation.
        </p>
      } @else {
        <div class="choice-grid">
          @for (currency of currencies(); track currency) {
            <button
              type="button"
              class="choice-card"
              [attr.data-testid]="'currency-' + currency"
              (click)="select(currency)"
            >
              <span class="choice-label">{{ currency }}</span>
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .step-currency {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .step-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
    }

    .step-state {
      margin: 0;
      color: #667085;
    }

    .step-error {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.75rem 1rem;
      border: 1px solid #fda29b;
      border-radius: 0.5rem;
      background: #fef3f2;
      color: #b42318;
    }

    .step-error p {
      margin: 0;
    }

    .step-error button {
      padding: 0.375rem 0.75rem;
      border: 1px solid #b42318;
      border-radius: 0.375rem;
      background: #fff;
      color: #b42318;
      cursor: pointer;
    }

    .choice-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(7rem, 1fr));
      gap: 0.75rem;
    }

    .choice-card {
      padding: 1rem;
      border: 1px solid #d0d5dd;
      border-radius: 0.5rem;
      background: #fff;
      cursor: pointer;
      text-align: center;
    }

    .choice-card:hover {
      border-color: #98a2b3;
      background: #f9fafb;
    }

    .choice-label {
      font-weight: 600;
      color: #101828;
    }
  `,
})
export class StepCurrencyComponent implements OnInit {
  private readonly wizardState = inject(WizardStateService);
  private readonly api = inject(WizardApiService);

  readonly stepComplete = output<void>();

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly currencies = signal<string[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const orderType = this.wizardState.state().orderType;
    if (!orderType) {
      this.loading.set(false);
      this.error.set('Order type must be selected before choosing a currency.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.currencies.set([]);

    const request$ =
      orderType === 'TERM' ? this.api.listTermCurrencies() : this.api.listOnCallCurrencies();

    request$.subscribe({
      next: (response) => {
        this.currencies.set(response.currencies);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load currencies. Please try again.');
      },
    });
  }

  select(currency: string): void {
    this.wizardState.setCurrency(currency);
    this.wizardState.completeAndAdvance();
    this.stepComplete.emit();
  }
}
