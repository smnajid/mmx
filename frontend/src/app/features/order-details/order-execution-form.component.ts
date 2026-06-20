import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CounterpartyRow,
  OrderCreationApiService,
} from '../../core/api/order-creation-api.service';
import type { ExecuteOrderRequest, OrderDetails } from '../../core/models/order.model';
import { OrderType } from '../../core/models/order-type.enum';

@Component({
  selector: 'mmx-order-execution-form',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="panel">
      <h2 class="panel-title">Record execution</h2>
      <p class="hint">
        Counterparty was chosen at intake. Dealing reference and contract number are generated on submit.
      </p>

      <div class="locked-counterparty">
        <span class="label">Counterparty</span>
        <p class="counterparty-value">
          {{ order().counterparty }}
          <span class="mono muted">{{ order().institutionCode }}</span>
        </p>
      </div>

      @if (rateLoading()) {
        <p class="hint">Loading proposed rate…</p>
      } @else {
        <form class="form" (ngSubmit)="onSubmit()">
          <label class="field">
            <span class="label">Executed rate</span>
            <input
              type="number"
              name="executedRate"
              step="any"
              min="0"
              class="input mono"
              [(ngModel)]="rateModel"
              [disabled]="submitting()"
              required
              autocomplete="off"
            />
            @if (proposedRateDate()) {
              <span class="rate-meta mono">{{ proposedRateDate() }}</span>
            }
            @if (proposedIndicative()) {
              <span class="indicative-badge" data-testid="execute-indicative-badge">Indicative</span>
            }
            @if (noRateHint()) {
              <span class="no-rate-hint">{{ noRateHint() }}</span>
            }
          </label>
          @if (order().minimumRate !== null && order().minimumRate !== undefined) {
            <p class="floor-hint mono">PM floor: {{ order().minimumRate }}%</p>
          }
          @if (localError()) {
            <p class="field-error" role="alert">{{ localError() }}</p>
          }
          <button type="submit" class="submit" [disabled]="submitting()">
            {{ submitting() ? 'Submitting…' : 'Execute order' }}
          </button>
        </form>
      }
    </div>
  `,
  styles: `
    .panel {
      margin-top: 1.25rem;
      padding: 1.1rem 1.15rem;
      border: 1px solid var(--mmx-border);
      border-radius: 6px;
      background: rgba(0, 0, 0, 0.12);
    }

    .panel-title {
      font-family: var(--font-display);
      font-size: 1rem;
      font-weight: 600;
      margin: 0 0 0.35rem;
      color: var(--mmx-text);
    }

    .hint {
      margin: 0 0 1rem;
      font-size: 0.82rem;
      color: var(--mmx-text-muted);
      max-width: 52ch;
      line-height: 1.4;
    }

    .locked-counterparty {
      margin-bottom: 1rem;
    }

    .counterparty-value {
      margin: 0.25rem 0 0;
      font-size: 0.95rem;
      color: var(--mmx-text);
    }

    .muted {
      margin-left: 0.5rem;
      font-size: 0.82rem;
      color: var(--mmx-text-muted);
    }

    .form {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .label {
      font-family: var(--font-mono);
      font-size: 0.65rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
    }

    .input {
      padding: 0.5rem 0.65rem;
      border-radius: 4px;
      border: 1px solid var(--mmx-border);
      background: var(--mmx-surface);
      color: var(--mmx-text);
      font-size: 0.9rem;
    }

    .input:focus {
      outline: 1px solid var(--mmx-accent);
      border-color: var(--mmx-accent);
    }

    .input:disabled {
      opacity: 0.6;
    }

    .mono {
      font-family: var(--font-mono);
    }

    .rate-meta {
      font-size: 0.78rem;
      color: var(--mmx-text-muted);
    }

    .indicative-badge {
      align-self: flex-start;
      font-size: 0.72rem;
      padding: 0.125rem 0.5rem;
      border-radius: 999px;
      background: rgba(232, 168, 56, 0.2);
      color: var(--mmx-accent);
    }

    .no-rate-hint {
      font-size: 0.78rem;
      color: var(--mmx-text-muted);
    }

    .floor-hint {
      margin: 0;
      font-size: 0.78rem;
      color: var(--mmx-text-muted);
    }

    .field-error {
      margin: 0;
      font-size: 0.8rem;
      color: #fda4af;
    }

    .submit {
      align-self: flex-start;
      margin-top: 0.25rem;
      font-family: var(--font-mono);
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.55rem 1.1rem;
      border-radius: 4px;
      cursor: pointer;
      border: 1px solid var(--mmx-accent);
      background: var(--mmx-accent-dim);
      color: var(--mmx-accent);
      transition: background 0.2s ease;
    }

    .submit:hover:not(:disabled) {
      background: rgba(232, 168, 56, 0.18);
    }

    .submit:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderExecutionFormComponent {
  readonly order = input.required<OrderDetails>();
  readonly submitting = input(false);

  readonly submitExecute = output<ExecuteOrderRequest>();

  private readonly orderCreationApi = inject(OrderCreationApiService);

  readonly localError = signal<string | null>(null);
  readonly rateLoading = signal(true);
  readonly proposedRateDate = signal<string | null>(null);
  readonly proposedIndicative = signal(false);
  readonly noRateHint = signal<string | null>(null);

  rateModel = '';

  constructor() {
    effect(() => {
      const o = this.order();
      this.loadProposedRate(o);
    });
  }

  onSubmit(): void {
    this.localError.set(null);
    const raw = this.rateModel === '' ? NaN : Number(this.rateModel);
    if (Number.isNaN(raw) || raw < 0) {
      this.localError.set('Enter a valid executed rate (≥ 0).');
      return;
    }
    const floor = this.order().minimumRate;
    if (floor !== null && floor !== undefined && raw < floor) {
      this.localError.set(`Executed rate must be at least ${floor}%.`);
      return;
    }
    this.submitExecute.emit({ executedRate: raw });
  }

  private loadProposedRate(order: OrderDetails): void {
    this.rateLoading.set(true);
    this.proposedRateDate.set(null);
    this.proposedIndicative.set(false);
    this.noRateHint.set(null);
    this.rateModel = '';

    const code = order.institutionCode;
    if (!code) {
      this.rateLoading.set(false);
      this.noRateHint.set('No proposed rate found for this institution.');
      return;
    }

    const onSuccess = (rows: CounterpartyRow[]): void => {
      const match = rows.find((c) => c.institutionCode === code);
      if (match) {
        this.rateModel = String(match.rate);
        this.proposedRateDate.set(match.rateDate);
        this.proposedIndicative.set(match.indicative);
      } else {
        this.noRateHint.set('No proposed rate found for this institution.');
      }
      this.rateLoading.set(false);
    };

    const onError = (): void => {
      this.noRateHint.set('No proposed rate found for this institution.');
      this.rateLoading.set(false);
    };

    if (order.orderType === OrderType.TERM && order.tenor) {
      this.orderCreationApi.listTermCounterparties(order.currency, order.tenor).subscribe({
        next: (res) => onSuccess(res.counterparties),
        error: onError,
      });
      return;
    }

    if (order.orderType === OrderType.ON_CALL && order.noticePeriod) {
      this.orderCreationApi
        .listOnCallCounterparties(order.currency, order.noticePeriod, order.valueDate)
        .subscribe({
          next: (res) => onSuccess(res.counterparties),
          error: onError,
        });
      return;
    }

    this.rateLoading.set(false);
    this.noRateHint.set('No proposed rate found for this institution.');
  }
}
