# SimpleKnowledge + Qdrant 知识库（工业示例）

基于 AgentScope Java `SimpleKnowledge` + `QdrantStore` 的企业知识库示例：文档入库、向量检索、ReAct 对话，以及多知识库运维 UI。

与 `demo/`（Elasticsearch 版）功能对齐，向量库替换为 **Qdrant**。本 README 同时说明已落地的**运维可观测、文档治理与 Payload 过滤检索**等优化能力。

---

## 架构

```
frontend (React /ops)  ──proxy──►  Spring Boot :8083
                                        │
                      SimpleKnowledge + DashScope Embedding
                                        │
                                 QdrantStore (gRPC)
                                        │
                      Qdrant :6333（向量读写 + HTTP 运维 REST）
```

| 模块 | 说明 |
|------|------|
| `config/` | RAG、Agent、CORS、Qdrant / InMemory 向量库 Bean |
| `store/QdrantDocMaintenance` | 集合信息、Payload 索引、过滤检索、统计、按 doc_id 删除、文档聚合列表 |
| `store/PayloadMetaParser` | 解析 Qdrant 嵌套 `payload` 元数据（title、category、source 等） |
| `ops/OpsRetrieveService` | 仅检索调试（Embedding + Qdrant query，支持 filter） |
| `ingest/` | 单库通用入库 API |
| `ops/` | 多知识库（一库一 Qdrant collection） |
| `web/` | 检索、对话、FAQ、管理接口 |
| `frontend/` | 运维 UI：Dashboard / 文档 / **检索调试** / 对话 |

---

## 运维 UI 功能（四页）

| 页面 | 路径 | 能力 |
|------|------|------|
| **Dashboard** | `/ops/` | Chunk / 文档数、物料分布、Qdrant 连通与集合状态（维度、距离、payload 索引）、库级默认检索 K/θ |
| **文档** | `/ops/documents` | 入库 / 覆盖、按物料筛选列表、分类 / 来源文件 / 入库时间、chunk 健康提示、入库结果反馈（新增 / 删除条数） |
| **检索** | `/ops/retrieve` | **仅 Qdrant 检索、不调大模型**；调 limit / threshold；Payload 过滤；预设严格 / 标准 / 宽松；保存为知识库默认参数 |
| **AI 对话** | `/ops/chat` | RAG 对话；检索命中展示 **title / category / source_file / source** 等元数据 + 正文 |

---

## 优化能力说明

### 1. 检索可观测与调参（运维 · 高优先级）

**目标**：让运营 / 算法能解释「为什么没命中」「为什么答错」，无需改代码重启。

| 能力 | 说明 |
|------|------|
| 仅检索 API | `POST /api/v1/ops/knowledge-bases/{kbId}/retrieve`，不消耗对话模型 |
| 返回指标 | 耗时 `latencyMs`、命中数、约计上下文字符数 `estimatedContextChars`、实际 `limit` / `scoreThreshold`、生效的 `appliedFilters` |
| 每条命中 | `score`、`doc_id`、`chunk_id`、`content`，以及解析后的 `title` / `category` / `source` / `sourceFile` / `materialType` |
| 知识库级默认参数 | `GET/PUT .../retrieve-settings`，持久化到 `data/knowledge-bases.json` 的 `retrieveLimit`、`retrieveScoreThreshold` |
| UI 预设 | 严格（K=3, θ=0.45）、标准（5, 0.35）、宽松（8, 0.25） |

### 2. 入库质量与文档治理（运维 · 高优先级）

| 能力 | 说明 |
|------|------|
| 文档列表增强 | 除 doc_id / 标题 / 物料 / chunk 数外，增加 **分类、来源文件、入库时间** |
| 健康提示 | 如 `chunk 过多，建议拆分文档`（chunkCount > 200） |
| 物料筛选 | `GET .../documents?materialType=PDF` |
| 入库反馈 | 展示本次 **chunkCount**、覆盖删除约 **deletedChunks** 条旧向量 |

入库时写入的嵌套 `payload` 字段示例：`title`、`material_type`、`category`、`source`、`source_file`、`ingested_at`（FAQ 另有 `source=faq`）。

### 3. 知识库与 Qdrant 运维信息（运维 · 中优先级）

