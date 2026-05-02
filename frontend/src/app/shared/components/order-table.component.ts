import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
} from '@angular/core';
import { OrderSummary } from '../../core/models/order.model';
import { StatusBadgeComponent } from './status-badge.component';

@Component({
  selector: 'mmx-order-table',
  standalone: true,
  imports: [DecimalPipe, StatusBadgeComponent],
  template: `
    @if (loading) {
      <p class="state">Loading orders…</p>
    } @else if (errorMessage) {
      <p class="state state-error" role="alert">{{ errorMessage }}</p>
    } @else if (orders.length === 0) {
      <p class="state">No orders in this queue.</p>
    } @else {
      <div class="scroll">
        <table>
          <thead>
            <tr>
              <th>External ref</th>
              <th>Portfolio</th>
              <th class="num">Amount</th>
              <th>Ccy</th>
              <th>Value date</th>
              <th class="num">Min rate</th>
              <th>Operation</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            @for (row of orders; track row.orderId; let i = $index) {
              <tr [style.animation-delay.ms]="i * 28">
                <td class="mono">{{ row.externalOrderReference }}</td>
                <td class="mono">{{ row.portfolioNumber }}</td>
                <td class="num mono">{{ row.amount | number: '1.2-2' }}</td>
                <td class="mono">{{ row.currency }}</td>
                <td class="mono">{{ row.valueDate }}</td>
                <td class="num mono">{{ row.minimumRate | number: '1.2-8' }}</td>
                <td><span class="op">{{ row.orderOperation }}</span></td>
                <td><mmx-status-badge [status]="row.status" /></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .scroll {
      overflow-x: auto;
      border: 1px solid var(--mmx-border);
      border-radius: 6px;
      background: var(--mmx-surface);
      box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
    }

    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.875rem;
    }

    thead {
      background: var(--mmx-surface-elevated);
      position: sticky;
      top: 0;
      z-index: 1;
    }

    th {
      text-align: left;
      font-family: var(--font-mono);
      font-size: 0.65rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
      padding: 0.65rem 0.85rem;
      border-bottom: 1px solid var(--mmx-border);
    }

    th.num {
      text-align: right;
    }

    td {
      padding: 0.55rem 0.85rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      vertical-align: middle;
    }

    tbody tr {
      animation: row-in 0.4s ease backwards;
    }

    @keyframes row-in {
      from {
        opacity: 0;
        transform: translateX(-6px);
      }
    }

    tbody tr:hover {
      background: rgba(232, 168, 56, 0.04);
    }

    .mono {
      font-family: var(--font-mono);
      font-size: 0.8125rem;
    }

    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }

    .op {
      font-family: var(--font-mono);
      font-size: 0.75rem;
      color: var(--mmx-text-muted);
    }

    .state {
      margin: 0;
      padding: 1.25rem;
      font-style: italic;
      color: var(--mmx-text-muted);
      border: 1px dashed var(--mmx-border);
      border-radius: 6px;
      background: var(--mmx-surface);
    }

    .state-error {
      color: #fda4af;
      border-style: solid;
      font-style: normal;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderTableComponent {
  @Input() orders: OrderSummary[] = [];
  @Input() loading = false;
  @Input() errorMessage: string | null = null;
}
