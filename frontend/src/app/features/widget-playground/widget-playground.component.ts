import { DatePipe, JsonPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  OrderCreationWizardComponent,
  type OrderCreationPayload,
} from 'order-creation-widget';

type PlaygroundEvent = {
  at: string;
  type: 'orderReady' | 'cancelled';
  payload: OrderCreationPayload | null;
};

@Component({
  selector: 'app-widget-playground',
  standalone: true,
  imports: [FormsModule, JsonPipe, DatePipe, OrderCreationWizardComponent],
  template: `
    <section class="playground">
      <header class="playground-header">
        <h1>Order creation widget playground</h1>
        <p>Development-only harness for the embeddable PM order creation wizard.</p>
      </header>

      <div class="playground-layout">
        <aside class="playground-config" aria-label="Widget configuration">
          <h2>Configuration</h2>

          <form class="playground-config-form" (ngSubmit)="applyConfig()">
            <label class="field">
              <span>API base URL</span>
              <input
                type="text"
                name="apiBaseUrl"
                [(ngModel)]="apiBaseUrl"
                placeholder="Leave empty to use dev proxy (/api)"
                data-testid="playground-api-base-url"
              />
            </label>

            <label class="field">
              <span>Portfolio number</span>
              <input
                type="text"
                name="portfolioNumber"
                [(ngModel)]="portfolioNumber"
                required
                data-testid="playground-portfolio-number"
              />
            </label>

            <label class="field">
              <span>Order type</span>
              <select name="orderType" [(ngModel)]="orderType" data-testid="playground-order-type">
                <option value="">Full flow (choose in wizard)</option>
                <option value="TERM">Term</option>
                <option value="ON_CALL">On Call</option>
              </select>
            </label>

            <label class="field">
              <span>Contract number (OnCall shortcut)</span>
              <input
                type="text"
                name="contractNumber"
                [(ngModel)]="contractNumber"
                data-testid="playground-contract-number"
              />
            </label>

            @if (configFormError()) {
              <p class="playground-form-error" role="alert" data-testid="playground-form-error">
                {{ configFormError() }}
              </p>
            }

            <button type="submit" data-testid="playground-apply-config">Apply configuration</button>
          </form>

          @if (lastAppliedAt(); as appliedAt) {
            <p class="playground-applied" data-testid="playground-applied-summary">
              Active since {{ appliedAt | date: 'mediumTime' }} —
              portfolio <strong>{{ activePortfolioNumber() }}</strong>,
              API base <strong>{{ activeApiBaseUrl() || '(dev proxy)' }}</strong>
              @if (activeOrderType(); as type) {
                , order type <strong>{{ type }}</strong>
              }
              @if (activeContractNumber(); as contract) {
                , contract <strong>{{ contract }}</strong>
              }
            </p>
          }
        </aside>

        <div class="playground-widget" data-testid="playground-widget-panel">
          <h2>Widget</h2>
          @if (widgetMounted()) {
            <mmx-order-creation-wizard
              [apiBaseUrl]="activeApiBaseUrl()"
              [portfolioNumber]="activePortfolioNumber()"
              [orderType]="activeOrderType()"
              [contractNumber]="activeContractNumber()"
              (orderReady)="onOrderReady($event)"
              (cancelled)="onCancelled()"
            />
          } @else {
            <p class="playground-empty" data-testid="playground-widget-placeholder">
              Apply configuration to load the widget.
            </p>
          }
        </div>

        <aside class="playground-events" aria-label="Event log">
          <h2>Event log</h2>
          @if (events().length === 0) {
            <p class="playground-empty">No events yet.</p>
          } @else {
            <pre data-testid="playground-event-log">{{ events() | json }}</pre>
          }
        </aside>
      </div>
    </section>
  `,
  styles: `
    .playground {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      padding: 1rem 0 2rem;
    }

    .playground-header h1 {
      margin: 0 0 0.35rem;
      font-size: 1.35rem;
    }

    .playground-header p {
      margin: 0;
      color: var(--mmx-text-muted, #667085);
    }

    .playground-layout {
      display: grid;
      grid-template-columns: minmax(14rem, 18rem) minmax(0, 1fr) minmax(14rem, 20rem);
      gap: 1rem;
      align-items: start;
    }

    @media (max-width: 960px) {
      .playground-layout {
        grid-template-columns: 1fr;
      }
    }

    .playground-config,
    .playground-widget,
    .playground-events {
      border: 1px solid var(--mmx-border, #d0d5dd);
      border-radius: 0.5rem;
      padding: 1rem;
      background: var(--mmx-surface, #fff);
    }

    .playground-config h2,
    .playground-widget h2,
    .playground-events h2 {
      margin: 0 0 0.75rem;
      font-size: 0.95rem;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--mmx-text-muted, #667085);
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      margin-bottom: 0.75rem;
      font-size: 0.875rem;
    }

    .field input,
    .field select {
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--mmx-border, #d0d5dd);
      border-radius: 0.375rem;
      font: inherit;
    }

    .playground-config-form button[type='submit'] {
      width: 100%;
      padding: 0.5rem 0.75rem;
      border: none;
      border-radius: 0.375rem;
      background: #155eef;
      color: #fff;
      cursor: pointer;
      font: inherit;
    }

    .playground-form-error {
      margin: 0 0 0.75rem;
      color: #b42318;
      font-size: 0.875rem;
    }

    .playground-applied {
      margin: 0.75rem 0 0;
      padding-top: 0.75rem;
      border-top: 1px solid var(--mmx-border, #d0d5dd);
      color: var(--mmx-text-muted, #667085);
      font-size: 0.8125rem;
      line-height: 1.45;
    }

    .playground-empty {
      margin: 0;
      color: var(--mmx-text-muted, #667085);
      font-size: 0.875rem;
    }

    .playground-events pre {
      margin: 0;
      max-height: 28rem;
      overflow: auto;
      font-size: 0.75rem;
      white-space: pre-wrap;
      word-break: break-word;
    }
  `,
})
export class WidgetPlaygroundComponent implements OnInit {
  apiBaseUrl = '';
  portfolioNumber = 'PF-PLAYGROUND';
  orderType: '' | 'TERM' | 'ON_CALL' = '';
  contractNumber = '';

