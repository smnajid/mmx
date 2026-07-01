import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CurrencySettingsApiService,
  NoticePeriodCode,
  TenorCode,
} from '../../core/api/currency-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

const ALL_TENORS: TenorCode[] = ['1W', '2W', '1M', '3M', '6M', '1Y'];
const ALL_NOTICES: NoticePeriodCode[] = ['24H', '48H'];

@Component({
  selector: 'app-currency-settings-edit',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="settings-panel">
      <a routerLink="/settings/currencies" class="settings-back">← Back to list</a>
      <h1>{{ isNew() ? 'Onboard currency' : (trader.isClientRepresentative() ? 'View ' + code() : 'Edit ' + code()) }}</h1>

      @if (trader.isClientRepresentative()) {
        <p class="settings-state">Read-only view of hub-managed currency rules.</p>
      }

      @if (error()) {
        <p class="settings-error" role="alert">{{ error() }}</p>
      }

      <form class="settings-form" [formGroup]="form" (ngSubmit)="save()">
        @if (isNew()) {
          <label>
            ISO code
            <input formControlName="code" maxlength="3" style="text-transform: uppercase" />
          </label>
        }

        <label>
          Min subscription
          <input type="number" formControlName="minSubscriptionAmount" step="0.01" [readonly]="trader.isClientRepresentative()" />
        </label>
        <label>
          Min increase/decrease
          <input type="number" formControlName="minIncreaseDecreaseAmount" step="0.01" [readonly]="trader.isClientRepresentative()" />
        </label>

        <fieldset>
          <legend>Enabled tenors</legend>
          @for (t of ALL_TENORS; track t) {
            <label class="chk">
              <input
                type="checkbox"
                [checked]="tenorSelected(t)"
                [disabled]="trader.isClientRepresentative() || tenorDisableBlocked(t)"
                (change)="toggleTenor(t, $event)"
              />
              {{ t }}
            </label>
          }
        </fieldset>

        <fieldset>
          <legend>Enabled notice periods</legend>
          @for (n of ALL_NOTICES; track n) {
            <label class="chk">
              <input
                type="checkbox"
                [checked]="noticeSelected(n)"
                [disabled]="trader.isClientRepresentative() || noticeDisableBlocked(n)"
                (change)="toggleNotice(n, $event)"
              />
              {{ n }}
            </label>
          }
        </fieldset>

        @if (trader.isTrader()) {
          <div class="actions">
            <button type="submit" [disabled]="form.invalid || saving()">Save</button>
            @if (!isNew() && active()) {
              <button type="button" class="warn" (click)="disable()" [disabled]="saving()">Deactivate</button>
            }
            @if (!isNew() && !active()) {
              <button type="button" class="activate" (click)="enable()" [disabled]="saving()">Reactivate</button>
            }
          </div>
        }
      </form>
    </section>
  `,
})
export class CurrencySettingsEditComponent implements OnInit {
  protected readonly ALL_TENORS = ALL_TENORS;
  protected readonly ALL_NOTICES = ALL_NOTICES;
  protected readonly trader = inject(TraderContextService);

  private readonly api = inject(CurrencySettingsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly code = signal('');
  readonly isNew = signal(false);
  readonly active = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly selectedTenors = signal<Set<TenorCode>>(new Set(['3M']));
  readonly selectedNotices = signal<Set<NoticePeriodCode>>(new Set(['24H', '48H']));

  readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    minSubscriptionAmount: [1_000_000, [Validators.required, Validators.min(0.01)]],
    minIncreaseDecreaseAmount: [250_000, [Validators.required, Validators.min(0.01)]],
  });

  ngOnInit(): void {
    const param = this.route.snapshot.paramMap.get('code');
    if (param === 'new') {
      if (this.trader.isClientRepresentative()) {
        void this.router.navigate(['/settings/currencies']);
        return;
      }
      this.isNew.set(true);
      return;
    }
    this.isNew.set(false);
    this.code.set(param ?? '');
    this.form.controls.code.disable();
    this.api.get(this.trader.traderId(), this.code()).subscribe({
      next: (c) => {
        this.active.set(c.active);
        this.form.patchValue({
          minSubscriptionAmount: c.minSubscriptionAmount,
          minIncreaseDecreaseAmount: c.minIncreaseDecreaseAmount,
        });
        this.selectedTenors.set(new Set(c.enabledTenors));
        this.selectedNotices.set(new Set(c.enabledNoticePeriods));
      },
      error: () => this.error.set('Failed to load currency'),
    });
  }

  tenorSelected(t: TenorCode): boolean {
    return this.selectedTenors().has(t);
  }

  noticeSelected(n: NoticePeriodCode): boolean {
    return this.selectedNotices().has(n);
  }

  tenorDisableBlocked(t: TenorCode): boolean {
    if (this.selectedTenors().size !== 1 || !this.selectedTenors().has(t)) {
      return false;
    }
    return this.selectedNotices().size === 0;
  }

  noticeDisableBlocked(n: NoticePeriodCode): boolean {
    if (this.selectedNotices().size !== 1 || !this.selectedNotices().has(n)) {
      return false;
    }
    return this.selectedTenors().size === 0;
  }

  toggleTenor(t: TenorCode, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const next = new Set(this.selectedTenors());
    if (checked) next.add(t);
    else next.delete(t);
    this.selectedTenors.set(next);
  }

  toggleNotice(n: NoticePeriodCode, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const next = new Set(this.selectedNotices());
    if (checked) next.add(n);
    else next.delete(n);
    this.selectedNotices.set(next);
  }

  save(): void {
    if (this.selectedTenors().size === 0 && this.selectedNotices().size === 0) {
      this.error.set('Enable at least one workspace: Term tenors or OnCall notice periods');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const traderId = this.trader.traderId();
    const body = {
      minSubscriptionAmount: this.form.value.minSubscriptionAmount!,
      minIncreaseDecreaseAmount: this.form.value.minIncreaseDecreaseAmount!,
      enabledTenors: [...this.selectedTenors()],
      enabledNoticePeriods: [...this.selectedNotices()],
    };

    const req = this.isNew()
      ? this.api.onboard(traderId, {
          code: this.form.value.code!.toUpperCase(),
          ...body,
        })
      : this.api.updateRules(traderId, this.code(), body);

    req.subscribe({
      next: () => {
        this.saving.set(false);
        void this.router.navigate(['/settings/currencies']);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message ?? 'Save failed');
      },
    });
  }

  disable(): void {
    this.saving.set(true);
    this.api.disable(this.trader.traderId(), this.code()).subscribe({
      next: () => {
        this.saving.set(false);
        void this.router.navigate(['/settings/currencies']);
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Deactivate failed');
      },
    });
  }

  enable(): void {
    this.saving.set(true);
    this.error.set(null);
    this.api.enable(this.trader.traderId(), this.code()).subscribe({
      next: (c) => {
        this.active.set(c.active);
        this.saving.set(false);
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Reactivate failed');
      },
    });
  }
}
