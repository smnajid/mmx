import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  GrantedInstitution,
  InstitutionSettingsApiService,
  OnboardInstitutionRequest,
} from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-institution-settings-onboard',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="settings-panel">
      <a routerLink="/settings/institutions" class="settings-back">← Back to list</a>
      <h1>Onboard institution</h1>

      @if (trader.isTrader()) {
        <p class="settings-state">Institution code is assigned by the system after you save.</p>
      } @else {
        <p class="settings-state">
          Select one of your granted institutions. Its name is derived by the system; an offboarded
          institution is re-onboarded with its code and accounts.
        </p>
      }

      @if (error()) {
        <p class="settings-error" role="alert">{{ error() }}</p>
      }
      @if (createdCode()) {
        <p class="settings-state" role="status">
          Created with code <span class="mono">{{ createdCode() }}</span>
        </p>
      }

      <form class="settings-form" [formGroup]="form" (ngSubmit)="save()">
        @if (trader.isTrader()) {
          <label>
            Display name
            <input formControlName="displayName" maxlength="128" autocomplete="organization" />
          </label>
        } @else {
          <label>
            Granted institution
            <select formControlName="hubInstitutionCode">
              <option value="" disabled>Select…</option>
              @for (g of grantedOptions(); track g.hubInstitutionCode) {
                <option [value]="g.hubInstitutionCode">
                  {{ g.displayName }}{{ g.onboardedInstitutionCode ? ' (re-onboard)' : '' }}
                </option>
              }
            </select>
          </label>
        }
        <label>
          Term counterparty account (optional)
          <input formControlName="termCounterpartyAccount" maxlength="34" />
        </label>
        <label>
          OnCall counterparty account (optional)
          <input formControlName="onCallCounterpartyAccount" maxlength="34" />
        </label>
        <div class="actions">
          <button type="submit" [disabled]="form.invalid || saving()">Onboard</button>
        </div>
      </form>
    </section>
  `,
  styles: `
    .mono {
      font-family: var(--font-mono);
      font-weight: 600;
      color: var(--mmx-accent);
    }
  `,
})
export class InstitutionSettingsOnboardComponent implements OnInit {
  protected readonly trader = inject(TraderContextService);

  private readonly api = inject(InstitutionSettingsApiService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    displayName: [''],
    hubInstitutionCode: [''],
    termCounterpartyAccount: ['', Validators.maxLength(34)],
    onCallCounterpartyAccount: ['', Validators.maxLength(34)],
  });

  /** Granted institutions not yet open for the client: never onboarded, or offboarded (re-onboard). */
  readonly grantedOptions = signal<GrantedInstitution[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly createdCode = signal<string | null>(null);

  ngOnInit(): void {
    if (this.trader.isClientRepresentative()) {
      this.form.controls.displayName.clearValidators();
      this.form.controls.hubInstitutionCode.setValidators([Validators.required]);
      this.api.listGrantedInstitutions(this.trader.userId()).subscribe({
        next: (granted) => {
          this.grantedOptions.set(
            granted
              .filter((g) => !g.onboardedInstitutionCode || g.closedToNewBusiness)
              .sort((a, b) => a.displayName.localeCompare(b.displayName))
          );
        },
        error: (err) => {
          this.error.set(
            err?.error?.message ?? err?.message ?? 'Failed to load granted institutions'
          );
        },
      });
    } else {
      this.form.controls.displayName.setValidators([Validators.required, Validators.minLength(1)]);
      this.form.controls.hubInstitutionCode.clearValidators();
    }
    this.form.controls.displayName.updateValueAndValidity();
    this.form.controls.hubInstitutionCode.updateValueAndValidity();
  }

  save(): void {
    if (this.form.invalid) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const userId = this.trader.userId();
    const body: OnboardInstitutionRequest = this.trader.isClientRepresentative()
      ? { hubInstitutionCode: this.form.value.hubInstitutionCode! }
      : { displayName: (this.form.value.displayName ?? '').trim() };
    const term = (this.form.value.termCounterpartyAccount ?? '').trim();
    const onCall = (this.form.value.onCallCounterpartyAccount ?? '').trim();
    if (term) {
      body.termCounterpartyAccount = term;
    }
    if (onCall) {
      body.onCallCounterpartyAccount = onCall;
    }

    this.api.onboard(userId, body).subscribe({
      next: (inst) => {
        this.createdCode.set(inst.institutionCode);
        this.saving.set(false);
        void this.router.navigate(['/settings/institutions', inst.institutionCode]);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Onboard failed');
        this.saving.set(false);
      },
    });
  }
}
