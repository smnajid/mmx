import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { OrderDetails, UpdateOrderRequest } from '../../core/models/order.model';

@Component({
  selector: 'mmx-order-update-form',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  template: `
    <div class="panel">
      <h2 class="panel-title">Adjust order parameters</h2>
      <p class="hint">
        Change amount or value date before execution. Minimum rate is set only by Portfolio Management at intake and
        cannot be changed here. Counterparty preference from intake is shown for reference only.
      </p>
      @if (order().minimumRate !== null && order().minimumRate !== undefined) {
        <div class="readonly-block">
          <span class="readonly-label">Minimum rate (PM floor)</span>
          <p class="readonly-value mono">{{ order().minimumRate | number: '1.2-8' }}</p>
        </div>
      }
      @if (order().desiredCounterpartyComment) {
        <div class="readonly-comment">
          <span class="readonly-label">Counterparty comment (intake)</span>
          <p class="readonly-value">{{ order().desiredCounterpartyComment }}</p>
        </div>
      }
      <form class="form" (ngSubmit)="onSubmit()">
        <label class="field">
          <span class="label">Amount</span>
          <input
            type="number"
            name="amount"
            step="any"
            min="0"
            class="input mono"
            [(ngModel)]="amountModel"
            [disabled]="submitting()"
            autocomplete="off"
          />
        </label>
        <label class="field">
          <span class="label">Value date</span>
          <input
            type="date"
            name="valueDate"
            class="input mono"
            [(ngModel)]="valueDateModel"
            [disabled]="submitting()"
          />
        </label>
        @if (localError()) {
          <p class="field-error" role="alert">{{ localError() }}</p>
        }
        <button type="submit" class="submit" [disabled]="submitting()">
          {{ submitting() ? 'Saving…' : 'Save changes' }}
        </button>
      </form>
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

    .readonly-comment,
    .readonly-block {
      margin-bottom: 1rem;
      padding: 0.65rem 0.75rem;
      border-radius: 4px;
      border: 1px solid var(--mmx-border);
      background: rgba(0, 0, 0, 0.15);
    }

    .readonly-label {
      display: block;
      font-family: var(--font-mono);
      font-size: 0.6rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
      margin-bottom: 0.35rem;
    }

    .readonly-value {
      margin: 0;
      font-size: 0.85rem;
      color: var(--mmx-text-muted);
      line-height: 1.4;
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
export class OrderUpdateFormComponent {
  readonly order = input.required<OrderDetails>();

  readonly submitting = input(false);

  readonly submitUpdate = output<UpdateOrderRequest>();

  readonly localError = signal<string | null>(null);

  amountModel = '';
  valueDateModel = '';

  constructor() {
    effect(() => {
      const o = this.order();
      this.amountModel = String(o.amount);
      this.valueDateModel = o.valueDate;
    });
  }

  onSubmit(): void {
    this.localError.set(null);
    const amount = this.amountModel === '' ? NaN : Number(this.amountModel);
    const vd = this.valueDateModel?.trim() ?? '';
    if (Number.isNaN(amount) || amount <= 0) {
      this.localError.set('Enter a valid amount (> 0).');
      return;
    }
    if (!vd) {
      this.localError.set('Value date is required.');
      return;
    }
    const body: UpdateOrderRequest = {
      amount,
      valueDate: vd,
    };
    this.submitUpdate.emit(body);
  }
}
