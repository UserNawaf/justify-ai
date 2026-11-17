import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import App from "./App";

const templates = [{ id: "firewall-rule", name: "طلب فتح قاعدة جدار ناري", description: "", fields: [] }];
const needsInfo = {
  id: "abc",
  templateId: "firewall-rule",
  status: "NEEDS_INFO",
  round: 1,
  maxRounds: 3,
  questions: { duration: "القاعدة دائمة ولا مؤقتة؟" },
  qualityScore: 7.2,
  completeness: 0.83,
  clarity: 6,
};
const complete = {
  ...needsInfo,
  status: "COMPLETE",
  round: 2,
  questions: undefined,
  standardizedText: "Request type: Firewall rule request\nDuration: Permanent",
  qualityScore: 9.2,
};

let calls: { url: string; init?: RequestInit }[] = [];

function respond(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
}

beforeEach(() => {
  calls = [];
  localStorage.clear();
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init?: RequestInit) => {
      calls.push({ url, init });
      if (url.endsWith("/templates")) return respond(templates);
      if (url.endsWith("/answers")) return respond(complete);
      if (url.endsWith("/justifications")) return respond(needsInfo, 201);
      return respond({ title: "Not found" }, 404);
    }),
  );
});

afterEach(() => vi.unstubAllGlobals());

test("submit, answer the question, get the fixed-format result", async () => {
  const user = userEvent.setup();
  render(<App />);

  expect(await screen.findByRole("option", { name: "طلب فتح قاعدة جدار ناري" })).toBeInTheDocument();
  expect(document.documentElement.dir).toBe("rtl");

  await user.type(screen.getByRole("textbox"), "افتحوا 443 من سيرفر الرواتب للبنك");
  await user.click(screen.getByRole("button", { name: "تحقق من المبرر" }));

  expect(await screen.findByText("القاعدة دائمة ولا مؤقتة؟")).toBeInTheDocument();
  expect(screen.getByText("الجولة 1 من 3")).toBeInTheDocument();
  const submit = calls.find((c) => c.url.endsWith("/justifications"))!;
  expect(JSON.parse(String(submit.init!.body))).toMatchObject({ templateId: "firewall-rule", source: "web" });
  expect((submit.init!.headers as Record<string, string>)["Accept-Language"]).toBe("ar");

  await user.type(screen.getByRole("textbox"), "دائمة");
  await user.click(screen.getByRole("button", { name: "إرسال الإجابات" }));

  expect(await screen.findByText("المبرر جاهز")).toBeInTheDocument();
  expect(screen.getByText(/Duration: Permanent/)).toBeInTheDocument();
  expect(screen.getByText(/9\.2 \/ 10/)).toBeInTheDocument();
  const answer = calls.find((c) => c.url.endsWith("/answers"))!;
  expect(JSON.parse(String(answer.init!.body))).toEqual({ answers: { duration: "دائمة" } });
});

test("model outage shows a friendly message", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) =>
      url.endsWith("/templates") ? respond(templates) : respond({ title: "Model unavailable", detail: "x" }, 503),
    ),
  );
  const user = userEvent.setup();
  render(<App />);

  await user.type(screen.getByRole("textbox"), "test");
  await user.click(screen.getByRole("button", { name: "تحقق من المبرر" }));

  expect(await screen.findByText("الموديل غير متاح الحين، جرب بعد دقيقة.")).toBeInTheDocument();
});

test("switching to English flips direction", async () => {
  const user = userEvent.setup();
  render(<App />);
  await user.click(screen.getByRole("button", { name: "English" }));

  await waitFor(() => expect(document.documentElement.dir).toBe("ltr"));
  expect(screen.getByRole("button", { name: "Check justification" })).toBeInTheDocument();
});
