import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GlobalAccount, GlobalAccountsApiService } from '../../core/api/global-accounts-api.service';
import { DeskReturnService } from '../../core/trader/desk-return.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-global-accounts-list',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="settings-panel">
      <nav class="settings-toolbar">
        <a [routerLink]="deskReturn.getReturnUrl()" class="settings-back">← Back to desk</a>
      </nav>

      <header class="settings-header">
        <h1>Global accounts</h1>
        @if (trader.isTrader()) {
          <a routerLink="/settings/global-accounts/new" class="btn-primary" data-testid="create-global-account">
            Add account
          </a>
        }
      </header>

      <p class="settings-lede">
        Map each TradingClient and currency to the nostro account used when routing orders to this hub.
      </p>

      @if (!trader.isTrader()) {
        <p class="settings-error" role="alert" data-testid="trader-only-message">
          Global account management is available only to a Trader on the TradingHub.
        </p>
      } @else if (error()) {
        <p class="settings-error" role="alert">{{ error() }}</p>
      } @else if (loading()) {
        <p class="settings-state">Loading…</p>
      } @else if (accounts().length === 0) {
        <p class="settings-state">No global accounts configured yet.</p>
      } @else {
        <div class="settings-table-wrap">
          <table class="mmx-table">
            <thead>
              <tr>
                <th>Client</th>
                <th>Currency</th>
                <th>Account ref</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (a of accounts(); track accountKey(a)) {
                <tr>
                  <td class="mono">{{ a.clientLegalEntityCode }}</td>
                  <td class="mono">{{ a.currency }}</td>
                  <td class="mono">{{ a.accountRef }}</td>
                  <td>
                    <a [routerLink]="editLink(a)">Edit</a>
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
export class GlobalAccountsListComponent implements OnInit {
  protected readonly trader = inject(TraderContextService);
  protected readonly deskReturn = inject(DeskReturnService);
  private readonly api = inject(GlobalAccountsApiService);

  readonly accounts = signal<GlobalAccount[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    if (!this.trader.isTrader()) {
      return;
    }
    this.loading.set(true);
    this.api.list(this.trader.userId()).subscribe({
      next: (rows) => {
        this.accounts.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load global accounts.');
        this.loading.set(false);
      },
    });
  }

  accountKey(a: GlobalAccount): string {
    return `${a.clientLegalEntityCode}-${a.currency}`;
  }

  editLink(a: GlobalAccount): string {
    return `/settings/global-accounts/${encodeURIComponent(a.clientLegalEntityCode)}/${encodeURIComponent(a.currency)}`;
  }
}
