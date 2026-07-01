import { DecimalPipe } from '@angular/common';
import { Component, inject, input, output } from '@angular/core';
import type { OrderCreationPayload } from '../models/order-creation-payload.model';
import { WizardHostConfigService } from '../services/wizard-host-config.service';
import { WizardStateService } from '../services/wizard-state.service';

@Component({
  selector: 'mmx-step-review',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <section class="step-review">
      <h2 class="step-title">Review order</h2>

      <dl class="review-summary" data-testid="review-summary">
        <div><dt>Portfolio</dt><dd>{{ portfolioNumber() }}</dd></div>
        <div><dt>Order type</dt><dd>{{ state().orderType }}</dd></div>
        <div><dt>Currency</dt><dd>{{ state().currency }}</dd></div>
        <div><dt>Operation</dt><dd>{{ state().operation }}</dd></div>
        @if (state().tenor) {
          <div><dt>Tenor</dt><dd>{{ state().tenor }}</dd></div>
        }
        @if (state().noticePeriod) {
          <div><dt>Notice period</dt><dd>{{ state().noticePeriod }}</dd></div>
        }
        <div>
          <dt>Counterparty</dt>
          <dd>
            {{ state().counterparty }}
            @if (state().counterpartyRate != null) {
              <span class="review-rate">({{ state().counterpartyRate | number: '1.2-4' }}%)</span>
            }
          </dd>
        </div>
        <div><dt>Amount</dt><dd>{{ state().amount | number: '1.0-0' }}</dd></div>
        <div><dt>Value date</dt><dd>{{ state().valueDate }}</dd></div>
        @if (state().minimumRate != null) {
          <div><dt>Minimum rate</dt><dd>{{ state().minimumRate | number: '1.2-4' }}%</dd></div>
        }
        @if (state().sourceContractNumber) {
          <div><dt>Source contract</dt><dd>{{ state().sourceContractNumber }}</dd></div>
        }
      </dl>

      <div class="review-actions">
        <button type="button" data-testid="review-cancel" (click)="cancelled.emit()">Cancel</button>
        <button type="button" data-testid="review-create-order" (click)="confirm()">Create Order</button>
      </div>
    </section>
  `,
  styles: `
    .step-review {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .step-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
    }

    .review-summary {
      display: grid;
      gap: 0.75rem;
      margin: 0;
    }

    .review-summary div {
      display: grid;
      grid-template-columns: 10rem 1fr;
      gap: 0.5rem;
    }

    .review-summary dt {
      margin: 0;
      color: #667085;
      font-weight: 500;
    }

    .review-summary dd {
      margin: 0;
      color: #101828;
      font-weight: 600;
    }

    .review-rate {
      color: #667085;
      font-weight: 500;
    }

    .review-actions {
      display: flex;
      gap: 0.75rem;
    }

    .review-actions button {
      padding: 0.5rem 1rem;
      border-radius: 0.375rem;
      cursor: pointer;
    }

    .review-actions button[data-testid='review-cancel'] {
      border: 1px solid #d0d5dd;
      background: #fff;
      color: #344054;
    }

    .review-actions button[data-testid='review-create-order'] {
      border: none;
      background: #155eef;
      color: #fff;
    }
  `,
})
export class StepReviewComponent {
  private readonly wizardState = inject(WizardStateService);
  private readonly hostConfig = inject(WizardHostConfigService);

  readonly portfolioNumber = input.required<string>();

  readonly orderReady = output<OrderCreationPayload>();
  readonly cancelled = output<void>();

  readonly state = this.wizardState.state;

  confirm(): void {
    const current = this.wizardState.state();
    if (
      !current.orderType ||
      !current.currency ||
      !current.operation ||
      !current.institutionCode ||
      !current.counterparty ||
      current.amount == null ||
      !current.valueDate
    ) {
      return;
    }

    const payload: OrderCreationPayload = {
      legalEntityCode: this.hostConfig.legalEntityCode,
      portfolioNumber: this.portfolioNumber(),
      orderType: current.orderType,
      currency: current.currency,
      operation: current.operation,
      institutionCode: current.institutionCode,
      counterparty: current.counterparty,
      amount: current.amount,
      valueDate: current.valueDate,
    };

    if (current.orderType === 'TERM' && current.tenor) {
      payload.tenor = current.tenor;
    }
    if (current.orderType === 'ON_CALL' && current.noticePeriod) {
      payload.noticePeriod = current.noticePeriod;
    }
    if (current.minimumRate != null) {
      payload.minimumRate = current.minimumRate;
    }
    if (current.sourceContractNumber) {
      payload.sourceContractNumber = current.sourceContractNumber;
    }

    this.orderReady.emit(payload);
  }
}
