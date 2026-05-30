import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import {
  Institution,
  InstitutionSettingsApiService,
} from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-institution-settings-detail',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="settings-panel">
      <a routerLink="/settings/institutions" class="settings-back">← Back to list</a>

      @if (loading()) {
        <p class="settings-state">Loading…</p>
      } @else if (institution(); as i) {
        <header class="settings-header">
          <h1>{{ i.displayName }}</h1>
        </header>

        <dl class="detail-dl">
          <dt>Institution code</dt>
          <dd class="mono">{{ i.institutionCode }}</dd>
          <dt>Status</dt>
          <dd>
            <span
              class="status-badge"
              [class.status-badge--active]="i.active"
              [class.status-badge--inactive]="!i.active"
            >
              {{ i.active ? 'Active' : 'Inactive' }}
            </span>
          </dd>
        </dl>

        @if (error()) {
          <p class="settings-error" role="alert">{{ error() }}</p>
        }

        <div class="actions">
          @if (i.active) {
            <button type="button" class="warn" (click)="deactivate()" [disabled]="acting()">Deactivate</button>
          } @else {
            <button type="button" class="activate" (click)="activate()" [disabled]="acting()">Reactivate</button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .detail-dl {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.35rem 1.25rem;
      margin: 0 0 1.25rem;
      font-size: 0.9rem;
    }

    .detail-dl dt {
      font-family: var(--font-mono);
      font-size: 0.65rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
    }

    .detail-dl dd {
      margin: 0;
    }

    .mono {
      font-family: var(--font-mono);
    }

    .actions {
      display: flex;
      gap: 0.75rem;
    }

    .actions button {
      font-family: var(--font-mono);
      font-size: 0.68rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.4rem 0.9rem;
      border-radius: 4px;
      cursor: pointer;
    }

    .actions button.warn {
      background: rgba(180, 83, 9, 0.2);
      border: 1px solid rgba(251, 191, 36, 0.45);
      color: #fcd34d;
    }

    .actions button.activate {
      background: var(--mmx-accent-dim);
      border: 1px solid var(--mmx-accent);
      color: var(--mmx-accent);
    }
  `,
})
export class InstitutionSettingsDetailComponent implements OnInit {
  private readonly api = inject(InstitutionSettingsApiService);
  private readonly trader = inject(TraderContextService);
  private readonly route = inject(ActivatedRoute);

  readonly institution = signal<Institution | null>(null);
  readonly loading = signal(true);
  readonly acting = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('institutionCode') ?? '';
    this.api.get(this.trader.traderId(), code).subscribe({
      next: (inst) => {
        this.institution.set(inst);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Failed to load institution');
        this.loading.set(false);
      },
    });
  }

  deactivate(): void {
    const i = this.institution();
    if (!i) {
      return;
    }
    this.runToggle(() => this.api.deactivate(this.trader.traderId(), i.institutionCode));
  }

  activate(): void {
    const i = this.institution();
    if (!i) {
      return;
    }
    this.runToggle(() => this.api.activate(this.trader.traderId(), i.institutionCode));
  }

  private runToggle(call: () => Observable<Institution>): void {
    this.acting.set(true);
    this.error.set(null);
    call().subscribe({
      next: (updated) => {
        this.institution.set(updated);
        this.acting.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Update failed');
        this.acting.set(false);
      },
    });
  }
}
