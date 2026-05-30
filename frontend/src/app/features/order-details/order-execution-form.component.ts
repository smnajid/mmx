import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  Institution,
  InstitutionSettingsApiService,
} from '../../core/api/institution-settings-api.service';
import type { ExecuteOrderRequest } from '../../core/models/order.model';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'mmx-order-execution-form',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="panel">
      <h2 class="panel-title">Record execution</h2>
      <p class="hint">
        Select an onboarded institution (counterparty). Dealing reference and contract number are generated on submit.
      </p>

      @if (catalogLoading()) {
        <p class="hint">Loading institutions…</p>
      } @else if (catalogEmpty()) {
        <p class="empty-catalog" role="alert">
          No institutions onboarded — execute is disabled.
          <a routerLink="/settings/institutions">Onboard in Settings</a>
        </p>
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
          </label>
          <label class="field">
            <span class="label">Counterparty</span>
            <input
              type="text"
              name="institutionPicker"
              class="input"
              [(ngModel)]="pickerLabel"
              [disabled]="submitting()"
              list="institution-options"
              required
              placeholder="Start typing institution name"
              autocomplete="off"
              (input)="onPickerInput()"
            />
            <datalist id="institution-options">
              @for (i of activeInstitutions(); track i.institutionCode) {
                <option [value]="i.displayName"></option>
              }
            </datalist>
          </label>
          @if (localError()) {
            <p class="field-error" role="alert">{{ localError() }}</p>
          }
          <button type="submit" class="submit" [disabled]="submitting() || !selectedCode()">
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

    .empty-catalog {
      margin: 0;
      font-size: 0.85rem;
      color: #fda4af;
      line-height: 1.5;
    }

    .empty-catalog a {
      color: var(--mmx-accent);
      margin-left: 0.35rem;
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
export class OrderExecutionFormComponent implements OnInit {
  readonly submitting = input(false);

  readonly submitExecute = output<ExecuteOrderRequest>();

  private readonly institutionApi = inject(InstitutionSettingsApiService);
  private readonly trader = inject(TraderContextService);

  readonly localError = signal<string | null>(null);
  readonly catalogLoading = signal(true);
  readonly catalogEmpty = signal(false);
  readonly activeInstitutions = signal<Institution[]>([]);
  readonly selectedCode = signal<string | null>(null);

  rateModel = '';
  pickerLabel = '';

  ngOnInit(): void {
    this.institutionApi.list(this.trader.traderId(), true).subscribe({
      next: (list) => {
        this.activeInstitutions.set(list);
        this.catalogEmpty.set(list.length === 0);
        this.catalogLoading.set(false);
      },
      error: () => {
        this.catalogEmpty.set(true);
        this.catalogLoading.set(false);
      },
    });
  }

  onPickerInput(): void {
    const label = this.pickerLabel.trim();
    const match = this.activeInstitutions().find(
      (i) => i.displayName.toLowerCase() === label.toLowerCase()
    );
    this.selectedCode.set(match?.institutionCode ?? null);
  }

  onSubmit(): void {
    this.localError.set(null);
    const raw = this.rateModel === '' ? NaN : Number(this.rateModel);
    if (Number.isNaN(raw) || raw < 0) {
      this.localError.set('Enter a valid executed rate (≥ 0).');
      return;
    }
    this.onPickerInput();
    const code = this.selectedCode();
    if (!code) {
      this.localError.set('Select an active institution from the list.');
      return;
    }
    this.submitExecute.emit({ executedRate: raw, institutionCode: code });
  }
}
