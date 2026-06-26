# DocPilot Demo Smoke Record

> Last updated: 2026-06-26

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
| noEvidenceRate | `1.0000` |
| scopeViolationRate | `0.0000` |

Boundary: this eval is offline/mock-oriented evidence, not a real external model eval.

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

## 10. Current Boundaries

What can be safely claimed:

- Single-document upload, parse, indexing, RAG retrieve, QA, SSE, Agent `rag_qa_tool`, and ToolCall API have been smoke tested.
- Multi-document KnowledgeBase create/add/retrieve/QA with Qdrant has been smoke tested.
- Scope isolation has been smoke tested for ToolCall, KnowledgeBase access, and cross-user document add.
- Real answer generation model has been smoke tested.
- Real embedding provider + Qdrant indexing / retrieval has been smoke tested.
- Conversation Context / Agent Memory with accepted user memory and KnowledgeBase-bound evidence has been smoke tested.
- MinIO active storage has been smoke tested through upload and parse readback.
- RocketMQ + Outbox active parse flow has been smoke tested through producer, consumer and final parse status.
- Offline Function Calling adapter tests and multi-document eval artifact have passed.
- KnowledgeBase Hybrid / Rerank optional enhancement has local unit/build evidence and remains disabled by default.

What should be described with caveats:

- Offline eval still uses mock embedding + in-memory vector store.
- Function Calling is currently an OpenAI-compatible mock/offline adapter flow, not a live external model tool-call loop.
- Real answer model and real embedding were verified in separate smoke runs, not in one combined run.
- Populated KnowledgeBase no-evidence detection needs a score threshold or equivalent policy before it can be presented as robust.
- KnowledgeBase Hybrid / Rerank has not been re-smoked with a real rerank provider in this record.
