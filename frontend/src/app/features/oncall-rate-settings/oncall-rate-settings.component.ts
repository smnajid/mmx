import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AddOnCallRateRequest,
  OnCallNoticePeriod,
  OnCallRateSegment,
  OnCallRateSettingsApiService,
} from '../../core/api/oncall-rate-settings-api.service';
import {
  Institution,
  InstitutionSettingsApiService,
} from '../../core/api/institution-settings-api.service';
import { DeskReturnService } from '../../core/trader/desk-return.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import {
  curvePointKey,
  formatOnCallEndDate,
  groupOnCallRateSegmentsForReview,
  type OnCallCurvePointNode,
} from './group-oncall-rate-segments-for-review';

const NOTICE_PERIODS: OnCallNoticePeriod[] = ['24H', '48H'];

@Component({
  selector: 'app-oncall-rate-settings',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink],
  template: `
    <section class="settings-panel">
      <nav class="settings-toolbar">
        <a [routerLink]="deskReturn.getReturnUrl()" class="settings-back">← Back to desk</a>
      </nav>

      <header class="settings-header">
        <h1>OnCall rates</h1>
      </header>

      <p class="settings-lede">
        Maintain value-dated OnCall rate curves per institution. Pending segments price new orders
        immediately; back office confirmation refreshes in-life contracts.
      </p>

      <section class="settings-card" aria-labelledby="institution-heading">
        <h2 id="institution-heading" class="settings-card__title">Institution</h2>
        <label class="settings-field">
          <span class="settings-field__label">Select institution</span>
          <select
            class="settings-select"
            [ngModel]="selectedInstitutionCode()"
            (ngModelChange)="onInstitutionChange($event)"
            [disabled]="loading() || institutions().length === 0"
          >
            @if (institutions().length === 0) {
              <option value="">No institutions onboarded</option>
            } @else {
              @for (i of institutions(); track i.institutionCode) {
                <option [value]="i.institutionCode">{{ i.institutionCode }} — {{ i.displayName }}</option>
              }
            }
          </select>
        </label>
      </section>

      @if (selectedInstitutionCode()) {
        <section class="settings-card" aria-labelledby="add-heading">
          <h2 id="add-heading" class="settings-card__title">Add rate</h2>
          <form class="settings-form-grid" (ngSubmit)="submitAdd()">
            <label class="settings-field">
              <span class="settings-field__label">Currency</span>
              <input
                class="settings-input"
                type="text"
                [(ngModel)]="addForm.currency"
                name="currency"
                required
              />
            </label>
            <label class="settings-field">
              <span class="settings-field__label">Notice period</span>
              <select class="settings-select" [(ngModel)]="addForm.noticePeriod" name="noticePeriod">
                @for (np of noticePeriods; track np) {
                  <option [value]="np">{{ np }}</option>
                }
              </select>
            </label>
            <label class="settings-field">
              <span class="settings-field__label">Rate (%)</span>
              <input
                class="settings-input"
                type="number"
                step="0.0001"
                [(ngModel)]="addForm.rate"
                name="rate"
                required
              />
            </label>
            <label class="settings-field">
              <span class="settings-field__label">Value date</span>
              <input
                class="settings-input"
                type="date"
                [(ngModel)]="addForm.valueDate"
                name="valueDate"
                required
              />
            </label>
            <div class="settings-form-actions">
              <button type="submit" class="btn-primary" [disabled]="busy() || !canSubmitAdd()">
                Add rate
              </button>
            </div>
          </form>
        </section>

        <section class="settings-card" aria-labelledby="curve-heading">
          <h2 id="curve-heading" class="settings-card__title">Curve segments</h2>

          @if (loading()) {
            <p class="settings-state">Loading…</p>
          } @else if (reviewTree().length === 0) {
            <p class="settings-state">No rate segments for this institution yet.</p>
          } @else {
            <div class="settings-review-tree__toolbar">
              <button type="button" class="btn-secondary" (click)="expandAllReview()">Expand all</button>
              <button type="button" class="btn-secondary" (click)="collapseAllReview()">
                Collapse all
              </button>
            </div>
            <div class="settings-review-tree" data-testid="oncall-rates-review-tree">
              @for (point of reviewTree(); track curvePointKey(point.currency, point.noticePeriod)) {
                <details
                  class="settings-review-tree__curve-point"
                  [class.settings-review-tree__curve-point--selected]="
                    isCurvePointSelected(point.currency, point.noticePeriod)
                  "
                  [open]="isCurvePointOpen(point.currency, point.noticePeriod)"
                  (toggle)="onCurvePointToggle($event, point)"
                >
                  <summary>
                    <span class="settings-review-tree__curve-point-label"
                      >{{ point.currency }} · {{ point.noticePeriod }}</span
                    >
                    <span class="settings-review-tree__meta">{{ currentRateSummary(point) }}</span>
                  </summary>
                  <div class="settings-review-tree__body">
                    <table class="settings-review-tree__segment-table">
                      <thead>
                        <tr>
                          <th>Value date</th>
                          <th>End date</th>
                          <th>Rate</th>
                          <th>Status</th>
                          <th></th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (s of point.segments; track s.segmentId) {
                          <tr>
                            <td>{{ s.valueDate }}</td>
                            <td>{{ formatEndDate(s.endDate) }}</td>
                            <td>{{ s.rate | number: '1.4-4' }}</td>
                            <td>
                              <span class="status-badge" [class]="statusBadgeClass(s.status)">
                                {{ statusLabel(s.status) }}
                              </span>
                            </td>
                            <td>
                              @if (s.status === 'PENDING_CONFIRMATION') {
                                <button
                                  type="button"
                                  class="btn-secondary btn-compact"
                                  (click)="cancelSegment(s)"
                                  [disabled]="busy()"
                                >
                                  Cancel
                                </button>
                              }
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </details>
              }
            </div>
          }
        </section>
      }

      @if (error()) {
        <p class="settings-error" role="alert">{{ error() }}</p>
      }
      @if (success()) {
        <p class="settings-success" role="status">{{ success() }}</p>
      }
    </section>
  `,
  styles: `
    .settings-form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
      gap: 0.75rem 1rem;
      align-items: end;
    }

    .settings-form-actions {
      grid-column: 1 / -1;
    }

    .settings-field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .settings-field__label {
      font-family: var(--font-mono);
      font-size: 0.68rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
    }

    .settings-input,
    .settings-select {
      font-family: var(--font-mono);
      font-size: 0.85rem;
      padding: 0.45rem 0.55rem;
      border: 1px solid var(--mmx-border);
      border-radius: 4px;
      background: var(--mmx-bg);
      color: var(--mmx-text);
    }

    .btn-compact {
      font-size: 0.75rem;
      padding: 0.3rem 0.55rem;
    }

    .status-badge--pending {
      color: var(--mmx-warn, #e6a700);
      border-color: var(--mmx-warn, #e6a700);
      background: rgba(230, 167, 0, 0.12);
    }

    .status-badge--valid {
      color: var(--mmx-accent);
      border-color: var(--mmx-accent);
      background: var(--mmx-accent-dim);
    }

    .status-badge--canceled {
      color: var(--mmx-text-muted);
      border-color: var(--mmx-border);
      background: transparent;
    }
  `,
})
export class OnCallRateSettingsComponent implements OnInit {
  protected readonly deskReturn = inject(DeskReturnService);
  protected readonly noticePeriods = NOTICE_PERIODS;
  protected readonly curvePointKey = curvePointKey;
  protected readonly formatEndDate = formatOnCallEndDate;

