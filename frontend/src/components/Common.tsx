import { useEffect, useRef, type ReactNode } from "react";
import { AlertCircle, ArrowUpRight, X } from "lucide-react";
import { Button } from "./ui/button";
import { ApiError } from "../lib/api";
export function ErrorBox({ error, retry }: { error: unknown; retry?: () => void }) {
  if (!error) return null;
  return <div className="notice error" role="alert"><AlertCircle size={18}/><div>{error instanceof Error ? error.message : "Something went wrong."}{error instanceof ApiError && Object.keys(error.fieldErrors).length > 0 && <ul>{Object.entries(error.fieldErrors).map(([key, value]) => <li key={key}>{key}: {value}</li>)}</ul>}{retry && <Button variant="ghost" size="small" onClick={retry}>Try again</Button>}</div></div>;
}
export function Loading() { return <div role="status" aria-label="Loading" className="loading"><div className="skeleton"/><div className="skeleton"/><div className="skeleton tall"/></div>; }
export function Empty({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) { return <div className="empty"><div className="empty-icon"><ArrowUpRight/></div><h2>{title}</h2><p>{children}</p>{action}</div>; }
export function PageHeader({ eyebrow, title, children, action }: { eyebrow?: string; title: string; children: ReactNode; action?: ReactNode }) { return <header className="page-header"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1><p className="muted">{children}</p></div>{action}</header>; }
export function Modal({ title, children, close }: { title: string; children: ReactNode; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { dialog.current?.showModal(); const node = dialog.current; return () => node?.close(); }, []);
  return <dialog ref={dialog} onCancel={close} className="modal" aria-label={title}><div className="modal-heading"><h2>{title}</h2><Button variant="ghost" onClick={close} aria-label="Close dialog"><X size={20}/></Button></div>{children}</dialog>;
}
export function Confirm({ title, children, confirm, close, pending, error }: { title: string; children: ReactNode; confirm: () => void; close: () => void; pending: boolean; error: unknown }) { return <Modal title={title} close={close}><p className="muted">{children}</p><ErrorBox error={error}/><div className="actions"><Button variant="secondary" onClick={close}>Cancel</Button><Button variant="destructive" disabled={pending} onClick={confirm}>{pending ? "Deleting…" : "Delete"}</Button></div></Modal>; }
export function Field({ label, error, children, hint }: { label: string; error?: string; children: ReactNode; hint?: string }) { return <label className="field"><span>{label}</span>{children}{hint && <small className="muted">{hint}</small>}{error && <small className="field-error">{error}</small>}</label>; }
