import { Injectable, signal } from '@angular/core';
import { ReceivedListView } from '../api/order-api.service';

@Injectable({ providedIn: 'root' })
export class ReceivedViewModeService {
  readonly mode = signal<ReceivedListView>('NEAR_TERM');

  setShowAll(value: boolean): void {
    this.mode.set(value ? 'ALL' : 'NEAR_TERM');
  }

  showAll(): boolean {
    return this.mode() === 'ALL';
  }
}
