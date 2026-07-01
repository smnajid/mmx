import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DelegatedGrant,
  DelegatedGrantsApiService,
} from '../../core/api/delegated-grants-api.service';
import { DeskReturnService } from '../../core/trader/desk-return.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-delegated-grants-list',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="settings-panel">
      <nav class="settings-toolbar">
        <a [routerLink]="deskReturn.getReturnUrl()" class="settings-back">← Back to desk</a>
      </nav>

      <header class="settings-header">
        <h1>Delegated institution grants</h1>
        @if (trader.isTrader()) {
          <a routerLink="/settings/delegated-grants/new" class="btn-primary" data-testid="create-grant">
            Create grant
          </a>
        }
      </header>

      <p class="settings-lede">
        Delegate a hub native institution to a TradingClient per currency with an enabled subset of
        Term tenors and OnCall notice periods.
      </p>

      @if (!trader.isTrader()) {
        <p class="settings-error" role="alert" data-testid="trader-only-message">
          Grant management is available only to a Trader on the TradingHub.
        </p>
      } @else if (error()) {
        <p class="settings-error" role="alert">{{ error() }}</p>
      } @else if (loading()) {
        <p class="settings-state">Loading…</p>
      } @else if (grants().length === 0) {
        <p class="settings-state">No grants configured yet.</p>
      } @else {
        <div class="settings-table-wrap">
          <table class="mmx-table">
            <thead>
              <tr>
                <th>Hub institution</th>
                <th>Client</th>
                <th>Currency</th>
                <th>Tenors</th>
                <th>Notices</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (g of grants(); track grantKey(g)) {
                <tr>
                  <td class="mono">{{ g.hubInstitutionCode }}</td>
                  <td class="mono">{{ g.clientLegalEntityCode }}</td>
                  <td class="mono">{{ g.currency }}</td>
                  <td>{{ g.enabledTenors.join(', ') || '—' }}</td>
                  <td>{{ g.enabledNoticePeriods.join(', ') || '—' }}</td>
                  <td>
                    <span
                      class="status-badge"
                      [class.status-badge--active]="g.active"
                      [class.status-badge--inactive]="!g.active"
                    >
                      {{ g.active ? 'Active' : 'Inactive' }}
                    </span>
                  </td>
                  <td>
                    <a [routerLink]="grantEditLink(g)">Edit</a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class DelegatedGrantsListComponent implements OnInit {
  protected readonly deskReturn = inject(DeskReturnService);
  protected readonly trader = inject(TraderContextService);

  private readonly api = inject(DelegatedGrantsApiService);

  readonly grants = signal<DelegatedGrant[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    if (!this.trader.isTrader()) {
      this.loading.set(false);
      return;
    }
    this.api.listHubGrants(this.trader.userId()).subscribe({
      next: (list) => {
        this.grants.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load grants');
        this.loading.set(false);
      },
    });
  }

  grantKey(g: DelegatedGrant): string {
    return `${g.hubInstitutionCode}:${g.clientLegalEntityCode}:${g.currency}`;
  }

  grantEditLink(g: DelegatedGrant): string[] {
    return [
      '/settings/delegated-grants',
      g.hubInstitutionCode,
      g.clientLegalEntityCode,
      g.currency,
    ];
  }
}
