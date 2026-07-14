import { expect, test, type Page } from "@playwright/test";

const documentId = 123;

function apiBody(data: unknown) {
  return JSON.stringify({ code: 0, message: "success", data });
}

function documentDetail() {
  return {
    documentId,
    fileRecordId: 456,
    title: "SSE Failure Fixture",
    fileName: "sse-failure-fixture.txt",
    fileType: "txt",
    parseStatus: "SUCCESS",
    parseStatusLabel: "解析完成",
    summary: "route mocked document",
    content: "SSE browser regression fixture",
    createTime: "2026-07-10T00:00:00Z",
    updateTime: "2026-07-10T00:00:00Z",
  };
}

async function prepareDocumentPage(page: Page, streamBody: string) {
  let streamRequests = 0;
  let nonStreamRagRequests = 0;

  await page.addInitScript(() => {
    window.localStorage.setItem("docpilot_token", "playwright-route-token");
  });

  await page.route("**/backend/api/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;

    if (path === "/backend/api/document/detail") {
      await route.fulfill({ contentType: "application/json", body: apiBody(documentDetail()) });
      return;
    }
    if (path === "/backend/api/ai/qa/history") {
      await route.fulfill({ contentType: "application/json", body: apiBody([]) });
      return;
    }
    if (path === "/backend/api/task/parse/status") {
      await route.fulfill({
        contentType: "application/json",
        body: apiBody({
          taskId: 789,
          userId: 7,
          documentId,
          fileRecordId: 456,
          status: "SUCCESS",
          statusLabel: "解析完成",
          statusDescription: "解析已完成",
          documentParseStatus: "SUCCESS",
          terminal: true,
          processing: false,
          retryAllowed: false,
          reparseAllowed: true,
          safeReindexAllowed: true,
          contentOnlyReindexAllowed: false,
          parsedContentPresent: true,
          stale: false,
          recoveryAction: "NONE",
          recoveryDescription: "无需恢复操作",
          retryCount: 0,
          updateTime: "2026-07-10T00:00:00Z",
        }),
      });
      return;
    }
    if (path === "/backend/api/quality/status") {
      await route.fulfill({
        contentType: "application/json",
        body: apiBody({
          enabled: false,
          authorized: false,
          reason: "quality console disabled in e2e fixture",
          dataMode: "disabled",
          runCount: 0,
          lastImportedAt: null,
          environment: "playwright",
        }),
      });
      return;
    }
    if (path === `/backend/api/documents/${documentId}/qa/rag/stream`) {
      streamRequests += 1;
      await route.fulfill({
        contentType: "text/event-stream",
        headers: { "Cache-Control": "no-cache" },
        body: streamBody,
      });
      return;
    }
    if (path === `/backend/api/documents/${documentId}/qa/rag`) {
      nonStreamRagRequests += 1;
      await route.fulfill({
        contentType: "application/json",
        body: apiBody({
          documentId,
          question: "fixture question",
          answer: "FALLBACK-RAG-ANSWER-MARKER",
          sessionId: "fallback-session",
          noEvidence: false,
          retrieval: { documentId, query: "fixture question", hits: [], citations: [] },
          citations: [],
        }),
      });
      return;
    }
    await route.fulfill({ status: 404, contentType: "application/json", body: apiBody(null) });
  });

  const consoleErrors: string[] = [];
  const pageErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      consoleErrors.push(message.text());
    }
  });
  page.on("pageerror", (error) => pageErrors.push(error.message));

  await page.goto(`/documents/${documentId}`, { waitUntil: "networkidle" });
  await expect(page.locator("#qa-question-input")).toBeVisible();
  await expect(page.getByRole("button", { name: "发送问题" })).toBeEnabled();

  return {
    consoleErrors,
    pageErrors,
    getStreamRequests: () => streamRequests,
    getNonStreamRagRequests: () => nonStreamRagRequests,
  };
}

