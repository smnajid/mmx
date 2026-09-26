import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DelegatedGrantsApiService } from '../../core/api/delegated-grants-api.service';
import { InstitutionSettingsApiService } from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-institution-settings-onboard',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="settings-panel">
      <a routerLink="/settings/institutions" class="settings-back">← Back to list</a>
      <h1>{{ trader.isClientRepresentative() ? 'Onboard proxy institution' : 'Onboard institution' }}</h1>

      @if (trader.isTrader()) {
        <p class="settings-state">Institution code is assigned by the system after you save.</p>
      } @else {
        <p class="settings-state">
          Select a hub institution from an active delegation grant. The proxy name is derived by the
          system.
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
            Hub institution (from grant)
            <select formControlName="hubInstitutionCode">
              <option value="" disabled>Select…</option>
              @for (hub of grantHubOptions(); track hub) {
                <option [value]="hub">{{ hub }}</option>
              }
            </select>
          </label>
        }
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
  private readonly grantsApi = inject(DelegatedGrantsApiService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    displayName: [''],
    hubInstitutionCode: [''],
  });

  readonly grantHubOptions = signal<string[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly createdCode = signal<string | null>(null);

  ngOnInit(): void {
    if (this.trader.isClientRepresentative()) {
      this.form.controls.displayName.clearValidators();
      this.form.controls.hubInstitutionCode.setValidators([Validators.required]);
      this.grantsApi.listClientGrants(this.trader.userId()).subscribe({
        next: (grants) => {
          const hubs = [
            ...new Set(
              grants.filter((g) => g.active).map((g) => g.hubInstitutionCode)
            ),
          ];
          this.grantHubOptions.set(hubs);
        },
        error: (err) => {
          this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load grants');
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
    const body = this.trader.isClientRepresentative()
      ? { hubInstitutionCode: this.form.value.hubInstitutionCode! }
      : { displayName: (this.form.value.displayName ?? '').trim() };

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
