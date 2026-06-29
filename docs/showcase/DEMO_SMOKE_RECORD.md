# DocPilot Demo Smoke Record

> Last updated: 2026-06-29

This file records the demo smoke evidence collected during the A1 real-link verification. It is intended for interview/showcase preparation and should keep implementation boundaries explicit.

## 2026-06-29 Memory Governance Edit / Resolve Smoke

Status: PASS

Runner:

- `scripts/smoke/memory-quality-smoke.ps1`

Marker: `docpilot-memory-quality-20260629140941-6668d9`

Verified gates:

- Memory governance now supports explicit user actions for conflicting suggestions: keep the active memory, replace the active memory with the suggestion, or merge with user-confirmed content.
- `memoryQuality` passed: conflicting suggestion direct accept was blocked, `KEEP_ACTIVE` moved the suggestion to `IGNORED`, `REPLACE_ACTIVE` updated the active memory, sensitive edit was rejected with code `1028`, normal edit persisted with priority `46`, and `MERGE_WITH_ACTIVE` updated the active memory.
- Core real-link gates remained PASS: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, answer grounding, no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Conversation Trace still separated memory and RAG evidence: `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=6`, `memoryCount=1`, `evidenceCount=6`.

Boundary: artifact is stored under ignored `backend/target/memory-quality/.../artifact.json`; do not commit artifact raw content, memory text, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a user-controlled memory governance smoke, not a large-scale personalization benchmark or real-model memory extraction evaluation.

## 2026-06-29 RAG Hard Negative Support Gate Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629130454-1d1d6c`

Verified gates:

- The v3.6 hard-negative REVIEW was addressed by a near-threshold evidence support gate in KnowledgeBase retrieval.
- `realQaHardGate` passed: hard negative returned `0` retrieve hits and `0` QA citations; answer faithfulness kept target citation count `1` and forbidden citation count `0`.
- Core real-link gates remained PASS: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, ordinary no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Representative Corpus KB returned `8` retrieve hits and `8` citations, with documentHitCounts covering Gamma `214:2`, Beta `213:3`, Alpha `212:3`.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. The support gate is a narrow near-threshold heuristic, not a general entailment model or large-scale benchmark.

## 2026-06-29 RAG Real QA Hard Gate Smoke

Status: REVIEW

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629125627-c0915e`

Verified gates:

- `cloud-quality-smoke.ps1` now includes optional `realQaHardGate`; `rag-real-qa-eval-smoke.ps1` enables it by default and can skip it with `-SkipRealQaHardGate`.
- Core real-link gates passed: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, ordinary no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- `answerFaithfulness` passed: target citation count `1`, forbidden citation count `0`, expected marker satisfied, forbidden marker absent and citation marker present.
- `hardNegative` remained REVIEW: a high lexical-overlap unsupported question returned `3` retrieve hits and `3` QA citations, with vector scores around `0.50-0.55`.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is small real-link evidence of a hard-negative quality gap, not a large-scale benchmark.

## 2026-06-29 RAG Answer Grounding Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629003157-630db5`

Verified gates:

- `cloud-quality-smoke.ps1` now includes `answerGrounding` for single-document RAG, KnowledgeBase RAG and representative corpus QA answers.
- `rag-real-qa-eval-smoke.ps1` plan output includes `answer_grounding` and `answerGrounding`.
- Single-document, KnowledgeBase and representative corpus answer checks all passed: answer present, expected evidence markers satisfied, forbidden marker absent and citation marker present.
- Representative Corpus KB still returned `8` retrieve hits and `8` citations.
- documentHitCounts covered all three temporary documents: Gamma `203:2`, Beta `202:3`, Alpha `201:3`.
- Delegated cloud quality gates passed: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, representative corpus, populated-KB no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a small real-link answer grounding gate, not a large-scale answer faithfulness benchmark or online SLA.

## 2026-06-28 RAG Real Corpus Representative Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260628234235-5c1b94`

Verified gates:

- `rag-real-qa-eval-smoke.ps1` now defaults to the representative corpus gate and can skip it with `-SkipRepresentativeCorpusGate`.
- Delegated cloud quality gates passed: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, populated-KB no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Representative Corpus KB contained Alpha, Beta and Gamma temporary documents.
- Representative gate returned `8` retrieve hits and `8` citations.
- documentHitCounts covered all three documents: Gamma `196:2`, Beta `195:3`, Alpha `194:3`.
- Conversation Trace still showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=6`.
- Permission isolation negative checks and artifact redaction remained PASS.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a small real-link representative corpus gate, not a large-scale relevance benchmark or online SLA.

## 2026-06-28 RAG Real QA Eval Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260628164757-ac2a1d`

