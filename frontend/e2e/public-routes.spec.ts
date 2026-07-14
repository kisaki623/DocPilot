import { expect, test } from "@playwright/test";

const publicRoutes = [
  "/",
  "/login",
  "/dashboard",
  "/upload",
  "/documents",
  "/knowledge-bases",
  "/conversations",
  "/agent",
  "/agent/tools",
];

for (const route of publicRoutes) {
  test(`public route ${route} renders without browser errors`, async ({ page }) => {
    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];

    page.on("console", (message) => {
      if (message.type() === "error") {
        consoleErrors.push(message.text());
      }
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    const response = await page.goto(route, { waitUntil: "networkidle" });

    expect(response, `route ${route} should return a response`).not.toBeNull();
    expect(response?.status(), `route ${route} should not be an HTTP error`).toBeLessThan(400);
    await expect(page).toHaveTitle(/DocPilot/);
    await expect(page.locator("main")).toBeVisible();
    expect(pageErrors, `route ${route} page errors`).toEqual([]);
    expect(consoleErrors, `route ${route} console errors`).toEqual([]);
  });
}
