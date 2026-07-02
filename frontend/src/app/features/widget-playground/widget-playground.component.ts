import { DatePipe, JsonPipe } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  OrderCreationWizardComponent,
  type OrderCreationPayload,
} from 'order-creation-widget';
import type { ReceiveOrderResponse } from '../../core/models/order.model';
import {
  generatePlaygroundExternalReference,
  mapOrderCreationPayloadToReceiveRequest,
} from './order-creation-payload.mapper';

type PlaygroundSubmitStatus = 'idle' | 'submitting' | 'success' | 'error';

type PlaygroundEvent = {
  at: string;
  type: 'orderReady' | 'cancelled';
  payload: OrderCreationPayload | null;
  submitStatus?: PlaygroundSubmitStatus;
  submitResult?: { orderId: string; httpStatus: number };
  submitError?: string;
  externalOrderReference?: string;
};

type LiveContract = {
  contractNumber: string;
  orderType: 'TERM' | 'ON_CALL';
  currency: string;
  noticePeriod?: string;
  tenor?: string;
  valueDate: string;
  endDate?: string;
  originalAmount: number;
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
              <span>Legal entity code</span>
              <input
                type="text"
                name="legalEntityCode"
                [(ngModel)]="legalEntityCode"
                required
                maxlength="3"
                data-testid="playground-legal-entity-code"
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

            @if (configFormError()) {
              <p class="playground-form-error" role="alert" data-testid="playground-form-error">
                {{ configFormError() }}
              </p>
            }

            <button type="submit" data-testid="playground-apply-config">Apply configuration</button>
          </form>

          @if (showContractPicker()) {
            <div class="contract-picker" data-testid="playground-contract-picker">
              <h3>Live contracts</h3>
              @if (contractsLoading()) {
                <p class="playground-empty">Loading contracts…</p>
              } @else if (contractsError(); as error) {
                <p class="playground-form-error" role="alert">{{ error }}</p>
              } @else if (liveContracts().length === 0) {
                <p class="playground-empty" data-testid="playground-contract-picker-empty">
                  No live contracts for this portfolio and type. Seed executed On Call
                  subscriptions (e.g. <code>./scripts/seed-demo-orders.sh</code> with portfolio
                  <strong>PF-DEMO</strong>), or use <strong>Full flow</strong> in order type for a
                  new subscription.
                </p>
              } @else {
                <ul class="contract-picker-list">
                  @for (contract of liveContracts(); track contract.contractNumber) {
                    <li>
                      @if (contract.orderType === 'ON_CALL') {
                        <button
                          type="button"
                          class="contract-picker-option"
                          [class.contract-picker-option--selected]="
                            activeContractNumber() === contract.contractNumber
                          "
                          (click)="selectContract(contract)"
                          data-testid="playground-contract-option"
                        >
                          {{ contract.contractNumber }} — {{ contract.currency }}
                          @if (contract.noticePeriod) {
                            ({{ contract.noticePeriod }})
                          }
                        </button>
                      } @else {
                        <span
                          class="contract-picker-option contract-picker-option--disabled"
                          data-testid="playground-contract-option-term"
                        >
                          {{ contract.contractNumber }} — {{ contract.currency }}
                          (Term lifecycle not yet available)
                        </span>
                      }
                    </li>
                  }
                </ul>
              }
            </div>
          }

          @if (lastAppliedAt(); as appliedAt) {
            <p class="playground-applied" data-testid="playground-applied-summary">
              Active since {{ appliedAt | date: 'mediumTime' }} —
              legal entity <strong>{{ activeLegalEntityCode() }}</strong>,
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
              [legalEntityCode]="activeLegalEntityCode()"
              [portfolioNumber]="activePortfolioNumber()"
              [orderType]="activeOrderType()"
              [contractNumber]="activeContractNumber()"
              (orderReady)="onOrderReady($event)"
              (cancelled)="onCancelled()"
            />
          } @else {
            <p class="playground-empty" data-testid="playground-widget-placeholder">
              {{ widgetPlaceholderMessage() }}
            </p>
          }
        </div>

        <aside class="playground-events" aria-label="Event log">
          <h2>Event log</h2>
          @if (events().length === 0) {
            <p class="playground-empty">No events yet.</p>
          } @else {
            <ul class="event-log-list" data-testid="playground-event-log">
              @for (event of events(); track event.at; let index = $index) {
                <li class="event-log-item">
                  <div class="event-log-meta">{{ event.at }} · {{ event.type }}</div>
                  @if (event.payload) {
                    <pre class="event-log-payload">{{ event.payload | json }}</pre>
                  }
                  @if (event.type === 'orderReady' && event.payload) {
                    @if (event.submitStatus === 'success' && event.submitResult) {
                      <p class="event-log-success" data-testid="playground-submit-success">
                        Submitted ({{ event.submitResult.httpStatus }}) · order
                        {{ event.submitResult.orderId }}
                      </p>
                    } @else if (event.submitStatus === 'error') {
                      <p class="event-log-error" role="alert" data-testid="playground-submit-error">
                        {{ event.submitError }}
                      </p>
                      <button
                        type="button"
                        class="event-log-send"
                        data-testid="playground-send-order"
                        (click)="submitOrder(index)"
                      >
                        Retry send
                      </button>
                    } @else if (event.submitStatus === 'submitting') {
                      <p class="event-log-pending" data-testid="playground-submit-pending">
                        Sending to mmx…
                      </p>
                    } @else {
                      <button
                        type="button"
                        class="event-log-send"
                        data-testid="playground-send-order"
                        (click)="submitOrder(index)"
                      >
                        Send to mmx
                      </button>
                    }
                  }
                </li>
              }
            </ul>
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

    .contract-picker {
      margin-top: 0.75rem;
      padding-top: 0.75rem;
      border-top: 1px solid var(--mmx-border, #d0d5dd);
    }

    .contract-picker h3 {
      margin: 0 0 0.5rem;
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--mmx-text-muted, #667085);
    }

    .contract-picker-list {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .contract-picker-option {
      display: block;
      width: 100%;
      padding: 0.45rem 0.65rem;
      border: 1px solid var(--mmx-border, #d0d5dd);
      border-radius: 0.375rem;
      background: #fff;
      text-align: left;
      font: inherit;
      font-size: 0.8125rem;
      cursor: pointer;
    }

    .contract-picker-option--selected {
      border-color: #155eef;
      background: #eff4ff;
    }

    .contract-picker-option--disabled {
      color: var(--mmx-text-muted, #667085);
      cursor: not-allowed;
      background: #f9fafb;
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
      line-height: 1.45;
    }

    .playground-empty code {
      font-family: var(--font-mono, ui-monospace, monospace);
      font-size: 0.8125rem;
    }

    .playground-events pre {
      margin: 0;
      max-height: 28rem;
      overflow: auto;
      font-size: 0.75rem;
      white-space: pre-wrap;
      word-break: break-word;
    }

    .event-log-list {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      max-height: 28rem;
      overflow: auto;
    }

    .event-log-item {
      padding: 0.65rem 0.75rem;
      border: 1px solid var(--mmx-border);
      border-radius: 0.375rem;
      background: var(--mmx-surface-elevated);
    }

    .event-log-meta {
      margin-bottom: 0.35rem;
      font-size: 0.7rem;
      color: var(--mmx-text-muted, #667085);
    }

    .event-log-payload {
      margin: 0 0 0.5rem;
      font-size: 0.7rem;
      color: var(--mmx-text);
    }

    .event-log-send {
      padding: 0.4rem 0.75rem;
      border: none;
      border-radius: 0.375rem;
      background: #155eef;
      color: #fff;
      cursor: pointer;
      font: inherit;
      font-size: 0.75rem;
    }

    .event-log-send:disabled {
      opacity: 0.55;
      cursor: not-allowed;
    }

    .event-log-success {
      margin: 0;
      font-size: 0.75rem;
      color: #027a48;
    }

    .event-log-error {
      margin: 0 0 0.5rem;
      font-size: 0.75rem;
      color: #b42318;
    }

    .event-log-pending {
      margin: 0;
      font-size: 0.75rem;
      color: var(--mmx-text-muted, #667085);
    }
  `,
})
export class WidgetPlaygroundComponent {
  private readonly http = inject(HttpClient);

  apiBaseUrl = '';
  legalEntityCode = 'LOC';
  portfolioNumber = 'PF-PLAYGROUND';
  orderType: '' | 'TERM' | 'ON_CALL' = '';

  readonly widgetMounted = signal(false);
  readonly events = signal<PlaygroundEvent[]>([]);
  readonly configFormError = signal<string | null>(null);
  readonly lastAppliedAt = signal<Date | null>(null);
  readonly liveContracts = signal<LiveContract[]>([]);
  readonly contractsLoading = signal(false);
  readonly contractsError = signal<string | null>(null);
  readonly showContractPicker = signal(false);

  readonly activeApiBaseUrl = signal('');
  readonly activeLegalEntityCode = signal('');
  readonly activePortfolioNumber = signal('');
  readonly activeOrderType = signal<'TERM' | 'ON_CALL' | undefined>(undefined);
  readonly activeContractNumber = signal<string | undefined>(undefined);

  applyConfig(): void {
    const portfolio = this.portfolioNumber.trim();
    const entityCode = this.legalEntityCode.trim();
    if (!portfolio) {
      this.configFormError.set('Portfolio number is required.');
      this.widgetMounted.set(false);
      this.showContractPicker.set(false);
      return;
    }
    if (!entityCode || entityCode.length !== 3) {
      this.configFormError.set('Legal entity code must be exactly 3 characters.');
      this.widgetMounted.set(false);
      this.showContractPicker.set(false);
      return;
    }

    this.configFormError.set(null);
    this.activeApiBaseUrl.set(this.apiBaseUrl.trim());
    this.activeLegalEntityCode.set(entityCode);
    this.activePortfolioNumber.set(portfolio);
    this.activeOrderType.set(this.orderType || undefined);
    this.activeContractNumber.set(undefined);
    this.liveContracts.set([]);
    this.contractsError.set(null);

    const orderType = this.orderType;
    if (orderType === 'ON_CALL' || orderType === 'TERM') {
      this.showContractPicker.set(true);
      this.fetchLiveContracts(portfolio, orderType);
    } else {
      this.showContractPicker.set(false);
    }

    if (orderType === 'ON_CALL') {
      this.widgetMounted.set(false);
    } else {
      this.remountWidget();
    }
    this.lastAppliedAt.set(new Date());
  }

  selectContract(contract: LiveContract): void {
    if (contract.orderType !== 'ON_CALL') {
      return;
    }
    this.activeContractNumber.set(contract.contractNumber);
    this.remountWidget();
  }

  onOrderReady(payload: OrderCreationPayload): void {
    this.events.update((current) => [
      { at: new Date().toISOString(), type: 'orderReady', payload, submitStatus: 'idle' },
      ...current,
    ]);
  }

  submitOrder(index: number): void {
    const event = this.events()[index];
    if (!event || event.type !== 'orderReady' || !event.payload) {
      return;
    }
    if (event.submitStatus === 'submitting' || event.submitStatus === 'success') {
      return;
    }

    const externalOrderReference = generatePlaygroundExternalReference();
    this.patchEvent(index, {
      submitStatus: 'submitting',
      submitError: undefined,
      externalOrderReference,
    });

    const body = mapOrderCreationPayloadToReceiveRequest(event.payload, externalOrderReference);
    const base = this.activeApiBaseUrl();
    this.http
      .post<ReceiveOrderResponse>(`${base}/api/v1/orders`, body, { observe: 'response' })
      .subscribe({
        next: (response) => {
          const orderId = response.body?.orderId;
          if (!orderId) {
            this.patchEvent(index, {
              submitStatus: 'error',
              submitError: 'Submit succeeded but no orderId was returned.',
            });
            return;
          }
          this.patchEvent(index, {
            submitStatus: 'success',
            submitResult: { orderId, httpStatus: response.status },
          });
        },
        error: (err: HttpErrorResponse) => {
          this.patchEvent(index, {
            submitStatus: 'error',
            submitError: this.formatSubmitError(err),
          });
        },
      });
  }

  private patchEvent(index: number, patch: Partial<PlaygroundEvent>): void {
    this.events.update((current) =>
      current.map((event, i) => (i === index ? { ...event, ...patch } : event)),
    );
  }

  private formatSubmitError(err: HttpErrorResponse): string {
    const body = err.error as { message?: string } | string | null;
    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    return err.message || 'Submit failed.';
  }

  onCancelled(): void {
    this.events.update((current) => [
      { at: new Date().toISOString(), type: 'cancelled', payload: null },
      ...current,
    ]);
  }

  widgetPlaceholderMessage(): string {
    if (!this.lastAppliedAt()) {
      return 'Apply configuration to load the widget.';
    }
    if (this.activeOrderType() === 'ON_CALL') {
      if (this.contractsLoading()) {
        return 'Loading live contracts… Select one below to open the lifecycle shortcut.';
      }
      if (this.liveContracts().length === 0 && !this.contractsError()) {
        return (
          'No live contracts for this portfolio. Select one from the picker after seeding data, ' +
          'or switch order type to Full flow for a new subscription.'
        );
      }
      return (
        'Select a live contract from the picker to open the lifecycle shortcut, ' +
        'or switch order type to Full flow for a new subscription.'
      );
    }
    return 'Apply configuration to load the widget.';
  }

  private fetchLiveContracts(portfolio: string, orderType: 'TERM' | 'ON_CALL'): void {
    this.contractsLoading.set(true);
    const base = this.activeApiBaseUrl();
    const params = new HttpParams()
      .set('portfolioNumber', portfolio)
      .set('orderType', orderType);
    this.http
      .get<{ contracts: LiveContract[] }>(`${base}/api/v1/order-creation/contracts`, { params })
      .subscribe({
        next: (response) => {
          this.liveContracts.set(response.contracts);
          this.contractsLoading.set(false);
        },
        error: () => {
          this.contractsError.set('Failed to load live contracts.');
          this.liveContracts.set([]);
          this.contractsLoading.set(false);
        },
      });
  }

  private remountWidget(): void {
    this.widgetMounted.set(false);
    // Defer remount to the next macrotask so @if sees false and destroys the wizard
    // before recreating it (queueMicrotask can coalesce with the same CD cycle).
    setTimeout(() => this.widgetMounted.set(true), 0);
  }
}
