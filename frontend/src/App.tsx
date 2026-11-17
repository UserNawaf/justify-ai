import { useEffect, useState } from "react";
import { api, ApiError, setLanguage, type Justification, type TemplateView } from "./api";
import { t, type Lang } from "./i18n";

export default function App() {
  const [lang, setLang] = useState<Lang>(() => {
    try {
      return (localStorage.getItem("lang") as Lang) || "ar";
    } catch {
      return "ar";
    }
  });
  const s = t(lang);
  const [templates, setTemplates] = useState<TemplateView[]>([]);
  const [templateId, setTemplateId] = useState("firewall-rule");
  const [text, setText] = useState("");
  const [current, setCurrent] = useState<Justification | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  setLanguage(lang);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    try {
      localStorage.setItem("lang", lang);
    } catch {
      /* storage unavailable */
    }
  }, [lang]);

  // Reload localized template names, and the open questions, when the language changes.
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
      const result = await action();
      setCurrent(result);
      setAnswers({});
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
    setAnswers({});
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

  const questions = current?.status === "NEEDS_INFO" ? current.questions ?? {} : {};
  const closed = current && current.status !== "NEEDS_INFO";
  const canAnswer = Object.values(answers).some((v) => v.trim() !== "");

  return (
    <div className="page">
      <header>
        <div className="brand">
          <span className="logo">J</span> Justify AI
        </div>
        <button className="link" onClick={() => setLang(lang === "ar" ? "en" : "ar")}>
          {s.switchLang}
        </button>
      </header>

      <main>
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
              <textarea
                rows={7}
                value={text}
                maxLength={8000}
                placeholder={s.placeholder}
                onChange={(e) => setText(e.target.value)}
                dir="auto"
              />
            </label>
            <button className="primary" disabled={busy || !text.trim()}>
              {busy ? s.working : s.submit}
            </button>
          </form>
        )}

        {current?.status === "NEEDS_INFO" && (
          <form
            className="card"
            onSubmit={(e) => {
              e.preventDefault();
              if (canAnswer) run(() => api.answer(current.id, answers));
            }}
          >
            <div className="row">
              <h2>{s.questionsTitle}</h2>
              <span className="pill">{s.round(current.round, current.maxRounds)}</span>
            </div>
            {Object.entries(questions).map(([key, q]) => (
              <label key={key}>
                <span dir="auto" className="question">
                  {q}
                </span>
                <input
                  dir="auto"
                  value={answers[key] ?? ""}
                  onChange={(e) => setAnswers({ ...answers, [key]: e.target.value })}
                />
              </label>
            ))}
            <button className="primary" disabled={busy || !canAnswer}>
              {busy ? s.working : s.send}
            </button>
          </form>
        )}

        {closed && (
          <section className="card">
            <div className="row">
              <h2>{current.status === "COMPLETE" ? s.complete : s.incomplete}</h2>
              <span className={`score ${current.qualityScore >= 8 ? "good" : current.qualityScore >= 5 ? "ok" : "low"}`}>
                {s.score}: {current.qualityScore.toFixed(1)} / 10
              </span>
            </div>
            {current.status === "INCOMPLETE" && <p className="muted">{s.incompleteHint}</p>}
            <pre dir="ltr">{current.standardizedText}</pre>
            <div className="row">
              <button className="primary" onClick={copy}>
                {copied ? s.copied : s.copy}
              </button>
              <button className="link" onClick={reset}>
                {s.again}
              </button>
            </div>
          </section>
        )}

        {error && <p className="error">{error}</p>}
      </main>
    </div>
  );
}
