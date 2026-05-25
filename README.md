# SimpleKnowledge + Qdrant 知识库（工业示例）

基于 AgentScope Java `SimpleKnowledge` + `QdrantStore` 的企业知识库示例：文档入库、向量检索、ReAct 对话，以及多知识库运维 UI。

与 `demo/`（Elasticsearch 版）功能对齐，向量库替换为 **Qdrant**。

## 架构

```
frontend (React)  ──proxy──►  Spring Boot :8083
                                  │
                    SimpleKnowledge + DashScope Embedding
                                  │
                           QdrantStore (gRPC)
                                  │
                    Qdrant :6333 (HTTP 运维 REST)
```

| 模块 | 说明 |
|------|------|
| `config/` | RAG、Agent、CORS、向量库 Bean |
| `store/QdrantDocMaintenance` | 集合统计、按 doc_id 删除、文档列表（REST） |
| `ingest/` | 单库通用入库 API |
| `ops/` | 多知识库（一库一 Qdrant collection） |
| `web/` | 检索、对话、FAQ、管理接口 |
| `frontend/` | 运维 Dashboard / 文档 / 对话 |

## 前置条件

1. **Qdrant** 已启动（默认 `http://localhost:6333`）
2. **DashScope API Key**（Embedding + 可选对话）

```bash
export DASHSCOPE_API_KEY=sk-your-key
```

## 启动后端

```bash
cd 04-simple-qdrant-code
mvn spring-boot:run
```

- API: http://localhost:8083
- Swagger: http://localhost:8083/swagger-ui.html
- 健康检查: http://localhost:8083/actuator/health

## 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 Vite 提示地址（默认 http://localhost:5173/ops/），API 代理到 `8083`。

## 配置要点

`src/main/resources/application.yml`：

| 环境变量 | 含义 | 默认 |
|----------|------|------|
| `QDRANT_URL` | Qdrant HTTP 地址 | `http://localhost:6333` |
| `QDRANT_COLLECTION` | 默认集合名 | `agentscope_kb` |
| `DASHSCOPE_API_KEY` | 百炼密钥 | 必填 |
| `RAG_STORE_TYPE` | `qdrant` / `memory` | `qdrant` |

多知识库：在 UI 创建时指定 **collection 名称**（对应 `KnowledgeBaseDescriptor.indexName` 字段，与 demo 的 ES index 语义一致）。

## 常用 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/ops/knowledge-bases/{kbId}/documents` | 文本入库 |
| POST | `/api/v1/ops/knowledge-bases/{kbId}/documents/upload` | 文件入库 |
| DELETE | `/api/v1/ops/knowledge-bases/{kbId}/documents/{docId}` | 按 doc_id 删除 |
| POST | `/api/v1/ops/knowledge-bases/{kbId}/chat` | 带 RAG 的对话 |
| GET | `/api/v1/ops/knowledge-bases/{kbId}/dashboard` | 仪表盘指标 |

## 与 demo（Elasticsearch）的差异

- 向量存储：`QdrantStore` + `io.qdrant:client`
- 运维 REST：`QdrantDocMaintenance`（替代 `ElasticsearchDocMaintenance`）
- 默认端口：`8083`（避免与 ES 版 `8082` 冲突）
- 测试默认 `store-type=memory`，无需 Qdrant 即可 `mvn test`

## 目录说明

- `demo/`：保留的 ES 参考实现，不参与本工程构建
- `src/`：Qdrant 版后端
- `frontend/`：运维 UI（与 demo 同源，文案与类型已改为 Qdrant）
