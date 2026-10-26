import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { API_BASE_URL } from '../../../core/config/api.config';
import { portalColors } from '../../../core/models/client-configuration.model';
import { PublicApp } from '../../../core/models/public-app.model';
import { PublicAppService } from '../../../core/services/public-app.service';
import { siteSlug } from '../../../core/services/site-hiring.service';

/**
 * The "manage" area of ONE client's website (/app/:slug/manage/...). It wears that client's colours and logo,
 * and holds the hiring screens (requirements now, candidates next). The Admin Portal is not involved.
 */
@Component({
  selector: 'app-site-manage-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  styles: `
    :host { display: block; min-height: 100vh; background: var(--bg); }
    .manage { --primary-soft: color-mix(in srgb, var(--primary) 12%, #fff); min-height: 100vh; }
    .wrap { max-width: 1180px; margin: 0 auto; padding: 0 24px; }
    .bar { background: linear-gradient(135deg, var(--primary), var(--primary-dark)); color: var(--on-primary); }
    .bar-in { display: flex; align-items: center; gap: 28px; min-height: 64px; flex-wrap: wrap; padding-top: 8px; padding-bottom: 8px; }
    .bar a { color: inherit; text-decoration: none; }
    .brand { display: flex; align-items: center; gap: 12px; font-weight: 700; font-size: 17px; letter-spacing: -.01em; }
    .brand img, .mark { flex: none; width: 38px; height: 38px; border-radius: 9px; background: #fff; object-fit: contain; padding: 3px; box-shadow: 0 1px 4px rgba(0,0,0,.2); }
    .mark { display: flex; align-items: center; justify-content: center; font-weight: 700; color: var(--primary); }
    nav { display: flex; gap: 4px; }
    nav a { padding: 7px 14px; border-radius: 8px; font-weight: 600; font-size: 14px; opacity: .85; }
    nav a:hover { opacity: 1; background: color-mix(in srgb, var(--on-primary) 14%, transparent); }
    nav a.active { opacity: 1; background: color-mix(in srgb, var(--on-primary) 20%, transparent); }
    .back { margin-left: auto; font-size: 13.5px; font-weight: 600; opacity: .9; }
    .back:hover { opacity: 1; text-decoration: underline; text-underline-offset: 3px; }
    .content { padding-top: 32px; padding-bottom: 56px; }
    :host ::ng-deep .btn.primary { color: var(--on-primary); }
    /* space between the buttons at the top right of every manage screen */
    :host ::ng-deep .page-head > div:last-child { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
    @media (max-width: 560px) { .wrap { padding: 0 16px; } .bar-in { gap: 12px; } .back { margin-left: 0; } }
  `,
  template: `
    <div class="manage" [style.--primary]="colors.header" [style.--primary-dark]="colors.header2" [style.--on-primary]="colors.onHeader">
      <header class="bar">
        <div class="wrap bar-in">
          <a class="brand" [routerLink]="['/app', slug]">
            @if (logo) { <img [src]="logo" alt="" /> } @else { <span class="mark">{{ initial }}</span> }
            <span>{{ app?.applicationName || app?.name || 'Manage' }}</span>
          </a>
          <nav aria-label="Manage">
            <a [routerLink]="['/app', slug, 'manage', 'requirements']" routerLinkActive="active">Requirements</a>
          </nav>
          <a class="back" [routerLink]="['/app', slug]">← Back to website</a>
        </div>
      </header>
      <main class="wrap content"><router-outlet /></main>
    </div>
  `,
})
export class SiteManageLayoutComponent {
  slug = siteSlug(inject(ActivatedRoute));
  app: PublicApp | null = null;

  constructor() {
    // Only for the colours, logo and name. If it fails the screens still work with the default colours.
    inject(PublicAppService).getApplication(this.slug).subscribe({ next: (a) => (this.app = a), error: () => {} });
  }

  get colors() { return portalColors(this.app); }
  get logo() { return this.app?.logoUrl ? API_BASE_URL + this.app.logoUrl : null; }
  get initial() { return (this.app?.name ?? 'M').charAt(0).toUpperCase(); }
}
