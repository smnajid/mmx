import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencySettingsApiService, ManagedCurrency } from '../../core/api/currency-settings-api.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-currency-settings-list',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  template: `
    <section class="settings-panel">
      <header class="settings-header">
        <h1>Managed currencies</h1>
        <a routerLink="/settings/currencies/new" class="btn-primary">Onboard currency</a>
      </header>

      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }

      @if (loading()) {
        <p>Loading…</p>
      } @else if (currencies().length === 0) {
        <p class="empty">No currencies onboarded. Intake is blocked until you add at least one.</p>
      } @else {
        <table class="currency-table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Status</th>
              <th>Min subscription</th>
              <th>Min lifecycle</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (c of currencies(); track c.code) {
              <tr>
                <td>{{ c.code }}</td>
                <td>{{ c.active ? 'Active' : 'Inactive' }}</td>
                <td>{{ c.minSubscriptionAmount | number: '1.2-2' }}</td>
                <td>{{ c.minIncreaseDecreaseAmount | number: '1.2-2' }}</td>
                <td><a [routerLink]="['/settings/currencies', c.code]">Edit</a></td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: [
    `
      .settings-panel {
        padding: 1.5rem;
      }
      .settings-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 1rem;
      }
      .currency-table {
        width: 100%;
        border-collapse: collapse;
      }
      .currency-table th,
      .currency-table td {
        text-align: left;
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid var(--border-subtle, #e2e8f0);
      }
      .btn-primary {
        padding: 0.4rem 0.9rem;
        border-radius: 6px;
        background: var(--accent, #0f766e);
        color: #fff;
        text-decoration: none;
      }
      .error {
        color: #b91c1c;
      }
      .empty {
        color: #64748b;
      }
    `,
  ],
})
export class CurrencySettingsListComponent implements OnInit {
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
}
