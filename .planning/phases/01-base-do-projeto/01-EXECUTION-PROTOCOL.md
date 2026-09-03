# Phase 01 Execution Protocol — Orchestrator Contract

## Purpose and authority

This document governs the orchestration boundary around the five Phase 01 PLANs. It is not a task for `gsd-executor` and its commands are never executed automatically. The orchestrator runs a command block only after the corresponding explicit user approval.

Never execute Phase 01 in one-shot mode. Dispatch exactly one PLAN at a time to one `gsd-executor`. The executor implements and validates that ticket, creates and commits `{plan}-SUMMARY.md`, and then returns. Push, pull-request management, merge, remote proof, synchronization of `main`, and dispatch of the next PLAN all happen outside the PLAN XML because the SUMMARY exists only after its tasks finish.

The GSD plan-phase never creates, renames, or switches branches. Branch operations begin only during the user-approved execution of a single ticket.

## Fixed sequence

| PLAN | Branch | Required predecessor on `origin/main` |
|------|--------|----------------------------------------|
| `01-01` | `ticket/01-setup-spring-boot` | none |
| `01-02` | `ticket/02-enums` | `01-01-SUMMARY.md` and every Ticket 01 commit listed in it |
| `01-03` | `ticket/03-entidades-jpa` | `01-02-SUMMARY.md` and every Ticket 02 commit listed in it |
| `01-04` | `ticket/04-repositories-jpa` | `01-03-SUMMARY.md` and every Ticket 03 commit listed in it |
| `01-05` | `ticket/05-flyway-validacao` | `01-04-SUMMARY.md` and every Ticket 04 commit listed in it |

The dependency chain is strictly `01-01 → 01-02 → 01-03 → 01-04 → 01-05`. Branches are never stacked. A failed proof blocks the workflow and forbids creation of the next branch.

## PowerShell fail-fast rule

All examples target Windows PowerShell 5.1. After every native `git` or `gh` invocation, inspect `$LASTEXITCODE` immediately and throw when it is nonzero. Do not join native commands with `;` or logical chaining operators in a way that can mask failure.

The snippets below are contracts for the orchestrator. Before using one, set the values from the fixed-sequence table:

```powershell
$ErrorActionPreference = 'Stop'
$Repo = 'C:\projetos'
$PhaseDir = '.planning/phases/01-base-do-projeto'
$Plan = '01-01'
$Branch = 'ticket/01-setup-spring-boot'
$Summary = "$PhaseDir/$Plan-SUMMARY.md"
Set-Location $Repo
```

## Gate 1 — approval before code and predecessor proof

Before dispatching a PLAN, the orchestrator presents that PLAN to the user and obtains explicit approval for that ticket only. Approval does not authorize any later ticket, push, PR, or merge.

For `01-02` through `01-05`, set `$PreviousSummary` to the predecessor path and run this fail-closed proof before creating the new branch or writing code:

```powershell
& git fetch origin main
if ($LASTEXITCODE -ne 0) { throw 'git fetch origin main failed' }

$remoteSummaryText = & git show "origin/main:$PreviousSummary"
if ($LASTEXITCODE -ne 0) { throw "Previous SUMMARY is absent from origin/main: $PreviousSummary" }
if ([string]::IsNullOrWhiteSpace(($remoteSummaryText | Out-String))) { throw "Previous SUMMARY is empty: $PreviousSummary" }

$previousSummaryCommitLines = & git log -1 --format=%H origin/main -- $PreviousSummary
if ($LASTEXITCODE -ne 0) { throw "Could not locate predecessor SUMMARY commit: $PreviousSummary" }
$previousSummaryCommit = ($previousSummaryCommitLines | Out-String).Trim()
if ([string]::IsNullOrWhiteSpace($previousSummaryCommit)) { throw "Predecessor SUMMARY has no commit on origin/main: $PreviousSummary" }

$previousHashes = [regex]::Matches(($remoteSummaryText | Out-String), '(?<![0-9a-f])[0-9a-f]{7,40}(?![0-9a-f])') | ForEach-Object { $_.Value } | Select-Object -Unique
if (@($previousHashes).Count -eq 0) { throw "Predecessor SUMMARY lists no ticket commits: $PreviousSummary" }
foreach ($hash in $previousHashes) {
    & git cat-file -e "$hash^{commit}"
    if ($LASTEXITCODE -ne 0) { throw "Commit listed in predecessor SUMMARY is unavailable locally: $hash" }
    & git merge-base --is-ancestor $hash origin/main
    if ($LASTEXITCODE -ne 0) { throw "Commit listed in predecessor SUMMARY is not in origin/main: $hash" }
}
```

