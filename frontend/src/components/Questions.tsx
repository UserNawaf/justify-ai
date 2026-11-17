import { useState } from "react";
import type { Strings } from "../i18n";

interface Props {
  s: Strings;
  questions: Record<string, string>;
  round: number;
  maxRounds: number;
  busy: boolean;
  onSubmit: (answers: Record<string, string>) => void;
}

/** The "a few details are missing" card, shared by the checker and the portal. */
export function Questions({ s, questions, round, maxRounds, busy, onSubmit }: Props) {
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const canAnswer = Object.values(answers).some((v) => v.trim() !== "");
  return (
    <form
      className="questions"
      onSubmit={(e) => {
        e.preventDefault();
        if (canAnswer) onSubmit(answers);
      }}
    >
      <div className="row-between">
        <span className="label">{s.questionsTitle}</span>
        <span className="pill">{s.round(round, maxRounds)}</span>
      </div>
      {Object.entries(questions).map(([key, q]) => (
        <div className="field" key={key}>
          <label htmlFor={`q-${key}`} dir="auto">{q}</label>
          <input id={`q-${key}`} dir="auto" value={answers[key] ?? ""}
            onChange={(e) => setAnswers({ ...answers, [key]: e.target.value })} />
        </div>
      ))}
      <button className="btn primary" disabled={busy || !canAnswer}>
        {busy ? s.working : s.send}
      </button>
    </form>
  );
}

export function Score({ s, value }: { s: Strings; value: number }) {
  const cls = value >= 8 ? "good" : value >= 5 ? "ok" : "low";
  return (
    <span className={`score ${cls}`}>
      {s.score}: <bdi dir="ltr">{value.toFixed(1)} / 10</bdi>
    </span>
  );
}

export function StatusPill({ s, status }: { s: Strings; status: string }) {
  const label = (s as unknown as Record<string, string>)[`status${status}`] ?? status;
  return <span className={`status status-${status.toLowerCase()}`}>{label}</span>;
}
