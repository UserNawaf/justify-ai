export type Status = "NEEDS_INFO" | "COMPLETE" | "INCOMPLETE";

export interface TemplateField {
  key: string;
  label: string;
  required: boolean;
  question: string;
  description: string;
}

export interface TemplateView {
  id: string;
  name: string;
  description: string;
  fields: TemplateField[];
}

export interface Justification {
  id: string;
  templateId: string;
  status: Status;
  round: number;
  maxRounds: number;
  questions?: Record<string, string>;
  missingFields?: string[];
  fields?: Record<string, string>;
  standardizedText?: string;
  qualityScore: number;
  completeness: number;
  clarity: number;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const problem = await res.json();
      detail = problem.detail ?? problem.title ?? detail;
    } catch {
      /* not JSON */
    }
    throw new ApiError(res.status, detail);
  }
  return res.json() as Promise<T>;
}

export const api = {
  templates: () => call<TemplateView[]>("/templates"),
  submit: (templateId: string, text: string) =>
    call<Justification>("/justifications", {
      method: "POST",
      body: JSON.stringify({ templateId, text, source: "web" }),
    }),
  answer: (id: string, answers: Record<string, string>) =>
    call<Justification>(`/justifications/${id}/answers`, {
      method: "POST",
      body: JSON.stringify({ answers }),
    }),
};
