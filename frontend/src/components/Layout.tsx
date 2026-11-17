import { useState, type ReactNode } from "react";
import { loadProfile, saveProfile, type Profile } from "../api";
import type { Strings } from "../i18n";
import { BuildingIcon, ChevronIcon, UserIcon } from "./Icons";

export function Breadcrumb({ s, current }: { s: Strings; current: string }) {
  return (
    <nav className="breadcrumb" aria-label="breadcrumb">
      <a href="#/requests">{s.home}</a>
      <ChevronIcon />
      <a href="#/new">{s.itServices}</a>
      <ChevronIcon />
      <span aria-current="page">{current}</span>
    </nav>
  );
}

export function ProfileDialog({ s, onClose }: { s: Strings; onClose: (p: Profile | null) => void }) {
  const [p, setP] = useState<Profile>(loadProfile);
  const set = (k: keyof Profile) => (e: { target: { value: string } }) => setP({ ...p, [k]: e.target.value });
  const ok = p.requesterName.trim() && p.employeeId.trim() && p.department.trim();
  return (
    <div className="overlay" role="presentation" onClick={() => onClose(null)}>
      <form className="dialog" role="dialog" aria-modal="true" aria-labelledby="profile-title"
        onClick={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault();
          if (!ok) return;
          saveProfile(p);
          onClose(p);
        }}>
        <h2 id="profile-title">{s.profileTitle}</h2>
        <p className="hint">{s.profileHint}</p>
        <div className="field"><label htmlFor="p-name">{s.requesterName} <span className="req">*</span></label>
          <input id="p-name" value={p.requesterName} onChange={set("requesterName")} dir="auto" autoFocus /></div>
        <div className="field"><label htmlFor="p-emp">{s.employeeId} <span className="req">*</span></label>
          <input id="p-emp" value={p.employeeId} onChange={set("employeeId")} dir="ltr" /></div>
        <div className="field"><label htmlFor="p-dep">{s.department} <span className="req">*</span></label>
          <input id="p-dep" value={p.department} onChange={set("department")} dir="auto" /></div>
        <div className="field"><label htmlFor="p-mail">{s.email} <span className="opt">{s.optional}</span></label>
          <input id="p-mail" type="email" value={p.email} onChange={set("email")} dir="ltr" /></div>
        <div className="actions row-actions">
          <button className="btn primary" disabled={!ok}>{s.save}</button>
          <button type="button" className="btn outline" onClick={() => onClose(null)}>{s.cancel}</button>
        </div>
      </form>
    </div>
  );
}

export function Shell({ s, path, onToggleLang, onProfile, children }: {
  s: Strings; path: string; onToggleLang: () => void; onProfile: () => void; children: ReactNode;
}) {
  const nav = [
    { path: "/new", label: s.navNew },
    { path: "/requests", label: s.navRequests },
    { path: "/check", label: s.navCheck },
  ];
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <a className="brand" href="#/new">
            <span className="brand-icon"><BuildingIcon /></span>
            <span>{s.portalName}</span>
          </a>
          <nav className="topnav">
            {nav.map((n) => (
              <a key={n.path} href={`#${n.path}`} className={path.startsWith(n.path) ? "active" : undefined}>{n.label}</a>
            ))}
          </nav>
          <button className="lang" onClick={onToggleLang}>{s.switchLang}</button>
          <button className="avatar" onClick={onProfile} aria-label={s.profileTitle} title={s.profileTitle}><UserIcon /></button>
        </div>
      </header>
      <main className="container">{children}</main>
    </>
  );
}
