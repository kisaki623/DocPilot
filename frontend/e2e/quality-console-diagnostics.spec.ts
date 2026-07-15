import { expect, test, type Page } from "@playwright/test";

function apiBody(data: unknown) {
  return JSON.stringify({ code: 0, message: "success", data });
}

function qualityRun(marker: string, status: string) {
  return {
    marker,
    source: "backend/target/agent-quality-eval",
    artifactName: `${marker}/artifact.json`,
    status,
    updatedAt: "2026-07-15T00:00:00Z",
    gateCount: 1,
    failedGateCount: status.startsWith("FAILED") ? 1 : 0,
    reviewGateCount: status === "REVIEW" ? 1 : 0,
    failureBuckets: [],
    reviewBuckets: [],
    tokenUsage: {},
    artifactMissing: false,
    artifactParseFailed: false,
    environment: "playwright",
    dataSource: "artifact_import",
    importedAt: "2026-07-15T00:00:00Z",
  };
}

function trendPoint(marker: string, status: string, index: number) {
  return {
    marker,
    status,
    updatedAt: `2026-07-15T00:${String(index).padStart(2, "0")}:00Z`,
    failedGateCount: status.startsWith("FAILED") ? 1 : 0,
    reviewGateCount: status === "REVIEW" ? 1 : 0,
    casePassRate: status === "PASS" ? 1 : 0,
    totalTokens: null,
    estimatedCost: null,
    latencyMs: null,
    durationMs: index < 5 ? 4000 + index * 100 : null,
    failureBuckets: [],
    reviewBuckets: [],
  };
}

async function prepareQualityPage(page: Page) {
  const runs = [
    ...Array.from({ length: 9 }, (_, index) => qualityRun(`pass-${index}`, "PASS")),
    ...Array.from({ length: 6 }, (_, index) => qualityRun(`review-${index}`, "REVIEW")),
    ...Array.from({ length: 5 }, (_, index) => qualityRun(`failed-${index}`, "FAILED_CORE_FLOW")),
  ];
  const points = runs.map((run, index) => trendPoint(run.marker, run.status, index));

  await page.addInitScript(() => {
    window.localStorage.setItem("docpilot_token", "quality-console-diagnostics-fixture-token");
  });

  await page.route("**/backend/api/quality/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/backend/api/quality/status") {
      await route.fulfill({
        contentType: "application/json",
        body: apiBody({
          enabled: true,
          authorized: true,
          reason: "OK",
          dataMode: "DB",
          runCount: 20,
          lastImportedAt: "2026-07-15T00:30:00Z",
          environment: "playwright",
        }),
      });
      return;
    }
    if (path === "/backend/api/quality/runs") {
      await route.fulfill({ contentType: "application/json", body: apiBody(runs) });
      return;
    }
    if (path === "/backend/api/quality/trends") {
      await route.fulfill({
        contentType: "application/json",
        body: apiBody({
          limit: 20,
          runCount: 20,
          statusCounts: { PASS: 9, REVIEW: 6, FAILED_CORE_FLOW: 5 },
          failureBucketCounts: {},
          reviewBucketCounts: {},
          averageCasePassRate: 0.45,
          totalTokens: null,
          estimatedCost: null,
          averageLatencyMs: null,
          averageDurationMs: 4200,
          repeatedCases: [],
          points,
          domainTrends: {},
        }),
      });
      return;
    }
    if (path === "/backend/api/quality/eval-cases") {
      await route.fulfill({ contentType: "application/json", body: apiBody([]) });
      return;
    }
    const marker = decodeURIComponent(path.replace("/backend/api/quality/runs/", ""));
    const summary = runs.find((run) => run.marker === marker) || runs[0];
    await route.fulfill({
      contentType: "application/json",
      body: apiBody({
        summary,
        gates: [],
        evalCases: [],
        traceReferences: [],
        diagnostics: {
          runObservation: {
            schemaVersion: 1,
            suiteId: "memory_quality",
            suiteVersion: "2026-07-15",
            coverageProfile: "runtime_full",
            startedAt: "2026-07-15T00:00:00Z",
            finishedAt: "2026-07-15T00:01:00Z",
            durationMs: 60000,
            latencyMs: null,
            sampleGaps: ["tokenUsageMissing", "costMetricMissing"],
          },
        },
      }),
    });
  });

  const consoleErrors: string[] = [];
  const pageErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      consoleErrors.push(message.text());
    }
  });
  page.on("pageerror", (error) => pageErrors.push(error.message));

  await page.goto("/quality?autoload=1", { waitUntil: "networkidle" });
  return { consoleErrors, pageErrors };
}

test("quality overview diagnostics expose sample gaps without hiding failures", async ({ page }) => {
  const fixture = await prepareQualityPage(page);

  await expect(page.getByText("45.0% (9 / 20)")).toBeVisible();
  await expect(page.getByText("30.0% (6 / 20)")).toBeVisible();
  await expect(page.getByText("25.0% (5 / 20)")).toBeVisible();
  await expect(page.getByText("暂无 latency 样本 (0 / 20)")).toBeVisible();
  await expect(page.getByText("暂无 token 样本 (0 / 20)")).toBeVisible();
  await expect(page.getByText("暂无成本样本 (0 / 9)")).toBeVisible();
  await expect(page.getByText(/已有 5 \/ 20 条整次运行 durationMs/)).toBeVisible();
  await expect(page.getByText(/存在失败运行，但 artifact 没有提供结构化 failure bucket/)).toBeVisible();
  await expect(page.getByText(/存在复查运行，但 artifact 没有提供结构化 review bucket/)).toBeVisible();
  await expect(page.getByText("memory_quality")).toBeVisible();
  await expect(page.getByText("真实链路完整覆盖")).toBeVisible();
  await expect(page.getByText("缺少 token 样本 / 缺少成本样本").first()).toBeVisible();
  await expect(page.getByText("缺少 token 样本 / 缺少成本样本")).toHaveCount(2);
  await expect(page.getByText("平均模型延迟")).toBeVisible();
  await expect(page.getByText("4,400 (5 / 20)")).toHaveCount(0);

  expect(fixture.pageErrors).toEqual([]);
  expect(fixture.consoleErrors).toEqual([]);
});
