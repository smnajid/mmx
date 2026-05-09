import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { TraderContextService } from './core/trader/trader-context.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        {
          provide: TraderContextService,
          useValue: { traderId: signal('test-trader'), setTraderId: (): void => {} },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should show MMx brand', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.name')?.textContent).toContain('MMx');
  });

  describe('desk navigation highlighting', () => {
    let fixture: ComponentFixture<App>;
    let router: Router;

    beforeEach(async () => {
      fixture = TestBed.createComponent(App);
      router = TestBed.inject(Router);
      fixture.detectChanges();
    });

    it('marks Term workspace and Assigned queue active from /term/assigned', async () => {
      await router.navigateByUrl('/term/assigned');
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const ws = el.querySelectorAll('.workspace-nav a');
      expect(ws[0].classList.contains('active')).toBe(false);
      expect(ws[1].classList.contains('active')).toBe(true);

      const q = el.querySelectorAll('.sub-nav a');
      expect(q[0].classList.contains('active')).toBe(false);
      expect(q[1].classList.contains('active')).toBe(true);
      expect(q[2].classList.contains('active')).toBe(false);
    });

    it('marks On-call and Executed active from order-details URL query params', async () => {
      await router.navigateByUrl(
        '/orders/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee?ws=oncall&queue=executed'
      );
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const ws = el.querySelectorAll('.workspace-nav a');
      expect(ws[0].classList.contains('active')).toBe(true);
      expect(ws[1].classList.contains('active')).toBe(false);

      const q = el.querySelectorAll('.sub-nav a');
      expect(q[2].classList.contains('active')).toBe(true);
    });

    it('does not mark workspace tabs active on order details without ws/queue', async () => {
      await router.navigateByUrl('/orders/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee');
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const ws = el.querySelectorAll('.workspace-nav a');
      expect(ws[0].classList.contains('active')).toBe(false);
      expect(ws[1].classList.contains('active')).toBe(false);
    });
  });
});
