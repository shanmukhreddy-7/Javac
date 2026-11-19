/**
 * Wallet balance, ledger, and manual top-up/withdrawal request forms.
 * Reload the server response after submission instead of assuming a payment or credit succeeded.
 */
import { useState } from "react";
import { api } from "../services/api";
import { Feedback } from "../components/ui/Feedback";
import { useRemote } from "./useRemote";
import { money } from "./types";

interface WalletData {
  balance: number;
  transactions: { id:number; kind:string; amount:number; status:string; note:string; created_at:string }[];
  topups: { id:number; amount:number; utr:string; status:string; review_note:string; created_at:string }[];
  withdrawals: { id:number; amount:number; upi_id:string; status:string; review_note:string; created_at:string }[];
}
export default function Wallet() {
  const {data,error,reload}=useRemote<WalletData>("/api/wallet",20000);
  const [busy,setBusy]=useState(false),[message,setMessage]=useState(""),[actionError,setError]=useState("");
  // Send an optional private receipt with the UTR; submission stays pending until an admin reviews
  // it.
  async function topup(e:React.FormEvent<HTMLFormElement>){
    e.preventDefault();const form=e.currentTarget;const values=new FormData(form);const payload=new FormData();
    payload.set("amount",String(values.get("amount")));payload.set("utr",String(values.get("utr")));
    const proof=values.get("proof");if(proof instanceof File&&proof.size)payload.set("proof",proof);
    setBusy(true);setError("");setMessage("");
    try{await api("/api/wallet/topups","POST",payload);form.reset();setMessage("Top-up request submitted. Your balance will change after the admin verifies the payment.");await reload();}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  // The server reserves this amount; the form does not initiate a UPI payment.
  async function withdraw(e:React.FormEvent<HTMLFormElement>){
    e.preventDefault();const form=e.currentTarget;const values=new FormData(form);setBusy(true);setError("");setMessage("");
    try{await api("/api/wallet/withdrawals","POST",{amount:Number(values.get("amount")),upiId:String(values.get("upiId"))});form.reset();setMessage("Withdrawal request sent. The amount is reserved while the admin reviews it.");await reload();}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  return <section className="market-page wallet-page">
    <span className="eyebrow">YOUR JAVAC BALANCE</span><h1>Wallet.</h1>
    <p>Top-ups and withdrawals are reviewed manually. A request only changes status after admin review.</p>
    <Feedback message={error||actionError}/><Feedback message={message} success/>
    {data ? <>
      <div className="wallet-balance"><span>Available balance</span><strong>{money(Number(data.balance))}</strong><small>₹100 welcome credit is included in your transaction history.</small></div>
      <div className="wallet-forms">
        <section className="market-panel"><h2>Request a top-up</h2><p>Pay using the admin’s shared UPI details, then submit the payment UTR.</p>
          <form className="market-form" onSubmit={(e)=>void topup(e)}>
            <label>Amount (₹)<input name="amount" type="number" min="1" max="50000" step="0.01" required/></label>
            <label>Payment UTR / reference<input name="utr" minLength={6} maxLength={40} pattern="[A-Za-z0-9-]{6,40}" required placeholder="Enter the bank reference"/></label>
            <label>Payment screenshot (optional)<input name="proof" type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg"/></label>
            <button className="button" disabled={busy}>{busy?"Submitting…":"Submit for verification"}</button>
          </form>
        </section>
        <section className="market-panel"><h2>Request a withdrawal</h2><p>Enter your UPI ID. The requested amount is reserved while it is pending.</p>
          <form className="market-form" onSubmit={(e)=>void withdraw(e)}>
            <label>Amount (₹)<input name="amount" type="number" min="1" max="50000" step="0.01" required/></label>
            <label>Your UPI ID<input name="upiId" required maxLength={100} pattern="[A-Za-z0-9._-]{2,64}@[A-Za-z0-9]{2,32}" placeholder="name@bank"/></label>
            <button className="button secondary" disabled={busy}>{busy?"Submitting…":"Request withdrawal"}</button>
          </form>
        </section>
      </div>
      <section className="market-panel"><h2>Top-up requests</h2>{data.topups.length?data.topups.map(t=><article className="wallet-row" key={t.id}><div><strong>{money(Number(t.amount))}</strong><small>UTR {t.utr} · {new Date(t.created_at).toLocaleString()}</small>{t.review_note?<small>{t.review_note}</small>:null}</div><span className={`job-status status-${t.status.toLowerCase()}`}>{t.status}</span></article>):<p>No top-up requests yet.</p>}</section>
      <section className="market-panel"><h2>Withdrawal requests</h2>{data.withdrawals.length?data.withdrawals.map(w=><article className="wallet-row" key={w.id}><div><strong>{money(Number(w.amount))}</strong><small>{w.upi_id} · {new Date(w.created_at).toLocaleString()}</small>{w.review_note?<small>{w.review_note}</small>:null}</div><span className={`job-status status-${w.status.toLowerCase()}`}>{w.status}</span></article>):<p>No withdrawal requests yet.</p>}</section>
      <section className="market-panel"><h2>Transaction history</h2>{data.transactions.map(t=><article className="wallet-row" key={t.id}><div><strong>{t.kind.replaceAll("_"," ")}</strong><small>{t.note} · {new Date(t.created_at).toLocaleString()}</small></div><div className="wallet-amount"><strong className={Number(t.amount)<0?"amount-debit":"amount-credit"}>{Number(t.amount)>0?"+":""}{money(Number(t.amount))}</strong><span>{t.status}</span></div></article>)}</section>
    </>:!error?<p>Loading wallet…</p>:null}
  </section>;
}