Verified gates:

- Local MySQL / Qdrant tunnel was started by the runner.
- Backend health and seven frontend routes passed.
- Temporary user A / user B, two txt documents, KnowledgeBase and Conversation were created by the runner.
- Two documents parsed and indexed successfully; each had `3` MySQL chunks and `3` matched Qdrant points.
- Chunk quality passed offset ordering, length/token checks, duplicate hash checks and indexed vector id checks.
- MySQL / Qdrant payload consistency passed with no missing vector IDs or structure payload fields.
- Single-document RAG returned `3` hits and `3` citations.
- KnowledgeBase RAG returned `6` hits and `6` citations, with document distribution covering both temporary documents.
- Populated-KB no-evidence gate returned `0` hits and `0` citations.
- Conversation Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=6`, and two-document hit distribution.
- Permission isolation negative checks passed for foreign KB detail, foreign KB retrieve, foreign document add and foreign trace access.
- Artifact redaction and cleanup gates passed.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a small real-link smoke quality gate, not a large-scale relevance benchmark or online SLA.

## 2026-06-28 Memory Quality Smoke

Status: PASS

Runner:

- `scripts/smoke/memory-quality-smoke.ps1`

Marker: `docpilot-memory-quality-20260628193150-625bf6`

Verified gates:

- Local MySQL / Qdrant tunnel, backend health and seven frontend routes passed.
- Temporary user A / user B, two txt documents, KnowledgeBase, Conversation and memory records were created by the runner.
- Delegated cloud quality gates passed: upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase two-document RAG, populated-KB no-evidence, Conversation Trace, permission isolation and artifact redaction.
- Memory quality gate extracted `2` suggestions from a real temporary conversation.
- Accepted suggestion became `ACTIVE`; ignored suggestion became `IGNORED` and was absent from the ACTIVE memory list.
- Bound-KB trace showed `recentMessages=2`, `userMemory=1`, `ragEvidence=6`, `memoryCount=1`, `evidenceCount=6`, and documentHitCounts covering both temporary documents.

Boundary: artifact is stored under ignored `backend/target/memory-quality/.../artifact.json`; do not commit artifact raw content, conversation text, memory content, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This validates rule-based memory quality gates and trace separation; it does not claim real-model long-term memory extraction or large-scale personalization quality.

## 2026-06-28 Memory Governance Smoke

Status: PASS

Runner:

- `scripts/smoke/memory-quality-smoke.ps1`

Marker: `docpilot-memory-quality-20260628223255-0a06e6`

Verified gates:

- Delegated cloud quality gates passed: tunnel, backend health, frontend routes, two-document upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, populated-KB no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Memory quality gate extracted `2` suggestions; accepted suggestion became `ACTIVE`, ignored suggestion became `IGNORED`, and the ignored suggestion was absent from the ACTIVE memory list.
- Bound-KB trace kept user memory and RAG evidence separated, with `userMemory=1`, `ragEvidence=6`, `memoryCount=1`, `evidenceCount=6`, and two-document hit distribution.
- Memory governance gate created a temporary ACTIVE `ANSWER_STYLE` baseline, extracted a conflicting answer-style suggestion, and verified `governanceHint=conflict_active_memory` with non-empty `conflictWithId`.
- Direct accept of the conflicting suggestion was blocked before it could become ACTIVE, and the blocked reason matched the governance requirement.

Boundary: artifact is stored under ignored `backend/target/memory-quality/.../artifact.json`; do not commit artifact raw content, conversation text, memory content, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This validates the first accept-before-ACTIVE governance gate for rule-based memory suggestions; it does not claim automatic memory merge/edit workflows or real-model long-term memory extraction quality.

## 2026-06-28 Frontend UX Audit

Status: PASS

Marker: `docpilot-frontend-ux-2647184760`

Verified flow:

- Browser context created a temporary user, two txt documents, KnowledgeBase, ACTIVE memory and a KnowledgeBase-bound Conversation.
- Documents `175` and `176` parsed successfully.
- Conversation Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=2`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=2`, and document distribution `{175:1,176:1}`.
- `/conversations` displayed the assistant footer as `2 条来源`; Trace and Memory tabs were reachable through real clicks, and the ACTIVE memory was visible.
- `/knowledge-bases` displayed provider / collection fields, `来源不足: 否`, document distribution `#175: 1 / #176: 1`, retrieved snippets and citation cards containing both temporary document markers.
- Mobile `390x844` checks found no horizontal overflow on `/conversations` or `/knowledge-bases`.
- Follow-up `360x780` and `320x740` checks added a long ACTIVE memory and confirmed the Memory drawer and KnowledgeBase page still had no horizontal overflow.

