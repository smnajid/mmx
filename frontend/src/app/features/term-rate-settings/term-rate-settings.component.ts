import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  TermRate,
  TermRateIngestError,
  TermRateRowError,
  TermRateSettingsApiService,
} from '../../core/api/term-rate-settings-api.service';
import { InstitutionSettingsApiService } from '../../core/api/institution-settings-api.service';
import { DeskReturnService } from '../../core/trader/desk-return.service';
import { TraderContextService } from '../../core/trader/trader-context.service';
import {
  groupTermRatesForReview,
  reviewCurrencyKey,
} from './group-term-rates-for-review';

const REPLACE_DAY_CONFIRM =
  'Re-upload will replace all rates for the selected trading day. Continue?';

@Component({
  selector: 'app-term-rate-settings',
  standalone: true,
  imports: [FormsModule, DecimalPipe, DatePipe, RouterLink],
  template: `
    <section class="settings-panel">
      <nav class="settings-toolbar">
        <a [routerLink]="deskReturn.getReturnUrl()" class="settings-back">← Back to desk</a>
      </nav>

      <header class="settings-header">
        <h1>Term rates</h1>
      </header>

      <p class="settings-lede">
        @if (trader.isClientRepresentative()) {
          Read-only view of hub term rate reference data.
        } @else {
          Morning reference sheet per trading day. Upload a CSV after editing the sample; re-upload
          replaces the entire day for that date.
        }
      </p>

      @if (trader.isTrader()) {
      <section class="settings-card" aria-labelledby="prepare-heading">
        <h2 id="prepare-heading" class="settings-card__title">Prepare</h2>
        <p class="settings-hint">
          Download a template with your institution and currency combinations, then fill in rates.
        </p>
        <button type="button" class="btn-secondary" (click)="downloadSample()" [disabled]="busy()">
          Download sample CSV
        </button>
      </section>

      <section class="settings-card" aria-labelledby="upload-heading">
        <h2 id="upload-heading" class="settings-card__title">Upload</h2>
        <p class="settings-hint">
          CSV columns:
          <span class="mono">tradingDate, institutionCode, currency, tenor, rate</span>.
        </p>
        <div class="settings-upload-row">
          <input
            type="file"
            class="settings-file-input"
            accept=".csv,text/csv"
            (change)="onFileSelected($event)"
          />
          <button type="button" class="btn-primary" (click)="upload()" [disabled]="!selectedFile() || busy()">
            Upload
          </button>
        </div>
        @if (uploadSuccess()) {
          <p class="settings-success" role="status">
            Uploaded {{ uploadSuccess()!.rowCount }} rate(s) for {{ uploadSuccess()!.tradingDate }}.
          </p>
        }
        @if (error() && rowErrors().length === 0) {
          <p class="settings-error" role="alert">{{ error() }}</p>
        }
        @if (rowErrors().length > 0) {
          <p class="settings-error" role="alert">{{ error() }}</p>
          <ul class="settings-row-errors" role="alert">
            @for (e of rowErrors(); track e.line + (e.field ?? '') + e.message) {
              <li>Line {{ e.line }}@if (e.field) { ({{ e.field }}) }: {{ e.message }}</li>
            }
          </ul>
        }
      </section>
      }

      <section class="settings-card" aria-labelledby="review-heading">
        <h2 id="review-heading" class="settings-card__title">Review</h2>

        @if (tradingDays().length > 0) {
          <div class="settings-day-chips" role="group" aria-label="Trading days with uploads">
            @for (d of tradingDays(); track d.tradingDate) {
              <button
                type="button"
                class="settings-day-chip"
                [class.settings-day-chip--active]="d.tradingDate === tradingDate()"
                (click)="selectTradingDay(d.tradingDate)"
              >
                {{ d.tradingDate }}
              </button>
            }
          </div>
        }

        <label class="settings-date-label">
          Trading date
          <input type="date" [ngModel]="tradingDate()" (ngModelChange)="onDateChange($event)" />
        </label>

        @if (daySummary(); as summary) {
          <p class="settings-day-summary" role="status">
            <strong>{{ summary.date }}</strong> · {{ summary.count }} rate(s) · last upload
            {{ summary.lastUpload | date: 'short' }}
          </p>
        }

        @if (loading()) {
          <p class="settings-state">Loading…</p>
        } @else if (rates().length === 0) {
          <p class="settings-state">No rates uploaded for this trading day.</p>
          <button type="button" class="btn-secondary" (click)="downloadSample()" [disabled]="busy()">
            Download sample CSV
          </button>
        } @else {
          <div class="settings-review-tree__toolbar">
            <button type="button" class="btn-secondary" (click)="expandAllReview()">Expand all</button>
            <button type="button" class="btn-secondary" (click)="collapseAllReview()">Collapse all</button>
          </div>
          <div class="settings-review-tree" data-testid="term-rates-review-tree">
            @for (inst of reviewTree(); track inst.institutionCode) {
              <details
                class="settings-review-tree__institution"
                [open]="isInstitutionOpen(inst.institutionCode)"
                (toggle)="onInstitutionToggle($event, inst.institutionCode)"
              >
                <summary>
                  <span class="settings-review-tree__inst-name">{{
                    institutionLabel(inst.institutionCode)
                  }}</span>
                  @if (hasInstitutionDisplayName(inst.institutionCode)) {
                    <span class="settings-review-tree__inst-code">{{ inst.institutionCode }}</span>
                  }
                  <span class="settings-review-tree__meta"
                    >{{ inst.currencies.length }} currencies · {{ inst.rateCount }} rates</span
                  >
                </summary>
                <div class="settings-review-tree__body">
                  @for (ccy of inst.currencies; track ccy.currency) {
                    <details
                      class="settings-review-tree__currency"
                      [open]="isCurrencyOpen(inst.institutionCode, ccy.currency)"
                      (toggle)="onCurrencyToggle($event, inst.institutionCode, ccy.currency)"
                    >
                      <summary>
                        <span class="settings-review-tree__currency-label">{{ ccy.currency }}</span>
                        <span class="settings-review-tree__meta">{{ ccy.tenors.length }} tenors</span>
                      </summary>
                      <div class="settings-review-tree__body">
                        <ul class="settings-review-tree__leaves">
                          @for (leaf of ccy.tenors; track leaf.tenor) {
                            <li class="settings-review-tree__leaf">
                              <span class="settings-review-tree__leaf-tenor">{{ leaf.tenor }}</span>
                              <span class="settings-review-tree__leaf-rate">{{
                                leaf.rate | number: '1.2-8'
                              }}</span>
                            </li>
                          }
                        </ul>
                      </div>
                    </details>
                  }
                </div>
              </details>
            }
          </div>
        }
      </section>
    </section>
  `,
})
export class TermRateSettingsComponent implements OnInit {
  protected readonly deskReturn = inject(DeskReturnService);

