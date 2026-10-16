import { Component, ElementRef, HostListener, OnDestroy, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ClientConfiguration } from '../../../core/models/client-configuration.model';
import { AuthService } from '../../../core/services/auth.service';
import { ClientConfigurationService } from '../../../core/services/client-configuration.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';

type DialogKind = 'delete' | 'freeze' | 'unfreeze';

@Component({
  selector: 'app-clients',
  imports: [RouterLink, DatePipe, StatusBadgeComponent],
  templateUrl: './clients.component.html',
  styles: `
    .actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; min-width: 250px; }
    .notice { margin-bottom: 16px; }
    .menu {
      position: fixed; z-index: 1000; min-width: 190px; padding: 6px; background: #fff;
      border: 1px solid #e5e7eb; border-radius: 10px; box-shadow: 0 10px 30px rgba(15, 23, 42, 0.18);
      display: flex; flex-direction: column; gap: 2px;
    }
    .menu a, .menu button {
      display: block; width: 100%; text-align: left; padding: 9px 12px; border: 0; border-radius: 8px;
      background: transparent; color: #111827; font: inherit; font-size: 14px; cursor: pointer; text-decoration: none;
    }
    .menu a:hover, .menu button:hover, .menu a:focus-visible, .menu button:focus-visible { background: #f3f4f6; outline: none; }
    .menu .danger { color: #b91c1c; }
    .menu .danger:hover, .menu .danger:focus-visible { background: #fee2e2; }
    .backdrop {
      position: fixed; inset: 0; z-index: 1100; background: rgba(15, 23, 42, 0.5);
      display: flex; align-items: center; justify-content: center; padding: 16px;
    }
    .dialog { width: 100%; max-width: 460px; background: #fff; border-radius: 14px; padding: 24px; box-shadow: 0 20px 50px rgba(15, 23, 42, 0.3); }
    .dialog h2 { margin: 0 0 8px; font-size: 18px; }
    .dialog p { margin: 0 0 12px; color: #4b5563; line-height: 1.5; }
    .dialog .buttons { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
  `,
})
export class ClientsComponent implements OnDestroy {
  private service = inject(ClientConfigurationService);
  private host = inject(ElementRef<HTMLElement>);

  /** Only the admin manages client portals. Everyone else just looks. */
  canManage = inject(AuthService).hasRole(['ADMIN']);

  clients: ClientConfiguration[] = [];
  loading = true;
  loadError = false;

  /** Success or error message shown above the table. */
  notice: { type: 'success' | 'error'; text: string } | null = null;

  /** The Settings menu: which client it is open for, and where to draw it. */
  menuFor: ClientConfiguration | null = null;
  menuPos = { top: 0, right: 0 };

  /** The confirmation dialog (delete, freeze or unfreeze). */
  dialog: { kind: DialogKind; client: ClientConfiguration } | null = null;
  busy = false;
  dialogError = '';

  private closeOnScroll = () => this.closeMenu();

  constructor() {
    this.load();
    // The page scrolls inside its own container, so listen for scrolling anywhere (capture phase).
    window.addEventListener('scroll', this.closeOnScroll, true);
  }

  ngOnDestroy() {
    window.removeEventListener('scroll', this.closeOnScroll, true);
  }

  load() {
    this.loading = true;
    this.loadError = false;
    this.service.getClients().subscribe({
      next: (list) => { this.clients = list; this.loading = false; }, // backend already sorts newest first
      error: () => { this.loading = false; this.loadError = true; },
    });
  }

  // ---------------------------------------------------------------- Settings menu

  toggleMenu(event: MouseEvent, c: ClientConfiguration) {
    event.stopPropagation(); // so the click-outside handler below does not close it straight away
    if (this.menuFor?.id === c.id) { this.closeMenu(); return; }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menuPos = { top: rect.bottom + 6, right: Math.max(window.innerWidth - rect.right, 8) };
    this.menuFor = c;
  }

  closeMenu() {
    this.menuFor = null;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event) {
    // A click anywhere outside the menu closes it. (Clicks on menu items close it themselves.)
    const menu = (this.host.nativeElement as HTMLElement).querySelector('.menu');
    if (this.menuFor && menu && !menu.contains(event.target as Node)) this.closeMenu();
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.dialog && !this.busy) this.cancelDialog();
    this.closeMenu();
  }

  @HostListener('window:resize')
  onResize() {
    this.closeMenu();
  }

  // ---------------------------------------------------------------- Delete / Freeze / Unfreeze dialog

  ask(kind: DialogKind, c: ClientConfiguration) {
    this.closeMenu();
    this.notice = null;
    this.dialogError = '';
    this.dialog = { kind, client: c };
  }

  cancelDialog() {
    if (this.busy) return;
    this.dialog = null;
    this.dialogError = '';
  }

  get dialogTitle(): string {
    if (!this.dialog) return '';
    const name = this.dialog.client.clientName;
    return { delete: `Delete "${name}"?`, freeze: `Freeze "${name}"?`, unfreeze: `Unfreeze "${name}"?` }[this.dialog.kind];
  }

  get dialogText(): string {
    if (!this.dialog) return '';
    return {
      delete: 'This also permanently deletes its hiring managers, job requirements, candidates and resumes. This cannot be undone.',
      freeze: 'A frozen client portal keeps all its data, but takes no new applications and its candidates cannot be changed until you unfreeze it. Other client portals are not affected.',
      unfreeze: 'The client portal will accept applications and changes again.',
    }[this.dialog.kind];
  }

  get dialogButton(): string {
    if (!this.dialog) return '';
    const label = { delete: 'Delete', freeze: 'Freeze', unfreeze: 'Unfreeze' }[this.dialog.kind];
    const busyLabel = { delete: 'Deleting…', freeze: 'Freezing…', unfreeze: 'Unfreezing…' }[this.dialog.kind];
    return this.busy ? busyLabel : label;
  }

  confirm() {
    const d = this.dialog;
    if (!d || this.busy) return;
    const name = d.client.clientName;
    const call: Observable<unknown> =
      d.kind === 'delete' ? this.service.deleteClient(d.client.id)
      : d.kind === 'freeze' ? this.service.freezeClient(d.client.id)
      : this.service.unfreezeClient(d.client.id);
    const done = { delete: 'was deleted.', freeze: 'was frozen.', unfreeze: 'was unfrozen.' }[d.kind];

    this.busy = true;
    this.dialogError = '';
    call.subscribe({
      next: () => {
        this.busy = false;
        this.dialog = null;
        this.notice = { type: 'success', text: `"${name}" ${done}` };
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.busy = false;
        this.dialogError = e.status === 403
          ? 'Only an admin can do this.'
          : e.error?.message ?? 'Something went wrong. Please try again.';
      },
    });
  }
}