Dashboard 与 `GET .../dashboard` 扩展字段：

| 字段 | 含义 |
|------|------|
| `qdrantStatus` | 集合状态（如 green） |
| `qdrantVectorSize` / `configuredDimensions` | 集合向量维度 vs 配置维度 |
| `dimensionMatch` | 是否一致（换 Embedding 模型需重建 collection） |
| `qdrantDistance` | 距离度量（如 Cosine） |
| `payloadIndexes` | 已创建的 payload 索引字段列表 |
| `retrieveLimit` / `retrieveScoreThreshold` | 当前库生效的检索默认参数 |

### 4. 对话体验（运维 · 中优先级）

对话页「检索命中」面板除正文外，展示 payload 中的 **标题、分类、来源文件、source、物料类型**，便于核对 RAG 依据。

---

## 后端：Payload 索引 + 检索过滤（高优先级）

AgentScope `SimpleKnowledge.retrieve` 为全集合向量 Top-K；本工程在运维检索路径上增加 **Qdrant 原生过滤**，适合按物料 / 分类 / FAQ 来源等缩小召回范围。

### Payload 索引

应用启动及**新建知识库 collection** 时，自动调用 Qdrant REST 创建索引（已存在则跳过）：

- `doc_id`（keyword）
- `payload.material_type`
- `payload.category`
- `payload.source`
- `payload.source_file`

### 检索过滤

`QdrantDocMaintenance.queryByVector` 使用 `POST /collections/{name}/points/query`：

- 顶层：`doc_id` 精确匹配
- 嵌套：对 `payload` 结构内 `material_type` / `category` / `source` 使用 **nested filter**
- 服务端对命中结果再做一次 Java 侧校验，与入库 metadata 对齐

请求体示例（检索调试 API）：

```json
{
  "query": "年假有多少天？",
  "limit": 5,
  "scoreThreshold": 0.35,
  "materialType": "TEXT",
  "category": "人事",
  "source": "faq",
  "docId": "faq-001"
}
```

---

## 前置条件

1. **Qdrant** 已启动（默认 `http://localhost:6333`，健康检查 `/healthz`）
2. **DashScope API Key**（Embedding 必填；对话需开启 Agent）

```bash
export DASHSCOPE_API_KEY=sk-your-key
```

---

## 快速启动

### 后端

```bash
cd 04-simple-qdrant-code
mvn spring-boot:run
```

| 入口 | 地址 |
|------|------|
| API | http://localhost:8083 |
| Swagger | http://localhost:8083/swagger-ui.html |
| 健康检查 | http://localhost:8083/actuator/health |

### 前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 **http://localhost:5173/ops/**（Vite 将 `/api` 代理到 `8083`）。

### 测试（无需 Qdrant）

```bash
mvn test
```

测试使用 `store-type=memory`，不依赖本机 Qdrant。

---

## 配置

`src/main/resources/application.yml`（推荐用环境变量覆盖密钥）：

| 环境变量 | 含义 | 默认 |
|----------|------|------|
| `QDRANT_URL` | Qdrant HTTP 地址 | `http://localhost:6333` |
| `QDRANT_COLLECTION` | 默认集合名 | `agentscope_kb` |
| `QDRANT_API_KEY` | Qdrant API Key（可选） | 空 |
| `DASHSCOPE_API_KEY` | 百炼密钥 | **必填** |
| `DASHSCOPE_EMBEDDING_MODEL` | Embedding 模型 | `text-embedding-v3` |
| `DASHSCOPE_MODEL_NAME` | 对话模型 | `qwen-plus` |
| `RAG_STORE_TYPE` | `qdrant` / `memory` | `qdrant` |
| `RAG_OPS_DATA_DIR` | 运维数据目录 | `./data` |

检索默认（可被知识库级设置覆盖）：

| 配置项 | 默认 |
|--------|------|
| `agentscope.rag.simple.retrieve.limit` | 5 |
| `agentscope.rag.simple.retrieve.score-threshold` | 0.35 |
| `agentscope.agent.retrieve.limit` | 3 |

多知识库：UI 创建时指定 **Qdrant collection 名**（持久化字段 `indexName`，与 demo 的 ES index 语义一致，一库一 collection）。

---

## API 一览

