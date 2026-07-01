import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import {
  DelegatedGrant,
  DelegatedGrantsApiService,
} from '../../core/api/delegated-grants-api.service';
import {
  Institution,
  InstitutionSettingsApiService,
} from '../../core/api/institution-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { WorkspaceToggleFieldsetComponent } from '../../shared/workspace-toggle-fieldset.component';

@Component({
  selector: 'app-institution-settings-detail',
  standalone: true,
  imports: [RouterLink, WorkspaceToggleFieldsetComponent],
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
          @if (i.hubInstitutionCode) {
            <dt>Hub institution</dt>
            <dd class="mono">{{ i.hubInstitutionCode }} via {{ i.hubLegalEntityCode }}</dd>
          }
        </dl>

        @if (trader.isClientRepresentative() && selectedGrant(); as grant) {
          <section class="settings-card" data-testid="grant-intake-panel">
            <h2 class="settings-card__title">Granted intake · {{ grant.currency }}</h2>
            <p class="settings-hint">
              Tenors and notice periods available for intake are bounded by your active delegation
              grant.
            </p>
            <app-workspace-toggle-fieldset
              [grantBounded]="true"
              [readOnly]="true"
              [grantTenors]="grant.enabledTenors"
              [grantNotices]="grant.enabledNoticePeriods"
              [selectedTenors]="grant.enabledTenors"
              [selectedNotices]="grant.enabledNoticePeriods"
            />
          </section>
        }

        @if (error()) {
          <p class="settings-error" role="alert">{{ error() }}</p>
        }

        @if (trader.isTrader()) {
          <div class="actions">
            @if (i.active) {
              <button type="button" class="warn" (click)="deactivate()" [disabled]="acting()">
                Deactivate
              </button>
            } @else {
              <button type="button" class="activate" (click)="activate()" [disabled]="acting()">
                Reactivate
              </button>
            }
          </div>
        }
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
  protected readonly trader = inject(TraderContextService);

  private readonly api = inject(InstitutionSettingsApiService);
  private readonly grantsApi = inject(DelegatedGrantsApiService);
  private readonly route = inject(ActivatedRoute);

  readonly institution = signal<Institution | null>(null);
  readonly clientGrants = signal<DelegatedGrant[]>([]);
  readonly loading = signal(true);
  readonly acting = signal(false);
  readonly error = signal<string | null>(null);

  readonly selectedGrant = computed(() => {
    const inst = this.institution();
    if (!inst?.hubInstitutionCode) {
      return null;
    }
    return (
      this.clientGrants().find(
        (g) => g.active && g.hubInstitutionCode === inst.hubInstitutionCode
      ) ?? null
    );
  });

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('institutionCode') ?? '';
    this.api.get(this.trader.userId(), code).subscribe({
      next: (inst) => {
        this.institution.set(inst);
        this.loading.set(false);
        if (this.trader.isClientRepresentative() && inst.hubInstitutionCode) {
          this.loadClientGrants();
        }
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
    this.runToggle(() => this.api.deactivate(this.trader.userId(), i.institutionCode));
  }

  activate(): void {
    const i = this.institution();
    if (!i) {
      return;
    }
    this.runToggle(() => this.api.activate(this.trader.userId(), i.institutionCode));
  }

  private loadClientGrants(): void {
    this.grantsApi.listClientGrants(this.trader.userId()).subscribe({
      next: (grants) => this.clientGrants.set(grants),
      error: () => this.clientGrants.set([]),
    });
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