Only after that proof succeeds may the approved ticket synchronize `main` and create its fixed branch. Ticket 01 uses the same synchronization block without a predecessor proof:

```powershell
$preflightStatusLines = & git status --porcelain
if ($LASTEXITCODE -ne 0) { throw 'git status --porcelain failed before branch creation' }
$preflightStatus = ($preflightStatusLines | Out-String).Trim()
if (-not [string]::IsNullOrWhiteSpace($preflightStatus)) {
    throw "Working tree is not clean; preserve and resolve these changes before starting the ticket: $preflightStatus"
}
& git switch main
if ($LASTEXITCODE -ne 0) { throw 'git switch main failed' }
& git pull --ff-only origin main
if ($LASTEXITCODE -ne 0) { throw 'git pull --ff-only origin main failed' }
& git switch -c $Branch
if ($LASTEXITCODE -ne 0) { throw "Could not create approved ticket branch: $Branch" }
```

The orchestrator then dispatches exactly `$Plan` to `gsd-executor`. It does not dispatch the next PLAN in the same run.

## Gate 2 — executor return and local evidence

The executor must implement and validate the PLAN, create and commit `$Summary`, and return. The orchestrator then confirms the SUMMARY and every listed local commit before presenting the result to the user:

```powershell
Set-Location $Repo
$currentBranchLines = & git branch --show-current
if ($LASTEXITCODE -ne 0) { throw 'Could not read current branch' }
$currentBranch = ($currentBranchLines | Out-String).Trim()
if ($currentBranch -ne $Branch) { throw "Unexpected branch: $currentBranch; expected $Branch" }
if (-not (Test-Path -LiteralPath (Join-Path $Repo $Summary))) { throw "Executor did not create SUMMARY: $Summary" }

$summaryCommitLines = & git log -1 --format=%H -- $Summary
if ($LASTEXITCODE -ne 0) { throw "Could not locate local SUMMARY commit: $Summary" }
$summaryCommit = ($summaryCommitLines | Out-String).Trim()
if ([string]::IsNullOrWhiteSpace($summaryCommit)) { throw "SUMMARY is not committed: $Summary" }
& git merge-base --is-ancestor $summaryCommit HEAD
if ($LASTEXITCODE -ne 0) { throw "SUMMARY commit is not reachable from HEAD: $summaryCommit" }

$summaryText = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $Repo $Summary)
$ticketHashes = [regex]::Matches($summaryText, '(?<![0-9a-f])[0-9a-f]{7,40}(?![0-9a-f])') | ForEach-Object { $_.Value } | Select-Object -Unique
if (@($ticketHashes).Count -eq 0) { throw "SUMMARY lists no ticket commits: $Summary" }
foreach ($hash in $ticketHashes) {
    & git cat-file -e "$hash^{commit}"
    if ($LASTEXITCODE -ne 0) { throw "SUMMARY lists an unavailable local commit: $hash" }
    & git merge-base --is-ancestor $hash HEAD
    if ($LASTEXITCODE -ne 0) { throw "SUMMARY lists a commit not reachable from HEAD: $hash" }
}

$worktreeState = & git status --porcelain
if ($LASTEXITCODE -ne 0) { throw 'git status failed' }
if (-not [string]::IsNullOrWhiteSpace(($worktreeState | Out-String))) { throw 'Worktree is not clean after executor return' }
```