Boundary: this is a real-browser user-experience audit over temporary smoke data. It did not change backend or frontend code, did not delete business data, did not alter schema, did not operate remote Docker, and did not commit artifacts, screenshots, raw logs, tokens, cloud addresses or connection strings.

## 2026-06-28 Memory Product UI Audit

Status: PASS

Marker: `docpilot-memory-ui-product-1782651263292`

Verified flow:

- Browser context created a temporary user, `3` ACTIVE memories, `2` suggested memories and a Conversation `41`.
- `/conversations` Memory drawer displayed active / suggested / duplicate KPI badges, memory type distribution, source labels, priority, confidence, updated time and duplicate warnings.
- Desktop check showed `cardCount=5`, `scrollWidth=clientWidth=1265`.
- Mobile `390x844` check showed `scrollWidth=clientWidth=375`, `metaCount=17`, `cardCount=5`.
- Mobile `320x740` check showed `scrollWidth=clientWidth=305`, `kpiCount=3`, `metaCount=17`, `cardCount=5`.

Boundary: this validates Memory management UX over temporary data. It does not claim real-model long-term memory extraction quality, large-scale personalization quality or conflict-resolution automation.

## 2026-06-28 Phase 2 Real Experience Audit

Status: PASS after follow-up fixes

Marker: `docpilot-phase2-ui-audit-1782628501578`

Verified flow:

- Local MySQL / Qdrant tunnel, backend and frontend were running locally.
- Browser UI registration on `localhost:3007` succeeded after the local CORS allowlist was extended for smoke ports.
- Two temporary txt documents parsed successfully: `150`, `151`.
- Single-document RAG returned `1` hit and `1` citation.
- KnowledgeBase API path returned `2` hits and `2` citations with document distribution `{150:1,151:1}`.
- Conversation bound to KB `26` returned an answer with two evidence references; Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=2`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=2`, and `documentHitCounts={150:1,151:1}`.

Experience findings:

- Fixed during audit: browser requests from frontend dev ports `3007` / `3100` were blocked by backend CORS before `WebMvcConfig` was updated.
- Single-document detail page can generate an answer with `[1]`, but the right-side citation panel still says no citation source.
- KnowledgeBase page exposes provider, collection, model, scores, citations and document distribution, but a manual two-document question only retrieved document `150`; `151` was missed even though the prompt requested both markers.
- Conversation answer text displayed `[1]` / `[2]` and Trace showed two RAG evidence items, but the chat bubble footer showed `0` citations.
- Mobile `/conversations` at `390x844` had horizontal overflow: the main chat area remained wider than the viewport while side panels were off-canvas.

Follow-up fix in the same Phase 2 cycle: `/conversations` now loads the latest assistant trace for historical messages and displays `2 条来源` from `contextTrace.evidenceCount` when citation details are not embedded in the message list response.

Follow-up fix in the same Phase 2 cycle: document detail RAG streaming now consumes `retrieval` and `citation` SSE events, so the citation panel updates during a streamed answer and shows hit count, citation score, chunk version and snippet.

Follow-up fix in the same Phase 2 cycle: mobile `/conversations` now constrains the chat main area, topbar, thread and composer to the viewport width; the long KB label is clipped instead of stretching the page.

Follow-up fix in the same Phase 2 cycle: KnowledgeBase hybrid retrieval now keeps keyword-supported summary-intent candidates long enough for scope guard, rerank and multi-document diversity selection. Real smoke `docpilot-rag-real-quality-20260628150434-2b7b39` passed with KnowledgeBase document distribution `{152:3,153:3}`, no-evidence threshold PASS, Conversation Trace PASS, permission isolation PASS and frontend route smoke PASS.

Boundary: no raw artifact, token, password, prompt, evidence context, cloud address or connection string is committed. Temporary data was created only for this real-link audit.

## 2026-06-28 RAG Quality Smoke

Status: PASS

Marker: `docpilot-rag-real-quality-20260628141419-fb7c21`

Verified gates:

