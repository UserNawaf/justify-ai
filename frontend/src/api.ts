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

let language = "ar";

/** Questions and labels come back in this language; the standardised text stays English. */
export function setLanguage(lang: string) {
  language = lang;
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", "Accept-Language": language, ...(init?.headers ?? {}) },
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
  get: (id: string) => call<Justification>(`/justifications/${id}`),
  answer: (id: string, answers: Record<string, string>) =>
    call<Justification>(`/justifications/${id}/answers`, {
      method: "POST",
      body: JSON.stringify({ answers }),
    }),
};

// ---- Firewall request portal ----

export type RequestStatus = "AWAITING_INFO" | "PENDING_REVIEW" | "APPROVED" | "REJECTED";

export type RequestType = "NEW_ACCESS" | "MODIFY_ACCESS" | "REMOVE_ACCESS";
export type Priority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type Environment = "PRODUCTION" | "UAT" | "DEVELOPMENT" | "DR";

export interface Profile {
  requesterName: string;
  employeeId: string;
  department: string;
  email: string;
}

/** What the form collects (requester details come from the profile). */
export interface FirewallForm {
  requestType: RequestType | "";
  priority: Priority | "";
  applicationName: string;
  sourceIp: string;
  sourceHostname: string;
  destinationIp: string;
  destinationHost: string;
  ports: string;
  protocol: "TCP" | "UDP";
  environment: Environment | "";
  justification: string;
  duration: "" | "PERMANENT" | "1" | "3" | "6" | "12" | "CUSTOM";
  startDate: string;
  endDate: string;
  notes: string;
}

export interface FirewallRequest extends Profile {
  id: string;
  ticketNo: string;
  status: RequestStatus;
  requestType: RequestType;
  priority: Priority;
  applicationName: string;
  environment: Environment;
  sourceIp: string;
  sourceHostname?: string;
  destinationIp: string;
  destinationHost?: string;
  protocol: "TCP" | "UDP";
  ports: string;
  durationType: "PERMANENT" | "TEMPORARY";
  startDate: string;
  endDate?: string;
  notes?: string;
  justification: {
    id: string;
    status: Status;
    round: number;
    maxRounds: number;
    questions?: Record<string, string>;
    missingFields?: string[];
    standardizedText?: string;
    qualityScore: number;
    originalText?: string;
  };
  decidedBy?: string;
  decisionNote?: string;
  decidedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface RequestSummary {
  id: string;
  ticketNo: string;
  status: RequestStatus;
  requesterName: string;
  department: string;
  applicationName: string;
  environment: Environment;
  priority: Priority;
  source: string;
  destination: string;
  service: string;
  qualityScore?: number;
  createdAt: string;
}

export interface PageView<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface FieldError {
  field: string;
  message: string;
}

export class FormError extends ApiError {
  constructor(public errors: FieldError[]) {
    super(400, "form");
  }
}

async function callForm<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Accept-Language": language },
    body: JSON.stringify(body),
  });
  if (res.status === 400) {
    const problem = await res.json().catch(() => ({}));
    if (Array.isArray(problem.errors)) throw new FormError(problem.errors);
    throw new ApiError(400, problem.detail ?? "Invalid input");
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    throw new ApiError(res.status, problem.detail ?? res.statusText);
  }
  return res.json() as Promise<T>;
}

export const portal = {
  create: (form: FirewallForm, profile: Profile) =>
    callForm<FirewallRequest>("/firewall-requests", toRequestBody(form, profile)),
  get: (id: string) => call<FirewallRequest>(`/firewall-requests/${id}`),
  list: (params: { status?: string; q?: string; employeeId?: string; page?: number }) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => v !== undefined && v !== "" && qs.set(k, String(v)));
    return call<PageView<RequestSummary>>(`/firewall-requests?${qs}`);
  },
  answer: (id: string, answers: Record<string, string>) =>
    call<FirewallRequest>(`/firewall-requests/${id}/answers`, { method: "POST", body: JSON.stringify({ answers }) }),
  decide: (id: string, decision: "APPROVED" | "REJECTED", engineer: string, note: string) =>
    callForm<FirewallRequest>(`/firewall-requests/${id}/decision`, { decision, engineer, note: note || undefined }),
};

/** "3" months from the start date, or the custom end date; the API only knows PERMANENT / TEMPORARY + endDate. */
export function endDateFor(form: Pick<FirewallForm, "duration" | "startDate" | "endDate">): string | undefined {
  if (form.duration === "CUSTOM") return form.endDate || undefined;
  if (!form.duration || form.duration === "PERMANENT" || !form.startDate) return undefined;
  const d = new Date(`${form.startDate}T00:00:00Z`);
  d.setUTCMonth(d.getUTCMonth() + Number(form.duration));
  return d.toISOString().slice(0, 10);
}

export function toRequestBody(form: FirewallForm, profile: Profile): Record<string, unknown> {
  const body: Record<string, unknown> = {
    ...profile,
    requestType: form.requestType || undefined,
    priority: form.priority || undefined,
    applicationName: form.applicationName,
    sourceIp: form.sourceIp,
    sourceHostname: form.sourceHostname || undefined,
    destinationIp: form.destinationIp,
    destinationHost: form.destinationHost || undefined,
    ports: form.ports,
    protocol: form.protocol,
    environment: form.environment || undefined,
    justification: form.justification,
    durationType: form.duration === "PERMANENT" ? "PERMANENT" : form.duration ? "TEMPORARY" : undefined,
    startDate: form.startDate || undefined,
    endDate: form.duration && form.duration !== "PERMANENT" ? endDateFor(form) : undefined,
    notes: form.notes || undefined,
  };
  if (!profile.email) delete body.email;
  return body;
}

export function loadProfile(): Profile {
  try {
    return { requesterName: "", employeeId: "", department: "", email: "", ...JSON.parse(localStorage.getItem("profile") ?? "{}") };
  } catch {
    return { requesterName: "", employeeId: "", department: "", email: "" };
  }
}

export function saveProfile(p: Profile) {
  try {
    localStorage.setItem("profile", JSON.stringify(p));
  } catch {
    /* storage unavailable */
  }
}
