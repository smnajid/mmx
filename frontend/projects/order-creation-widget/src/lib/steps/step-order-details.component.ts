import { Component, inject, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import type { OrderOperation } from '../models/order-creation-payload.model';
import { WizardStateService } from '../services/wizard-state.service';
import { isOnOrAfterMinSettlementDate, minSettlementDate } from '../utils/settlement-date';

const LIFECYCLE_OPERATIONS: OrderOperation[] = ['INCREASE', 'DECREASE', 'REDEMPTION'];

@Component({
  selector: 'mmx-step-order-details',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <section class="step-order-details">
      <h2 class="step-title">Order details</h2>

      <form [formGroup]="form" (ngSubmit)="submit()">
        <label class="field">
          <span>Amount</span>
          <input type="number" formControlName="amount" data-testid="details-amount" />
          @if (form.controls.amount.touched && form.controls.amount.hasError('min')) {
            <span class="field-error" data-testid="details-amount-error">
              Amount must be at least {{ minAmount() }}.
            </span>
          }
        </label>

        <label class="field">
          <span>Value date</span>
          <input
            type="date"
            formControlName="valueDate"
            [min]="minValueDate"
            data-testid="details-value-date"
          />
          @if (form.controls.valueDate.touched && form.controls.valueDate.hasError('minSettlement')) {
            <span class="field-error" data-testid="details-value-date-error">
              Value date must be at least two calendar days from today.
            </span>
          }
        </label>

        <label class="field">
          <span>Minimum rate (optional)</span>
          <input type="number" step="0.0001" formControlName="minimumRate" data-testid="details-minimum-rate" />
          @if (form.controls.minimumRate.touched && form.controls.minimumRate.invalid) {
            <span class="field-error" data-testid="details-minimum-rate-error">
              Enter a valid minimum rate.
            </span>
          }
        </label>

        @if (showSourceContractNumber()) {
          <label class="field">
            <span>Source contract number</span>
            <input
              type="text"
              formControlName="sourceContractNumber"
              data-testid="details-source-contract"
            />
          </label>
        }

        <button type="submit" data-testid="details-continue">Continue</button>
      </form>
    </section>
  `,
  styles: `
    .step-order-details {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .step-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
    }

    form {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
      font-size: 0.875rem;
      color: #344054;
    }

    .field input {
      padding: 0.5rem 0.75rem;
      border: 1px solid #d0d5dd;
      border-radius: 0.375rem;
    }

    .field-error {
      color: #b42318;
      font-size: 0.8125rem;
    }

    button[type='submit'] {
      align-self: flex-start;
      padding: 0.5rem 1rem;
      border: none;
      border-radius: 0.375rem;
      background: #155eef;
      color: #fff;
      cursor: pointer;
    }
  `,
})
export class StepOrderDetailsComponent implements OnInit {
  private readonly wizardState = inject(WizardStateService);
  private readonly fb = inject(FormBuilder);

  readonly stepComplete = output<void>();

  readonly minAmount = signal(0);
  readonly minValueDate = minSettlementDate();

  readonly form = this.fb.nonNullable.group({
    amount: [0, [Validators.required, Validators.min(0)]],
    valueDate: ['', Validators.required],
    minimumRate: this.fb.control<number | null>(null),
    sourceContractNumber: [''],
  });

  ngOnInit(): void {
    const state = this.wizardState.state();
    this.minAmount.set(state.operationMinAmount ?? 0);
    this.form.controls.amount.setValidators([
      Validators.required,
      Validators.min(this.minAmount()),
    ]);
    this.form.controls.valueDate.setValidators([
      Validators.required,
      (control) =>
        control.value && isOnOrAfterMinSettlementDate(control.value)
          ? null
          : { minSettlement: true },
    ]);
    this.form.patchValue({
      amount: state.amount ?? 0,
      valueDate: state.valueDate ?? '',
      minimumRate: state.minimumRate ?? null,
      sourceContractNumber: state.sourceContractNumber ?? '',
    });
  }

  showSourceContractNumber(): boolean {
    const operation = this.wizardState.state().operation;
    return !!operation && LIFECYCLE_OPERATIONS.includes(operation);
  }

  submit(): void {
    this.form.updateValueAndValidity();
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const { amount, valueDate, minimumRate, sourceContractNumber } = this.form.getRawValue();
    this.wizardState.setOrderDetails(
      amount,
      valueDate,
      minimumRate ?? undefined,
      sourceContractNumber || undefined,
    );
    this.wizardState.completeAndAdvance();
    this.stepComplete.emit();
  }
}
