import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { OrderApiService } from '../../core/api/order-api.service';
import { OrderSummary } from '../../core/models/order.model';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { OrderTableComponent } from '../../shared/components/order-table.component';

@Component({
  selector: 'mmx-term-order-list',
  standalone: true,
  imports: [OrderTableComponent],
  template: `
    <section class="feature">
      <header class="feature-head">
        <div class="feature-head-row">
          <h1>Received — Term</h1>
          <button type="button" class="refresh" (click)="refresh()">Refresh</button>
        </div>
        <p class="lede">
          Subscription and placement orders with an explicit tenor. Queue for traders before assignment.
        </p>
      </header>
      <mmx-order-table
        [orders]="orders()"
        [loading]="loading()"
        [errorMessage]="error()"
        [showTenorColumn]="true"
        [enableAssign]="true"
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
export class TermOrderListComponent implements OnInit {
  private readonly api = inject(OrderApiService);
  private readonly trader = inject(TraderContextService);

  readonly orders = signal<OrderSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  refresh(): void {
    this.load();
  }

  onAssign(row: OrderSummary): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.assignOrder(row.orderId, this.trader.traderId()).subscribe({
      next: () => this.load(),
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.formatHttpError(err));
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listReceivedTermOrders(this.trader.traderId(), { page: 0, size: 100 })
      .subscribe({
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
    return 'Could not load Term orders. Is the API running (proxy /api → backend)?';
  }
}
