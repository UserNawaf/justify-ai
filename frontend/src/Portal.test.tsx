import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { endDateFor } from "./api";
import App from "./App";

const ID = "11111111-2222-3333-4444-555555555555";
const base = {
  id: ID,
  ticketNo: "FW-2026-00042",
  requesterName: "Nawaf Alotaibi",
  employeeId: "E10234",
  department: "Finance IT",
  email: "",
  requestType: "NEW_ACCESS",
  priority: "HIGH",
  applicationName: "Payroll System",
  environment: "PRODUCTION",
  sourceHostname: "payroll-app-01",
  sourceIp: "10.10.5.20",
  destinationIp: "203.0.113.10",
  protocol: "TCP",
  ports: "443",
  durationType: "PERMANENT",
  startDate: "2026-11-20",
  createdAt: "2026-11-17T10:00:00Z",
};
const awaiting = {
  ...base,
  status: "AWAITING_INFO",
  justification: { id: "j", status: "NEEDS_INFO", round: 1, maxRounds: 3, qualityScore: 6.4,
    questions: { impact_if_rejected: "What happens to the business if this rule is not opened?" } },
};
const pending = {
  ...base,
  status: "PENDING_REVIEW",
  justification: { id: "j", status: "COMPLETE", round: 2, maxRounds: 3, qualityScore: 9.0,
    standardizedText: "Request type: Firewall rule request\nPort / protocol: TCP 443", originalText: "For payroll" },
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
  localStorage.setItem("profile", JSON.stringify({ requesterName: "Nawaf Alotaibi", employeeId: "E10234", department: "Finance IT", email: "" }));
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
        return respond({ items: [{ id: ID, ticketNo: base.ticketNo, status: "PENDING_REVIEW", requesterName: "Nawaf Alotaibi",
          department: "Finance IT", applicationName: "Payroll System", environment: "PRODUCTION", priority: "HIGH",
          source: "payroll-app-01 (10.10.5.20)", destination: "203.0.113.10", service: "TCP 443", qualityScore: 9,
          createdAt: base.createdAt }], page: 0, size: 20, total: 1, totalPages: 1 });
      if (url.endsWith(`/firewall-requests/${ID}`)) return respond(pending);
      return respond({ title: "Not found" }, 404);
    }),
  );
});

afterEach(() => vi.unstubAllGlobals());

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByLabelText(/Request Type/), "NEW_ACCESS");
  await user.selectOptions(screen.getByLabelText(/Request Priority/), "HIGH");
  await user.type(screen.getByLabelText(/Application \/ System Name/), "Payroll System");
  await user.type(screen.getByLabelText(/Source IP Address/), "10.10.5.20");
  await user.type(screen.getByLabelText(/Destination IP Address/), "203.0.113.10");
  await user.type(screen.getByLabelText(/Destination Port/), "443");
  await user.selectOptions(screen.getByLabelText(/Environment/), "PRODUCTION");
  await user.type(screen.getByLabelText(/^Business Justification/), "For payroll");
  await user.selectOptions(screen.getByLabelText(/Required Access Duration/), "3");
}

test("form looks like the enterprise portal and checks required fields first", async () => {
  window.location.hash = "#/new";
  const user = userEvent.setup();
  render(<App />);

  expect(screen.getByRole("heading", { name: "Firewall Access Request" })).toBeInTheDocument();
  expect(screen.getByText("Generated on submit")).toBeInTheDocument();
  expect(screen.getByText("Draft")).toBeInTheDocument();
  expect(screen.getByText("Nawaf Alotaibi")).toBeInTheDocument();
  expect(screen.getByRole("radio", { name: "TCP" })).toHaveAttribute("aria-checked", "true");

  await user.click(screen.getByRole("button", { name: /Submit Request/ }));
  expect(screen.getAllByText("This field is required").length).toBeGreaterThanOrEqual(8);
  expect(calls.filter((c) => c.init?.method === "POST")).toHaveLength(0);
});

