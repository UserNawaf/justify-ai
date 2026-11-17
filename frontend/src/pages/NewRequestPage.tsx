import { useState } from "react";
import { ApiError, FormError, portal, type FieldError, type FirewallForm, type FirewallRequest, type Profile } from "../api";
import { SaveIcon, SendIcon, SparkIcon } from "../components/Icons";
import { Breadcrumb } from "../components/Layout";
import { Questions } from "../components/Questions";
import type { Lang, Strings } from "../i18n";

const DRAFT_KEY = "fw-draft";

const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

const EMPTY: FirewallForm = {
  requestType: "",
  priority: "",
  applicationName: "",
  sourceIp: "",
  sourceHostname: "",
  destinationIp: "",
  destinationHost: "",
  ports: "",
  protocol: "TCP",
  environment: "",
  justification: "",
  duration: "",
  startDate: today(),
  endDate: "",
  notes: "",
};

function loadDraft(): FirewallForm | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY);
    return raw ? { ...EMPTY, ...JSON.parse(raw) } : null;
  } catch {
    return null;
  }
}

const REQUIRED: (keyof FirewallForm)[] = ["requestType", "priority", "applicationName", "sourceIp", "destinationIp",
  "ports", "environment", "justification", "duration", "startDate"];

/** API field names that differ from the form's. */
const SERVER_FIELD: Record<string, keyof FirewallForm> = { durationType: "duration" };

interface Props {
  s: Strings;
  lang: Lang;
  profile: Profile;
  onNeedProfile: () => void;
  navigate: (path: string) => void;
}

