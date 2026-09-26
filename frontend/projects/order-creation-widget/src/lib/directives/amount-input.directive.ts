import {
  Directive,
  ElementRef,
  forwardRef,
  HostListener,
  inject,
  Renderer2,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { formatAmountForInput, parseAmountInput } from '../utils/parse-amount-input';

@Directive({
  selector: 'input[mmxAmountInput]',
  standalone: true,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AmountInputDirective),
      multi: true,
    },
  ],
  host: {
    type: 'text',
    inputmode: 'decimal',
    autocomplete: 'off',
  },
})
export class AmountInputDirective implements ControlValueAccessor {
  private readonly el = inject(ElementRef<HTMLInputElement>);
  private readonly renderer = inject(Renderer2);

  private onChange: (value: number | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: number | null): void {
    const display = value == null ? '' : formatAmountForInput(value);
    this.renderer.setProperty(this.el.nativeElement, 'value', display);
  }

  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.renderer.setProperty(this.el.nativeElement, 'disabled', isDisabled);
  }

  @HostListener('input')
  onInput(): void {
    const raw = this.el.nativeElement.value;
    const trimmed = raw.trim();
    if (/[kmb]$/i.test(trimmed)) {
      this.commitValue(raw);
      return;
    }
    const parsed = parseAmountInput(raw);
    if (parsed !== null && /^[+-]?\d[\d,]*(\.\d+)?$/.test(trimmed)) {
      this.onChange(parsed);
    }
  }

  @HostListener('blur')
  onBlur(): void {
    this.commitValue(this.el.nativeElement.value);
    this.onTouched();
  }

  private commitValue(raw: string): void {
    const trimmed = raw.trim();
    if (!trimmed) {
      this.renderer.setProperty(this.el.nativeElement, 'value', '');
      this.onChange(null);
      return;
    }

    const parsed = parseAmountInput(raw);
    if (parsed === null) {
      return;
    }

    this.renderer.setProperty(this.el.nativeElement, 'value', formatAmountForInput(parsed));
    this.onChange(parsed);
  }
}
