import { useEffect, useState } from "react";
import { portal, type PageView, type RequestStatus, type RequestSummary } from "../api";
import { Breadcrumb } from "../components/Layout";
import { StatusPill } from "../components/Questions";
import type { Lang, Strings } from "../i18n";

const FILTERS: (RequestStatus | "")[] = ["", "PENDING_REVIEW", "AWAITING_INFO", "APPROVED", "REJECTED"];

/** Network security team queue and everyone's request history. */
export function RequestsPage({ s, lang, navigate }: { s: Strings; lang: Lang; navigate: (path: string) => void }) {
  const [status, setStatus] = useState<RequestStatus | "">("PENDING_REVIEW");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageView<RequestSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const L = s as unknown as Record<string, string>;

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
    new Date(iso).toLocaleDateString(lang === "ar" ? "ar-SA-u-nu-latn-ca-gregory" : "en-GB",
      { day: "numeric", month: "short", year: "numeric" });

  return (
    <>
      <Breadcrumb s={s} current={s.queueTitle} />
      <div className="sheet">
        <div className="card-head row-head">
          <h1>{s.queueTitle}</h1>
          <a className="btn primary small" href="#/new">+ {s.newRequest}</a>
        </div>
        <div className="toolbar">
          <div className="tabs" role="tablist">
            {FILTERS.map((f) => (
              <button key={f || "all"} role="tab" aria-selected={status === f} className={status === f ? "tab active" : "tab"}
                onClick={() => { setStatus(f); setPage(0); }}>
                {f ? L[`status${f}`] : s.all}
              </button>
            ))}
          </div>
          <input className="search" placeholder={s.search} value={q} dir="auto" aria-label={s.search}
            onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>

        {error && <p className="error pad">{error}</p>}
        {!data && !error && <p className="sub pad">{s.loading}</p>}
        {data && data.items.length === 0 && <p className="sub pad">{s.empty}</p>}
        {data && data.items.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{s.colTicket}</th>
                  <th>{s.colApplication}</th>
                  <th>{s.colConnection}</th>
                  <th>{s.colService}</th>
                  <th>{s.colEnv}</th>
                  <th>{s.colPriority}</th>
                  <th>{s.colScore}</th>
                  <th>{s.colStatus}</th>
                  <th>{s.colDate}</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((r) => (
                  <tr key={r.id} onClick={() => navigate(`/requests/${r.id}`)} tabIndex={0}
                    onKeyDown={(e) => e.key === "Enter" && navigate(`/requests/${r.id}`)}>
                    <td><a className="ticket" href={`#/requests/${r.id}`} onClick={(e) => e.stopPropagation()}>{r.ticketNo}</a></td>
                    <td>{r.applicationName}<div className="muted-sm">{r.requesterName} · {r.department}</div></td>
                    <td className="mono"><bdi dir="ltr">{r.source}</bdi><div className="muted-sm"><bdi dir="ltr">→ {r.destination}</bdi></div></td>
                    <td className="mono"><bdi dir="ltr">{r.service}</bdi></td>
                    <td><span className={`env env-${r.environment.toLowerCase()}`}>{r.environment === "DEVELOPMENT" ? "DEV" : r.environment === "PRODUCTION" ? "PROD" : r.environment}</span></td>
                    <td><span className={`prio prio-${r.priority.toLowerCase()}`}>{L[`prio${r.priority}`]}</span></td>
                    <td className="num">{r.qualityScore?.toFixed(1) ?? "–"}</td>
                    <td><StatusPill s={s} status={r.status} /></td>
                    <td className="nowrap">{date(r.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && data.totalPages > 1 && (
          <div className="sheet-foot pager">
            <button className="btn outline small" disabled={page === 0} onClick={() => setPage(page - 1)}>{s.prev}</button>
            <span className="sub">{page + 1} / {data.totalPages}</span>
            <button className="btn outline small" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>{s.next}</button>
          </div>
        )}
      </div>
    </>
  );
}
