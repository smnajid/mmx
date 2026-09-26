import { Component, inject, input, output } from '@angular/core';
import type { OrderCreationPayload } from '../models/order-creation-payload.model';
import { WizardStepId } from '../models/wizard-state.model';
import { WizardStateService } from '../services/wizard-state.service';
import { StepCounterpartyComponent } from '../steps/step-counterparty.component';
import { StepCurrencyComponent } from '../steps/step-currency.component';
import { StepOperationComponent } from '../steps/step-operation.component';
import { StepOrderDetailsComponent } from '../steps/step-order-details.component';
import { StepOrderTypeComponent } from '../steps/step-order-type.component';
import { StepReviewComponent } from '../steps/step-review.component';
import { StepTenorNoticePeriodComponent } from '../steps/step-tenor-notice-period.component';
import { StepValueDateComponent } from '../steps/step-value-date.component';
import { WIZARD_STEP_LABELS } from './wizard-step-labels';

@Component({
  selector: 'mmx-wizard-shell',
  standalone: true,
  imports: [
    StepOrderTypeComponent,
    StepCurrencyComponent,
    StepOperationComponent,
    StepTenorNoticePeriodComponent,
    StepValueDateComponent,
    StepCounterpartyComponent,
    StepOrderDetailsComponent,
    StepReviewComponent,
  ],
  template: `
    <div class="wizard-shell">
      <nav class="step-indicator" aria-label="Wizard progress">
        @for (step of wizardState.visibleSteps(); track step; let index = $index) {
          <button
            type="button"
            class="step-indicator-item"
            [attr.data-testid]="'step-indicator-' + step"
            [class.step-indicator-item--active]="wizardState.state().currentStep === step"
            [class.step-indicator-item--completed]="isCompleted(step)"
            [disabled]="!canGoTo(step)"
            (click)="goToStep(step)"
          >
            <span class="step-indicator-index">{{ index + 1 }}</span>
            <span class="step-indicator-label">{{ stepLabel(step) }}</span>
          </button>
        }
      </nav>

      <div class="wizard-content">
        @switch (wizardState.state().currentStep) {
          @case (stepIds.ORDER_TYPE) {
            <mmx-step-order-type />
          }
          @case (stepIds.CURRENCY) {
            <mmx-step-currency />
          }
          @case (stepIds.OPERATION) {
            <mmx-step-operation />
          }
          @case (stepIds.TENOR_OR_NOTICE_PERIOD) {
            <mmx-step-tenor-notice-period />
          }
          @case (stepIds.VALUE_DATE) {
            <mmx-step-value-date />
          }
          @case (stepIds.COUNTERPARTY) {
            <mmx-step-counterparty />
          }
          @case (stepIds.ORDER_DETAILS) {
            <mmx-step-order-details />
          }
          @case (stepIds.REVIEW) {
            <mmx-step-review
              [portfolioNumber]="portfolioNumber()"
              (orderReady)="orderReady.emit($event)"
              (cancelled)="cancelled.emit()"
            />
          }
        }
      </div>

      <div class="wizard-nav">
        <button
          type="button"
          data-testid="wizard-back"
          [disabled]="!canGoBack()"
          (click)="back()"
        >
          Back
        </button>
        <button
          type="button"
          data-testid="wizard-next"
          [disabled]="!canGoForward()"
          (click)="next()"
        >
          Next
        </button>
      </div>
    </div>
  `,
  styles: `
    .wizard-shell {
      display: flex;
      flex-direction: column;
      gap: 1rem;
      max-height: 100%;
      min-height: 0;
    }

    .step-indicator {
      display: flex;
      gap: 0.5rem;
      overflow-x: auto;
      padding-bottom: 0.25rem;
    }

    .step-indicator-item {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.125rem;
      min-width: 5.5rem;
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--wizard-border, #d0d5dd);
      border-radius: 0.5rem;
      background: var(--wizard-surface-hover, #fff);
      cursor: pointer;
      text-align: left;
    }

    .step-indicator-item:disabled {
      cursor: not-allowed;
      opacity: 0.55;
    }

    .step-indicator-item--active {
      border-color: var(--wizard-primary, #155eef);
      background: var(--wizard-primary-surface, #eff4ff);
    }

    .step-indicator-item--completed:not(.step-indicator-item--active) {
      border-color: var(--wizard-border-strong, #98a2b3);
    }

    .step-indicator-index {
      font-size: 0.75rem;
      color: var(--wizard-text-muted, #667085);
      font-weight: 600;
    }

    .step-indicator-label {
      font-size: 0.8125rem;
      color: var(--wizard-text, #101828);
      font-weight: 600;
      white-space: nowrap;
    }

    .wizard-content {
      flex: 1 1 auto;
      min-height: 0;
      overflow-y: auto;
      padding-right: 0.25rem;
    }

    .wizard-nav {
      display: flex;
      justify-content: space-between;
      gap: 0.75rem;
    }

    .wizard-nav button {
      padding: 0.5rem 1rem;
      border-radius: 0.375rem;
      cursor: pointer;
    }

    .wizard-nav button:disabled {
      cursor: not-allowed;
      opacity: 0.55;
    }

    .wizard-nav button[data-testid='wizard-back'] {
      border: 1px solid var(--wizard-border, #d0d5dd);
      background: var(--wizard-surface-hover, #fff);
      color: var(--wizard-text-secondary, #344054);
    }

    .wizard-nav button[data-testid='wizard-next'] {
      border: none;
      background: var(--wizard-primary, #155eef);
      color: var(--wizard-on-primary, #fff);
    }
  `,
})
export class WizardShellComponent {
  protected readonly wizardState = inject(WizardStateService);
  protected readonly stepIds = WizardStepId;

  readonly portfolioNumber = input.required<string>();

  readonly orderReady = output<OrderCreationPayload>();
  readonly cancelled = output<void>();

  stepLabel(step: WizardStepId): string {
    return WIZARD_STEP_LABELS[step];
  }

  isCompleted(step: WizardStepId): boolean {
    return this.wizardState.state().completedSteps.includes(step);
  }

  canGoTo(step: WizardStepId): boolean {
    const current = this.wizardState.state();
    return current.completedSteps.includes(step) || current.currentStep === step;
  }

  canGoBack(): boolean {
    const visible = this.wizardState.visibleSteps();
    const index = visible.indexOf(this.wizardState.state().currentStep);
    return index > 0;
  }

  canGoForward(): boolean {
    const state = this.wizardState.state();
    const visible = this.wizardState.visibleSteps();
    const index = visible.indexOf(state.currentStep);
    return (
      index >= 0 &&
      index < visible.length - 1 &&
      this.wizardState.isStepComplete(state.currentStep)
    );
  }

  goToStep(step: WizardStepId): void {
    if (!this.wizardState.state().completedSteps.includes(step)) {
      return;
    }
    this.wizardState.goTo(step);
  }

  back(): void {
    this.wizardState.back();
  }

  next(): void {
    this.wizardState.next();
  }
}
