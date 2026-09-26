import { Component, inject, output } from '@angular/core';
import type { OrderType } from '../models/order-creation-payload.model';
import { WizardStateService } from '../services/wizard-state.service';

@Component({
  selector: 'mmx-step-order-type',
  standalone: true,
  template: `
    <section class="step-order-type">
      <h2 class="step-title">Choose order type</h2>
      <div class="choice-grid">
        <button
          type="button"
          class="choice-card"
          data-testid="order-type-term"
          (click)="select('TERM')"
        >
          <span class="choice-label">Term</span>
          <span class="choice-hint">Fixed tenor money market deposit</span>
        </button>
        <button
          type="button"
          class="choice-card"
          data-testid="order-type-oncall"
          (click)="select('ON_CALL')"
        >
          <span class="choice-label">On Call</span>
          <span class="choice-hint">Notice-period callable deposit</span>
        </button>
      </div>
    </section>
  `,
  styles: `
    .step-order-type {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .step-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
    }

    .choice-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 0.75rem;
    }

    @media (min-width: 480px) {
      .choice-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }

    .choice-card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.25rem;
      padding: 1rem;
      border: 1px solid #d0d5dd;
      border-radius: 0.5rem;
      background: #fff;
      cursor: pointer;
      text-align: left;
    }

    .choice-card:hover {
      border-color: #98a2b3;
      background: #f9fafb;
    }

    .choice-label {
      font-weight: 600;
      color: #101828;
    }

    .choice-hint {
      font-size: 0.875rem;
      color: #667085;
    }
  `,
})
export class StepOrderTypeComponent {
  private readonly wizardState = inject(WizardStateService);

  readonly stepComplete = output<void>();

  select(orderType: OrderType): void {
    this.wizardState.setOrderType(orderType);
    this.wizardState.completeAndAdvance();
    this.stepComplete.emit();
  }
}
