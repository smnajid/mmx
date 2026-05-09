import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderSummary } from '../../core/models/order.model';
import { OrderStatus } from '../../core/models/order-status.enum';
import { StatusBadgeComponent } from './status-badge.component';

@Component({
  selector: 'mmx-order-table',
  standalone: true,
  imports: [DecimalPipe, StatusBadgeComponent, RouterLink],
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
              @if (showTenorColumn) {
                <th>Tenor</th>
              }
              @if (showNoticePeriodColumn) {
                <th>Notice period</th>
              }
              <th class="num">Min rate</th>
              <th>Operation</th>
              <th>Status</th>
              <th class="col-actions">Actions</th>
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
                @if (showTenorColumn) {
                  <td class="mono">
                    @if (row.tenor) {
                      {{ row.tenor }}
                    } @else {
                      —
                    }
                  </td>
                }
                @if (showNoticePeriodColumn) {
                  <td class="mono">
                    @if (row.noticePeriod) {
                      {{ row.noticePeriod }}
                    } @else {
                      —
                    }
                  </td>
                }
                <td class="num mono">
                  @if (row.minimumRate !== null && row.minimumRate !== undefined) {
                    {{ row.minimumRate | number: '1.2-8' }}
                  } @else {
                    —
                  }
                </td>
                <td><span class="op">{{ row.orderOperation }}</span></td>
                <td><mmx-status-badge [status]="row.status" /></td>
                <td class="actions">
                  <a class="link" [routerLink]="['/orders', row.orderId]">View</a>
                  @if (enableAssign && row.status === received) {
                    <button type="button" class="btn-action" (click)="assignClick.emit(row)">
                      Assign
                    </button>
                  }
                  @if (enableUnassign && row.status === assigned && row.assignedTraderId === actingTraderId) {
                    <button type="button" class="btn-action secondary" (click)="unassignClick.emit(row)">
                      Unassign
                    </button>
                  }
                </td>
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

    th.col-actions {
      text-align: right;
      white-space: nowrap;
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

    .actions {
      text-align: right;
      white-space: nowrap;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .link {
      font-family: var(--font-mono);
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.04em;
      color: var(--mmx-accent);
      text-decoration: none;
    }

    .link:hover {
      text-decoration: underline;
    }

    .btn-action {
      font-family: var(--font-mono);
      font-size: 0.68rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.35rem 0.55rem;
      border-radius: 4px;
      border: 1px solid var(--mmx-accent);
      background: var(--mmx-accent-dim);
      color: var(--mmx-accent);
      cursor: pointer;
      transition:
        background 0.2s ease,
        border-color 0.2s ease;
    }

    .btn-action:hover {
      background: rgba(232, 168, 56, 0.18);
    }

    .btn-action.secondary {
      border-color: var(--mmx-border);
      background: transparent;
      color: var(--mmx-text-muted);
    }

    .btn-action.secondary:hover {
      border-color: var(--mmx-text-muted);
      color: var(--mmx-text);
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
  readonly received = OrderStatus.RECEIVED;
  readonly assigned = OrderStatus.ASSIGNED;

  @Input() orders: OrderSummary[] = [];
  @Input() loading = false;
  @Input() errorMessage: string | null = null;
  /** Term received queue: show tenor from API summary. */
  @Input() showTenorColumn = false;
  /** OnCall received queue: show notice period from API summary. */
  @Input() showNoticePeriodColumn = false;
  /** Received queues: show Assign for RECEIVED rows. */
  @Input() enableAssign = false;
  /** Assigned queue: show Unassign for ASSIGNED rows. */
  @Input() enableUnassign = false;
  /** Current trader (required when enableUnassign; desk-wide Assigned lists only allow unassign for own rows). */
  @Input() actingTraderId: string | null = null;

  @Output() readonly assignClick = new EventEmitter<OrderSummary>();
  @Output() readonly unassignClick = new EventEmitter<OrderSummary>();
}
