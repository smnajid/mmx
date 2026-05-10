import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { OrderStatus } from '../../core/models/order-status.enum';

@Component({
  selector: 'mmx-status-badge',
  standalone: true,
  template: `
    <span class="badge" [attr.data-status]="status">{{ label }}</span>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .badge {
      font-family: var(--font-mono);
      font-size: 0.6875rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.2rem 0.55rem;
      border-radius: 3px;
      border: 1px solid transparent;
      animation: badge-in 0.35s ease backwards;
    }

    @keyframes badge-in {
      from {
        opacity: 0;
        transform: translateY(4px);
      }
    }

    .badge[data-status='RECEIVED'] {
      background: rgba(56, 189, 248, 0.12);
      border-color: rgba(56, 189, 248, 0.35);
      color: #7dd3fc;
    }

    .badge[data-status='ASSIGNED'] {
      background: var(--mmx-accent-dim);
      border-color: var(--mmx-border);
      color: var(--mmx-accent);
    }

    .badge[data-status='EXECUTED'] {
      background: rgba(52, 211, 153, 0.12);
      border-color: rgba(52, 211, 153, 0.35);
      color: #6ee7b7;
    }

    .badge[data-status='ACCOUNTED'] {
      background: rgba(110, 160, 130, 0.1);
      border-color: rgba(134, 180, 150, 0.28);
      color: #9cb8a8;
    }

    .badge[data-status='CANCELLED'],
    .badge[data-status='REJECTED'] {
      background: rgba(251, 113, 133, 0.08);
      border-color: rgba(251, 113, 133, 0.28);
      color: #fda4af;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: OrderStatus;

  get label(): string {
    return this.status?.replace(/_/g, ' ') ?? '';
  }
}
