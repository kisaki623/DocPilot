# Offline RAG Retrieval Evaluation

- Mode: `offline`
- Embedding provider: `fake`
- Fallback reason: `qdrant_http_error`
- Sanitized: `true`

| Provider | Total | Positive hit rate | Avg retrieved | No-match | Empty doc | Isolation | Fallback |
| --- | ---: | ---: | ---: | --- | --- | --- | --- |
| in_memory | 7 | 1.0000 | 0.86 | true | true | true | false |
| qdrant_fake_server | 1 | 1.0000 | 1.00 | true | true | true | false |
| qdrant_fake_server_fallback | 1 | 0.0000 | 0.00 | true | true | true | true |

| Provider | Case | Expected hit | Expected marker | Retrieved | Hit | Miss | Passed |
| --- | --- | --- | --- | ---: | --- | --- | --- |
| in_memory | cache-hit | true | cache-evidence | 1 | true | false | true |
| in_memory | upload-hit | true | upload-evidence | 1 | true | false | true |
| in_memory | agent-hit | true | agent-evidence | 1 | true | false | true |
| in_memory | no-match-query | false | payment-gateway | 1 | false | true | true |
| in_memory | empty-document | false | empty-document | 0 | false | true | true |
| in_memory | same-keyword-wrong-topic | false | wrong-topic-agent-policy | 1 | false | true | true |
| in_memory | topk-over-available-chunks | true | topk-boundary | 1 | true | false | true |
| qdrant_fake_server | qdrant-fake-hit | true | qdrant-artifact | 1 | true | false | true |
| qdrant_fake_server_fallback | qdrant-fallback | false | qdrant-fallback | 0 | false | true | true |

Artifacts are generated from synthetic fixtures only and intentionally omit source text.