  private readonly api = inject(TermRateSettingsApiService);
  private readonly institutionApi = inject(InstitutionSettingsApiService);
  protected readonly trader = inject(TraderContextService);

  readonly tradingDate = signal(todayIso());
  readonly tradingDays = signal<{ tradingDate: string }[]>([]);
  readonly rates = signal<TermRate[]>([]);
  readonly institutionNames = signal<ReadonlyMap<string, string>>(new Map());
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly rowErrors = signal<TermRateRowError[]>([]);
  readonly uploadSuccess = signal<{ tradingDate: string; rowCount: number } | null>(null);
  readonly selectedFile = signal<File | null>(null);

  private readonly openInstitutions = signal<ReadonlySet<string>>(new Set());
  private readonly openCurrencies = signal<ReadonlySet<string>>(new Set());

  readonly reviewTree = computed(() => groupTermRatesForReview(this.rates()));

  readonly daySummary = computed(() => {
    const list = this.rates();
    if (list.length === 0) {
      return null;
    }
    const lastUpload = list.reduce((max, r) => (r.uploadedAt > max ? r.uploadedAt : max), list[0].uploadedAt);
    return {
      date: this.tradingDate(),
      count: list.length,
      lastUpload,
    };
  });

  ngOnInit(): void {
    this.loadTradingDays();
    this.loadRates();
  }

  protected institutionLabel(institutionCode: string): string {
    return this.institutionNames().get(institutionCode) ?? institutionCode;
  }