  private readonly api = inject(OnCallRateSettingsApiService);
  private readonly institutionApi = inject(InstitutionSettingsApiService);
  private readonly trader = inject(TraderContextService);

  readonly institutions = signal<Institution[]>([]);
  readonly selectedInstitutionCode = signal('');
  readonly segments = signal<OnCallRateSegment[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  private readonly openCurvePoints = signal<ReadonlySet<string>>(new Set());
  private readonly selectedCurvePointKey = signal<string | null>(null);

  addForm: AddOnCallRateRequest = {
    currency: 'EUR',
    noticePeriod: '24H',
    rate: 0,
    valueDate: new Date().toISOString().slice(0, 10),
  };

  readonly reviewTree = computed(() => groupOnCallRateSegmentsForReview(this.segments()));

  ngOnInit(): void {
    this.institutionApi.list(this.trader.traderId()).subscribe({
      next: (list) => {
        this.institutions.set(list);
        if (list.length > 0) {
          this.selectedInstitutionCode.set(list[0].institutionCode);
          this.loadSegments(list[0].institutionCode);
        } else {
          this.loading.set(false);
        }
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Failed to load institutions');
        this.loading.set(false);
      },
    });
  }

  onInstitutionChange(code: string): void {
    this.selectedInstitutionCode.set(code);
    this.clearMessages();
    this.loadSegments(code);
  }

  canSubmitAdd(): boolean {
    return (
      !!this.addForm.currency.trim() &&
      this.addForm.rate > 0 &&
      !!this.addForm.valueDate &&
      !!this.selectedInstitutionCode()
    );
  }

  submitAdd(): void {
    if (!this.canSubmitAdd() || this.busy()) {
      return;
    }
    const code = this.selectedInstitutionCode();
    this.busy.set(true);
    this.clearMessages();
    this.api.add(this.trader.traderId(), code, { ...this.addForm }).subscribe({
      next: (segment) => {
        this.segments.update((list) => [...list, segment]);
        this.success.set('Rate added — awaiting back-office confirmation.');
        this.busy.set(false);
        this.reloadSegments(code);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to add rate');
        this.busy.set(false);
      },
    });
  }

  cancelSegment(segment: OnCallRateSegment): void {
    if (this.busy()) {
      return;
    }
    const code = this.selectedInstitutionCode();
    this.busy.set(true);
    this.clearMessages();
    this.api.cancel(this.trader.traderId(), code, segment.segmentId).subscribe({
      next: () => {
        this.success.set('Pending rate canceled.');
        this.busy.set(false);
        this.reloadSegments(code);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to cancel rate');
        this.busy.set(false);
      },
    });
  }

  isCurvePointOpen(currency: string, noticePeriod: OnCallNoticePeriod): boolean {
    return this.openCurvePoints().has(curvePointKey(currency, noticePeriod));
  }

  isCurvePointSelected(currency: string, noticePeriod: OnCallNoticePeriod): boolean {
    return this.selectedCurvePointKey() === curvePointKey(currency, noticePeriod);
  }

  expandAllReview(): void {
    const next = new Set<string>();
    for (const point of this.reviewTree()) {
      next.add(curvePointKey(point.currency, point.noticePeriod));
    }
    this.openCurvePoints.set(next);
  }

  collapseAllReview(): void {
    this.openCurvePoints.set(new Set());
    this.selectedCurvePointKey.set(null);
  }

  onCurvePointToggle(event: Event, point: OnCallCurvePointNode): void {
    const el = event.target as HTMLDetailsElement;
    const key = curvePointKey(point.currency, point.noticePeriod);
    const next = new Set(this.openCurvePoints());
    if (el.open) {
      next.add(key);
      this.selectedCurvePointKey.set(key);
      this.addForm.currency = point.currency;
      this.addForm.noticePeriod = point.noticePeriod;
    } else {
      next.delete(key);
    }
    this.openCurvePoints.set(next);
  }

  currentRateSummary(point: OnCallCurvePointNode): string {
    const open = point.currentSegment;
    if (!open) {
      return 'No open segment';
    }
    const rate = open.rate.toFixed(4);
    return `${rate} · ${this.statusLabel(open.status)}`;
  }

  statusLabel(status: OnCallRateSegment['status']): string {
    switch (status) {
      case 'PENDING_CONFIRMATION':
        return 'Pending confirmation';
      case 'VALID':
        return 'Valid';
      case 'CANCELED':
        return 'Canceled';
    }
  }

  statusBadgeClass(status: OnCallRateSegment['status']): string {
    switch (status) {
      case 'PENDING_CONFIRMATION':
        return 'status-badge status-badge--pending';
      case 'VALID':
        return 'status-badge status-badge--valid';
      case 'CANCELED':
        return 'status-badge status-badge--canceled';
    }
  }

  private loadSegments(institutionCode: string): void {
    this.loading.set(true);
    this.collapseAllReview();
    this.api.list(this.trader.traderId(), institutionCode).subscribe({
      next: (list) => {
        this.segments.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Failed to load segments');
        this.loading.set(false);
      },
    });
  }

  private reloadSegments(institutionCode: string): void {
    this.api.list(this.trader.traderId(), institutionCode).subscribe({
      next: (list) => this.segments.set(list),
    });
  }

  private clearMessages(): void {
    this.error.set(null);
    this.success.set(null);
  }
}