test("submit, AI asks for the missing why, then submitted", async () => {
  window.location.hash = "#/new";
  const user = userEvent.setup();
  render(<App />);

  await fillForm(user);
  await user.click(screen.getByRole("radio", { name: "UDP" }));
  await user.click(screen.getByRole("button", { name: /Submit Request/ }));

  expect(await screen.findByText("What happens to the business if this rule is not opened?")).toBeInTheDocument();
  expect(screen.getByText("FW-2026-00042")).toBeInTheDocument();
  const body = JSON.parse(String(calls.find((c) => c.init?.method === "POST")!.init!.body));
  expect(body).toMatchObject({ requesterName: "Nawaf Alotaibi", requestType: "NEW_ACCESS", priority: "HIGH",
    sourceIp: "10.10.5.20", destinationIp: "203.0.113.10", protocol: "UDP", environment: "PRODUCTION",
    durationType: "TEMPORARY" });
  expect(body.endDate).toBe(endDateFor({ duration: "3", startDate: body.startDate, endDate: "" }));
  expect(body).not.toHaveProperty("sourceHostname");

  await user.type(screen.getByRole("textbox"), "Salaries are late");
  await user.click(screen.getByRole("button", { name: /Send answers/ }));
  expect(await screen.findByText("Request submitted")).toBeInTheDocument();
});

test("server field errors appear under the right field", async () => {
  window.location.hash = "#/new";
  createResponse = { status: 400, body: { title: "Invalid request form",
    errors: [{ field: "sourceIp", message: "must be an IPv4 address or subnet" }] } };
  const user = userEvent.setup();
  render(<App />);

  await fillForm(user);
  await user.click(screen.getByRole("button", { name: /Submit Request/ }));

  expect(await screen.findByText("must be an IPv4 address or subnet")).toBeInTheDocument();
  expect(screen.getByLabelText(/Source IP Address/)).toHaveAttribute("aria-invalid", "true");
});

test("save draft keeps the form for later", async () => {
  window.location.hash = "#/new";
  const user = userEvent.setup();
  render(<App />);

  await user.type(screen.getByLabelText(/Application \/ System Name/), "Billing Portal");
  await user.click(screen.getByRole("button", { name: /Save Draft/ }));

  expect(screen.getByText("Draft saved")).toBeInTheDocument();
  expect(JSON.parse(localStorage.getItem("fw-draft")!)).toMatchObject({ applicationName: "Billing Portal" });
});

test("queue opens the request; engineer approves", async () => {
  window.location.hash = "#/requests";
  const user = userEvent.setup();
  render(<App />);

  const ticket = await screen.findByRole("link", { name: "FW-2026-00042" });
  expect(within(ticket.closest("tr")!).getByText("Pending review")).toBeInTheDocument();
  await user.click(ticket);

  expect(await screen.findByText(/Port \/ protocol: TCP 443/)).toBeInTheDocument();
  const approve = screen.getByRole("button", { name: "Approve" });
  expect(approve).toBeDisabled();
  await user.type(screen.getByLabelText(/Engineer name/), "Sara");
  await user.click(approve);

  expect((await screen.findAllByText("Approved")).length).toBeGreaterThan(0);
  const decision = JSON.parse(String(calls.find((c) => c.url.endsWith("/decision"))!.init!.body));
  expect(decision).toEqual({ decision: "APPROVED", engineer: "Sara" });
});

test("duration presets become an end date", () => {
  expect(endDateFor({ duration: "3", startDate: "2026-11-20", endDate: "" })).toBe("2027-02-20");
  expect(endDateFor({ duration: "PERMANENT", startDate: "2026-11-20", endDate: "" })).toBeUndefined();
  expect(endDateFor({ duration: "CUSTOM", startDate: "2026-11-20", endDate: "2026-12-01" })).toBe("2026-12-01");
});
