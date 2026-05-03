import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrderApiService } from '../../core/api/order-api.service';
import type { ExecuteOrderRequest, OrderDetails } from '../../core/models/order.model';
import { OrderStatus } from '../../core/models/order-status.enum';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { OrderExecutionFormComponent } from './order-execution-form.component';

@Component({
  selector: 'mmx-order-details',
  standalone: true,
  imports: [
    DecimalPipe,
    RouterLink,
    StatusBadgeComponent,
    OrderExecutionFormComponent,
  ],
  template: `
    <section class="feature">
      <nav class="crumb">
        <a routerLink="/term-orders">← Queues</a>
      </nav>

      @if (loading()) {
        <p class="state">Loading order…</p>
      } @else if (error()) {
        <p class="state state-error" role="alert">{{ error() }}</p>
      } @else if (order(); as o) {
        <header class="head">
          <div class="head-row">
            <h1>{{ o.externalOrderReference }}</h1>
            <mmx-status-badge [status]="o.status" />
          </div>
          <p class="meta mono">{{ o.orderType }} · {{ o.orderOperation }} · {{ o.portfolioNumber }}</p>
        </header>

        <dl class="grid">
          <dt>Amount</dt>
          <dd class="mono">{{ o.amount | number: '1.2-2' }} {{ o.currency }}</dd>
          <dt>Value date</dt>
          <dd class="mono">{{ o.valueDate }}</dd>
          <dt>Minimum rate</dt>
          <dd class="mono">{{ o.minimumRate | number: '1.2-8' }}</dd>
          @if (o.tenor) {
            <dt>Tenor</dt>
            <dd class="mono">{{ o.tenor }}</dd>
          }
          @if (o.noticePeriod) {
            <dt>Notice</dt>
            <dd class="mono">{{ o.noticePeriod }}</dd>
          }
          @if (o.sourceContractNumber) {
            <dt>Source contract</dt>
            <dd class="mono">{{ o.sourceContractNumber }}</dd>
          }
          @if (o.desiredCounterpartyComment) {
            <dt>Comment</dt>
            <dd>{{ o.desiredCounterpartyComment }}</dd>
          }
          @if (o.assignedTraderId) {
            <dt>Assigned trader</dt>
            <dd class="mono">{{ o.assignedTraderId }}</dd>
          }
          @if (o.status === executed) {
            <dt>Executed rate</dt>
            <dd class="mono">{{ o.executedRate | number: '1.2-8' }}</dd>
            <dt>Counterparty</dt>
            <dd>{{ o.counterparty }}</dd>
            <dt>Dealing reference</dt>
            <dd class="mono">{{ o.dealingReference }}</dd>
            <dt>Contract number</dt>
            <dd class="mono">{{ o.generatedContractNumber }}</dd>
            <dt>Execution time</dt>
            <dd class="mono">{{ o.executionTime }}</dd>
          }
        </dl>

        @if (o.status === received) {
          <div class="actions">
            <button type="button" class="btn primary" [disabled]="acting()" (click)="assign()">
              Assign to me
            </button>
          </div>
        }
        @if (o.status === assigned) {
          <div class="actions">
            <button type="button" class="btn secondary" [disabled]="acting()" (click)="unassign()">
              Unassign
            </button>
          </div>
          <mmx-order-execution-form
            [submitting]="acting()"
            (submitExecute)="execute($event)"
          />
        }
      }
    </section>
  `,
  styles: `
    .feature {
      max-width: 720px;
      margin: 0 auto;
    }

    .crumb {
      margin-bottom: 1.25rem;
    }

    .crumb a {
      font-family: var(--font-mono);
      font-size: 0.75rem;
      color: var(--mmx-accent);
      text-decoration: none;
    }

    .crumb a:hover {
      text-decoration: underline;
    }

    .head-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }

    h1 {
      font-family: var(--font-display);
      font-size: 1.5rem;
      margin: 0;
      color: var(--mmx-text);
    }

    .meta {
      margin: 0.35rem 0 1.25rem;
      font-size: 0.82rem;
      color: var(--mmx-text-muted);
    }

    .grid {
      display: grid;
      grid-template-columns: 11rem 1fr;
      gap: 0.5rem 1rem;
      margin: 0 0 1.5rem;
      padding: 1rem 1.1rem;
      border: 1px solid var(--mmx-border);
      border-radius: 6px;
      background: var(--mmx-surface);
    }

    dt {
      margin: 0;
      font-family: var(--font-mono);
      font-size: 0.65rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
    }

    dd {
      margin: 0;
      color: var(--mmx-text);
    }

    .actions {
      display: flex;
      gap: 0.75rem;
      flex-wrap: wrap;
      margin-bottom: 0.5rem;
    }

    .btn {
      font-family: var(--font-mono);
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.55rem 1rem;
      border-radius: 4px;
      cursor: pointer;
      border: 1px solid var(--mmx-border);
      transition:
        background 0.2s ease,
        border-color 0.2s ease;
    }

    .btn:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }

    .btn.primary {
      background: var(--mmx-accent-dim);
      border-color: var(--mmx-accent);
      color: var(--mmx-accent);
    }

    .btn.primary:hover:not(:disabled) {
      background: rgba(232, 168, 56, 0.18);
    }

    .btn.secondary {
      background: transparent;
      color: var(--mmx-text-muted);
    }

    .btn.secondary:hover:not(:disabled) {
      border-color: var(--mmx-text-muted);
      color: var(--mmx-text);
    }

    .state {
      margin: 0;
      padding: 1.25rem;
      color: var(--mmx-text-muted);
      border: 1px dashed var(--mmx-border);
      border-radius: 6px;
    }

    .state-error {
      color: #fda4af;
      border-style: solid;
    }

    .mono {
      font-family: var(--font-mono);
      font-size: 0.875rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = inject(OrderApiService);
  private readonly trader = inject(TraderContextService);

  readonly received = OrderStatus.RECEIVED;
  readonly assigned = OrderStatus.ASSIGNED;
  readonly executed = OrderStatus.EXECUTED;

  readonly order = signal<OrderDetails | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly acting = signal(false);

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id');
      if (!id) {
        this.loading.set(false);
        this.error.set('Missing order id.');
        return;
      }
      this.fetch(id);
    });
  }

  assign(): void {
    const id = this.order()?.orderId;
    if (!id) {
      return;
    }
    this.acting.set(true);
    this.error.set(null);
    this.api.assignOrder(id, this.trader.traderId()).subscribe({
      next: (details) => {
        this.order.set(details);
        this.acting.set(false);
      },
      error: (err) => {
        this.acting.set(false);
        this.error.set(this.formatHttpError(err));
      },
    });
  }

  unassign(): void {
    const id = this.order()?.orderId;
    if (!id) {
      return;
    }
    this.acting.set(true);
    this.error.set(null);
    this.api.unassignOrder(id, this.trader.traderId()).subscribe({
      next: (details) => {
        this.order.set(details);
        this.acting.set(false);
      },
      error: (err) => {
        this.acting.set(false);
        this.error.set(this.formatHttpError(err));
      },
    });
  }

  execute(body: ExecuteOrderRequest): void {
    const id = this.order()?.orderId;
    if (!id) {
      return;
    }
    this.acting.set(true);
    this.error.set(null);
    this.api.executeOrder(id, this.trader.traderId(), body).subscribe({
      next: (details) => {
        this.order.set(details);
        this.acting.set(false);
      },
      error: (err) => {
        this.acting.set(false);
        this.error.set(this.formatHttpError(err));
      },
    });
  }

  private fetch(orderId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getOrderDetails(orderId, this.trader.traderId()).subscribe({
      next: (details) => {
        this.order.set(details);
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
    return 'Request failed. Check trader id and API availability.';
  }
}