export function NewRequestPage({ s, lang, profile, onNeedProfile, navigate }: Props) {
  const [draft] = useState(loadDraft);
  const [form, setForm] = useState<FirewallForm>(draft ?? EMPTY);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [busy, setBusy] = useState(false);
  const [banner, setBanner] = useState<string | null>(draft ? s.draftRestored : null);
  const [error, setError] = useState<string | null>(null);
  const [request, setRequest] = useState<FirewallRequest | null>(null);

  const set = (k: keyof FirewallForm) => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: e.target.value });
    if (errors.some((x) => x.field === k)) setErrors(errors.filter((x) => x.field !== k));
  };
  const err = (k: keyof FirewallForm) => errors.find((x) => x.field === k)?.message;
  const profileOk = !!(profile.requesterName.trim() && profile.employeeId.trim() && profile.department.trim());

  async function run(action: () => Promise<FirewallRequest>, clearDraft = false) {
    setBusy(true);
    setError(null);
    try {
      const r = await action();
      setRequest(r);
      setErrors([]);
      if (clearDraft) {
        try {
          localStorage.removeItem(DRAFT_KEY);
        } catch {
          /* storage unavailable */
        }
      }
    } catch (e) {
      if (e instanceof FormError) {
        setErrors(e.errors.map((x) => ({ ...x, field: SERVER_FIELD[x.field] ?? x.field })));
        setError(s.fixFields);
      } else if (e instanceof ApiError && e.status === 503) setError(s.errorModel);
      else setError(s.error + (e instanceof Error ? e.message : String(e)));
    } finally {
      setBusy(false);
    }
  }

  function submit() {
    setBanner(null);
    const missing: FieldError[] = REQUIRED.filter((k) => !String(form[k]).trim()).map((k) => ({ field: k, message: s.required }));
    if (form.duration === "CUSTOM" && !form.endDate) missing.push({ field: "endDate", message: s.required });
    if (missing.length) {
      setErrors(missing);
      setError(s.fixFields);
      document.getElementById(`f-${missing[0].field}`)?.focus();
      return;
    }
    if (!profileOk) {
      onNeedProfile();
      return;
    }
    run(() => portal.create(form, profile), true);
  }

  function saveDraft() {
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(form));
      setBanner(s.draftSaved);
    } catch {
      /* storage unavailable */
    }
  }

  function cancel() {
    try {
      localStorage.removeItem(DRAFT_KEY);
    } catch {
      /* storage unavailable */
    }
    setForm(EMPTY);
    setErrors([]);
    setError(null);
    setBanner(null);
    navigate("/requests");
  }

  const label = (k: keyof FirewallForm, text: string, required: boolean) => (
    <label htmlFor={`f-${k}`}>
      {text} {required ? <span className="req">*</span> : <span className="opt">{s.optional}</span>}
    </label>
  );
  const fieldError = (k: keyof FirewallForm) =>
    err(k) && <small id={`e-${k}`} className="field-error">{err(k)}</small>;
  const aria = (k: keyof FirewallForm) => ({
    id: `f-${k}`,
    "aria-invalid": !!err(k),
    "aria-describedby": err(k) ? `e-${k}` : undefined,
  });

  const input = (k: keyof FirewallForm, text: string, required: boolean, props: Record<string, unknown> = {}) => (
    <div className={err(k) ? "field invalid" : "field"}>
      {label(k, text, required)}
      <input value={form[k]} onChange={set(k)} dir="auto" {...aria(k)} {...props} />
      {fieldError(k)}
    </div>
  );

  const select = (k: keyof FirewallForm, text: string, options: [string, string][]) => (
    <div className={err(k) ? "field invalid" : "field"}>
      {label(k, text, true)}
      <select value={form[k]} onChange={set(k)} className={form[k] ? undefined : "placeholder"} {...aria(k)}>
        <option value="" disabled>{s.selectOption}</option>
        {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
      {fieldError(k)}
    </div>
  );

  const dateFmt = new Date().toLocaleDateString(lang === "ar" ? "ar-SA-u-nu-latn-ca-gregory" : "en-GB",
    { day: "numeric", month: "short", year: "numeric" });

  const meta = (
    <dl className="meta">
      <div><dt>{s.requestNo}</dt><dd className={request ? "ticket" : undefined}>{request?.ticketNo ?? s.generatedOnSubmit}</dd></div>
      <div><dt>{s.status}</dt><dd>{request ? (s as unknown as Record<string, string>)[`status${request.status}`] : s.statusDRAFT}</dd></div>
      <div><dt>{s.requestedBy}</dt><dd>{profileOk ? profile.requesterName
        : <button type="button" className="linkish" onClick={onNeedProfile}>{s.setProfile}</button>}</dd></div>
      <div><dt>{s.requestDate}</dt><dd>{dateFmt}</dd></div>
    </dl>
  );

  const head = (
    <div className="card-head">
      <h1>{s.newTitle}</h1>
      <p className="sub">{s.newSubtitle}</p>
      {!request && <p className="mandatory">{s.mandatory.split("*")[0]}<span className="req">*</span>{s.mandatory.split("*")[1]}</p>}
    </div>
  );

  // Step 2: the AI found gaps in the justification
  if (request?.status === "AWAITING_INFO" && request.justification.questions) {
    const j = request.justification;
    return (
      <>
        <Breadcrumb s={s} current={s.newTitle} />
        <div className="sheet">
          {head}
          {meta}
          <div className="sheet-body">
            <div className="callout"><SparkIcon /><div><strong>{s.moreInfoTitle}</strong><p>{s.moreInfoText}</p></div></div>
            <Questions key={j.round} s={s} questions={j.questions!} round={j.round} maxRounds={j.maxRounds} busy={busy}
              onSubmit={(a) => run(() => portal.answer(request.id, a))} />
            {error && <p className="error">{error}</p>}
          </div>
        </div>
      </>
    );
  }

  // Step 3: submitted
  if (request) {
    return (
      <>
        <Breadcrumb s={s} current={s.newTitle} />
        <div className="sheet">
          {head}
          {meta}
          <div className="sheet-body success">
            <h2>{s.created}</h2>
            <p className="sub">{s.createdReview}</p>
          </div>
          <div className="sheet-foot">
            <button className="btn primary" onClick={() => navigate(`/requests/${request.id}`)}>{s.openRequest}</button>
            <button className="btn outline" onClick={() => { setRequest(null); setForm({ ...EMPTY, startDate: today() }); }}>
              {s.newRequest}
            </button>
          </div>
        </div>
      </>
    );
  }

  // Step 1: the form
  return (
    <>
      <Breadcrumb s={s} current={s.newTitle} />
      <form className="sheet" onSubmit={(e) => { e.preventDefault(); submit(); }} noValidate>
        {head}
        {meta}
        <div className="sheet-body">
          {banner && <p className="banner">{banner}</p>}

          <section>
            <h2>{s.sec1}</h2>
            <div className="cols">
              {select("requestType", s.requestTypeLabel, [
                ["NEW_ACCESS", s.typeNEW_ACCESS], ["MODIFY_ACCESS", s.typeMODIFY_ACCESS], ["REMOVE_ACCESS", s.typeREMOVE_ACCESS]])}
              {select("priority", s.priority, [
                ["LOW", s.prioLOW], ["MEDIUM", s.prioMEDIUM], ["HIGH", s.prioHIGH], ["CRITICAL", s.prioCRITICAL]])}
            </div>
            {input("applicationName", s.applicationName, true, { placeholder: s.applicationPh })}
          </section>

          <section>
            <h2>{s.sec2}</h2>
            <div className="cols">
              {input("sourceIp", s.sourceIp, true, { placeholder: "e.g. 10.20.30.40 or 10.20.30.0/24", dir: "ltr" })}
              {input("sourceHostname", s.sourceHostname, false, { placeholder: "e.g. billing-app-01", dir: "ltr" })}
              {input("destinationIp", s.destinationIp, true, { placeholder: "e.g. 172.16.5.10", dir: "ltr" })}
              {input("destinationHost", s.destinationHost, false, { placeholder: "e.g. api.partner.example", dir: "ltr" })}
            </div>
            <div className="cols">
              <div className={err("ports") ? "field invalid" : "field"}>
                {label("ports", s.ports, true)}
                <input value={form.ports} onChange={set("ports")} dir="ltr" placeholder="e.g. 443" {...aria("ports")} />
                {fieldError("ports") || <small className="hint">{s.portsHint}</small>}
              </div>
              <div className="field">
                <span className="label" id="l-protocol">{s.protocol} <span className="req">*</span></span>
                <div className="segmented" role="radiogroup" aria-labelledby="l-protocol">
                  {(["TCP", "UDP"] as const).map((p) => (
                    <button key={p} type="button" role="radio" aria-checked={form.protocol === p}
                      className={form.protocol === p ? "on" : undefined} onClick={() => setForm({ ...form, protocol: p })}>
                      {p}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            {select("environment", s.environment, [
              ["PRODUCTION", s.envPRODUCTION], ["UAT", s.envUAT], ["DEVELOPMENT", s.envDEVELOPMENT], ["DR", s.envDR]])}
          </section>

          <section>
            <h2>{s.sec3}</h2>
            <div className={err("justification") ? "field invalid" : "field"}>
              {label("justification", s.justificationLabel, true)}
              <textarea rows={5} value={form.justification} onChange={set("justification")} maxLength={8000}
                placeholder={s.justificationPh} dir="auto" {...aria("justification")} />
              {fieldError("justification")}
              <small className="hint">{s.justificationHint}</small>
              <small className="ai-hint"><SparkIcon /> {s.aiHint}</small>
            </div>
          </section>

          <section>
            <h2>{s.sec4}</h2>
            <div className="cols">
              {select("duration", s.durationLabel, [
                ["PERMANENT", s.durPERMANENT], ["1", s.dur1], ["3", s.dur3], ["6", s.dur6], ["12", s.dur12],
                ["CUSTOM", s.durCUSTOM]])}
              {input("startDate", s.startDate, true, { type: "date", min: today(), dir: "ltr" })}
            </div>
            {form.duration === "CUSTOM" && input("endDate", s.endDate, true, { type: "date", min: form.startDate, dir: "ltr" })}
            <div className="field">
              {label("notes", s.notes, false)}
              <textarea rows={3} value={form.notes} onChange={set("notes")} maxLength={2000} placeholder={s.notesPh} dir="auto" />
            </div>
          </section>
          {error && <p className="error" role="alert">{error}</p>}
        </div>

        <div className="sheet-foot">
          <button className="btn primary" disabled={busy}><SendIcon /> {busy ? s.working : s.submitRequest}</button>
          <button type="button" className="btn outline" onClick={saveDraft} disabled={busy}><SaveIcon /> {s.saveDraft}</button>
          <button type="button" className="btn outline" onClick={cancel} disabled={busy}>{s.cancel}</button>
        </div>
      </form>
    </>
  );
}
