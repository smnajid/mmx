import {
  ChangeDetectionStrategy,
  Component,
  Input,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { OrderApiService } from '../../core/api/order-api.service';
import { OrderSummary } from '../../core/models/order.model';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { OrderTableComponent } from '../../shared/components/order-table.component';

export type WorkspaceKind = 'term' | 'oncall';

@Component({
  selector: 'mmx-executed-order-list',
  standalone: true,
  imports: [OrderTableComponent],
  template: `
    <section class="feature">
      <header class="feature-head">
        <div class="feature-head-row">
          <h1>Executed — {{ workspaceLabel() }}</h1>
          <button type="button" class="refresh" (click)="refresh()">Refresh</button>
        </div>
        <p class="lede">
          Executed orders that are not yet accounted at portfolio level. Execution counterparty is shown below; accounting
          confirmation removes rows via the back-office path.
        </p>
      </header>
      <mmx-order-table
        [orders]="orders()"
        [loading]="loading()"
        [errorMessage]="error()"
        [showTenorColumn]="workspace() === 'term'"
        [showNoticePeriodColumn]="workspace() === 'oncall'"
        [showCounterpartyColumn]="true"
        [listWorkspace]="workspace()"
        listQueue="executed"
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
export class ExecutedOrderListComponent implements OnInit {
  private readonly api = inject(OrderApiService);
  private readonly trader = inject(TraderContextService);
  private readonly route = inject(ActivatedRoute);

  /** When set (thin workspace wrappers), bypasses router data for workspace resolution. */
  @Input() fixedWorkspace: WorkspaceKind | null = null;

  readonly orders = signal<OrderSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly workspace = signal<WorkspaceKind>('oncall');

  readonly workspaceLabel = signal<string>('On-call');

  ngOnInit(): void {
    const fromInput = this.fixedWorkspace;
    const fromRoute = this.route.snapshot.data['workspace'] as WorkspaceKind | undefined;
    const ws = fromInput ?? (fromRoute === 'term' || fromRoute === 'oncall' ? fromRoute : 'oncall');
    this.workspace.set(ws);
    this.workspaceLabel.set(ws === 'term' ? 'Term' : 'On-call');
    this.load();
  }

  refresh(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const traderId = this.trader.traderId();
    const req =
      this.workspace() === 'term'
        ? this.api.listExecutedTermOrders(traderId, { page: 0, size: 100 })
        : this.api.listExecutedOnCallOrders(traderId, { page: 0, size: 100 });

    req.subscribe({
      next: (page) => {
        this.orders.set(page.content ?? []);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.formatHttpError(err));
      },
    });
  }

  private formatHttpError(err: unknown): string {
    if (err && typeof err === 'object' && 'error' in err) {
      const body = (err as { error?: { message?: string } }).error;
      if (body?.message) {
        return body.message;
      }
    }
    return 'Could not load executed orders. Is the API running (proxy /api → backend)?';
  }
}
