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
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { OrderApiService } from '../../core/api/order-api.service';
import { formatHttpError } from '../../core/http/format-http-error';
import type {
  ExecuteOrderRequest,
  OrderDetails,
  RejectOrderRequest,
  UpdateOrderRequest,
} from '../../core/models/order.model';
import { OrderStatus } from '../../core/models/order-status.enum';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { OrderExecutionFormComponent } from './order-execution-form.component';
import { OrderUpdateFormComponent } from './order-update-form.component';
import { canShowAction, type OrderDetailAction } from './order-detail-actions';

@Component({
  selector: 'mmx-order-details',
  standalone: true,
  imports: [
    DecimalPipe,
    RouterLink,
    StatusBadgeComponent,
    OrderExecutionFormComponent,
    OrderUpdateFormComponent,
    ConfirmDialogComponent,
  ],
  template: `
    <section class="feature">
      <nav class="crumb">
        <a [routerLink]="queuesReturn().link">← Back to {{ queuesReturn().label }}</a>
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
          @if (o.minimumRate !== null && o.minimumRate !== undefined) {
            <dt>Minimum rate</dt>
            <dd class="mono">{{ o.minimumRate | number: '1.2-8' }}</dd>
          }
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
          @if (o.status === executed || o.status === accounted) {
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
          @if (o.status === rejected && o.rejectionReason) {
            <dt>Rejection reason</dt>
            <dd>{{ o.rejectionReason }}</dd>
          }
        </dl>

        @if (showAction(o, 'assign') || showAction(o, 'cancel') || showAction(o, 'reject')) {
          <div class="actions">
            @if (showAction(o, 'assign')) {
              <button type="button" class="btn primary" [disabled]="acting()" (click)="assign()">
                Assign to me
              </button>
            }
            @if (showAction(o, 'cancel')) {
              <button type="button" class="btn secondary" [disabled]="acting()" (click)="openCancelDialog()">
                Cancel order
              </button>
            }
            @if (showAction(o, 'reject')) {
              <button type="button" class="btn danger-outline" [disabled]="acting()" (click)="openRejectDialog()">
                Reject
              </button>
            }
          </div>
        }
        @if (showAction(o, 'unassign') || showAction(o, 'reject')) {
          <div class="actions">
            @if (showAction(o, 'unassign')) {
              <button type="button" class="btn secondary" [disabled]="acting()" (click)="unassign()">
                Unassign
              </button>
            }
            @if (showAction(o, 'reject')) {
              <button
                type="button"
                class="btn danger-outline"
                [disabled]="acting()"
                (click)="openRejectDialog()"
              >
                Reject
              </button>
            }
          </div>
        }
        @if (showAction(o, 'update')) {
          <mmx-order-update-form
            [order]="o"
            [submitting]="acting()"
            (submitUpdate)="updateOrder($event)"
          />
        }
        @if (showAction(o, 'execute')) {
          <mmx-order-execution-form
            [submitting]="acting()"
            (submitExecute)="execute($event)"
          />
        }
      }

      <mmx-confirm-dialog
        [open]="cancelDialogOpen()"
        title="Cancel order"
        message="Withdraw this order? Its status will become CANCELLED."
        confirmLabel="Cancel order"
        cancelLabel="Keep open"
        (confirm)="confirmCancel()"
        (cancel)="cancelDialogOpen.set(false)"
      />

      @if (rejectDialogOpen()) {
        <div class="backdrop" role="presentation" (click)="closeRejectDialogOnBackdrop($event)">
          <div class="reject-panel" role="dialog" aria-modal="true" (click)="$event.stopPropagation()">
            <h2 class="reject-title">Reject order</h2>
            <p class="reject-hint">A reason is required for audit.</p>
            <label class="reject-label" for="reject-reason">Reason</label>
            <textarea
              id="reject-reason"
              class="reject-input"
              rows="4"
              maxlength="500"
              [value]="rejectReasonDraft()"
              (input)="rejectReasonDraft.set($any($event.target).value)"
              [disabled]="acting()"
            ></textarea>
            <div class="reject-row">
              <button type="button" class="btn secondary" [disabled]="acting()" (click)="closeRejectDialog()">
                Back
              </button>
              <button
                type="button"
                class="btn danger"
                [disabled]="acting() || !rejectReasonDraft().trim()"
                (click)="submitReject()"
              >
                Reject order
              </button>
            </div>
          </div>
        </div>
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

    .btn.danger-outline {
      background: transparent;
      border-color: rgba(248, 113, 113, 0.45);
      color: #fecaca;
    }

    .btn.danger-outline:hover:not(:disabled) {
      background: rgba(248, 113, 113, 0.1);
      border-color: rgba(248, 113, 113, 0.65);
    }

    .btn.danger {
      background: rgba(248, 113, 113, 0.12);
      border-color: rgba(248, 113, 113, 0.45);
      color: #fecaca;
    }

    .btn.danger:hover:not(:disabled) {
      background: rgba(248, 113, 113, 0.2);
    }

    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 90;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      background: rgba(5, 8, 12, 0.72);
      backdrop-filter: blur(4px);
    }

    .reject-panel {
      width: 100%;
      max-width: 420px;
      padding: 1.25rem 1.35rem;
      border-radius: 8px;
      border: 1px solid var(--mmx-border);
      background: var(--mmx-surface);
      box-shadow: 0 18px 48px rgba(0, 0, 0, 0.35);
    }

    .reject-title {
      margin: 0 0 0.4rem;
      font-family: var(--font-display);
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--mmx-text);
    }

    .reject-hint {
      margin: 0 0 0.85rem;
      font-size: 0.78rem;
      color: var(--mmx-text-muted);
    }

    .reject-label {
      display: block;
      margin-bottom: 0.35rem;
      font-family: var(--font-mono);
      font-size: 0.65rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
    }

    .reject-input {
      width: 100%;
      box-sizing: border-box;
      margin-bottom: 1rem;
      padding: 0.6rem 0.75rem;
      border-radius: 4px;
      border: 1px solid var(--mmx-border);
      background: rgba(0, 0, 0, 0.2);
      color: var(--mmx-text);
      font-family: var(--font-sans, system-ui);
      font-size: 0.875rem;
      line-height: 1.45;
      resize: vertical;
      min-height: 5rem;
    }

    .reject-input:focus {
      outline: none;
      border-color: var(--mmx-accent);
    }

    .reject-input:disabled {
      opacity: 0.5;
    }

    .reject-row {
      display: flex;
      justify-content: flex-end;
      gap: 0.65rem;
      flex-wrap: wrap;
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
  readonly trader = inject(TraderContextService);

  readonly executed = OrderStatus.EXECUTED;
  readonly accounted = OrderStatus.ACCOUNTED;
  readonly rejected = OrderStatus.REJECTED;

  readonly order = signal<OrderDetails | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly acting = signal(false);
  readonly cancelDialogOpen = signal(false);
  readonly rejectDialogOpen = signal(false);
  readonly rejectReasonDraft = signal('');
  /** From ?ws=&queue= on /orders/:id — restores the desk queue and drives shell nav highlighting. */
  readonly queuesReturn = signal<{ link: string[]; label: string }>({
    link: ['/oncall', 'received'],
    label: 'Queues',
  });

  ngOnInit(): void {
    this.applyQueuesReturnQuery(this.route.snapshot.queryParamMap);
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((q) => {
      this.applyQueuesReturnQuery(q);
    });

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

  private applyQueuesReturnQuery(q: ParamMap): void {
    const ws = q.get('ws');
    const queue = q.get('queue');
    const labels: Record<string, string> = {
      received: 'Received',
      assigned: 'Assigned',
      executed: 'Executed',
    };
    if (
      (ws === 'term' || ws === 'oncall') &&
      (queue === 'received' || queue === 'assigned' || queue === 'executed')
    ) {
      this.queuesReturn.set({
        link: ['/', ws, queue],
        label: labels[queue],
      });
    } else {
      this.queuesReturn.set({
        link: ['/oncall', 'received'],
        label: 'Queues',
      });
    }
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

  openCancelDialog(): void {
    this.cancelDialogOpen.set(true);
  }

  confirmCancel(): void {
    const id = this.order()?.orderId;
    if (!id) {
      return;
    }
    this.acting.set(true);
    this.error.set(null);
    this.cancelDialogOpen.set(false);
    this.api.cancelOrder(id, this.trader.traderId()).subscribe({
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

  openRejectDialog(): void {
    this.rejectReasonDraft.set('');
    this.rejectDialogOpen.set(true);
  }

  closeRejectDialog(): void {
    this.rejectDialogOpen.set(false);
  }

  closeRejectDialogOnBackdrop(ev: MouseEvent): void {
    if (ev.target === ev.currentTarget) {
      this.closeRejectDialog();
    }
  }

  submitReject(): void {
    const id = this.order()?.orderId;
    const reason = this.rejectReasonDraft().trim();
    if (!id || !reason) {
      return;
    }
    this.acting.set(true);
    this.error.set(null);
    const body: RejectOrderRequest = { reason };
    this.api.rejectOrder(id, this.trader.traderId(), body).subscribe({
      next: (details) => {
        this.order.set(details);
        this.rejectDialogOpen.set(false);
        this.acting.set(false);
      },
      error: (err) => {
        this.acting.set(false);
        this.error.set(this.formatHttpError(err));
      },
    });
  }

  updateOrder(body: UpdateOrderRequest): void {
    const id = this.order()?.orderId;
    if (!id) {
      return;
    }
    this.acting.set(true);
    this.error.set(null);
    this.api.updateOrder(id, this.trader.traderId(), body).subscribe({
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

  showAction(o: OrderDetails, action: OrderDetailAction): boolean {
    return canShowAction(o, this.trader.traderId(), action);
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
    return formatHttpError(err, 'Request failed. Check trader id and API availability.');
  }
}
