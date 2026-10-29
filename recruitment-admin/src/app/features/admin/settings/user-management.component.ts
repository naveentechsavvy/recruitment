import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

const API_URL = 'http://localhost:8080/api';

interface ClientOption {
  id: number;
  name: string;
  slug?: string;
}

type PortalRole =
  | 'ADMIN'
  | 'RECRUITER'
  | 'HIRING_MANAGER'
  | 'MANAGER'
  | 'HR_BP'
  | 'CLIENT_ADMIN';

interface RoleAssignment {
  id: number;
  email: string;
  role: PortalRole;
  clientId: number;
  hasAccount: boolean;
  assignedBy?: string;
}

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-head">
      <div>
        <h1>User Management</h1>
        <p>Manage portal access and user invitations.</p>
      </div>
    </div>

    <section class="card user-management">
      <div class="section-head">
        <div>
          <h2>User Management</h2>
          <p>
            Assign a role to an email address. The same person can have a
            different role on each client. New users receive a link to set
            their own password.
          </p>
        </div>
      </div>

      <div class="notice" *ngIf="message" role="status">
        {{ message }}
      </div>

      <div class="error" *ngIf="error" role="alert">
        {{ error }}
      </div>

      <div class="form-grid">
        <label>
          <span>Client portal</span>
          <select
            [(ngModel)]="selectedClientId"
            (ngModelChange)="loadAssignments()"
            name="clientId"
          >
            <option [ngValue]="null">Select a client</option>
            <option
              *ngFor="let client of clients"
              [ngValue]="client.id"
            >
              {{ client.name }}
            </option>
          </select>
        </label>

        <label>
          <span>Email address</span>
          <input
            type="email"
            [(ngModel)]="email"
            name="email"
            placeholder="person@company.com"
            autocomplete="email"
          >
        </label>

        <label>
          <span>Role</span>
          <select [(ngModel)]="role" name="role">
            <option value="RECRUITER">Recruiter</option>
            <option value="HIRING_MANAGER">Hiring Manager</option>
            <option value="MANAGER">Manager</option>
            <option value="HR_BP">HR BP</option>
            <option value="CLIENT_ADMIN">Client Admin</option>
          </select>
        </label>

        <div class="form-action">
          <button
            type="button"
            class="btn primary"
            [disabled]="saving || !selectedClientId || !email.trim()"
            (click)="assignRole()"
          >
            {{ saving ? 'Sending…' : 'Assign role & invite' }}
          </button>
        </div>
      </div>

      <p class="hint">
        Admin accounts have access to all clients. Other roles are
        restricted by their assigned client and permitted recruitment stages.
      </p>
    </section>

    <section class="card assignments">
      <div class="section-head">
        <div>
          <h2>Assigned users</h2>
          <p>{{ selectedClientName }}</p>
        </div>

        <button
          type="button"
          class="btn"
          (click)="loadAssignments()"
          [disabled]="!selectedClientId || loading"
        >
          Refresh
        </button>
      </div>

      <div class="empty-state" *ngIf="!selectedClientId">
        Select a client to view its assigned users.
      </div>

      <div
        class="empty-state"
        *ngIf="selectedClientId && loading"
      >
        Loading users…
      </div>

      <div
        class="empty-state"
        *ngIf="selectedClientId && !loading && assignments.length === 0"
      >
        No role assignments for this client yet.
      </div>

      <div
        class="table-wrap"
        *ngIf="selectedClientId && !loading && assignments.length"
      >
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>Role</th>
              <th>Account</th>
              <th>Action</th>
            </tr>
          </thead>

          <tbody>
            <tr *ngFor="let user of assignments">
              <td>{{ user.email }}</td>

              <td>{{ roleLabel(user.role) }}</td>

              <td>
                <span
                  class="status"
                  [class.active]="user.hasAccount"
                >
                  {{ user.hasAccount ? 'Active account' : 'Invitation pending' }}
                </span>
              </td>

              <td>
                <button
                  type="button"
                  class="btn danger"
                  [disabled]="deletingId === user.id"
                  (click)="deleteAssignment(user)"
                >
                  {{ deletingId === user.id ? 'Removing…' : 'Delete' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
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

    .user-management,
    .assignments {
      padding: 24px;
      margin-bottom: 20px;
    }

    .section-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 20px;
    }

    h2 {
      font-size: 18px;
      margin: 0 0 6px;
      color: #35271f;
    }

    p {
      margin: 0;
      color: #776b64;
      line-height: 1.5;
    }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 18px;
    }

    label {
      display: flex;
      flex-direction: column;
      gap: 7px;
      font-size: 13px;
      font-weight: 600;
      color: #51433a;
    }

    input,
    select {
      width: 100%;
      box-sizing: border-box;
      border: 1px solid #ddd1c7;
      border-radius: 8px;
      padding: 11px 12px;
      background: #fff;
      font: inherit;
      color: #35271f;
      min-height: 42px;
    }

    .form-action {
      display: flex;
      align-items: flex-end;
    }

    .btn {
      border: 1px solid #ddd1c7;
      background: #fff;
      color: #493a31;
      border-radius: 8px;
      padding: 10px 14px;
      cursor: pointer;
      font: inherit;
      font-weight: 600;
    }

    .btn.primary {
      background: #f4623a;
      color: white;
      border-color: #f4623a;
    }

    .btn.danger {
      color: #a83220;
    }

    .btn.danger:hover:not(:disabled) {
      background: #fff0ed;
      border-color: #e8b4a9;
    }

    .btn:disabled {
      opacity: 0.55;
      cursor: not-allowed;
    }

    .hint {
      font-size: 12px;
      margin-top: 14px;
    }

    .notice,
    .error {
      padding: 11px 13px;
      border-radius: 8px;
      margin-bottom: 16px;
      font-size: 13px;
    }

    .notice {
      background: #edf8ef;
      color: #236331;
    }

    .error {
      background: #fff0ed;
      color: #a83220;
    }

    .empty-state {
      padding: 18px 0;
      color: #776b64;
      font-size: 14px;
    }

    .table-wrap {
      overflow: auto;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }

    th,
    td {
      text-align: left;
      padding: 13px 10px;
      border-bottom: 1px solid #eee4dc;
    }

    th {
      color: #776b64;
      font-weight: 600;
    }

    .status {
      display: inline-block;
      border-radius: 20px;
      padding: 4px 9px;
      background: #fff3df;
      color: #8a5b12;
      font-size: 12px;
    }

    .status.active {
      background: #edf8ef;
      color: #236331;
    }

    @media (max-width: 700px) {
      .form-grid {
        grid-template-columns: 1fr;
      }

      .user-management,
      .assignments {
        padding: 16px;
      }

      .section-head {
        flex-direction: column;
      }
    }
  `],
})
export class UserManagementComponent {
  private http = inject(HttpClient);

  clients: ClientOption[] = [];
  assignments: RoleAssignment[] = [];

  selectedClientId: number | null = null;
  email = '';
  role: PortalRole = 'RECRUITER';

  loading = false;
  saving = false;
  deletingId: number | null = null;

  message = '';
  error = '';

  constructor() {
    this.http.get<ClientOption[]>(`${API_URL}/clients`).subscribe({
      next: clients => {
        this.clients = clients;
      },
      error: () => {
        this.error =
          'Could not load clients. Please refresh or sign in again.';
      },
    });
  }

  get selectedClientName(): string {
    return (
      this.clients.find(c => c.id === this.selectedClientId)?.name ??
      'Select a client to manage access'
    );
  }

  loadAssignments(): void {
    this.message = '';
    this.error = '';
    this.assignments = [];

    if (!this.selectedClientId) return;

    this.loading = true;

    this.http
      .get<RoleAssignment[]>(
        `${API_URL}/clients/${this.selectedClientId}/roles`
      )
      .subscribe({
        next: rows => {
          this.assignments = rows;
          this.loading = false;
        },
        error: () => {
          this.error = 'Could not load assigned users.';
          this.loading = false;
        },
      });
  }

  assignRole(): void {
    if (!this.selectedClientId || !this.email.trim()) return;

    this.saving = true;
    this.message = '';
    this.error = '';

    this.http
      .post<RoleAssignment>(
        `${API_URL}/clients/${this.selectedClientId}/roles`,
        {
          email: this.email.trim(),
          role: this.role,
        }
      )
      .subscribe({
        next: () => {
          this.email = '';
          this.saving = false;
          // loadAssignments() clears message and error, so the message is set after it
          this.loadAssignments();
          this.message =
            'Role assigned. New users receive an invitation to set their password; existing users receive an access notification.';
        },
        error: err => {
          this.error =
            err?.error?.message ||
            err?.error?.error ||
            'Could not assign this role. Check the email, client and email-server settings.';
          this.saving = false;
        },
      });
  }

  deleteAssignment(user: RoleAssignment): void {
    if (!this.selectedClientId) return;

    if (
      !confirm(
        `Remove ${user.email} (${this.roleLabel(user.role)}) from ${this.selectedClientName}?`
      )
    ) {
      return;
    }

    const removedEmail = user.email;
    this.deletingId = user.id;
    this.message = '';
    this.error = '';

    this.http
      .delete<void>(
        `${API_URL}/clients/${this.selectedClientId}/roles/${user.id}`
      )
      .subscribe({
        next: () => {
          this.deletingId = null;
          // loadAssignments() clears message and error, so the message is set after it
          this.loadAssignments();
          this.message = `${removedEmail} has been removed from this client.`;
        },
        error: err => {
          this.error =
            err?.error?.message ||
            err?.error?.error ||
            'Could not remove this user.';
          this.deletingId = null;
        },
      });
  }

  roleLabel(role: PortalRole): string {
    return ({
      ADMIN: 'Admin',
      RECRUITER: 'Recruiter',
      HIRING_MANAGER: 'Hiring Manager',
      MANAGER: 'Manager',
      HR_BP: 'HR BP',
      CLIENT_ADMIN: 'Client Admin',
    } as Record<PortalRole, string>)[role];
  }
}