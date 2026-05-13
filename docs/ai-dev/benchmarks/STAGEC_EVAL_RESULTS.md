# Stage C Eval Results

- GeneratedAt: `2026-04-18T18:58:42.2763129+00:00`
- Dataset: `docs/ai-dev/benchmarks/datasets/stagec_eval_dataset.json`
- BackendBaseUrl: `http://127.0.0.1:8081`
- DocumentId: `54`
- CaseCount / StreamPairs: `20 / 8`

## Core Metrics

| metric | value |
|---|---:|
| answerSuccessRate (%) | 90 |
| citationHitRate (%) | 100 |
| casePassRate (%) | 85 |
| responseTime avg (ms) | 3222.393 |
| responseTime p95 (ms) | 6785 |
| streamVsNonStreamConsistency (%) | 87.5 |
| stream first token avg (ms) | 59.625 |
| stream first token p95 (ms) | 97 |

## Gate

- Enabled: `True`
- Passed: `True`

| check | pass | expect | actual |
|---|---|---|---|
| caseCount | True | >= 18 | 20 |
| streamPairCount | True | >= 8 | 8 |
| answerSuccessRate | True | >= 65 | 90 |
| citationHitRate | True | >= 70 | 100 |
| casePassRate | True | >= 60 | 85 |
| streamVsNonStreamConsistency | True | >= 80 | 87.5 |
| responseTimeP95Ms | True | <= 20000 | 6785 |
| streamFirstTokenP95Ms | True | <= 8000 | 97 |

## Per-case Detail

| caseId | mode | type | answerSuccess | citationSatisfied | casePass | keywordHits | citationHits | citationCount | decision | streamConsistent | streamReason | nonStreamMs | streamMs |
|---|---|---|---|---|---|---:|---:|---:|---|---|---|---:|---:|
| summary-core | qa | summary | False | True | False | 2 | 2 | 2 |  |  |  | 6785 |  |
| summary-boundary | qa | summary | True | True | True | 3 | 2 | 3 |  |  |  | 5680 |  |
| fact-default-ai-mode | qa | fact | True | True | True | 1 | 1 | 3 |  |  |  | 2166 |  |
| fact-real-prereq | qa | fact | True | True | True | 4 | 4 | 3 |  |  |  | 3455 |  |
| fact-upload-flow | qa | fact | True | True | True | 1 | 1 | 3 |  |  |  | 2250 |  |
| citation-fallback-policy | qa | citation | True | True | True | 2 | 1 | 2 |  |  |  | 3185 |  |
| citation-non-goal | qa | citation | True | True | True | 1 | 1 | 3 |  |  |  | 3939 |  |
| citation-engineering-highlights | qa | citation | True | True | True | 4 | 4 | 3 |  |  |  | 2970 |  |
| citation-rag-boundary | qa | citation | True | True | True | 2 | 2 | 3 |  |  |  | 6732 |  |
| citation-stream-consistency-policy | qa | citation | True | True | True | 1 | 1 | 3 |  |  |  | 2643 |  |
| stream-default-ai-mode-consistency | qa | consistency | True | True | True | 1 | 1 | 3 |  | True | text_equivalent | 3198 | 322 |
| stream-fallback-consistency | qa | consistency | True | True | True | 2 | 1 | 3 |  | True | text_equivalent | 5427 | 330 |
| stream-upload-flow-consistency | qa | consistency | False | True | False | 1 | 1 | 3 |  | False | keyword_or_forbidden_mismatch | 5537 | 344 |
| stream-rag-boundary-consistency | qa | consistency | True | True | True | 2 | 2 | 3 |  | True | text_equivalent | 2812 | 328 |
| stream-sse-contract-consistency | qa | consistency | True | True | True | 2 | 2 | 3 |  | True | text_equivalent | 2923 | 346 |
| stream-real-prereq-consistency | qa | consistency | True | True | True | 4 | 4 | 3 |  | True | text_equivalent | 3316 | 341 |
| stream-engineering-consistency | qa | consistency | True | True | True | 4 | 4 | 3 |  | True | text_equivalent | 3615 | 327 |
| stream-non-goal-consistency | qa | consistency | True | True | True | 1 | 1 | 3 |  | True | text_equivalent | 4521 | 374 |
| agent-summary-decision | agent | agent | True | True | False | 1 | 0 | 2 | qa_tool |  |  | 10907 |  |
| agent-evidence-decision | agent | agent | True | True | True | 4 | 4 | 3 | qa_tool |  |  | 5454 |  |

## Scoring Rules

- answerSuccess: `keywordHits >= minKeywordHits AND mustNotContainKeywords not hit`
- citationSatisfied: `expectCitation=true then citationKeywordHits >= minCitationKeywordHits AND citationCount/validCount >= minCitationCount; otherwise true`
- casePass: `answerSuccess AND citationSatisfied AND mode checks (and stream consistency for stream pair)`
- streamConsistency: `both sides pass keyword/forbidden checks AND citation overlap ratio >= threshold (if expectCitation) AND (text equivalent OR length delta ratio <= 0.35)`

## Boundary Notes

- Current QA is lightweight retrieval-enhanced QA, not vector RAG.
- This eval runs against current local/backend environment and does not represent online SLA.
- PDF parsing remains placeholder; main parsing support is txt/md.

## Artifact

- JSON: `docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`