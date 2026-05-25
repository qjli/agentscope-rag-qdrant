/** Dashboard / 运维 UI 中 Qdrant 相关概念的通俗说明 */

export const qdrantHelp = {
  sectionTitle:
    "Qdrant 是向量数据库：文档切块后变成「向量点」存进集合。问答和检索调试都在这里找语义相近的片段。",
  chunkTotal:
    "一篇文档会切成多段，每段在 Qdrant 里是一个点（一条向量 + 标题/分类等元数据）。这里的数字是所有 chunk 条数，不是文档篇数。",
  collection:
    "集合（Collection）名称。每个知识库绑定一个集合，该库的全部向量都写在这个集合里。",
  storeType:
    "当前知识库使用的存储实现，本项目为 Qdrant。",
  qdrantOnline:
    "能否连上 Qdrant 服务（默认本机 http://localhost:6333）。离线时无法入库、检索和对话。",
  documentCount:
    "按 doc_id 去重后的业务文档篇数。一篇文档通常对应多个 chunk。",
  qdrantAddress:
    "Qdrant HTTP 服务地址。显示「连通正常」表示运维接口能访问该地址。",
  status:
    "Qdrant 回报的本集合健康状态（如 green 表示正常）。集合不存在或维度异常时可能显示其它状态。",
  vectorSize:
    "每条向量有多少个数字，由嵌入模型决定（本项目默认 1024）。查询向量必须与库内向量维度一致才能检索。",
  configuredDimensions:
    "应用里配置的嵌入维度。若与集合实际维度不一致，会出现「维度不一致」，需用正确维度新建集合并重新入库。",
  distance:
    "衡量两条向量有多「像」的算法。文本检索常用 Cosine（余弦相似度）。创建集合后一般不要改，否则旧数据语义会对不上。",
  retrieveDefaults:
    "K：一次最多取回几条 chunk。θ（阈值）：相似度分数低于此值的命中会被丢掉。可在「检索调试」页临时改；对话默认用知识库这里保存的值。",
  payloadIndexes:
    "为 payload 里的字段（如 category、material_type、doc_id）建的索引，用于按分类/物料等条件过滤时加速查询。",
  emptyCollectionHint:
    "集合能连上，但里面还没有向量点。请先到「文档」页入库，再试检索或对话。",
} as const;
