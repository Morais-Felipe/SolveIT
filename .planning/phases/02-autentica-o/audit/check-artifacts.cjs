const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const root = path.resolve(__dirname, '../../../..');
const cli = path.join(root, '.codex/gsd-core/bin/gsd-tools.cjs');
const { extractFrontmatter } = require(path.join(root, '.codex/gsd-core/bin/lib/frontmatter.cjs'));
const phase = path.dirname(__dirname);
const scope = new Set();
const results = [];
const run = args => JSON.parse(cp.execFileSync(process.execPath, [cli, ...args], {
  cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe']
}));
for (let number = 1; number <= 8; number++) {
  const id = '02-' + String(number).padStart(2, '0');
  const planText = fs.readFileSync(path.join(phase, id + '-PLAN.md'), 'utf8');
  const summaryPath = path.join(phase, id + '-SUMMARY.md');
  const summaryText = fs.readFileSync(summaryPath, 'utf8');
  const plan = extractFrontmatter(planText);
  const summary = extractFrontmatter(summaryText);
  const keyFiles = summary['key-files'] || {};
  const files = [...(keyFiles.created || []), ...(keyFiles.modified || [])];
  files.forEach(file => scope.add(file));
  const missingFiles = files.filter(file => !fs.existsSync(path.join(root, file)));
  const invalidTestRefs = [];
  for (const match of summaryText.matchAll(/ref:\s*(solveit-api\/[^\s#]+\.java)(?:#([^\s]+))?/g)) {
    const file = path.join(root, match[1]);
    if (!fs.existsSync(file) || (match[2] && !fs.readFileSync(file, 'utf8').includes(match[2] + '('))) {
      invalidTestRefs.push(match[0]);
    }
  }
  const summaryRelative = path.relative(root, summaryPath).replace(/\\/g, '/');
  const verify = run(['verify-summary', summaryRelative]);
  const coverage = run(['uat', 'classify-coverage', '--summary', summaryRelative]);
  results.push({ id, requirements: plan.requirements,
    requirementSetsMatch: JSON.stringify([...plan.requirements].sort()) === JSON.stringify([...summary['requirements-completed']].sort()),
    summaryStatus: summary.status, summaryVerified: verify.passed, summaryErrors: verify.errors,
    coverageTotal: coverage.total, coverageAutoCovered: coverage.all_auto_covered,
    coverageErrors: coverage.errors, missingFiles, invalidTestRefs });
}
const git = args => cp.execFileSync('git', ['-c', 'safe.directory=C:/SolveIT', ...args], { cwd: root, encoding: 'utf8' }).trim();
const changed = git(['diff', '--name-only', '57b3f4e^..ee8c6b9', '--', 'solveit-api']).split(/\r?\n/).filter(Boolean);
const omittedFromSummaries = changed.filter(file => !scope.has(file));
changed.forEach(file => scope.add(file));
const result = { auditedCommit: 'ee8c6b9', base: git(['rev-parse', '57b3f4e^']),
  plans: results, omittedFromSummaries, scope: [...scope].sort(),
  note: 'Structural checks only; passing coverage metadata does not disprove audit findings.' };
fs.writeFileSync(path.join(__dirname, 'artifact-checks.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