If any check fails, stop. Do not push, create a PR, merge, create the next branch, or dispatch the next PLAN.

## Gate 3 — user approval for push and PR

After local evidence passes, the orchestrator presents the ticket summary, validation results, scoped diff, and commits to the user. It explicitly asks whether to push the branch and create or update its PR. No command in this section runs before that approval.

After explicit approval only:

```powershell
& git push -u origin $Branch
if ($LASTEXITCODE -ne 0) { throw "git push failed: $Branch" }

$prNumberLines = & gh pr list --head $Branch --base main --state open --json number --jq '.[0].number'
if ($LASTEXITCODE -ne 0) { throw 'Could not query the pull request' }
$PrNumber = ($prNumberLines | Out-String).Trim()
if ([string]::IsNullOrWhiteSpace($PrNumber)) {
    $prUrlLines = & gh pr create --head $Branch --base main --title "$Plan — SolveIT" --body-file $Summary
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the pull request' }
    $PrUrl = ($prUrlLines | Out-String).Trim()
    $prNumberLines = & gh pr view $Branch --json number --jq '.number'
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the created pull request' }
    $PrNumber = ($prNumberLines | Out-String).Trim()
} else {
    & gh pr edit $PrNumber --base main --body-file $Summary
    if ($LASTEXITCODE -ne 0) { throw 'Could not update the pull request' }
    $prUrlLines = & gh pr view $PrNumber --json url --jq '.url'
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the updated pull request' }
    $PrUrl = ($prUrlLines | Out-String).Trim()
}
if ([string]::IsNullOrWhiteSpace($PrNumber)) { throw 'Pull request number is empty' }

& git fetch origin $Branch
if ($LASTEXITCODE -ne 0) { throw "Could not fetch remote ticket branch: $Branch" }
$remoteTicketSummary = & git show "origin/$($Branch):$Summary"
if ($LASTEXITCODE -ne 0) { throw "SUMMARY is absent from remote ticket branch: $Summary" }
foreach ($hash in $ticketHashes) {
    & git merge-base --is-ancestor $hash "origin/$Branch"
    if ($LASTEXITCODE -ne 0) { throw "Ticket commit is absent from remote branch: $hash" }
}
```

Successful push/PR does not authorize merge.

## Gate 4 — separate human approval for merge

After the PR exists, the orchestrator asks a separate question: authorize merge or keep the PR open. Keeping it open blocks the next ticket. No merge command runs without a distinct explicit authorization.

After merge approval only:

```powershell
& gh pr merge $PrNumber --merge
if ($LASTEXITCODE -ne 0) { throw "Pull request merge failed: $PrNumber" }

& git fetch origin main
if ($LASTEXITCODE -ne 0) { throw 'git fetch origin main failed after merge' }
$mainSummaryText = & git show "origin/main:$Summary"
if ($LASTEXITCODE -ne 0) { throw "Merged SUMMARY is absent from origin/main: $Summary" }
if ([string]::IsNullOrWhiteSpace(($mainSummaryText | Out-String))) { throw "Merged SUMMARY is empty on origin/main: $Summary" }
foreach ($hash in $ticketHashes) {
    & git merge-base --is-ancestor $hash origin/main
    if ($LASTEXITCODE -ne 0) { throw "Merged ticket commit is absent from origin/main: $hash" }
}
```

Only after every proof above succeeds may the orchestrator request pre-code approval for the next PLAN. The next ticket's Gate 1 is the sole owner of switching to `main`, synchronizing it by fast-forward only, and creating the approved branch before dispatch. Gate 4 deliberately performs no branch switch or branch creation. After `01-05`, the remote proof closes Phase 01; it never starts Phase 02 automatically.

## Failure behavior

Any failed command, missing or empty SUMMARY, missing listed commit, dirty worktree, PR error, merge conflict, remote mismatch, or non-fast-forward synchronization halts the protocol. Preserve the current branch and evidence for diagnosis. Do not force-push, rewrite history, merge partially, create the next branch, or compensate by stacking branches.
