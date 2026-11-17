import { useEffect, useState } from "react";
import { portal, type PageView, type RequestStatus, type RequestSummary } from "../api";
import { StatusPill } from "../components/Questions";
import type { Lang, Strings } from "../i18n";

const FILTERS: (RequestStatus | "")[] = ["", "PENDING_REVIEW", "AWAITING_INFO", "APPROVED", "REJECTED"];

/** Engineer's queue and everyone's request history. */
export function RequestsPage({ s, lang, navigate }: { s: Strings; lang: Lang; navigate: (path: string) => void }) {
  const [status, setStatus] = useState<RequestStatus | "">("PENDING_REVIEW");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageView<RequestSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      portal
        .list({ status, q, page })
        .then((d) => {
          setData(d);
          setError(null);
        })
        .catch((e) => setError(s.error + (e instanceof Error ? e.message : String(e))));
    }, 200);
    return () => clearTimeout(timer);
  }, [status, q, page, s]);

  const date = (iso: string) =>
    new Date(iso).toLocaleDateString(lang === "ar" ? "ar-SA-u-nu-latn" : "en-GB", { day: "numeric", month: "short" });

  return (
    <>
      <h1>{s.navRequests}</h1>
      <div className="toolbar">
        <div className="tabs" role="tablist">
          {FILTERS.map((f) => (
            <button key={f || "all"} role="tab" aria-selected={status === f} className={status === f ? "tab active" : "tab"}
              onClick={() => { setStatus(f); setPage(0); }}>
              {f ? (s as unknown as Record<string, string>)[`status${f}`] : s.all}
            </button>
          ))}
        </div>
        <input className="search" placeholder={s.search} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} dir="auto" />
      </div>

      {error && <p className="error">{error}</p>}
      {!data && !error && <p className="muted">{s.loading}</p>}
      {data && data.items.length === 0 && <p className="muted card">{s.empty}</p>}
      {data && data.items.length > 0 && (
        <div className="card table-card">
          <table>
            <thead>
              <tr>
                <th>{s.colTicket}</th>
                <th>{s.colRequester}</th>
                <th>{s.colConnection}</th>
                <th>{s.colService}</th>
                <th>{s.colScore}</th>
                <th>{s.colStatus}</th>
                <th>{s.colDate}</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((r) => (
                <tr key={r.id} onClick={() => navigate(`/requests/${r.id}`)} tabIndex={0}
                  onKeyDown={(e) => e.key === "Enter" && navigate(`/requests/${r.id}`)}>
                  <td className="ticket">{r.ticketNo}</td>
                  <td>{r.requesterName}<div className="sub">{r.department}</div></td>
                  <td dir="ltr" className="mono">{r.source}<div className="sub">→ {r.destination}</div></td>
                  <td dir="ltr" className="mono">{r.service}</td>
                  <td dir="ltr">{r.qualityScore?.toFixed(1) ?? "–"}</td>
                  <td><StatusPill s={s} status={r.status} /></td>
                  <td>{date(r.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.totalPages > 1 && (
            <div className="row pager">
              <button className="link" disabled={page === 0} onClick={() => setPage(page - 1)}>{s.prev}</button>
              <span className="muted">{page + 1} / {data.totalPages}</span>
              <button className="link" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>{s.next}</button>
            </div>
          )}
        </div>
      )}
    </>
  );
}