- Reused local MySQL / Qdrant tunnel.
- Backend health and frontend routes passed.
- Temporary users, txt documents, KnowledgeBase and Conversation were created by smoke runner.
- Chunk quality and MySQL / Qdrant payload consistency passed.
- Single-document RAG, KnowledgeBase two-document RAG and populated-KB no-evidence gate passed.
- Conversation Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, `memoryCount=1`, and separated `userMemory=1` / `ragEvidence=6`.
- Permission isolation negative checks and artifact redaction passed.

Boundary: artifact is stored under ignored `backend/target/rag-quality/.../artifact.json`; do not commit artifact raw content, prompts, evidence context, credentials, connection strings or cloud addresses.

## 2026-06-28 Rerank Effect Smoke

Status: PASS with small hard-fixture ranking uplift

Runner:

- `scripts/smoke/rerank-effect-smoke.ps1`

Validation performed:

| Check | Result |
| --- | --- |
| `-Mode plan` | PASS |
| `-Mode dry-run` | PASS |
| `-Mode run` overall status | PASS |
| hybrid-only baseline marker | `docpilot-rerank-effect-hybrid-20260628151134-170d38` |
| hybrid + rerank marker | `docpilot-rerank-effect-rerank-20260628151301-6b0060` |
| baseline KB gate | `6` retrieve hits, `6` QA citations, `2` covered documents |
| rerank KB gate | `6` retrieve hits, `6` QA citations, `2` covered documents |
| rerank provider evidence | `rerankApplied=true`, rerank score count `6`, score min `0.61774837970733643`, max `0.997183620929718` |
| no-evidence regression | `false` |
| security regression | `false` |
| hard fixture baseline marker | `docpilot-rerank-effect-hybrid-20260628204120-3e9f69` |
| hard fixture rerank marker | `docpilot-rerank-effect-rerank-20260628204339-7aac45` |
| hard fixture target rank | `2 -> 1` |
| hard fixture distractor rank | `3 -> 4` |
| hard fixture uplift observed | `true` |

Boundary: this proves the configured real rerank provider was called, did not regress the core RAG/security gates, and improved target/distractor ordering in a small hard smoke fixture. It does not prove broad relevance uplift or production-scale ranking quality.

## 1. Single Document Smoke

Status: PASS

Evidence source: live API smoke records from A1-3 and A1-4 agent/tool verification.

Verified flow:

- Registered and logged in with a demo user.
- Uploaded a non-sensitive txt document.
- Created a document record.
- Created a parse task.
- Document parsing reached `SUCCESS`.
- Parse success triggered RAG indexing.
- Single-document RAG retrieve returned hits.
- Single-document RAG QA returned an answer with citations.
- Single-document RAG SSE was verified in A1-3.
- Agent `rag_qa_tool` returned evidence-backed results.
- ToolCall API exposed and called `rag_qa_tool`.

Recorded IDs:

| Item | Value |
| --- | --- |
| A1-4 agent/tool userId | `78` |
| A1-4 agent/tool fileRecordId | `75` |
| A1-4 agent/tool documentId | `73` |
| A1-4 agent/tool parseTaskId | `70` |
| A1-4 agentTaskId | `26` |

Key results:

| Check | Result |
| --- | --- |
| Parse status | `SUCCESS` |
| Storage mode in this smoke | `local-path` |
| Single-document retrieve code | `0` |
| Single-document retrieve hit count | `1` |
| Agent run code | `0` |
| Agent success | `true` |
| Agent persisted step count | `2` |
| Agent RAG citation count | `1` |
| Tool list code | `0` |
| ToolCall `rag_qa_tool` status | `SUCCESS` |
| ToolCall hit count | `1` |
| ToolCall citation count | `1` |
| ToolCall userId violation | rejected with `403` |

Listed tools:

- `document_status_tool`
- `document_summary_tool`
- `document_qa_tool`
- `rag_qa_tool`

## 2. Multi-Document KnowledgeBase Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-a143-kb-smoke-summary.json`.

Verified flow:

- Created demo user A and demo user B.
- Uploaded two user A documents.
- Parsed both user A documents successfully.
- Verified both documents were individually indexed.
- Created a KnowledgeBase.
- Added both documents into the KnowledgeBase.
- Retrieved across multiple documents with Qdrant.
- Ran KnowledgeBase RAG QA.
- Verified citations covered both documents.
- Verified cross-user access failures.
- Verified cross-user document add failure.

