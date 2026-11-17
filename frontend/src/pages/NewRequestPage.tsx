import { useState } from "react";
import { ApiError, FormError, portal, type FieldError, type FirewallForm, type FirewallRequest } from "../api";
import { Questions } from "../components/Questions";
import type { Strings } from "../i18n";

const EMPTY: FirewallForm = {
  requesterName: "",
  employeeId: "",
  department: "",
  email: "",
  sourceHostname: "",
  sourceIp: "",
  destinationHost: "",
  destinationIp: "",
  protocol: "TCP",
  ports: "",
  durationType: "PERMANENT",
  endDate: "",
  justification: "",
};

function remembered(): FirewallForm {
  // Requester details are remembered on this browser so the next request is quicker.
  try {
    const saved = JSON.parse(localStorage.getItem("requester") ?? "{}");
    return { ...EMPTY, ...saved };
  } catch {
    return EMPTY;
  }
}

export function NewRequestPage({ s, navigate }: { s: Strings; navigate: (path: string) => void }) {
  const [form, setForm] = useState<FirewallForm>(remembered);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [request, setRequest] = useState<FirewallRequest | null>(null);

  const set = (k: keyof FirewallForm) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });
  const err = (k: keyof FirewallForm) => errors.find((x) => x.field === k)?.message;

  async function run(action: () => Promise<FirewallRequest>) {
    setBusy(true);
    setError(null);
    try {
      setRequest(await action());
      setErrors([]);
    } catch (e) {
      if (e instanceof FormError) {
        setErrors(e.errors);
        setError(s.fixFields);
      } else if (e instanceof ApiError && e.status === 503) setError(s.errorModel);
      else setError(s.error + (e instanceof Error ? e.message : String(e)));
    } finally {
      setBusy(false);
    }
  }

  function submit() {
    try {
      const { requesterName, employeeId, department, email } = form;
      localStorage.setItem("requester", JSON.stringify({ requesterName, employeeId, department, email }));
    } catch {
      /* storage unavailable */
    }
    run(() => portal.create(form));
  }

  const field = (k: keyof FirewallForm, label: string, props: Record<string, unknown> = {}) => (
    <div className={err(k) ? "field invalid" : "field"}>
      <label htmlFor={`f-${k}`}>{label}</label>
      <input id={`f-${k}`} value={form[k]} onChange={set(k)} dir="auto" aria-invalid={!!err(k)}
        aria-describedby={err(k) ? `e-${k}` : undefined} {...props} />
      {err(k) && <small id={`e-${k}`} className="field-error" dir="ltr">{err(k)}</small>}
    </div>
  );

  if (request?.status === "AWAITING_INFO" && request.justification.questions) {
    const j = request.justification;
    return (
      <>
        <h1>{s.newTitle}</h1>
        <p className="muted"><span className="ticket">{request.ticketNo}</span></p>
        <Questions key={j.round} s={s} questions={j.questions!} round={j.round} maxRounds={j.maxRounds} busy={busy}
          onSubmit={(a) => run(() => portal.answer(request.id, a))} />
        {error && <p className="error">{error}</p>}
      </>
    );
  }

  if (request) {
    return (
      <section className="card success">
        <h2>{s.created}</h2>
        <p className="muted">{s.createdReview}</p>
        <p>{s.ticket}: <span className="ticket">{request.ticketNo}</span></p>
        <div className="row">
          <button className="primary" onClick={() => navigate(`/requests/${request.id}`)}>{s.openRequest}</button>
          <button className="link" onClick={() => { setRequest(null); setForm({ ...remembered(), justification: "" }); }}>
            {s.navNew}
          </button>
        </div>
      </section>
    );
  }

  return (
    <>
      <h1>{s.newTitle}</h1>
      <p className="muted">{s.newSubtitle}</p>
      <form className="card" onSubmit={(e) => { e.preventDefault(); submit(); }} noValidate>
        <fieldset>
          <legend>{s.secRequester}</legend>
          <div className="grid">
            {field("requesterName", s.requesterName, { required: true })}
            {field("employeeId", s.employeeId, { required: true })}
            {field("department", s.department, { required: true })}
            {field("email", s.email, { type: "email" })}
          </div>
        </fieldset>

        <fieldset>
          <legend>{s.secConnection}</legend>
          <div className="grid">
            {field("sourceHostname", s.sourceHostname, { placeholder: "payroll-app-01", dir: "ltr" })}
            {field("sourceIp", s.sourceIp, { placeholder: "10.10.5.20", dir: "ltr" })}
            {field("destinationHost", s.destinationHost, { placeholder: "api.bank.example", dir: "ltr" })}
            {field("destinationIp", s.destinationIp, { placeholder: "203.0.113.10", dir: "ltr" })}
            <label>
              {s.protocol}
              <select value={form.protocol} onChange={set("protocol")}>
                <option>TCP</option>
                <option>UDP</option>
              </select>
            </label>
            {field("ports", s.ports, { placeholder: s.portsHint, dir: "ltr" })}
            <label>
              {s.duration}
              <select value={form.durationType} onChange={set("durationType")}>
                <option value="PERMANENT">{s.permanent}</option>
                <option value="TEMPORARY">{s.temporary}</option>
              </select>
            </label>
            {form.durationType === "TEMPORARY" && field("endDate", s.endDate, { type: "date" })}
          </div>
        </fieldset>

        <fieldset>
          <legend>{s.secWhy}</legend>
          <div className={err("justification") ? "field invalid" : "field"}>
            <label htmlFor="f-justification">{s.whyLabel}</label>
            <textarea id="f-justification" rows={5} value={form.justification} onChange={set("justification")}
              maxLength={8000} placeholder={s.whyPlaceholder} dir="auto" aria-invalid={!!err("justification")} />
            {err("justification") && <small className="field-error" dir="ltr">{err("justification")}</small>}
          </div>
        </fieldset>

        <button className="primary" disabled={busy}>{busy ? s.working : s.submitRequest}</button>
      </form>
      {error && <p className="error">{error}</p>}
    </>
  );
}
