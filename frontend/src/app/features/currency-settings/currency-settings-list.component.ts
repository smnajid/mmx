import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencySettingsApiService, ManagedCurrency } from '../../core/api/currency-settings-api.service';
import { DeskReturnService } from '../../core/trader/desk-return.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-currency-settings-list',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  template: `
    <section class="settings-panel">
      <nav class="settings-toolbar">
        <a [routerLink]="deskReturn.getReturnUrl()" class="settings-back">← Back to desk</a>
      </nav>

      <header class="settings-header">
        <h1>Managed currencies</h1>
        <a routerLink="/settings/currencies/new" class="btn-primary">Onboard currency</a>
      </header>

      @if (error()) {
        <p class="settings-error" role="alert">{{ error() }}</p>
      }

      @if (loading()) {
        <p class="settings-state">Loading…</p>
      } @else if (currencies().length === 0) {
        <p class="settings-state">No currencies onboarded. Intake is blocked until you add at least one.</p>
      } @else {
        <div class="settings-table-wrap">
          <table class="mmx-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Status</th>
                <th>Rules</th>
                <th class="num">Min subscription</th>
                <th class="num">Min lifecycle</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (c of currencies(); track c.code) {
                <tr>
                  <td class="mono">{{ c.code }}</td>
                  <td>
                    <span
                      class="status-badge"
                      [class.status-badge--active]="c.active"
                      [class.status-badge--inactive]="!c.active"
                    >
                      {{ c.active ? 'Active' : 'Inactive' }}
                    </span>
                  </td>
                  <td class="rules-summary">{{ rulesSummary(c) }}</td>
                  <td class="num mono">{{ c.minSubscriptionAmount | number: '1.2-2' }}</td>
                  <td class="num mono">{{ c.minIncreaseDecreaseAmount | number: '1.2-2' }}</td>
                  <td><a [routerLink]="['/settings/currencies', c.code]">Edit</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class CurrencySettingsListComponent implements OnInit {
  protected readonly deskReturn = inject(DeskReturnService);

  private readonly api = inject(CurrencySettingsApiService);
  private readonly trader = inject(TraderContextService);

  readonly currencies = signal<ManagedCurrency[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.list(this.trader.traderId()).subscribe({
      next: (list) => {
        this.currencies.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Failed to load currencies');
        this.loading.set(false);
      },
    });
  }

  protected rulesSummary(c: ManagedCurrency): string {
    const tenors = c.enabledTenors.length > 0 ? c.enabledTenors.join(', ') : 'off';
    const notices =
      c.enabledNoticePeriods.length > 0 ? c.enabledNoticePeriods.join(', ') : 'off';
    return `Term: ${tenors} · OnCall: ${notices}`;
  }
}