test("falls back once when RAG SSE fails before the first chunk", async ({ page }) => {
  const fixture = await prepareDocumentPage(
    page,
    [
      `event: meta\ndata: {"documentId":${documentId},"sessionId":"stream-session"}\n\n`,
      "event: error\ndata: {\"message\":\"stream generation failed\",\"stage\":\"generation\"}\n\n",
    ].join(""),
  );

  await page.locator("#qa-question-input").fill("fixture question");
  await page.getByRole("button", { name: "发送问题" }).click();

  await expect(page.getByText("FALLBACK-RAG-ANSWER-MARKER")).toBeVisible();
  await expect(page.getByText(/已自动切换为非流式问答/)).toBeVisible();
  expect(fixture.getStreamRequests()).toBe(1);
  expect(fixture.getNonStreamRagRequests()).toBe(1);
  expect(fixture.pageErrors).toEqual([]);
  expect(fixture.consoleErrors).toEqual([]);
});

test("keeps partial RAG SSE output without a non-stream retry", async ({ page }) => {
  const fixture = await prepareDocumentPage(
    page,
    [
      `event: meta\ndata: {"documentId":${documentId},"sessionId":"stream-session"}\n\n`,
      "event: chunk\ndata: PARTIAL-RAG-ANSWER-MARKER\n\n",
      "event: error\ndata: {\"message\":\"stream interrupted\",\"stage\":\"generation_partial\"}\n\n",
    ].join(""),
  );

  await page.locator("#qa-question-input").fill("fixture question");
  await page.getByRole("button", { name: "发送问题" }).click();

  await expect(page.getByText("PARTIAL-RAG-ANSWER-MARKER")).toBeVisible();
  await expect(page.getByText("实时输出中断，已保留当前已生成内容。请重试获取完整回答。")).toBeVisible();
  expect(fixture.getStreamRequests()).toBe(1);
  expect(fixture.getNonStreamRagRequests()).toBe(0);
  expect(fixture.pageErrors).toEqual([]);
  expect(fixture.consoleErrors).toEqual([]);
});

test("falls back once when RAG SSE reaches EOF before done and before the first chunk", async ({ page }) => {
  const fixture = await prepareDocumentPage(
    page,
    `event: meta\ndata: {"documentId":${documentId},"sessionId":"stream-session"}\n\n`,
  );

  await page.locator("#qa-question-input").fill("fixture question");
  await page.getByRole("button", { name: "发送问题" }).click();

  await expect(page.getByText("FALLBACK-RAG-ANSWER-MARKER")).toBeVisible();
  await expect(page.getByText(/已自动切换为非流式问答/)).toBeVisible();
  expect(fixture.getStreamRequests()).toBe(1);
  expect(fixture.getNonStreamRagRequests()).toBe(1);
  expect(fixture.pageErrors).toEqual([]);
  expect(fixture.consoleErrors).toEqual([]);
});

test("keeps partial RAG SSE output when EOF arrives before done", async ({ page }) => {
  const fixture = await prepareDocumentPage(
    page,
    [
      `event: meta\ndata: {"documentId":${documentId},"sessionId":"stream-session"}\n\n`,
      "event: chunk\ndata: PARTIAL-EOF-RAG-ANSWER-MARKER\n\n",
    ].join(""),
  );

  await page.locator("#qa-question-input").fill("fixture question");
  await page.getByRole("button", { name: "发送问题" }).click();

  await expect(page.getByText("PARTIAL-EOF-RAG-ANSWER-MARKER")).toBeVisible();
  await expect(page.getByText("实时输出中断，已保留当前已生成内容。请重试获取完整回答。")).toBeVisible();
  expect(fixture.getStreamRequests()).toBe(1);
  expect(fixture.getNonStreamRagRequests()).toBe(0);
  expect(fixture.pageErrors).toEqual([]);
  expect(fixture.consoleErrors).toEqual([]);
});

test("accepts a normal RAG SSE done event without fallback", async ({ page }) => {
  const fixture = await prepareDocumentPage(
    page,
    [
      `event: meta\ndata: {"documentId":${documentId},"sessionId":"stream-session"}\n\n`,
      "event: chunk\ndata: COMPLETE-RAG-ANSWER-MARKER\n\n",
      "event: done\ndata: {\"sessionId\":\"stream-session\"}\n\n",
    ].join(""),
  );

  await page.locator("#qa-question-input").fill("fixture question");
  await page.getByRole("button", { name: "发送问题" }).click();

  await expect(page.getByText("COMPLETE-RAG-ANSWER-MARKER")).toBeVisible();
  expect(fixture.getStreamRequests()).toBe(1);
  expect(fixture.getNonStreamRagRequests()).toBe(0);
  expect(fixture.pageErrors).toEqual([]);
  expect(fixture.consoleErrors).toEqual([]);
});
