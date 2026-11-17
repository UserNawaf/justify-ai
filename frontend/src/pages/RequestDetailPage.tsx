import { useEffect, useState } from "react";
import { ApiError, FormError, portal, type FirewallRequest } from "../api";
import { Questions, Score, StatusPill } from "../components/Questions";
import type { Lang, Strings } from "../i18n";

export function RequestDetailPage({ s, lang, id, navigate }: {
  s: Strings; lang: Lang; id: string; navigate: (path: string) => void;
}) {
  const [r, setR] = useState<FirewallRequest | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [engineer, setEngineer] = useState(() => {
    try {
      return localStorage.getItem("engineer") ?? "";
    } catch {
      return "";
    }
  });
  const [note, setNote] = useState("");

  useEffect(() => {
    portal.get(id).then(setR).catch((e) => setError(s.error + (e instanceof Error ? e.message : String(e))));
  }, [id, lang, s]);

  async function run(action: () => Promise<FirewallRequest>) {
    setBusy(true);
    setError(null);
    try {
      setR(await action());
    } catch (e) {
      if (e instanceof FormError) setError(e.errors.map((x) => x.message).join(" "));
      else if (e instanceof ApiError) setError(s.error + e.message);
      else setError(s.error + String(e));
    } finally {
      setBusy(false);
    }
  }

  function decide(decision: "APPROVED" | "REJECTED") {
    try {
      localStorage.setItem("engineer", engineer);
    } catch {
      /* storage unavailable */
    }
    run(() => portal.decide(id, decision, engineer, note));
  }

  if (!r) return <p className={error ? "error" : "muted"}>{error ?? s.loading}</p>;
  const j = r.justification;
  const when = (iso?: string) => (iso ? new Date(iso).toLocaleString(lang === "ar" ? "ar-SA-u-nu-latn" : "en-GB") : "");

  return (
    <>
      <button className="link back" onClick={() => navigate("/requests")}>{s.back}</button>
      <div className="row">
        <h1><span className="ticket">{r.ticketNo}</span></h1>
        <StatusPill s={s} status={r.status} />
      </div>

      <section className="card">
        <h2>{s.details}</h2>
        <dl className="details">
          <dt>{s.requesterName}</dt><dd>{r.requesterName} · {r.employeeId} · {r.department}</dd>
          <dt>{s.sourceHostname}</dt><dd dir="ltr" className="mono">{r.sourceHostname} ({r.sourceIp})</dd>
          <dt>{s.destinationHost}</dt>
          <dd dir="ltr" className="mono">{r.destinationHost}{r.destinationIp ? ` (${r.destinationIp})` : ""}</dd>
          <dt>{s.ports}</dt><dd dir="ltr" className="mono">{r.protocol} {r.ports}</dd>
          <dt>{s.duration}</dt>
          <dd>{r.durationType === "PERMANENT" ? s.permanent : `${s.temporary} · ${r.endDate}`}</dd>
          <dt>{s.colDate}</dt><dd>{when(r.createdAt)}</dd>
        </dl>
      </section>

      {r.status === "AWAITING_INFO" && j.questions && (
        <Questions key={j.round} s={s} questions={j.questions} round={j.round} maxRounds={j.maxRounds} busy={busy}
          onSubmit={(a) => run(() => portal.answer(r.id, a))} />
      )}

      {j.standardizedText && (
        <section className="card">
          <div className="row">
            <h2>{s.justification}</h2>
            <Score s={s} value={j.qualityScore} />
          </div>
          {j.status === "INCOMPLETE" && <p className="muted">{s.incompleteHint}</p>}
          <pre dir="ltr">{j.standardizedText}</pre>
          {j.originalText && (
            <details>
              <summary>{s.original}</summary>
              <p dir="auto" className="original">{j.originalText}</p>
            </details>
          )}
        </section>
      )}

      {r.status === "PENDING_REVIEW" && (
        <section className="card engineer">
          <h2>{s.engineerPanel}</h2>
          <div className="grid">
            <label>
              {s.engineerName}
              <input value={engineer} onChange={(e) => setEngineer(e.target.value)} dir="auto" />
            </label>
            <label>
              {s.note}
              <input value={note} onChange={(e) => setNote(e.target.value)} dir="auto" />
            </label>
          </div>
          <div className="row start">
            <button className="primary" disabled={busy || !engineer.trim()} onClick={() => decide("APPROVED")}>{s.approve}</button>
            <button className="danger" disabled={busy || !engineer.trim() || !note.trim()} onClick={() => decide("REJECTED")}>
              {s.reject}
            </button>
          </div>
        </section>
      )}

      {r.decidedBy && (
        <section className={`card decision decision-${r.status.toLowerCase()}`}>
          <p><strong>{s.decidedBy}:</strong> {r.decidedBy} · {when(r.decidedAt)}</p>
          {r.decisionNote && <p dir="auto">{r.decisionNote}</p>}
        </section>
      )}

      {error && <p className="error">{error}</p>}
    </>
  );
}
