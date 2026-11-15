#!/usr/bin/env bun
// Design-system / architecture scan with a ratchet. Identical file in companion-car and companion-phone.
//   bun ci/ratchet.ts            compare counts with ci/baseline.json and with the merge-base's ci/baseline.json
//   bun ci/ratchet.ts --print    print the counts as JSON (to refresh ci/baseline.json after a rule reaches a lower number)
// Rule semantics (design-system.md 3-2/3-3, architecture.md G-B2):
//   zero rules   baseline 0 and must stay 0.
//   ratchet      count may never exceed ci/baseline.json; the baseline itself may never exceed the merge-base's.
//                A count BELOW the baseline fails too ("lower ci/baseline.json") so a fix cannot silently regress later.
// No API calls: the merge-base content is read with `git show`.
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, relative } from "node:path";
import { spawnSync } from "node:child_process";

const root = new URL("..", import.meta.url).pathname.replace(/\/$/, "");
const repo = existsSync(join(root, "core/designsystem")) ? "car" : "phone";

function walk(dir: string, out: string[] = []): string[] {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) {
      if (name === "build" || name === "node_modules" || name === ".gradle") continue;
      walk(p, out);
    } else if (p.endsWith(".kt")) out.push(p);
  }
  return out;
}

const RX = {
  color: /\bColor\(\s*0x|\bColor\.(White|Black|Red|Green|Blue|Gray|Yellow|Cyan|Magenta|Transparent)\b|MaterialTheme\.colorScheme/,
  dp: /(?<![A-Za-z0-9_])[0-9][0-9_]*(\.[0-9]+)?f?\.(dp|sp)\b/,
  material3: /^\s*import\s+androidx\.compose\.material3/,
  foundation: /^\s*import\s+androidx\.compose\.foundation/,
  rawText: /(?<![A-Za-z0-9_.])(Text|Button)\(/,
};

function codeLines(file: string): string[] {
  return readFileSync(file, "utf8")
    .split("\n")
    .filter((l) => {
      const t = l.trimStart();
      return !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"));
    });
}

function count(files: string[], rx: RegExp): number {
  let n = 0;
  for (const f of files) for (const l of codeLines(f)) if (rx.test(l)) n++;
  return n;
}

const counts: Record<string, number> = {};
let zero: string[] = [];
if (repo === "car") {
  const screens = [...walk(join(root, "app/src")), ...walk(join(root, "feature"))].filter((f) => f.includes("/src/main/") || f.includes("/app/src/main"));
  const ds = walk(join(root, "core/designsystem/src/main"));
  counts["screens.color"] = count(screens, RX.color);
  counts["screens.dp"] = count(screens, RX.dp);
  counts["screens.material3"] = count(screens, RX.material3);
  counts["screens.foundation"] = count(screens, RX.foundation);
  counts["screens.rawText"] = count(screens, RX.rawText);
  counts["designsystem.color"] = count(ds, RX.color);
  counts["designsystem.dp"] = count(ds, RX.dp);
  zero = ["screens.color", "screens.material3", "screens.foundation", "screens.rawText"];
} else {
  const all = walk(join(root, "app/src/main"));
  const screens = all.filter((f) => !f.includes("/ui/"));
  counts["screens.color"] = count(screens, RX.color);
  counts["screens.dp"] = count(screens, RX.dp);
  counts["screens.material3"] = count(screens, RX.material3);
  counts["screens.foundation"] = count(screens, RX.foundation);
  counts["screens.rawText"] = count(screens, RX.rawText);
  zero = ["screens.rawText"];
}
// G-B2: no main .kt above 600 lines (zero).
const big = walk(root).filter((f) => f.includes("/src/main/") && !f.includes("/debug/") && !f.includes("/shared/") && readFileSync(f, "utf8").split("\n").length > 600);
counts["files.over600"] = big.length;
zero.push("files.over600");
// G-A1 (car): module dependency graph must match the declared table.
if (repo === "car") {
  const allowed: Record<string, string[]> = {
    app: ["core:designsystem", "core:model", "core:session", "core:vehicle", "feature:onboarding", "feature:memory", "feature:settings", "feature:home"],
    "core:session": ["core:model", "core:vehicle"],
    "core:vehicle": ["core:model"],
    "core:designsystem": [],
    "core:model": [],
    "feature:home": ["core:designsystem", "core:model", "core:session", "core:vehicle", "core:testing"],
    "feature:memory": ["core:designsystem", "core:model", "core:session", "core:testing"],
    "feature:settings": ["core:designsystem", "core:model", "core:session", "core:testing"],
    "feature:onboarding": ["core:designsystem", "core:model", "core:testing"],
  };
  let bad = 0;
  for (const [mod, ok] of Object.entries(allowed)) {
    bad += graphBad(join(root, mod.replace(":", "/"), "build.gradle.kts"), ok);
  }
  counts["modulegraph.violations"] = bad;
  zero.push("modulegraph.violations");
}
function graphBad(file: string, ok: string[]): number {
  if (!existsSync(file)) return 0;
  let n = 0;
  for (const m of readFileSync(file, "utf8").matchAll(/project\(":([a-z]+:[a-z]+)"\)/g)) {
    if (!ok.includes(m[1])) {
      console.error(`module graph: ${relative(root, file)} -> :${m[1]} is not in the allowed table`);
      n++;
    }
  }
  return n;
}

if (process.argv.includes("--print")) {
  console.log(JSON.stringify(counts, null, 2));
  process.exit(0);
}

const baselinePath = join(root, "ci/baseline.json");
const baseline: Record<string, number> = JSON.parse(readFileSync(baselinePath, "utf8"));
function git(...a: string[]) {
  return spawnSync("git", a, { cwd: root, encoding: "utf8" });
}
// merge-base baseline: only on MR pipelines (CI_MERGE_REQUEST_DIFF_BASE_SHA) or when origin/main is reachable.
const mbSha = process.env.CI_MERGE_REQUEST_DIFF_BASE_SHA ?? "";
let mb: Record<string, number> | null = null;
if (mbSha) {
  const r = git("show", `${mbSha}:ci/baseline.json`);
  if (r.status === 0) mb = JSON.parse(r.stdout);
}
const fails: string[] = [];
for (const [k, v] of Object.entries(counts)) {
  const b = baseline[k];
  if (b === undefined) { fails.push(`${k}: not in ci/baseline.json (count ${v})`); continue; }
  if (zero.includes(k) && b !== 0) fails.push(`${k}: zero rule but baseline is ${b}`);
  if (v > b) fails.push(`${k}: ${v} > baseline ${b} (new violations)`);
  else if (v < b) fails.push(`${k}: ${v} < baseline ${b} — lower ci/baseline.json to ${v} (ratchet tightens)`);
  if (mb && mb[k] !== undefined && b > mb[k]) fails.push(`${k}: baseline raised ${mb[k]} -> ${b} (not allowed)`);
}
console.log(`ratchet (${repo}): ` + Object.entries(counts).map(([k, v]) => `${k}=${v}/${baseline[k]}`).join(" "));
if (fails.length) {
  console.error("RATCHET FAIL\n" + fails.join("\n"));
  process.exit(1);
}
console.log("ratchet ok");
