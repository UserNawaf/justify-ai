import { useEffect, useState } from "react";
import { ApiError, FormError, portal, type FirewallRequest } from "../api";
import { Breadcrumb } from "../components/Layout";
import { Questions, Score, StatusPill } from "../components/Questions";
import type { Lang, Strings } from "../i18n";

export function RequestDetailPage({ s, lang, id }: { s: Strings; lang: Lang; id: string }) {
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
  const L = s as unknown as Record<string, string>;

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

  if (!r) return <p className={error ? "error" : "sub"}>{error ?? s.loading}</p>;
  const j = r.justification;
  const locale = lang === "ar" ? "ar-SA-u-nu-latn-ca-gregory" : "en-GB";
  const day = (iso?: string) => (iso ? new Date(`${iso.slice(0, 10)}T12:00:00`).toLocaleDateString(locale,
    { day: "numeric", month: "short", year: "numeric" }) : "");
  const when = (iso?: string) => (iso ? new Date(iso).toLocaleString(locale, { dateStyle: "medium", timeStyle: "short" }) : "");
  const ltr = (v?: string) => <bdi dir="ltr">{v}</bdi>;
  const hostIp = (host?: string, ip?: string) => (host ? `${host} (${ip})` : ip);

  const events: { label: string; at?: string; state: "done" | "current" | "rejected" }[] = [
    { label: s.evSubmitted, at: r.createdAt, state: "done" },
  ];
  if (r.status === "AWAITING_INFO") events.push({ label: s.evAwaiting, state: "current" });
  else events.push({ label: s.evChecked, state: r.status === "PENDING_REVIEW" ? "current" : "done" });
  if (r.status === "APPROVED") events.push({ label: s.evApproved, at: r.decidedAt, state: "done" });
  if (r.status === "REJECTED") events.push({ label: s.evRejected, at: r.decidedAt, state: "rejected" });

  return (
    <>
      <Breadcrumb s={s} current={r.ticketNo} />
      <div className="sheet">
        <div className="card-head row-head">
          <div>
            <h1>{s.newTitle}</h1>
            <p className="sub">{r.applicationName}</p>
          </div>
          <StatusPill s={s} status={r.status} />
        </div>
        <dl className="meta">
          <div><dt>{s.requestNo}</dt><dd className="ticket">{r.ticketNo}</dd></div>
          <div><dt>{s.status}</dt><dd>{L[`status${r.status}`]}</dd></div>
          <div><dt>{s.requestedBy}</dt><dd>{r.requesterName} · {ltr(r.employeeId)}<div className="muted-sm">{r.department}</div></dd></div>
          <div><dt>{s.requestDate}</dt><dd>{day(r.createdAt)}</dd></div>
        </dl>

        <div className="sheet-body">
          <section>
            <h2>{s.sec1}</h2>
            <dl className="kv">
              <dt>{s.requestTypeLabel}</dt><dd>{L[`type${r.requestType}`]}</dd>
              <dt>{s.priority}</dt><dd><span className={`prio prio-${r.priority.toLowerCase()}`}>{L[`prio${r.priority}`]}</span></dd>
              <dt>{s.applicationName}</dt><dd>{r.applicationName}</dd>
            </dl>
          </section>
          <section>
            <h2>{s.sec2}</h2>
            <dl className="kv">
              <dt>{s.sourceIp}</dt><dd className="mono">{ltr(hostIp(r.sourceHostname, r.sourceIp))}</dd>
              <dt>{s.destinationIp}</dt><dd className="mono">{ltr(hostIp(r.destinationHost, r.destinationIp))}</dd>
              <dt>{s.ports}</dt><dd className="mono">{ltr(`${r.protocol} ${r.ports}`)}</dd>
              <dt>{s.environment}</dt><dd>{L[`env${r.environment}`]}</dd>
            </dl>
          </section>
          <section>
            <h2>{s.sec3}</h2>
            {r.status === "AWAITING_INFO" && j.questions ? (
              <Questions key={j.round} s={s} questions={j.questions} round={j.round} maxRounds={j.maxRounds} busy={busy}
                onSubmit={(a) => run(() => portal.answer(r.id, a))} />
            ) : (
              <>
                <div className="row-between">
                  <span className="label">{s.justification}</span>
                  <Score s={s} value={j.qualityScore} />
                </div>
                {j.status === "INCOMPLETE" && <p className="sub">{s.incompleteHint}</p>}
                <pre dir="ltr">{j.standardizedText}</pre>
              </>
            )}
            {j.originalText && (
              <details>
                <summary>{s.original}</summary>
                <p dir="auto" className="original">{j.originalText}</p>
              </details>
            )}
          </section>
          <section>
            <h2>{s.sec4}</h2>
            <dl className="kv">
              <dt>{s.durationLabel}</dt>
              <dd>{r.durationType === "PERMANENT" ? s.durPERMANENT : <>{day(r.startDate)} {s.to} {day(r.endDate)}</>}</dd>
              <dt>{s.startDate}</dt><dd>{day(r.startDate)}</dd>
              {r.notes && <><dt>{s.notes}</dt><dd dir="auto">{r.notes}</dd></>}
            </dl>
          </section>
          <section>
            <h2>{s.timeline}</h2>
            <ol className="timeline">
              {events.map((e, i) => (
                <li key={i} className={e.state}>
                  <span className="dot" />
                  <span>{e.label}</span>
                  {e.at && <span className="muted-sm">{when(e.at)}</span>}
                </li>
              ))}
            </ol>
            {r.decidedBy && (
              <p className={`decision decision-${r.status.toLowerCase()}`}>
                <strong>{s.decidedBy}:</strong> {r.decidedBy}{r.decisionNote && <> · <span dir="auto">{r.decisionNote}</span></>}
              </p>
            )}
          </section>
        </div>

        {r.status === "PENDING_REVIEW" && (
          <div className="sheet-foot engineer">
            <h2>{s.engineerPanel}</h2>
            <div className="cols">
              <div className="field">
                <label htmlFor="eng-name">{s.engineerName} <span className="req">*</span></label>
                <input id="eng-name" value={engineer} onChange={(e) => setEngineer(e.target.value)} dir="auto" />
              </div>
              <div className="field">
                <label htmlFor="eng-note">{s.note}</label>
                <input id="eng-note" value={note} onChange={(e) => setNote(e.target.value)} dir="auto" />
              </div>
            </div>
            <div className="row-actions">
              <button className="btn primary" disabled={busy || !engineer.trim()} onClick={() => decide("APPROVED")}>{s.approve}</button>
              <button className="btn danger" disabled={busy || !engineer.trim() || !note.trim()} onClick={() => decide("REJECTED")}>
                {s.reject}
              </button>
            </div>
          </div>
        )}
        {error && <p className="error pad">{error}</p>}
      </div>
    </>
  );
}
