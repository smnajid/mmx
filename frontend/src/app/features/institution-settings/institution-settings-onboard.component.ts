import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { InstitutionSettingsApiService } from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-institution-settings-onboard',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="settings-panel">
      <a routerLink="/settings/institutions" class="settings-back">← Back to list</a>
      <h1>Onboard institution</h1>
      <p class="settings-state">Institution code is assigned by the system after you save.</p>

      @if (error()) {
        <p class="settings-error" role="alert">{{ error() }}</p>
      }
      @if (createdCode()) {
        <p class="settings-state" role="status">Created with code <span class="mono">{{ createdCode() }}</span></p>
      }

      <form class="settings-form" [formGroup]="form" (ngSubmit)="save()">
        <label>
          Display name
          <input formControlName="displayName" maxlength="128" autocomplete="organization" />
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
export class InstitutionSettingsOnboardComponent {
  private readonly api = inject(InstitutionSettingsApiService);
  private readonly trader = inject(TraderContextService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    displayName: ['', [Validators.required, Validators.minLength(1)]],
  });

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly createdCode = signal<string | null>(null);

  save(): void {
    if (this.form.invalid) {
      return;
    }
    const displayName = (this.form.value.displayName ?? '').trim();
    this.saving.set(true);
    this.error.set(null);
    this.api.onboard(this.trader.traderId(), { displayName }).subscribe({
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
