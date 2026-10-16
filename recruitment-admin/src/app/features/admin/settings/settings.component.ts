
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page-head">
      <div>
        <h1>Settings</h1>
        <p>Manage portal settings and access.</p>
      </div>
    </div>

    <a class="settings-card" routerLink="/admin/settings/users">
      <div class="icon">♙</div>

      <div class="details">
        <h2>User Management</h2>
        <p>Manage users, roles, invitations, and client access.</p>
      </div>

      <span class="arrow">›</span>
    </a>
  `,
  styles: [`
    .page-head {
      margin-bottom: 24px;
    }

    .page-head h1 {
      margin: 0 0 6px;
      color: #35271f;
    }

    .page-head p {
      margin: 0;
      color: #776b64;
      line-height: 1.5;
    }

    .settings-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 24px;
      background: #fff;
      border: 1px solid #eee4dc;
      border-radius: 12px;
      text-decoration: none;
      color: inherit;
      cursor: pointer;
      transition: border-color 0.2s;
    }

    .settings-card:hover {
      border-color: #f4623a;
    }

    .icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      flex-shrink: 0;
      border-radius: 10px;
      background: #fff0e9;
      color: #f4623a;
      font-size: 25px;
    }

    .details {
      flex: 1;
    }

    .details h2 {
      margin: 0 0 6px;
      font-size: 18px;
      color: #35271f;
    }

    .details p {
      margin: 0;
      color: #776b64;
      line-height: 1.5;
    }

    .arrow {
      font-size: 28px;
      color: #776b64;
    }
  `],
})
export class SettingsComponent {}
