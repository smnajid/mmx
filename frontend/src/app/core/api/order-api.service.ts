import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  OrderSummary,
  OrderDetails,
  ReceiveOrderRequest,
  ReceiveOrderResponse,
  UpdateOrderRequest,
  ExecuteOrderRequest,
  RejectOrderRequest,
} from '../models/order.model';

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
}

export interface PageParams {
  page?: number;
  size?: number;
}

const BASE_URL = '/api/v1/orders';

@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly http = inject(HttpClient);

  // ── Endpoint 1: Receive Order (Portfolio Management caller) ──────────────────

  receiveOrder(request: ReceiveOrderRequest): Observable<ReceiveOrderResponse> {
    return this.http.post<ReceiveOrderResponse>(BASE_URL, request);
  }

  // ── Endpoint 2: List Received Term Orders ────────────────────────────────────

  listReceivedTermOrders(
    traderId: string,
    params: PageParams = {}
  ): Observable<PagedResponse<OrderSummary>> {
    return this.http.get<PagedResponse<OrderSummary>>(
      `${BASE_URL}/term/received`,
      { headers: this.traderHeaders(traderId), params: this.pageParams(params) }
    );
  }

  // ── Endpoint 3: List Received OnCall Orders ──────────────────────────────────

  listReceivedOnCallOrders(
    traderId: string,
    params: PageParams = {}
  ): Observable<PagedResponse<OrderSummary>> {
    return this.http.get<PagedResponse<OrderSummary>>(
      `${BASE_URL}/oncall/received`,
      { headers: this.traderHeaders(traderId), params: this.pageParams(params) }
    );
  }

  // ── Endpoint 4: Get Order Details ────────────────────────────────────────────

  getOrderDetails(orderId: string, traderId: string): Observable<OrderDetails> {
    return this.http.get<OrderDetails>(`${BASE_URL}/${orderId}`, {
      headers: this.traderHeaders(traderId),
    });
  }

  // ── Endpoint 5: Assign Order ─────────────────────────────────────────────────

  assignOrder(orderId: string, traderId: string): Observable<OrderDetails> {
    return this.http.post<OrderDetails>(
      `${BASE_URL}/${orderId}/assign`,
      null,
      { headers: this.traderHeaders(traderId) }
    );
  }

  // ── Endpoint 6: Unassign Order ───────────────────────────────────────────────

  unassignOrder(orderId: string, traderId: string): Observable<OrderDetails> {
    return this.http.post<OrderDetails>(
      `${BASE_URL}/${orderId}/unassign`,
      null,
      { headers: this.traderHeaders(traderId) }
    );
  }

  // ── Endpoint 7: List Assigned Orders ────────────────────────────────────────

  listAssignedOrders(
    traderId: string,
    params: PageParams = {}
  ): Observable<PagedResponse<OrderSummary>> {
    return this.http.get<PagedResponse<OrderSummary>>(
      `${BASE_URL}/assigned`,
      { headers: this.traderHeaders(traderId), params: this.pageParams(params) }
    );
  }

  // ── Endpoint 8: Update Assigned Order ────────────────────────────────────────

  updateOrder(
    orderId: string,
    traderId: string,
    request: UpdateOrderRequest
  ): Observable<OrderDetails> {
    return this.http.put<OrderDetails>(`${BASE_URL}/${orderId}`, request, {
      headers: this.traderHeaders(traderId),
    });
  }

  // ── Endpoint 9: Execute Order ────────────────────────────────────────────────

  executeOrder(
    orderId: string,
    traderId: string,
    request: ExecuteOrderRequest
  ): Observable<OrderDetails> {
    return this.http.post<OrderDetails>(
      `${BASE_URL}/${orderId}/execute`,
      request,
      { headers: this.traderHeaders(traderId) }
    );
  }

  // ── Endpoint 10: Cancel Order ────────────────────────────────────────────────

  cancelOrder(orderId: string, traderId: string): Observable<OrderDetails> {
    return this.http.post<OrderDetails>(
      `${BASE_URL}/${orderId}/cancel`,
      null,
      { headers: this.traderHeaders(traderId) }
    );
  }

  // ── Endpoint 11: Reject Order ────────────────────────────────────────────────

  rejectOrder(
    orderId: string,
    traderId: string,
    request: RejectOrderRequest
  ): Observable<OrderDetails> {
    return this.http.post<OrderDetails>(
      `${BASE_URL}/${orderId}/reject`,
      request,
      { headers: this.traderHeaders(traderId) }
    );
  }

  // ── Private helpers ──────────────────────────────────────────────────────────

  private traderHeaders(traderId: string): HttpHeaders {
    return new HttpHeaders({ 'X-Trader-Id': traderId });
  }

  private pageParams(params: PageParams): HttpParams {
    let httpParams = new HttpParams();
    if (params.page !== undefined) {
      httpParams = httpParams.set('page', params.page.toString());
    }
    if (params.size !== undefined) {
      httpParams = httpParams.set('size', params.size.toString());
    }
    return httpParams;
  }
}
