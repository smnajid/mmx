import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { ExecuteOrderRequest } from '../../core/models/order.model';

@Component({
  selector: 'mmx-order-execution-form',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="panel">
      <h2 class="panel-title">Record execution</h2>
      <p class="hint">
        Captures the dealt rate and counterparty; dealing reference and contract number are generated on submit.
      </p>
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
        </label>
        <label class="field">
          <span class="label">Counterparty</span>
          <input
            type="text"
            name="counterparty"
            class="input"
            [(ngModel)]="counterpartyModel"
            [disabled]="submitting()"
            maxlength="200"
            required
            placeholder="e.g. BankCo International"
            autocomplete="organization"
          />
        </label>
        @if (localError()) {
          <p class="field-error" role="alert">{{ localError() }}</p>
        }
        <button type="submit" class="submit" [disabled]="submitting()">
          {{ submitting() ? 'Submitting…' : 'Execute order' }}
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
export class OrderExecutionFormComponent {
  readonly submitting = input(false);

  readonly submitExecute = output<ExecuteOrderRequest>();

  readonly localError = signal<string | null>(null);

  rateModel = '';
  counterpartyModel = '';

  onSubmit(): void {
    this.localError.set(null);
    const raw = this.rateModel === '' ? NaN : Number(this.rateModel);
    const cp = this.counterpartyModel.trim();
    if (Number.isNaN(raw) || raw < 0) {
      this.localError.set('Enter a valid executed rate (≥ 0).');
      return;
    }
    if (cp.length === 0) {
      this.localError.set('Counterparty is required.');
      return;
    }
    this.submitExecute.emit({ executedRate: raw, counterparty: cp });
  }
}
