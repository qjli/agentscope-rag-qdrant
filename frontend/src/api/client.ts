import type {
  ChatResponse,
  IngestResponse,
  KbDashboard,
  KbDocumentRow,
  KbRetrieveSettings,
  KnowledgeBaseSummary,
  MaterialType,
  OpsRetrieveRequest,
  OpsRetrieveResponse,
} from "./types";

const API = "/api/v1/ops";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  listKnowledgeBases: () =>
    request<KnowledgeBaseSummary[]>(`${API}/knowledge-bases`),

  createKnowledgeBase: (body: {
    id: string;
    indexName: string;
    displayName?: string;
    description?: string;
  }) =>
    request<KnowledgeBaseSummary>(`${API}/knowledge-bases`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),

  dashboard: (kbId: string) =>
    request<KbDashboard>(`${API}/knowledge-bases/${kbId}/dashboard`),

  documents: (
    kbId: string,
    limit = 50,
    materialType?: string,
    category?: string,
  ) => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (materialType) params.set("materialType", materialType);
    if (category) params.set("category", category);
    return request<KbDocumentRow[]>(
      `${API}/knowledge-bases/${kbId}/documents?${params}`,
    );
  },

  ingestText: (
    kbId: string,
    body: { docId: string; title?: string; category?: string; text: string },
  ) =>
    request<IngestResponse>(`${API}/knowledge-bases/${kbId}/documents`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),

  ingestFile: (
    kbId: string,
    docId: string,
    materialType: MaterialType,
    file: File,
    title?: string,
    category?: string,
  ) => {
    const form = new FormData();
    form.append("file", file);
    const params = new URLSearchParams({
      docId,
      materialType,
    });
    if (title) params.set("title", title);
    if (category) params.set("category", category);
    return request<IngestResponse>(
      `${API}/knowledge-bases/${kbId}/documents/upload?${params}`,
      { method: "POST", body: form },
    );
  },

  deleteDocument: (kbId: string, docId: string) =>
    request<IngestResponse>(
      `${API}/knowledge-bases/${kbId}/documents/${encodeURIComponent(docId)}`,
      { method: "DELETE" },
    ),

  chat: (kbId: string, message: string, sessionId?: string) =>
    request<ChatResponse>(`${API}/knowledge-bases/${kbId}/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, sessionId }),
    }),

  retrieve: (kbId: string, body: OpsRetrieveRequest) =>
    request<OpsRetrieveResponse>(`${API}/knowledge-bases/${kbId}/retrieve`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),

  getRetrieveSettings: (kbId: string) =>
    request<KbRetrieveSettings>(
      `${API}/knowledge-bases/${kbId}/retrieve-settings`,
    ),

  updateRetrieveSettings: (
    kbId: string,
    body: { retrieveLimit?: number; retrieveScoreThreshold?: number },
  ) =>
    request<KbRetrieveSettings>(
      `${API}/knowledge-bases/${kbId}/retrieve-settings`,
      {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      },
    ),
};
