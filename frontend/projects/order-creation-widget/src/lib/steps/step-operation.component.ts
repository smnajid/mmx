import { DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, output, signal } from '@angular/core';
import type { OperationOption } from '../models/api-responses.model';
import type { OrderOperation } from '../models/order-creation-payload.model';
import { WizardApiService } from '../services/wizard-api.service';
import { WizardStateService } from '../services/wizard-state.service';

const LIFECYCLE_OPERATIONS: OrderOperation[] = ['INCREASE', 'DECREASE', 'REDEMPTION'];

const OPERATION_LABELS: Record<OrderOperation, string> = {
  SUBSCRIPTION: 'Subscription',
  INCREASE: 'Increase',
  DECREASE: 'Decrease',
  REDEMPTION: 'Redemption',
};

@Component({
  selector: 'mmx-step-operation',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <section class="step-operation">
      <h2 class="step-title">Choose operation</h2>

      @if (loading()) {
        <p class="step-state" data-testid="operation-loading">Loading operations…</p>
      } @else if (error()) {
        <div class="step-error" data-testid="operation-error" role="alert">
          <p>{{ error() }}</p>
          <button type="button" data-testid="operation-retry" (click)="load()">Retry</button>
        </div>
      } @else if (operations().length === 0) {
        <p class="step-state" data-testid="operation-empty">
          No operations are currently available for this currency.
        </p>
      } @else {
        <div class="choice-grid">
          @for (option of operations(); track option.operation) {
            <button
              type="button"
              class="choice-card"
              [attr.data-testid]="'operation-' + option.operation"
              (click)="select(option)"
            >
              <span class="choice-label">{{ label(option.operation) }}</span>
              <span class="choice-hint">Min {{ option.minAmount | number: '1.0-0' }}</span>
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .step-operation {
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
      grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
      gap: 0.75rem;
    }

    .choice-card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.25rem;
      padding: 1rem;
      border: 1px solid #d0d5dd;
      border-radius: 0.5rem;
      background: #fff;
      cursor: pointer;
      text-align: left;
    }

    .choice-card:hover {
      border-color: #98a2b3;
      background: #f9fafb;
    }

    .choice-label {
      font-weight: 600;
      color: #101828;
    }

    .choice-hint {
      font-size: 0.875rem;
      color: #667085;
    }
  `,
})
export class StepOperationComponent implements OnInit {
  private readonly wizardState = inject(WizardStateService);
  private readonly api = inject(WizardApiService);

  readonly stepComplete = output<void>();

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly operations = signal<OperationOption[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const { orderType, currency, contractShortcut } = this.wizardState.state();
    if (!orderType || !currency) {
      this.loading.set(false);
      this.error.set('Currency must be selected before choosing an operation.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.operations.set([]);

    const request$ =
      orderType === 'TERM'
        ? this.api.listTermOperations(currency)
        : this.api.listOnCallOperations(currency);

    request$.subscribe({
      next: (response) => {
        const filtered = contractShortcut
          ? response.operations.filter((option) => LIFECYCLE_OPERATIONS.includes(option.operation))
          : response.operations;
        this.operations.set(filtered);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load operations. Please try again.');
      },
    });
  }

  label(operation: OrderOperation): string {
    return OPERATION_LABELS[operation];
  }

  select(option: OperationOption): void {
    this.wizardState.setOperation(option.operation, option.minAmount);
    this.wizardState.completeAndAdvance();
    this.stepComplete.emit();
  }
}
