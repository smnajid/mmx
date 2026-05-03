import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'mmx-confirm-dialog',
  standalone: true,
  template: `
    @if (open()) {
      <div class="backdrop" role="presentation" (click)="onBackdropClick($event)">
        <div
          class="panel"
          role="alertdialog"
          aria-modal="true"
          [attr.aria-labelledby]="titleId"
          (click)="$event.stopPropagation()"
        >
          <h2 class="title" [id]="titleId">{{ title() }}</h2>
          <p class="msg">{{ message() }}</p>
          <div class="row">
            <button type="button" class="btn secondary" (click)="cancel.emit()">{{ cancelLabel() }}</button>
            <button type="button" class="btn danger" (click)="confirm.emit()">{{ confirmLabel() }}</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 80;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      background: rgba(5, 8, 12, 0.72);
      backdrop-filter: blur(4px);
    }

    .panel {
      width: 100%;
      max-width: 400px;
      padding: 1.25rem 1.35rem;
      border-radius: 8px;
      border: 1px solid var(--mmx-border);
      background: var(--mmx-surface);
      box-shadow: 0 18px 48px rgba(0, 0, 0, 0.35);
    }

    .title {
      margin: 0 0 0.65rem;
      font-family: var(--font-display);
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--mmx-text);
    }

    .msg {
      margin: 0 0 1.15rem;
      font-size: 0.9rem;
      line-height: 1.45;
      color: var(--mmx-text-muted);
    }

    .row {
      display: flex;
      justify-content: flex-end;
      gap: 0.65rem;
      flex-wrap: wrap;
    }

    .btn {
      font-family: var(--font-mono);
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.55rem 1rem;
      border-radius: 4px;
      cursor: pointer;
      border: 1px solid var(--mmx-border);
      transition:
        background 0.2s ease,
        border-color 0.2s ease;
    }

    .btn.secondary {
      background: transparent;
      color: var(--mmx-text-muted);
    }

    .btn.secondary:hover {
      border-color: var(--mmx-text-muted);
      color: var(--mmx-text);
    }

    .btn.danger {
      background: rgba(248, 113, 113, 0.12);
      border-color: rgba(248, 113, 113, 0.45);
      color: #fecaca;
    }

    .btn.danger:hover {
      background: rgba(248, 113, 113, 0.2);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  readonly titleId = `mmx-confirm-${Math.random().toString(36).slice(2, 9)}`;

  readonly open = input(false);
  readonly title = input('Confirm');
  readonly message = input('');
  readonly confirmLabel = input('Confirm');
  readonly cancelLabel = input('Cancel');

  readonly confirm = output<void>();
  readonly cancel = output<void>();

  onBackdropClick(ev: MouseEvent): void {
    if (ev.target === ev.currentTarget) {
      this.cancel.emit();
    }
  }
}