  readonly widgetMounted = signal(false);
  readonly events = signal<PlaygroundEvent[]>([]);
  readonly configFormError = signal<string | null>(null);
  readonly lastAppliedAt = signal<Date | null>(null);

  readonly activeApiBaseUrl = signal('');
  readonly activePortfolioNumber = signal('');
  readonly activeOrderType = signal<'TERM' | 'ON_CALL' | undefined>(undefined);
  readonly activeContractNumber = signal<string | undefined>(undefined);

  ngOnInit(): void {
    this.applyConfig();
  }

  applyConfig(): void {
    const portfolio = this.portfolioNumber.trim();
    if (!portfolio) {
      this.configFormError.set('Portfolio number is required.');
      this.widgetMounted.set(false);
      return;
    }

    this.configFormError.set(null);
    this.activeApiBaseUrl.set(this.apiBaseUrl.trim());
    this.activePortfolioNumber.set(portfolio);
    this.activeOrderType.set(this.orderType || undefined);
    this.activeContractNumber.set(this.contractNumber.trim() || undefined);

    this.widgetMounted.set(false);
    queueMicrotask(() => {
      this.widgetMounted.set(true);
      this.lastAppliedAt.set(new Date());
    });
  }

  onOrderReady(payload: OrderCreationPayload): void {
    this.events.update((current) => [
      { at: new Date().toISOString(), type: 'orderReady', payload },
      ...current,
    ]);
  }

  onCancelled(): void {
    this.events.update((current) => [
      { at: new Date().toISOString(), type: 'cancelled', payload: null },
      ...current,
    ]);
  }
}
