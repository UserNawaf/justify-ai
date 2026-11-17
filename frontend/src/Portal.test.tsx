import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import App from "./App";

const ID = "11111111-2222-3333-4444-555555555555";
const base = {
  id: ID,
  ticketNo: "FW-2026-00042",
  requesterName: "نواف",
  employeeId: "E10234",
  department: "Finance IT",
  sourceHostname: "payroll-app-01",
  sourceIp: "10.10.5.20",
  destinationHost: "api.bank.example",
  protocol: "TCP",
  ports: "443",
  durationType: "PERMANENT",
  createdAt: "2026-11-17T10:00:00Z",
};
const awaiting = {
  ...base,
  status: "AWAITING_INFO",
  justification: { id: "j", status: "NEEDS_INFO", round: 1, maxRounds: 3, qualityScore: 6.4,
    questions: { impact_if_rejected: "وش يصير للعمل إذا ما انفتحت القاعدة؟" } },
};
const pending = {
  ...base,
  status: "PENDING_REVIEW",
  justification: { id: "j", status: "COMPLETE", round: 2, maxRounds: 3, qualityScore: 9.0,
    standardizedText: "Request type: Firewall rule request\nPort / protocol: TCP 443", originalText: "عشان الرواتب" },
};
const approved = { ...pending, status: "APPROVED", decidedBy: "Sara", decidedAt: "2026-11-17T11:00:00Z" };

type Call = { url: string; init?: RequestInit };
let calls: Call[] = [];
let createResponse: { status: number; body: unknown } = { status: 201, body: awaiting };

function respond(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
}

beforeEach(() => {
  calls = [];
  localStorage.clear();
  createResponse = { status: 201, body: awaiting };
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init?: RequestInit) => {
      calls.push({ url, init });
      if (url.endsWith("/firewall-requests") && init?.method === "POST")
        return respond(createResponse.body, createResponse.status);
      if (url.endsWith("/answers")) return respond(pending);
      if (url.endsWith("/decision")) return respond(approved);
      if (url.includes("/firewall-requests?"))
        return respond({ items: [{ id: ID, ticketNo: base.ticketNo, status: "PENDING_REVIEW", requesterName: "نواف",
          department: "Finance IT", source: "payroll-app-01 (10.10.5.20)", destination: "api.bank.example",
          service: "TCP 443", qualityScore: 9, createdAt: base.createdAt }], page: 0, size: 20, total: 1, totalPages: 1 });
      if (url.endsWith(`/firewall-requests/${ID}`)) return respond(pending);
      return respond({ title: "Not found" }, 404);
    }),
  );
});

afterEach(() => vi.unstubAllGlobals());

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("الاسم"), "نواف");
  await user.type(screen.getByLabelText("الرقم الوظيفي"), "E10234");
  await user.type(screen.getByLabelText("الإدارة"), "Finance IT");
  await user.type(screen.getByLabelText("اسم جهاز المصدر"), "payroll-app-01");
  await user.type(screen.getByLabelText("IP المصدر"), "10.10.5.20");
  await user.type(screen.getByLabelText("الوجهة (اسم أو رابط)"), "api.bank.example");
  await user.type(screen.getByLabelText("المنافذ"), "443");
  await user.type(screen.getByLabelText(/ليش تحتاج/), "عشان الرواتب");
}

test("new request: form, AI question, then submitted", async () => {
  window.location.hash = "#/new";
  const user = userEvent.setup();
  render(<App />);

  await fillForm(user);
  await user.click(screen.getByRole("button", { name: "إرسال الطلب" }));

  expect(await screen.findByText("وش يصير للعمل إذا ما انفتحت القاعدة؟")).toBeInTheDocument();
  expect(screen.getByText("FW-2026-00042")).toBeInTheDocument();
  const body = JSON.parse(String(calls.find((c) => c.init?.method === "POST")!.init!.body));
  expect(body).toMatchObject({ sourceIp: "10.10.5.20", ports: "443", durationType: "PERMANENT" });
  expect(body).not.toHaveProperty("endDate");

  await user.type(screen.getByRole("textbox"), "تتأخر الرواتب");
  await user.click(screen.getByRole("button", { name: "إرسال الإجابات" }));
  expect(await screen.findByText("انرفع طلبك")).toBeInTheDocument();
  expect(JSON.parse(localStorage.getItem("requester")!)).toMatchObject({ employeeId: "E10234" });
});

test("new request: server field errors are shown next to the fields", async () => {
  window.location.hash = "#/new";
  createResponse = { status: 400, body: { title: "Invalid request form",
    errors: [{ field: "sourceIp", message: "must be an IPv4 address or subnet" }] } };
  const user = userEvent.setup();
  render(<App />);

  await fillForm(user);
  await user.click(screen.getByRole("button", { name: "إرسال الطلب" }));

  expect(await screen.findByText("must be an IPv4 address or subnet")).toBeInTheDocument();
  expect(screen.getByLabelText("IP المصدر")).toHaveAttribute("aria-invalid", "true");
  expect(screen.getByText("فيه خانات تحتاج تصحيح.")).toBeInTheDocument();
});

test("requests list opens the request; engineer approves", async () => {
  window.location.hash = "#/requests";
  const user = userEvent.setup();
  render(<App />);

  const row = await screen.findByText("FW-2026-00042");
  expect(within(row.closest("tr")!).getByText("عند المهندس")).toBeInTheDocument();
  await user.click(row);

  expect(await screen.findByText(/Port \/ protocol: TCP 443/)).toBeInTheDocument();
  const approve = screen.getByRole("button", { name: "موافقة" });
  expect(approve).toBeDisabled();
  expect(screen.getByRole("button", { name: "رفض" })).toBeDisabled();

  await user.type(screen.getByLabelText("اسم المهندس"), "Sara");
  await user.click(approve);

  expect(await screen.findByText("تمت الموافقة")).toBeInTheDocument();
  const decision = JSON.parse(String(calls.find((c) => c.url.endsWith("/decision"))!.init!.body));
  expect(decision).toEqual({ decision: "APPROVED", engineer: "Sara" });
});