Recorded IDs:

| Item | Value |
| --- | --- |
| user A id | `79` |
| user B id | `80` |
| user A document 1 | `74` |
| user A document 2 | `75` |
| user B document | `76` |
| KnowledgeBase id | `1` |
| Empty KnowledgeBase id | `2` |

Key results:

| Check | Result |
| --- | --- |
| Document 74 parse status | `SUCCESS` |
| Document 75 parse status | `SUCCESS` |
| Document 74 single retrieve hits | `1` |
| Document 75 single retrieve hits | `1` |
| KnowledgeBase create code | `0` |
| Add documents code | `0` |
| Active document count | `2` |
| KnowledgeBase detail document count | `2` |
| KnowledgeBase RAG provider | `qdrant` |
| KnowledgeBase retrieve code | `0` |
| KnowledgeBase retrieve hit count | `2` |
| KnowledgeBase retrieve citation count | `2` |
| Distinct hit document IDs | `74, 75` |
| KnowledgeBase QA code | `0` |
| KnowledgeBase QA citation count | `2` |
| KnowledgeBase QA fallback used | `false` |

No-evidence results:

| Case | Result |
| --- | --- |
| Populated KB with unrelated query | returned nearest hits; `noEvidence=false` |
| Empty KB retrieve | `noEvidence=true`, hit count `0` |
| Empty KB QA | `noEvidence=true`, `fallbackUsed=true`, `fallbackReason=no_evidence` |

Boundary: this older smoke predates the v3 evidence confidence gate. Populated KnowledgeBase no-evidence is now covered by the RAG real quality gate in section 11.

## 3. Real Model Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-real-model-smoke-summary.json`.

Verified flow:

- Confirmed `.env` contains non-empty real model settings without printing values.
- Started backend with `AI_MODE=real`.
- Uploaded a short non-sensitive txt document.
- Parsed the document successfully.
- Called `POST /api/ai/qa`.
- Verified the answer contained the smoke marker.

Recorded IDs:

| Item | Value |
| --- | --- |
| userId | `81` |
| fileRecordId | `79` |
| documentId | `77` |
| parseTaskId | `73` |

Key results:

| Check | Result |
| --- | --- |
| Parse status | `SUCCESS` |
| QA code | `0` |
| QA elapsed | about `6184ms` |
| Answer length | `113` |
| Answer contained marker | `true` |
| Citation count | `1` |

Boundary: this verifies the real answer generation model through `RealAiAnswerService`. It does not verify a real embedding provider.

## 4. MinIO Active Storage Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-s3-minio-smoke-summary.json`.

Verified flow:

- Started backend with `FILE_STORAGE_MODE=minio`.
- Uploaded a short non-sensitive txt document.
- Created a document record and parse task.
- Verified upload response used `minio://` storage prefix.
- Verified parser read the object back successfully and document parse reached `SUCCESS`.

Recorded IDs:

| Item | Value |
| --- | --- |
| userId | `83` |
| fileRecordId | `81` |
| documentId | `79` |
| parseTaskId | `75` |

Key results:

| Check | Result |
| --- | --- |
| Storage prefix | `minio://` |
| Parse status | `SUCCESS` |
| Direct bucket listing | not separately performed |

Boundary: this verifies MinIO object write/readback through the application path. It does not claim production object lifecycle governance.

## 5. RocketMQ + Outbox Active Parse Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-s4-mq-smoke-summary.json` and sanitized backend log lines.

Verified flow:

- Started backend with RocketMQ enabled.
- Registered and logged in with a demo user.
- Uploaded a non-sensitive txt document.
- Created a document and parse task.
- `POST /api/task/parse/create` returned `PENDING`.
- Producer sent the parse task message with `SEND_OK`.
- Consumer received the parse task message.
- Parse consume entry accepted the task.
- Document parsing reached `SUCCESS`.

Recorded IDs:

| Item | Value |
| --- | --- |
| userId | `85` |
| fileRecordId | `82` |
| documentId | `80` |
| parseTaskId | `76` |

Key results:

| Check | Result |
| --- | --- |
| Parse create status | `PENDING` |
| MQ send status | `SEND_OK` |
| Consume status | success log observed |
| Final parse status | `SUCCESS` |
| Parsed content marker present | `true` |

Boundary: DB row-level verification for `tb_parse_task_outbox` and `tb_parse_task_consume_record` was not performed because safe read-only DB access without credentials was blocked. API status and application logs prove the active producer / consumer path for this smoke.

