import { Component, input, output } from '@angular/core';
import { ALL_NOTICES, ALL_TENORS } from '../core/api/workspace-codes';
import type { NoticePeriodCode, TenorCode } from '../core/api/currency-settings-api.service';

@Component({
  selector: 'app-workspace-toggle-fieldset',
  standalone: true,
  template: `
    <fieldset>
      <legend>Enabled tenors</legend>
      @for (t of ALL_TENORS; track t) {
        @if (showTenor(t)) {
          <label class="chk">
            <input
              type="checkbox"
              [checked]="selectedTenors().includes(t)"
              [disabled]="tenorDisabled(t)"
              (change)="onTenorToggle(t, $event)"
            />
            {{ t }}
          </label>
        }
      }
    </fieldset>

    <fieldset>
      <legend>Enabled notice periods</legend>
      @for (n of ALL_NOTICES; track n) {
        @if (showNotice(n)) {
          <label class="chk">
            <input
              type="checkbox"
              [checked]="selectedNotices().includes(n)"
              [disabled]="noticeDisabled(n)"
              (change)="onNoticeToggle(n, $event)"
            />
            {{ n }}
          </label>
        }
      }
    </fieldset>
  `,
  styles: `
    fieldset {
      border: 1px solid var(--mmx-border);
      border-radius: 4px;
      padding: 0.75rem 1rem;
      margin: 0 0 1rem;
    }

    legend {
      font-family: var(--font-mono);
      font-size: 0.65rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mmx-text-muted);
      padding: 0 0.25rem;
    }

    .chk {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      margin-right: 0.85rem;
      font-size: 0.85rem;
    }
  `,
})
export class WorkspaceToggleFieldsetComponent {
  protected readonly ALL_TENORS = ALL_TENORS;
  protected readonly ALL_NOTICES = ALL_NOTICES;

  readonly hubTenors = input<TenorCode[]>(ALL_TENORS);
  readonly hubNotices = input<NoticePeriodCode[]>(ALL_NOTICES);
  readonly grantTenors = input<TenorCode[] | null>(null);
  readonly grantNotices = input<NoticePeriodCode[] | null>(null);
  readonly selectedTenors = input<TenorCode[]>([]);
  readonly selectedNotices = input<NoticePeriodCode[]>([]);
  readonly readOnly = input(false);
  readonly grantBounded = input(false);

  readonly tenorToggled = output<{ code: TenorCode; checked: boolean }>();
  readonly noticeToggled = output<{ code: NoticePeriodCode; checked: boolean }>();

  showTenor(t: TenorCode): boolean {
    if (!this.grantBounded()) {
      return this.hubTenors().includes(t);
    }
    const grant = this.grantTenors();
    return grant == null || grant.includes(t);
  }

  showNotice(n: NoticePeriodCode): boolean {
    if (!this.grantBounded()) {
      return this.hubNotices().includes(n);
    }
    const grant = this.grantNotices();
    return grant == null || grant.includes(n);
  }

  tenorDisabled(t: TenorCode): boolean {
    if (this.readOnly()) {
      return true;
    }
    if (!this.hubTenors().includes(t)) {
      return true;
    }
    if (this.grantBounded()) {
      const grant = this.grantTenors();
      return grant != null && !grant.includes(t);
    }
    return false;
  }

  noticeDisabled(n: NoticePeriodCode): boolean {
    if (this.readOnly()) {
      return true;
    }
    if (!this.hubNotices().includes(n)) {
      return true;
    }
    if (this.grantBounded()) {
      const grant = this.grantNotices();
      return grant != null && !grant.includes(n);
    }
    return false;
  }

  onTenorToggle(t: TenorCode, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.tenorToggled.emit({ code: t, checked });
  }

  onNoticeToggle(n: NoticePeriodCode, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.noticeToggled.emit({ code: n, checked });
  }
}