  protected hasInstitutionDisplayName(institutionCode: string): boolean {
    return this.institutionNames().has(institutionCode);
  }

  protected isInstitutionOpen(institutionCode: string): boolean {
    return this.openInstitutions().has(institutionCode);
  }

  protected isCurrencyOpen(institutionCode: string, currency: string): boolean {
    return this.openCurrencies().has(reviewCurrencyKey(institutionCode, currency));
  }

  protected expandAllReview(): void {
    const inst = new Set<string>();
    const ccy = new Set<string>();
    for (const node of this.reviewTree()) {
      inst.add(node.institutionCode);
      for (const currencyNode of node.currencies) {
        ccy.add(reviewCurrencyKey(node.institutionCode, currencyNode.currency));
      }
    }
    this.openInstitutions.set(inst);
    this.openCurrencies.set(ccy);
  }

  protected collapseAllReview(): void {
    this.openInstitutions.set(new Set());
    this.openCurrencies.set(new Set());
  }

  protected onInstitutionToggle(event: Event, institutionCode: string): void {
    const open = (event.target as HTMLDetailsElement).open;
    const next = new Set(this.openInstitutions());
    if (open) {
      next.add(institutionCode);
    } else {
      next.delete(institutionCode);
    }
    this.openInstitutions.set(next);
  }

  protected onCurrencyToggle(event: Event, institutionCode: string, currency: string): void {
    const open = (event.target as HTMLDetailsElement).open;
    const key = reviewCurrencyKey(institutionCode, currency);
    const next = new Set(this.openCurrencies());
    if (open) {
      next.add(key);
    } else {
      next.delete(key);
    }
    this.openCurrencies.set(next);
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
    this.uploadSuccess.set(null);
    this.rowErrors.set([]);
    this.error.set(null);
  }

  protected onDateChange(value: string): void {
    this.tradingDate.set(value);
    this.loadRates();
  }

  protected selectTradingDay(date: string): void {
    this.tradingDate.set(date);
    this.loadRates();
  }

  protected downloadSample(): void {
    this.busy.set(true);
    this.api.downloadSample(this.trader.traderId()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'term-rates-sample.csv';
        a.click();
        URL.revokeObjectURL(url);
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Failed to download sample');
        this.busy.set(false);
      },
    });
  }

  protected upload(): void {
    const file = this.selectedFile();
    if (!file) {
      return;
    }
    if (this.rates().length > 0 && !confirm(REPLACE_DAY_CONFIRM)) {
      return;
    }
    this.performUpload(file);
  }

  private performUpload(file: File): void {
    this.busy.set(true);
    this.uploadSuccess.set(null);
    this.rowErrors.set([]);
    this.error.set(null);
    this.api.upload(this.trader.traderId(), file).subscribe({
      next: (result) => {
        this.uploadSuccess.set({ tradingDate: result.tradingDate, rowCount: result.rowCount });
        this.tradingDate.set(result.tradingDate);
        this.loadTradingDays();
        this.loadRates();
        this.busy.set(false);
      },
      error: (err) => {
        this.busy.set(false);
        const body = err?.error as TermRateIngestError | undefined;
        if (body?.errors?.length) {
          this.rowErrors.set(body.errors);
          this.error.set(body.message ?? 'Upload validation failed');
        } else {
          this.error.set(body?.message ?? err?.message ?? 'Upload failed');
        }
      },
    });
  }

  private loadTradingDays(): void {
    this.api.listTradingDays(this.trader.traderId()).subscribe({
      next: (days) => this.tradingDays.set(days),
      error: () => this.tradingDays.set([]),
    });
  }

  private loadRates(): void {
    this.loading.set(true);
    this.error.set(null);
    this.collapseAllReview();
    const traderId = this.trader.traderId();
    const tradingDate = this.tradingDate();
    forkJoin({
      rates: this.api.listForDay(traderId, tradingDate),
      institutions: this.institutionApi.list(traderId),
    }).subscribe({
      next: ({ rates, institutions }) => {
        const names = new Map<string, string>();
        for (const inst of institutions) {
          names.set(inst.institutionCode, inst.displayName);
        }
        this.institutionNames.set(names);
        this.rates.set(rates);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Failed to load rates');
        this.loading.set(false);
      },
    });
  }
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
