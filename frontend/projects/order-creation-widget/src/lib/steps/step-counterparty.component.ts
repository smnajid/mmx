import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, output, signal } from '@angular/core';
import type { CounterpartyOption } from '../models/api-responses.model';
import { WizardApiService } from '../services/wizard-api.service';
import { WizardStateService } from '../services/wizard-state.service';

@Component({
  selector: 'mmx-step-counterparty',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <section class="step-counterparty">
      <h2 class="step-title">Choose counterparty</h2>

      @if (wizardState.state().orderType === 'ON_CALL' && !wizardState.state().valueDate) {
        <p class="step-state" data-testid="counterparty-missing-value-date">
          Set a value date before choosing a counterparty.
        </p>
      } @else if (loading()) {
        <p class="step-state" data-testid="counterparty-loading">Loading counterparties…</p>
      } @else if (error()) {
        <div class="step-error" data-testid="counterparty-error" role="alert">
          <p>{{ error() }}</p>
          <button type="button" data-testid="counterparty-retry" (click)="load()">Retry</button>
        </div>
      } @else if (isOutflowShortcut()) {
        <div class="counterparty-list">
          <div class="counterparty-card counterparty-card--locked" data-testid="counterparty-locked">
            <div class="counterparty-header">
              <span class="counterparty-name">{{ lockedCounterpartyName() }}</span>
            </div>
            @if (lockedRate(); as rate) {
              <div class="counterparty-meta">
                <span>{{ rate.rate | number: '1.2-4' }}%</span>
                <span>{{ rate.rateDate }}</span>
              </div>
            } @else {
              <p class="step-state" data-testid="counterparty-no-rate">No rate published for this value date.</p>
            }
          </div>
        </div>
        <button
          type="button"
          class="continue-button"
          data-testid="counterparty-continue-locked"
          (click)="confirmLockedCounterparty()"
        >
          Continue
        </button>
      } @else if (counterparties().length === 0) {
        <p class="step-state" data-testid="counterparty-empty">
          @if (isInflowShortcut()) {
            No rate is available for the contract counterparty on this value date.
          } @else {
            No counterparties are currently available.
          }
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

    .counterparty-card--locked {
      cursor: default;
    }

    .counterparty-card:hover:not(.counterparty-card--locked) {
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

    .continue-button {
      align-self: flex-start;
      padding: 0.5rem 1rem;
      border: none;
      border-radius: 0.375rem;
      background: #155eef;
      color: #fff;
      cursor: pointer;
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
  readonly lockedRate = signal<CounterpartyOption | null>(null);

  readonly isInflowShortcut = computed(() => {
    const state = this.wizardState.state();
    return state.contractShortcut && state.operation === 'INCREASE';
  });

  readonly isOutflowShortcut = computed(() => {
    const state = this.wizardState.state();
    return (
      state.contractShortcut &&
      (state.operation === 'DECREASE' || state.operation === 'REDEMPTION')
    );
  });

  readonly lockedCounterpartyName = computed(
    () => this.wizardState.state().contractCounterparty ?? '',
  );

  ngOnInit(): void {
    if (this.wizardState.state().orderType === 'ON_CALL' && !this.wizardState.state().valueDate) {
      return;
    }
    this.load();
  }

  load(): void {
    const state = this.wizardState.state();
    const { orderType, currency, tenor, noticePeriod, valueDate } = state;
    if (!orderType || !currency) {
      this.error.set('Currency must be selected before choosing a counterparty.');
      return;
    }

    if (orderType === 'ON_CALL' && !valueDate) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.counterparties.set([]);
    this.lockedRate.set(null);

    const request$ =
      orderType === 'TERM'
        ? this.api.listTermCounterparties(currency, tenor!)
        : this.api.listOnCallCounterparties(currency, noticePeriod!, valueDate!);

    request$.subscribe({
      next: (response) => {
        const sorted = [...response.counterparties].sort((a, b) => b.rate - a.rate);
        if (this.isInflowShortcut()) {
          const lockedCode = state.contractInstitutionCode;
          const filtered = lockedCode
            ? sorted.filter((cp) => cp.institutionCode === lockedCode)
            : [];
          this.counterparties.set(filtered);
        } else if (this.isOutflowShortcut()) {
          const lockedCode = state.contractInstitutionCode;
          const match = lockedCode
            ? sorted.find((cp) => cp.institutionCode === lockedCode) ?? null
            : null;
          this.lockedRate.set(match);
        } else {
          this.counterparties.set(sorted);
        }
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

  confirmLockedCounterparty(): void {
    const state = this.wizardState.state();
    const institutionCode = state.contractInstitutionCode;
    const counterparty = state.contractCounterparty;
    if (!institutionCode || !counterparty) {
      return;
    }
    const rate = this.lockedRate();
    this.wizardState.setCounterparty(
      institutionCode,
      counterparty,
      rate?.rate,
      rate?.rateDate,
    );
    this.wizardState.completeAndAdvance();
    this.stepComplete.emit();
  }
}
