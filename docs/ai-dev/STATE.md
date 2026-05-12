# 当前状态（Single Source of Truth）

- 最后更新：2026-04-18
- 当前阶段：阶段 C 已完成，阶段 D 最小闭环已落地
- 当前状态：可继续阶段 D 深化（trace 可视化 / 工具扩展）

## 1. 本轮完成（阶段 C）

1. 新增最小 eval 数据集：
   - `docs/ai-dev/benchmarks/datasets/stagec_eval_dataset.json`
2. 新增可复现执行入口：
   - `backend/scripts/benchmark/run-stage-c-eval.ps1`
3. 新增方法文档与产物：
   - `docs/ai-dev/benchmarks/STAGEC_EVAL_METHOD.md`
   - `docs/ai-dev/benchmarks/STAGEC_EVAL_RESULTS.md`
   - `docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`
4. 阶段 C 当前权威基准（来自 `stagec_eval_latest.json`，不是本轮重新运行结果）：
   - `generatedAt=2026-04-18T18:58:42.2763129+00:00`
   - `datasetName=stagec-core-qa-eval`
   - `datasetVersion=2026-04-19-r2`
   - `caseCount/streamPairs=20/8`
   - `answerSuccessRate=90%`
   - `citationHitRate=100%`
   - `casePassRate=85%`
   - `streamVsNonStreamConsistency=87.5%`
   - 边界：artifact 未记录实际运行时 `AI_MODE`、模型名或 provider；该结果是本地/当前 artifact 证据，不代表线上 SLA。

## 2. 本轮完成（阶段 D 最小闭环）

1. 新增 Agent 后端入口：
   - `POST /api/ai/agent/run`
2. 新增工具抽象与最小编排：
   - `document_status_tool`
   - `document_summary_tool`
   - `document_qa_tool`
3. 最小 smoke 脚本：
   - `backend/scripts/agent/smoke-agent-min.ps1`
4. 实测结果：summary/qa 两条路径均返回步骤轨迹与最终回答。

## 3. 本轮验证结果

1. 后端编译通过：`mvn -DskipTests compile`
2. 后端全量测试通过：`mvn test -DskipITs`（141 tests）
3. 阶段 C 评测脚本实跑通过并生成 artifact
4. 阶段 D Agent smoke 实跑通过

## 4. 当前边界与风险

1. 当前检索为轻量检索增强，非向量数据库 RAG（无 embedding/vector index/rerank）。
2. 评测结果为本地/当前实现边界下的可复现证据，不等同线上 SLA。
3. `pdf` 解析仍为占位能力，主覆盖 `txt/md`。
4. 当前 Agent 为单 Agent 最小闭环，不是多 Agent 编排系统。

## 5. 下一步（阶段 D 深化）

1. 增加 Agent trace 前端可视化（可选）。
2. 扩展工具能力（文档状态->任务触发->问答）并补契约测试。
3. 视收益评估最小 MCP/tool server 接入。

## 6. 事实源规则

- 若文档与代码冲突，以代码、配置与可执行验证结果为准。
- 本文件仅维护当前态，不追加流水账。
