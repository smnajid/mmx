import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GlobalAccountsApiService } from '../../core/api/global-accounts-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-global-accounts-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="settings-panel">
      <nav class="settings-toolbar">
        <a routerLink="/settings/global-accounts" class="settings-back">← Global accounts</a>
      </nav>

      <header class="settings-header">
        <h1>{{ isEdit() ? 'Edit global account' : 'Add global account' }}</h1>
      </header>

      @if (!trader.isTrader()) {
        <p class="settings-error" role="alert" data-testid="trader-only-message">
          Global account management is available only to a Trader on the TradingHub.
        </p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" class="settings-form">
          <label>
            Client legal entity
            <input formControlName="clientLegalEntityCode" maxlength="3" [readonly]="isEdit()" />
          </label>
          <label>
            Currency
            <input formControlName="currency" maxlength="3" [readonly]="isEdit()" />
          </label>
          <label>
            Account reference
            <input formControlName="accountRef" maxlength="50" />
          </label>
          @if (error()) {
            <p class="settings-error" role="alert">{{ error() }}</p>
          }
          <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">
            {{ saving() ? 'Saving…' : 'Save' }}
          </button>
        </form>
      }
    </section>
  `,
})
export class GlobalAccountsFormComponent implements OnInit {
  protected readonly trader = inject(TraderContextService);
  private readonly api = inject(GlobalAccountsApiService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly isEdit = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    clientLegalEntityCode: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    currency: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    accountRef: ['', [Validators.required, Validators.maxLength(50)]],
  });

  ngOnInit(): void {
    const client = this.route.snapshot.paramMap.get('client');
    const currency = this.route.snapshot.paramMap.get('currency');
    if (client && currency) {
      this.isEdit.set(true);
      this.form.patchValue({ clientLegalEntityCode: client, currency });
      this.api.list(this.trader.userId()).subscribe({
        next: (rows) => {
          const match = rows.find(
            (r) => r.clientLegalEntityCode === client && r.currency === currency
          );
          if (match) {
            this.form.patchValue({ accountRef: match.accountRef });
          }
        },
      });
    }
  }

  submit(): void {
    if (this.form.invalid || !this.trader.isTrader()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.upsert(this.trader.userId(), this.form.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        void this.router.navigate(['/settings/global-accounts']);
      },
      error: () => {
        this.error.set('Failed to save global account.');
        this.saving.set(false);
      },
    });
  }
}
