import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, Role } from '../../../core/services/auth.service';
import { LOGO_MARK } from '../../brand';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  email = this.auth.email;
  role = this.auth.role;
  navOpen = false;
  logoMark = LOGO_MARK;

  private allNav: { label: string; path: string; icon: string; exact: boolean; roles: Role[] }[] = [
    { label: 'Dashboard', path: '/admin/dashboard', icon: '▦', exact: false, roles: ['ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'MANAGER', 'HR_BP'] },
    { label: 'Clients', path: '/admin/clients', icon: '☰', exact: true, roles: ['ADMIN', 'RECRUITER', 'HIRING_MANAGER', 'MANAGER', 'HR_BP'] },
    { label: 'Create Client', path: '/admin/clients/create', icon: '＋', exact: true, roles: ['ADMIN'] },
    { label: 'Settings', path: '/admin/settings', icon: '⚙', exact: false, roles: ['ADMIN'] },
  ];
  // The menu only shows what the logged-in role may open.
  nav = this.allNav.filter((item) => this.auth.hasRole(item.roles));

  logout() { this.auth.logout(); this.router.navigateByUrl('/admin/login'); }
}
