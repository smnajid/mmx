import { HttpHeaders } from '@angular/common/http';

/** Trader-facing API calls require `X-User-Id`; role is session-carried on the server. */
export function userHeaders(userId: string): HttpHeaders {
  return new HttpHeaders({ 'X-User-Id': userId });
}