## 6. Real Embedding + Qdrant Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-real-embedding-6333-smoke-summary.json`.

Verified flow:

- Confirmed required embedding configuration keys existed and were non-empty without printing values.
- Used a local Qdrant tunnel at `http://127.0.0.1:6333`.
- Started backend with `APP_RAG_EMBEDDING_PROVIDER=openai_compatible`, Qdrant vector store, and mock answer generation.
- Uploaded a short non-sensitive txt document.
- Created a document and parse task.
- Document parsing reached `SUCCESS`.
- Parse success triggered RAG indexing.
- Qdrant smoke collection existed after indexing.
- Single-document RAG retrieve returned a hit.
- Single-document RAG QA returned an answer with citation.

Recorded IDs:

| Item | Value |
| --- | --- |
| userId | `87` |
| fileRecordId | `84` |
| documentId | `82` |
| parseTaskId | `78` |

Key results:

| Check | Result |
| --- | --- |
| Embedding provider | `openai_compatible` |
| Embedding model | `Qwen/Qwen3-Embedding-0.6B` |
| Vector provider | `qdrant` |
| Vector dimension | `1024` |
| Qdrant collection | `docpilot_embedding_smoke_20260606_03` |
| Collection existed / created | `true` |
| Retrieve code | `0` |
| Retrieve hit count | `1` |
| QA code | `0` |
| QA answer length | `590` |
| QA citation count | `1` |
| Real answer model called | `false` |

Boundary: this verifies real embedding + Qdrant indexing / retrieval in a smoke collection. Answer generation stayed in mock mode, so this does not prove real answer model and real embedding in the same run.

## 7. Eval Artifact

Status: PASS

Artifact:

- `backend/target/rag-eval/knowledge-base-rag-eval-latest.json`

Test command used:

```powershell
cd backend
mvn "-Dtest=OpenAiFunctionCallingServiceImplTest,OpenAiToolCallParserTest,OpenAiToolResultAdapterTest,KnowledgeBaseRagEvalRunnerTest" test
```

Results:

| Metric | Value |
| --- | --- |
| Tests | `12` run, `0` failures, `0` errors, `0` skipped |
| Provider | `in_memory` |
| Embedding provider | `mock` |
| Case count | `5` |
| Model call count | `4` |
| No-evidence model call count | `0` |
| hitAtK | `1.0000` |
| documentHitRate | `1.0000` |
| citationHitRate | `1.0000` |
| answerHitRate | `1.0000` |
| citationCountRate | `1.0000` |
| multiDocumentCoverageRate | `1.0000` |
| forbiddenAnswerLeakRate | `0.0000` |
| noEvidenceRate | `1.0000` |
| scopeViolationRate | `0.0000` |

Boundary: this eval is offline/mock-oriented evidence, not a real external model eval. The v1 quality gate now checks retrieval markers, citation alignment, answer marker coverage, minimum citation count, multi-document coverage and forbidden answer leakage, but it still uses `MockEmbeddingProvider`, `InMemoryVectorStoreClient` and a synthetic answer service.

## 8. Conversation Context / Agent Memory Smoke

Status: PASS

Runtime setup:

- Local backend and frontend were started.
- Cloud MySQL and Qdrant were reached through the current local SSH tunnel entries.
- No real secrets, prompts, or evidence source text were copied into this record.

API smoke:

| Check | Result |
| --- | --- |
| Temporary user | `userId=94` |
| Uploaded / parsed document | `documentId=93`, parse `SUCCESS` |
| KnowledgeBase | `knowledgeBaseId=7` |
| KnowledgeBase retrieval | `1` hit, `noEvidence=false`, `documentHitCounts={93:1}` |
| Bound conversation | `conversationId=3`, mode `AGENT_MEMORY` |
| Conversation trace | `ragTriggered=true`, `ragRequired=true`, `evidenceCount=1` |
| Conversation citations | `1` |
| Fallback / model skipped | `false / false` |

Browser smoke:

| Check | Result |
| --- | --- |
| Page | `/conversations` |
| Bound KnowledgeBase | `#8` |
| Parsed document | `documentId=94` |
| User question | Chinese `根据知识库` intent |
| Assistant answer | Referenced `t013-ui-kb-0613093939.txt` |
| Trace evidence | `Evidence=1` |
| Trace RAG flags | `RAG triggered=yes`, `RAG required=yes`, `No Evidence=no` |
| Document hit distribution | `#94: 1` |

