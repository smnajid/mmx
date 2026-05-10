import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OrderApiService } from './order-api.service';

describe('OrderApiService', () => {
  let service: OrderApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listExecutedTermOrders calls GET …/term/executed', () => {
    service.listExecutedTermOrders('t1', { page: 2, size: 10 }).subscribe();
    const req = httpMock.expectOne(
      '/api/v1/orders/term/executed?page=2&size=10'
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('X-Trader-Id')).toBe('t1');
    req.flush({ content: [], totalElements: 0, page: 2, size: 10 });
  });

  it('listExecutedOnCallOrders calls GET …/oncall/executed', () => {
    service.listExecutedOnCallOrders('t2').subscribe();
    const req = httpMock.expectOne('/api/v1/orders/oncall/executed');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('X-Trader-Id')).toBe('t2');
    req.flush({ content: [], totalElements: 0, page: 0, size: 20 });
  });
});
