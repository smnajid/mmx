import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { OrderApiService } from '../../core/api/order-api.service';
import { formatHttpError } from '../../core/http/format-http-error';
import { OrderSummary } from '../../core/models/order.model';
import { ReceivedViewModeService } from '../../core/trader/received-view-mode.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { OrderTableComponent } from '../../shared/components/order-table.component';

export type WorkspaceKind = 'term' | 'oncall';

@Component({
  selector: 'mmx-received-order-list',
  standalone: true,
  imports: [OrderTableComponent],
  template: `
    <section class="feature">
      <header class="feature-head">
        <div class="feature-head-row">
          <h1>Received — {{ workspaceLabel() }}</h1>
          <div class="toolbar-actions">
            <label class="show-all">
              <input
                type="checkbox"
                [checked]="receivedMode.showAll()"
                (change)="onShowAllChange($event)"
              />
              Show all value dates
            </label>
            <button type="button" class="refresh" (click)="refresh()">Refresh</button>
          </div>
        </div>
        <p class="lede">{{ lede() }}</p>
      </header>
      <mmx-order-table
        [orders]="orders()"
        [loading]="loading()"
        [errorMessage]="error()"
        [showTenorColumn]="workspace() === 'term'"
        [showNoticePeriodColumn]="workspace() === 'oncall'"
        [enableAssign]="true"
        [listWorkspace]="workspace()"
        listQueue="received"
        (assignClick)="onAssign($event)"
      />
    </section>
  `,
  styles: `
    .feature {
      max-width: 1120px;
      margin: 0 auto;
    }

    .feature-head {
      margin-bottom: 1.5rem;
    }

    .feature-head-row {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }

    .toolbar-actions {
      display: flex;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
    }

    .show-all {
      font-family: var(--font-mono);
      font-size: 0.72rem;
      color: var(--mmx-text-muted);
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      cursor: pointer;
      user-select: none;
      letter-spacing: 0.04em;
    }

    .show-all input {
      accent-color: var(--mmx-accent);
    }

    .refresh {
      font-family: var(--font-mono);
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.45rem 0.75rem;
      border-radius: 4px;
      border: 1px solid var(--mmx-border);
      background: var(--mmx-surface-elevated);
      color: var(--mmx-accent);
      cursor: pointer;
      transition:
        background 0.2s ease,
        border-color 0.2s ease;
    }

    .refresh:hover {
      background: var(--mmx-accent-dim);
      border-color: var(--mmx-accent);
    }

    h1 {
      font-family: var(--font-display);
      font-weight: 600;
      font-size: 1.75rem;
      letter-spacing: -0.02em;
      margin: 0 0 0.35rem;
      color: var(--mmx-text);
    }

    .lede {
      margin: 0;
      max-width: 52ch;
      color: var(--mmx-text-muted);
      font-size: 0.95rem;
      line-height: 1.45;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceivedOrderListComponent implements OnInit {
  private readonly api = inject(OrderApiService);
  private readonly trader = inject(TraderContextService);
  private readonly route = inject(ActivatedRoute);
  readonly receivedMode = inject(ReceivedViewModeService);

  @Input() fixedWorkspace: WorkspaceKind | null = null;

  readonly orders = signal<OrderSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly workspace = signal<WorkspaceKind>('oncall');
  readonly workspaceLabel = signal<string>('ON-CALL');
  readonly lede = signal<string>('');

  ngOnInit(): void {
    const fromInput = this.fixedWorkspace;
    const fromRoute = this.route.snapshot.data['workspace'] as WorkspaceKind | undefined;
    const ws = fromInput ?? (fromRoute === 'term' || fromRoute === 'oncall' ? fromRoute : 'oncall');
    this.workspace.set(ws);
    this.workspaceLabel.set(ws === 'term' ? 'Term' : 'ON-CALL');
    this.lede.set(
      ws === 'term'
        ? 'Subscription and placement orders with an explicit tenor. Queue for traders before assignment.'
        : 'Notice-based liquidity; separate queue from Term so traders never mix workflows.'
    );
    this.load();
  }

  refresh(): void {
    this.load();
  }

  onShowAllChange(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.receivedMode.setShowAll(input.checked);
    this.load();
  }

  onAssign(row: OrderSummary): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.assignOrder(row.orderId, this.trader.traderId()).subscribe({
      next: () => this.load(),
      error: (err) => {
        this.loading.set(false);
        this.error.set(
          formatHttpError(err, 'Could not assign order. Is the API running (proxy /api → backend)?')
        );
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const traderId = this.trader.traderId();
    const req =
      this.workspace() === 'term'
        ? this.api.listReceivedTermOrders(traderId, {
            page: 0,
            size: 100,
            receivedView: this.receivedMode.mode(),
          })
        : this.api.listReceivedOnCallOrders(traderId, {
            page: 0,
            size: 100,
            receivedView: this.receivedMode.mode(),
          });

    req.subscribe({
      next: (page) => {
        this.orders.set(page.content ?? []);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(
          formatHttpError(
            err,
            `Could not load ${this.workspace() === 'term' ? 'Term' : 'ON-CALL'} received orders. Is the API running (proxy /api → backend)?`
          )
        );
      },
    });
  }
}
