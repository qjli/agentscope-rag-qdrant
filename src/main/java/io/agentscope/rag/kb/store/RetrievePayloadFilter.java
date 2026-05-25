package io.agentscope.rag.kb.store;

/** Qdrant payload 检索过滤条件（均为可选，AND 组合）。 */
public record RetrievePayloadFilter(String docId, String materialType, String category, String source) {

    public boolean isEmpty() {
        return (docId == null || docId.isBlank())
                && (materialType == null || materialType.isBlank())
                && (category == null || category.isBlank())
                && (source == null || source.isBlank());
    }
}
