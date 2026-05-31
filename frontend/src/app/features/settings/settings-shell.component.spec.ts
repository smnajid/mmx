import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { SettingsShellComponent } from './settings-shell.component';
import { SETTINGS_ROUTES } from './settings.routes';

describe('SettingsShellComponent', () => {
  let fixture: ComponentFixture<SettingsShellComponent>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsShellComponent],
      providers: [
        provideRouter([
          {
            path: 'settings',
            component: SettingsShellComponent,
            children: SETTINGS_ROUTES,
          },
        ]),
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(SettingsShellComponent);
  });

  it('highlights Currencies sub-nav', async () => {
    await router.navigateByUrl('/settings/currencies');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toContain('/settings/currencies');
    const links = fixture.nativeElement.querySelectorAll('.settings-hub-nav a') as NodeListOf<HTMLAnchorElement>;
    expect(links[0].getAttribute('href')).toContain('/settings/currencies');
    expect(links[1].getAttribute('href')).toContain('/settings/institutions');
    expect(links[2].getAttribute('href')).toContain('/settings/term-rates');
    expect(links[3].getAttribute('href')).toContain('/settings/oncall-rates');
    expect(router.isActive('/settings/currencies', false)).toBe(true);
    expect(router.isActive('/settings/institutions', false)).toBe(false);
  });

  it('highlights Term rates sub-nav', async () => {
    await router.navigateByUrl('/settings/term-rates');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toContain('/settings/term-rates');
    expect(router.isActive('/settings/term-rates', false)).toBe(true);
  });
});
