export type MaterialType = "TEXT" | "WORD" | "PDF";

export interface KnowledgeBaseSummary {
  id: string;
  displayName: string;
  indexName: string;
  description?: string;
  builtIn: boolean;
  createdAt?: string;
  qdrantPing: boolean;
  chunkCount: number;
  documentCount: number;
}

export interface KbDashboard {
  knowledgeBaseId: string;
  displayName: string;
  indexName: string;
  storeType: string;
  qdrantLocation: string;
  qdrantPing: boolean;
  chunkCount: number;
  documentCount: number;
  materialDistribution: { materialType: string; count: number }[];
}

export interface KbDocumentRow {
  docId: string;
  title: string;
  materialType: string;
  chunkCount: number;
  sourceFile?: string;
  ingestedAt?: string;
}

export interface IngestResponse {
  docId: string;
  chunkCount: number;
  deletedChunks: number;
}

export interface RetrievedChunk {
  id?: string;
  score?: number;
  content?: string;
  docId?: string;
  chunkId?: string;
  payload?: Record<string, unknown>;
}

export interface ChatResponse {
  query?: string;
  answer: string;
  sessionId?: string;
  knowledgeBaseId?: string;
  indexName?: string;
  retrievedDocuments?: RetrievedChunk[];
  retrievedCount?: number;
}
