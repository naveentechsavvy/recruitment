import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { API_BASE_URL } from '../../core/config/api.config';

interface PreonboardingItem { code: string; kind: 'DETAIL' | 'DOCUMENT'; label: string; mandatory: boolean; }
interface PreonboardingView { candidateName: string; clientName: string; status: string; dueAt: string; otpVerified: boolean; items: PreonboardingItem[]; }
interface UploadedDocument { id: number; itemCode: string; originalName: string; reviewStatus: string; reviewComment?: string; }
@Component({
 selector: 'app-preonboarding-page', standalone: true, imports: [FormsModule],
 styles: [`:host{display:block;min-height:100vh;background:#f6f3ef;padding:32px 16px;box-sizing:border-box;color:#35271f}.wrap{max-width:720px;margin:auto}.card{background:white;border:1px solid #e8e1d9;border-radius:14px;padding:26px;box-shadow:0 8px 24px #35271f0f}h1{font-size:24px;margin:0 0 12px}p{line-height:1.5}.muted{color:#776b64;font-size:13px}input,textarea{display:block;width:100%;box-sizing:border-box;padding:11px;border:1px solid #d6cdc3;border-radius:8px;margin:8px 0 16px;font:inherit}textarea{min-height:90px}button{border:0;border-radius:8px;padding:12px 18px;background:#f4623a;color:white;font-weight:700;cursor:pointer;margin:4px}button:disabled{opacity:.5}.error{color:#b42318}.success{color:#167a42}.field{margin-top:18px;padding-top:12px;border-top:1px solid #eee}.required{color:#b42318}.doc{padding:12px;border:1px solid #eee;border-radius:8px;margin:10px 0}.doc small{display:block;color:#776b64}`],
 template:`<main class="wrap"><div class="card"><p class="muted">{{view?.clientName || 'Candidate portal'}}</p>
 @if(loading){<p>Loading your secure pre-onboarding page…</p>}@else if(error && !view){<h1>Link unavailable</h1><p class="error">{{error}}</p>}@else if(view){
 <h1>Welcome, {{view.candidateName}}</h1><p>Please complete the information requested by HR before {{view.dueAt}}.</p>
 @if(!view.otpVerified){<div class="field"><button (click)="sendOtp()" [disabled]="busy">Send email OTP</button><label>6-digit verification code</label><input [(ngModel)]="otp" maxlength="6" inputmode="numeric" placeholder="Enter OTP"><button (click)="verifyOtp()" [disabled]="busy || otp.length!==6">Verify email</button></div>}
 @if(view.otpVerified && ['INVITED','IN_PROGRESS','CORRECTION_REQUIRED'].includes(view.status)){<p class="success">Email verified.</p>
 <h2>Personal details</h2>@for(item of detailItems; track item.code){<label>{{item.label}} @if(item.mandatory){<span class="required">*</span>}</label><textarea [(ngModel)]="details[item.code]" [placeholder]="item.label"></textarea>}
 <h2>Required documents</h2>@for(item of documentItems; track item.code){<div class="doc"><strong>{{item.label}} @if(item.mandatory){<span class="required">*</span>}</strong><input type="file" accept=".pdf,.jpg,.jpeg,.png" (change)="upload(item.code,$event)" [disabled]="busy"><small>PDF, JPG or PNG. Maximum 10 MB.</small>@for(doc of docsFor(item.code);track doc.id){<small>{{doc.originalName}} — {{doc.reviewStatus}} @if(doc.reviewComment){: {{doc.reviewComment}}}</small>}</div>}
 <button (click)="submit()" [disabled]="busy">Submit details to HR</button>}
 @if(view.status==='SUBMITTED'){<p class="success">Your information has been submitted. Please wait while HR reviews it.</p>}
 @if(view.status==='COMPLETED'){<p class="success">Your pre-onboarding has been approved. Thank you.</p>}
 @if(view.status==='EXPIRED'){<p class="error">Your submission deadline has passed. Please contact HR.</p>}
 <p class="muted">Current status: {{view.status}}</p>}
 @if(message){<p class="success">{{message}}</p>}@if(error && view){<p class="error">{{error}}</p>}
 </div></main>`
})
export class PreonboardingPageComponent {
 private http=inject(HttpClient); private route=inject(ActivatedRoute); private token=this.route.snapshot.paramMap.get('token')||'';
 private base=`${API_BASE_URL}/api/public/preonboarding/${encodeURIComponent(this.token)}`;
 view:PreonboardingView|null=null;loading=true;busy=false;otp='';details:Record<string,string>={};documents:UploadedDocument[]=[];error='';message='';
 get detailItems(){return (this.view?.items||[]).filter(i=>i.kind==='DETAIL');} get documentItems(){return (this.view?.items||[]).filter(i=>i.kind==='DOCUMENT');}
 constructor(){this.load();}
 load(){this.http.get<PreonboardingView>(this.base).subscribe({next:v=>{this.view=v;this.loading=false;if(v.otpVerified)this.loadDocuments();},error:e=>{this.error=e?.error?.message||'This invitation is invalid or expired.';this.loading=false;}});}
 loadDocuments(){this.http.get<UploadedDocument[]>(`${this.base}/documents`).subscribe({next:v=>this.documents=v,error:()=>{}});}
 docsFor(code:string){return this.documents.filter(d=>d.itemCode===code);}
 sendOtp(){this.run(this.http.post<PreonboardingView>(`${this.base}/otp`,{}),'A verification code was sent to your registered email.');}
 verifyOtp(){this.run(this.http.post<PreonboardingView>(`${this.base}/verify`,{otp:this.otp}),'Email verified successfully.');}
 upload(code:string,event:Event){const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;if(file.size>10*1024*1024){this.error='Maximum file size is 10 MB.';return;}const data=new FormData();data.append('itemCode',code);data.append('file',file);this.busy=true;this.error='';this.http.post<UploadedDocument>(`${this.base}/documents`,data).subscribe({next:d=>{this.documents=[...this.documents,d];this.message=`${d.originalName} uploaded.`;this.busy=false;},error:e=>{this.error=e?.error?.message||'Document upload failed.';this.busy=false;}});}
 submit(){for(const item of this.detailItems){if(item.mandatory&&!this.details[item.code]?.trim()){this.error=`Please enter ${item.label}.`;return;}}this.run(this.http.post<PreonboardingView>(`${this.base}/submit`,{details:JSON.stringify(this.details)}),'Your details were submitted to HR.');}
 private run(req:any,success:string){this.busy=true;this.error='';this.message='';req.subscribe({next:(v:PreonboardingView)=>{this.view=v;this.message=success;this.busy=false;},error:(e:any)=>{this.error=e?.error?.message||'Something went wrong. Please try again.';this.busy=false;}});}
}