Boundary: this verifies Conversation Context / Agent Memory MVP with KnowledgeBase-bound evidence in a non-streaming conversation path. It does not mean the existing Agent main chain has been replaced, and it does not add background automatic summaries or real-model memory extraction.

## 9. Permission Boundary Cases

Verified failures:

| Case | Result |
| --- | --- |
| ToolCall with mismatched `userId` | rejected with `403` |
| User B retrieves user A KnowledgeBase | rejected with code `1022` |
| User B reads user A KnowledgeBase detail | rejected with code `1022` |
| User A adds user B document to user A KnowledgeBase | rejected with code `1010` |

Boundary: before S2, some Chinese error messages were garbled in API output. Error codes were valid, but display text needed cleanup.

## 10. Cloud Quality Gate Smoke Runner

Status: PASS

Evidence source: `backend/target/smoke/docpilot-cloud-quality-20260627022219-37efd4/artifact.json`.

Runner:

- `scripts/smoke/cloud-quality-smoke.ps1`

Implemented modes:

| Mode | Behavior |
| --- | --- |
| `plan` | Prints the gate list and artifact target only; does not read `.env`, start services, or create data. |
| `dry-run` | Checks local prerequisites, ports and ignored artifact path; does not start services or create data. |
| `run` | Executes the full cloud quality smoke and writes a redacted ignored artifact. |

Implemented gates:

| Gate | Coverage |
| --- | --- |
| Tunnel / health | MySQL and Qdrant local tunnel ports, backend `/actuator/health`, frontend route smoke |
| Business flow | Temporary user A / B, two txt uploads, document create, parse task create, parse polling, indexing |
| Chunk quality | MySQL `tb_document_chunk` count, contiguous indexes, positive lengths, hashes, `INDEXED` status, vector ids |
| MySQL / Qdrant consistency | Qdrant scroll filtered by user / document / indexVersion and payload comparison against MySQL chunks |
| RAG | Single-document retrieve / QA citation and KnowledgeBase two-document retrieve / QA citation |
| Conversation Trace | Bound KB conversation requires `ragTriggered=true`, `ragRequired=true`, `evidenceCount>0`, and document hit counts |
| Security | Foreign KB detail, foreign KB retrieve, cross-user document add, and foreign trace access must fail |
| Artifact | Redacted JSON artifact, no tokens / API keys / cloud addresses / connection strings / chunk content |

Validation performed:

| Check | Result |
| --- | --- |
| Windows PowerShell parser | PASS |
| `-Mode plan` | PASS |
| `-Mode dry-run` | PASS |
| `-Mode run` overall status | PASS |
| smoke marker | `docpilot-cloud-quality-20260627022219-37efd4` |
| Temporary users | user A `102`, user B `103` |
| User A documents | `102`, `103` |
| User B document | `104` |
| KnowledgeBase | `10` |
| Conversation / message | `9` / `18` |
| Chunk quality | document `102`: `3/3` indexed chunks; document `103`: `3/3` indexed chunks |
| MySQL / Qdrant consistency | both documents matched `3/3` points, `0` missing vector ids |
| Single-document RAG | `3` retrieve hits, `3` QA citations |
| KnowledgeBase RAG | `6` retrieve hits, `6` QA citations, hit distribution `{102:3,103:3}` |
| Conversation Trace | `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, hit distribution `{102:3,103:3}` |
| Permission isolation | foreign KB detail, foreign KB retrieve, cross-user document add, and foreign trace access all rejected |
| Frontend route smoke | `/`, `/login`, `/dashboard`, `/upload`, `/documents`, `/knowledge-bases`, `/conversations` all HTTP 200 and non-blank |
| Artifact redaction | PASS, `0` local redaction-pattern matches in post-run scan |

Boundary: this run created temporary smoke business data and a local redacted artifact under `backend/target/smoke`. The artifact is not intended to be committed. The run did not operate remote Docker, did not use `hk-ops`, did not delete business data, did not change database schema, and did not push.

## 11. RAG Real Quality Gate Smoke

Status: PASS

Evidence source: `backend/target/rag-quality/docpilot-rag-real-quality-20260627213040-4038e1/artifact.json`.

Runner:

- `scripts/smoke/rag-real-quality-smoke.ps1`

Validation performed:

| Check | Result |
| --- | --- |
| `-Mode plan` | PASS |
| `-Mode dry-run` | PASS |
| `-Mode run` overall status | PASS |
| smoke marker | `docpilot-rag-real-quality-20260628150434-2b7b39` |
| Quality min similarity threshold | `0.50` |
| Chunk quality | document `152`: `3/3` indexed chunks; document `153`: `3/3` indexed chunks; duplicate hash count `0`; offset order and token/content length checks passed |
| MySQL / Qdrant consistency | both documents matched `3/3` points, `0` missing vector ids, `0` mismatched fields, `0` missing structure fields |
| Single-document RAG | `3` retrieve hits, `3` QA citations |
| KnowledgeBase RAG | `6` retrieve hits, `6` QA citations, hit distribution `{152:3,153:3}` |
| KnowledgeBase vector score summary | retrieve min `0.65310615`, citation min `0.6255937` |
| No-evidence threshold | PASS: unrelated populated-KB query returned `noEvidence=true`, `0` retrieve hits and `0` QA citations |
| Conversation Trace | `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, `memoryCount=1`, `contextSourceCounts={userMemory:1, ragEvidence:6}`, hit distribution `{152:3,153:3}` |
| Permission isolation | foreign KB detail, foreign KB retrieve, cross-user document add, and foreign trace access all rejected |
| Frontend route smoke | `/`, `/login`, `/dashboard`, `/upload`, `/documents`, `/knowledge-bases`, `/conversations` all HTTP 200 and non-blank |
| Artifact redaction | PASS, local redaction-pattern scan had `0` matches |

