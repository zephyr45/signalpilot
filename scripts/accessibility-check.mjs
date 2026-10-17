import AxeBuilder from "@axe-core/playwright";
import { chromium } from "playwright";
import { mkdir, writeFile } from "node:fs/promises";

const url = process.env.SIGNALPILOT_URL || "http://127.0.0.1:8091";
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const consoleErrors = [];
page.on("console", (message) => {
  if (message.type() === "error") consoleErrors.push(message.text());
});
page.on("pageerror", (error) => consoleErrors.push(error.message));

try {
  await page.goto(url, { waitUntil: "networkidle" });
  const launch = page.getByRole("button", { name: "Launch checkout incident" });
  if (await launch.isVisible()) await launch.click();
  await page.locator("#incident-title").waitFor({ state: "visible" });

  const axe = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .analyze();
  const blocking = axe.violations.filter((violation) =>
    ["critical", "serious"].includes(violation.impact),
  );
  if (blocking.length) {
    throw new Error(
      "Blocking axe violations: " +
        blocking
          .map((violation) => violation.id + " (" + violation.nodes.length + ")")
          .join(", "),
    );
  }

  const investigation = page.getByRole("tab", { name: "Investigation" });
  await investigation.focus();
  await page.keyboard.press("ArrowRight");
  const evidence = page.getByRole("tab", { name: /Evidence/ });
  if (
    (await evidence.getAttribute("aria-selected")) !== "true" ||
    !(await evidence.evaluate((element) => element === document.activeElement))
  ) {
    throw new Error("ArrowRight did not select and focus the next tab");
  }
  await page.keyboard.press("End");
  const postmortem = page.getByRole("tab", { name: "Postmortem" });
  if (
    (await postmortem.getAttribute("aria-selected")) !== "true" ||
    !(await postmortem.evaluate((element) => element === document.activeElement))
  ) {
    throw new Error("End did not select and focus the last tab");
  }

  await page.setViewportSize({ width: 320, height: 844 });
  const mobile = await page.evaluate(() => {
    const selector =
      'a[href],button,input,select,textarea,[role="button"],[role="tab"]';
    const undersized = [...document.querySelectorAll(selector)]
      .filter((element) => {
        const style = getComputedStyle(element);
        const box = element.getBoundingClientRect();
        return (
          element.getClientRects().length > 0 &&
          style.visibility !== "hidden" &&
          style.display !== "none" &&
          !element.closest("[hidden]") &&
          (box.width < 44 || box.height < 44)
        );
      })
      .map((element) => ({
        name:
          element.getAttribute("aria-label") ||
          element.textContent?.trim().slice(0, 60) ||
          element.id,
        width: Math.round(element.getBoundingClientRect().width),
        height: Math.round(element.getBoundingClientRect().height),
      }));
    const overflowing = [...document.querySelectorAll("body *")]
      .filter((element) => {
        const style = getComputedStyle(element);
        const box = element.getBoundingClientRect();
        return (
          style.visibility !== "hidden" &&
          style.display !== "none" &&
          !element.closest("[hidden]") &&
          (box.left < -1 || box.right > window.innerWidth + 1)
        );
      })
      .map((element) => ({
        tag: element.tagName.toLowerCase(),
        id: element.id,
        className: element.className,
        left: Math.round(element.getBoundingClientRect().left),
        right: Math.round(element.getBoundingClientRect().right),
      }))
      .slice(0, 20);
    return {
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      undersized,
      overflowing,
    };
  });
  if (mobile.horizontalOverflow)
    throw new Error("Page overflows horizontally at 320px: " + JSON.stringify(mobile));
  if (mobile.undersized.length)
    throw new Error("Undersized controls: " + JSON.stringify(mobile.undersized));
  if (consoleErrors.length)
    throw new Error("Browser console errors: " + consoleErrors.join(" | "));

  const report = {
    checkedAt: new Date().toISOString(),
    url,
    axeViolations: axe.violations,
    keyboardTabs: "passed",
    mobile320: "passed",
    consoleErrors,
  };
  await mkdir("target", { recursive: true });
  await writeFile(
    "target/accessibility-verification.json",
    JSON.stringify(report, null, 2),
  );
  console.log("Accessibility checks passed");
} finally {
  await context.close();
  await browser.close();
}