### 运维（推荐）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/ops/knowledge-bases` | 列出知识库 |
| POST | `/api/v1/ops/knowledge-bases` | 创建知识库（新 collection） |
| GET | `/api/v1/ops/knowledge-bases/{kbId}/dashboard` | Dashboard（含 Qdrant 集合与检索默认） |
| GET | `/api/v1/ops/knowledge-bases/{kbId}/documents` | 文档列表（`?limit=&materialType=`） |
| POST | `/api/v1/ops/knowledge-bases/{kbId}/documents` | 文本入库 |
| POST | `/api/v1/ops/knowledge-bases/{kbId}/documents/upload` | 文件入库 |
| DELETE | `/api/v1/ops/knowledge-bases/{kbId}/documents/{docId}` | 按 doc_id 删除向量点 |
| **POST** | **`/api/v1/ops/knowledge-bases/{kbId}/retrieve`** | **仅检索调试（支持 Payload 过滤）** |
| GET | `/api/v1/ops/knowledge-bases/{kbId}/retrieve-settings` | 获取库级检索参数 |
| PUT | `/api/v1/ops/knowledge-bases/{kbId}/retrieve-settings` | 保存库级检索参数 |
| POST | `/api/v1/ops/knowledge-bases/{kbId}/chat` | RAG 对话 |

### 通用（默认库）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/kb/documents` | 向默认 collection 入库 |
| POST | `/api/v1/kb/retrieve` | 检索 |
| POST | `/api/v1/kb/chat` | 对话（需 Agent） |
| GET | `/api/v1/kb/status` | 状态（含 Qdrant 连通与点数） |
| POST | `/api/v1/faq/reload` | FAQ 热加载 |

---

## 数据与持久化

| 路径 | 内容 |
|------|------|
| `data/knowledge-bases.json` | 多知识库注册表（含 `retrieveLimit`、`retrieveScoreThreshold`） |
| `data/uploads/` | 上传文件临时目录 |
| Qdrant collection | 向量点 + payload（`doc_id`、`chunk_id`、`content`、嵌套业务 `payload`） |

---

## 与 demo（Elasticsearch 版）的差异

| 项 | 本工程（Qdrant） | demo（ES） |
|----|------------------|------------|
| 向量库 | `QdrantStore` + `io.qdrant:client` | `ElasticsearchStore` |
| 运维客户端 | `QdrantDocMaintenance`（REST） | `ElasticsearchDocMaintenance` |
| 过滤检索 | Qdrant nested filter + payload 索引 | ES term / delete_by_query |
| 默认端口 | **8083** | 8082 |
| 运维页 | 含 **检索调试** 页 | 无独立检索调试 |
| 测试 | 默认 `memory`，无需 Qdrant | 同左 |

---

## 目录说明

```
04-simple-qdrant-code/
├── README.md                 # 本文档
├── pom.xml
├── src/                      # Spring Boot 后端
├── frontend/                 # React 运维 UI
├── data/                     # 运行时数据（gitignore）
└── demo/                     # ES 参考实现（不参与本工程构建）
```

---

## 典型工作流

1. 启动 Qdrant 与后端、前端，配置 `DASHSCOPE_API_KEY`。
2. 在 **文档** 页向目标知识库入库（勿误用默认库 API 写入 `agentscope_kb`）。
3. 在 **检索** 页用真实用户问题调 `limit` / `threshold` 与过滤条件，确认命中质量后 **保存为库默认参数**。
4. 在 **对话** 页验证 RAG 回答，展开「检索命中」核对 title / 分类 / 来源文件。
5. 在 **Dashboard** 查看 Qdrant 维度、索引与集合健康状态。

---

## 后续可扩展方向（未实现）

- 检索结果 MMR / 同 doc 去重、Rerank
- 查询改写、黄金问答集评测
- 文档列表侧车表（大规模时替代 scroll 聚合）
- 对话路径与检索调试共用同一套 Qdrant filter（当前对话仍主要走 `SimpleKnowledge` + Agent 内检索）

---

## 参考

- [AgentScope Java RAG 文档](https://java.agentscope.io/zh/task/rag.html)
- [Qdrant 文档](https://qdrant.tech/documentation/)
- 本仓库上级说明：[README-RAG.md](../README-RAG.md)
