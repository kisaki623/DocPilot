# DocPilot Demo Smoke Record

> Last updated: 2026-06-06

This file records the demo smoke evidence collected during the A1 real-link verification. It is intended for interview/showcase preparation and should keep implementation boundaries explicit.

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

Boundary: populated KnowledgeBase no-evidence is not thresholded yet. The current vector retrieval can return nearest hits even for unrelated questions.

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

## 4. Eval Artifact

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
| noEvidenceRate | `1.0000` |
| scopeViolationRate | `0.0000` |

Boundary: this eval is offline/mock-oriented evidence, not a real external model eval.

## 5. Permission Boundary Cases

Verified failures:

| Case | Result |
| --- | --- |
| ToolCall with mismatched `userId` | rejected with `403` |
| User B retrieves user A KnowledgeBase | rejected with code `1022` |
| User B reads user A KnowledgeBase detail | rejected with code `1022` |
| User A adds user B document to user A KnowledgeBase | rejected with code `1010` |

Boundary: before S2, some Chinese error messages were garbled in API output. Error codes were valid, but display text needed cleanup.

## 6. Current Boundaries

What can be safely claimed:

- Single-document upload, parse, indexing, RAG retrieve, QA, SSE, Agent `rag_qa_tool`, and ToolCall API have been smoke tested.
- Multi-document KnowledgeBase create/add/retrieve/QA with Qdrant has been smoke tested.
- Scope isolation has been smoke tested for ToolCall, KnowledgeBase access, and cross-user document add.
- Real answer generation model has been smoke tested.
- Offline Function Calling adapter tests and multi-document eval artifact have passed.

What should be described with caveats:

- RAG embedding used in these RAG smoke runs was mock/fake, not a real embedding provider.
- Function Calling is currently an OpenAI-compatible mock/offline adapter flow, not a live external model tool-call loop.
- MinIO active storage was not verified in these smoke runs; storage observed here was `local-path`.
- RocketMQ + Outbox active parse flow still needs a dedicated smoke record.
- Populated KnowledgeBase no-evidence detection needs a score threshold or equivalent policy before it can be presented as robust.

