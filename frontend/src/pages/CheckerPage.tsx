import { useEffect, useState } from "react";
import { api, ApiError, type Justification, type TemplateView } from "../api";
import { Questions, Score } from "../components/Questions";
import { Breadcrumb } from "../components/Layout";
import type { Lang, Strings } from "../i18n";

/** Free-text justification checker (any template), the original Justify AI screen. */
export function CheckerPage({ s, lang }: { s: Strings; lang: Lang }) {
  const [templates, setTemplates] = useState<TemplateView[]>([]);
  const [templateId, setTemplateId] = useState("firewall-rule");
  const [text, setText] = useState("");
  const [current, setCurrent] = useState<Justification | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    api
      .templates()
      .then((list) => {
        setTemplates(list);
        if (list.length && !list.some((x) => x.id === templateId)) setTemplateId(list[0].id);
      })
      .catch(() => setTemplates([]));
    if (current?.status === "NEEDS_INFO") {
      api.get(current.id).then(setCurrent).catch(() => undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lang]);

  async function run(action: () => Promise<Justification>) {
    setBusy(true);
    setError(null);
    try {
      setCurrent(await action());
    } catch (e) {
      if (e instanceof ApiError && e.status === 503) setError(s.errorModel);
      else setError(s.error + (e instanceof Error ? e.message : String(e)));
    } finally {
      setBusy(false);
    }
  }

  function reset() {
    setCurrent(null);
    setText("");
    setError(null);
    setCopied(false);
  }

  async function copy() {
    if (!current?.standardizedText) return;
    try {
      await navigator.clipboard.writeText(current.standardizedText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* clipboard blocked */
    }
  }

  const closed = current && current.status !== "NEEDS_INFO";

  return (
    <>
      <Breadcrumb s={s} current={s.navCheck} />
      <h1>{s.title}</h1>
      <p className="muted">{s.subtitle}</p>

      {!current && (
        <form
          className="card"
          onSubmit={(e) => {
            e.preventDefault();
            if (text.trim()) run(() => api.submit(templateId, text));
          }}
        >
          <label>
            {s.requestType}
            <select value={templateId} onChange={(e) => setTemplateId(e.target.value)}>
              {(templates.length ? templates : [{ id: "firewall-rule", name: "Firewall rule request" }]).map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            {s.yourText}
            <textarea rows={7} value={text} maxLength={8000} placeholder={s.placeholder}
              onChange={(e) => setText(e.target.value)} dir="auto" />
          </label>
          <button className="primary" disabled={busy || !text.trim()}>
            {busy ? s.working : s.submit}
          </button>
        </form>
      )}

      {current?.status === "NEEDS_INFO" && (
        <Questions key={current.round} s={s} questions={current.questions ?? {}} round={current.round}
          maxRounds={current.maxRounds} busy={busy} onSubmit={(a) => run(() => api.answer(current.id, a))} />
      )}

      {closed && (
        <section className="card">
          <div className="row">
            <h2>{current.status === "COMPLETE" ? s.complete : s.incomplete}</h2>
            <Score s={s} value={current.qualityScore} />
          </div>
          {current.status === "INCOMPLETE" && <p className="muted">{s.incompleteHint}</p>}
          <pre dir="ltr">{current.standardizedText}</pre>
          <div className="row">
            <button className="primary" onClick={copy}>{copied ? s.copied : s.copy}</button>
            <button className="link" onClick={reset}>{s.again}</button>
          </div>
        </section>
      )}

      {error && <p className="error">{error}</p>}
    </>
  );
}