Boundary: this is a stronger real-link quality gate than the offline eval because it uses the application upload / parse / indexing / Qdrant path. The v3 gate rejects the specific unrelated populated-KB query used by the smoke, v4 adds answer-audit fields plus offline grounding metrics, v5 verifies chunk structure metadata enters Qdrant payload without a schema migration, v6 keeps rerank external calls behind complete explicit provider configuration, and v7 verifies active user memory and KB RAG evidence remain separately visible in Conversation Trace. It is still not a broad production relevance benchmark across large corpora or many domains.

## 12. Current Boundaries

What can be safely claimed:

- Single-document upload, parse, indexing, RAG retrieve, QA, SSE, Agent `rag_qa_tool`, and ToolCall API have been smoke tested.
- Multi-document KnowledgeBase create/add/retrieve/QA with Qdrant has been smoke tested.
- Scope isolation has been smoke tested for ToolCall, KnowledgeBase access, and cross-user document add.
- Real answer generation model has been smoke tested.
- Real embedding provider + Qdrant indexing / retrieval has been smoke tested.
- Conversation Context / Agent Memory with accepted user memory and KnowledgeBase-bound evidence has been smoke tested.
- Unified cloud quality gate smoke has passed once, covering two-document upload / parse / indexing, chunk quality, MySQL / Qdrant consistency, single-document RAG, two-document KnowledgeBase RAG, Conversation Trace, permission isolation, frontend routes, and redacted artifact output.
- RAG real quality gate now passes with evidence confidence, answer audit, chunk structure payload checks, and rejects the smoke unrelated populated-KB query as no-evidence.
- Small real rerank effect smoke confirms the configured rerank provider can be called and returns rerank scores without regressing KB coverage, no-evidence or security gates.
- MinIO active storage has been smoke tested through upload and parse readback.
- RocketMQ + Outbox active parse flow has been smoke tested through producer, consumer and final parse status.
- Offline Function Calling adapter tests and multi-document eval artifact have passed.
- KnowledgeBase Hybrid / Rerank optional enhancement has local unit/build/eval evidence and remains disabled by default; v6 verifies incomplete rerank provider config falls back without external HTTP, and the 2026-06-28 rerank effect smoke verifies a configured real provider can be called without core-gate regression.

What should be described with caveats:

- Offline eval still uses mock embedding + in-memory vector store.
- Function Calling is currently an OpenAI-compatible mock/offline adapter flow, not a live external model tool-call loop.
- Real answer model and real embedding were verified in separate smoke runs, not in one combined run.
- Populated KnowledgeBase no-evidence has a calibrated smoke threshold, but broader no-evidence precision still needs more eval cases and domain coverage.
- The current RAG real quality gate result is PASS, but broader no-evidence robustness still requires more eval coverage beyond this smoke fixture.
- KnowledgeBase Hybrid / Rerank has only been smoke-tested with a small real rerank provider comparison; current fixture shows no regression but no measured coverage uplift, so broader rerank relevance uplift still needs harder eval cases.
