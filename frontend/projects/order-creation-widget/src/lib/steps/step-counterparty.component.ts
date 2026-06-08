import { DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, output, signal } from '@angular/core';
import type { CounterpartyOption } from '../models/api-responses.model';
import { WizardApiService } from '../services/wizard-api.service';
import { WizardStateService } from '../services/wizard-state.service';
import { minSettlementDate } from '../utils/settlement-date';

@Component({
  selector: 'mmx-step-counterparty',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <section class="step-counterparty">
      <h2 class="step-title">Choose counterparty</h2>

      @if (wizardState.state().orderType === 'ON_CALL' && !wizardState.state().valueDate) {
        <label class="field">
          <span>Value date</span>
          <input
            type="date"
            data-testid="counterparty-value-date"
            [min]="minValueDate"
            [value]="valueDateInput()"
            (change)="onValueDateChange($event)"
          />
        </label>
      }

      @if (loading()) {
        <p class="step-state" data-testid="counterparty-loading">Loading counterparties…</p>
      } @else if (error()) {
        <div class="step-error" data-testid="counterparty-error" role="alert">
          <p>{{ error() }}</p>
          <button type="button" data-testid="counterparty-retry" (click)="load()">Retry</button>
        </div>
      } @else if (counterparties().length === 0) {
        <p class="step-state" data-testid="counterparty-empty">
          No counterparties are currently available.
        </p>
      } @else {
        <div class="counterparty-list">
          @for (cp of counterparties(); track cp.institutionCode) {
            <button
              type="button"
              class="counterparty-card"
              [attr.data-testid]="'counterparty-' + cp.institutionCode"
              (click)="select(cp)"
            >
              <div class="counterparty-header">
                <span class="counterparty-name">{{ cp.displayName }}</span>
                @if (cp.indicative) {
                  <span
                    class="indicative-badge"
                    [attr.data-testid]="'counterparty-indicative-' + cp.institutionCode"
                  >
                    Indicative
                  </span>
                }
              </div>
              <div class="counterparty-meta">
                <span>{{ cp.rate | number: '1.2-4' }}%</span>
                <span>{{ cp.rateDate }}</span>
              </div>
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .step-counterparty {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .step-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
      font-size: 0.875rem;
      color: #344054;
    }

    .field input {
      padding: 0.5rem 0.75rem;
      border: 1px solid #d0d5dd;
      border-radius: 0.375rem;
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

    .counterparty-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      max-height: 24rem;
      overflow-y: auto;
    }

    .counterparty-card {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
      padding: 0.875rem 1rem;
      border: 1px solid #d0d5dd;
      border-radius: 0.5rem;
      background: #fff;
      cursor: pointer;
      text-align: left;
    }

    .counterparty-card:hover {
      border-color: #98a2b3;
      background: #f9fafb;
    }

    .counterparty-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }

    .counterparty-name {
      font-weight: 600;
      color: #101828;
    }

    .indicative-badge {
      font-size: 0.75rem;
      padding: 0.125rem 0.5rem;
      border-radius: 999px;
      background: #fef0c7;
      color: #b54708;
    }

    .counterparty-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.875rem;
      color: #667085;
    }
  `,
})
export class StepCounterpartyComponent implements OnInit {
  protected readonly wizardState = inject(WizardStateService);
  private readonly api = inject(WizardApiService);

  readonly stepComplete = output<void>();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly counterparties = signal<CounterpartyOption[]>([]);
  readonly valueDateInput = signal('');

  readonly minValueDate = minSettlementDate();

  ngOnInit(): void {
    const { orderType, valueDate } = this.wizardState.state();
    if (orderType === 'ON_CALL' && !valueDate) {
      this.valueDateInput.set(this.minValueDate);
      return;
    }
    this.load();
  }

  onValueDateChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.valueDateInput.set(value);
    this.wizardState.setValueDate(value);
    this.load();
  }

  load(): void {
    const state = this.wizardState.state();
    const { orderType, currency, tenor, noticePeriod } = state;
    if (!orderType || !currency) {
      this.error.set('Currency must be selected before choosing a counterparty.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.counterparties.set([]);

    const request$ =
      orderType === 'TERM'
        ? this.api.listTermCounterparties(currency, tenor!)
        : this.api.listOnCallCounterparties(
            currency,
            noticePeriod!,
            state.valueDate ?? this.valueDateInput(),
          );

    request$.subscribe({
      next: (response) => {
        const sorted = [...response.counterparties].sort((a, b) => b.rate - a.rate);
        this.counterparties.set(sorted);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load counterparties. Please try again.');
      },
    });
  }

  select(counterparty: CounterpartyOption): void {
    this.wizardState.setCounterparty(
      counterparty.institutionCode,
      counterparty.displayName,
      counterparty.rate,
      counterparty.rateDate,
    );
    this.wizardState.completeAndAdvance();
    this.stepComplete.emit();
  }
}
