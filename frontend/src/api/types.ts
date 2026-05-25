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
  qdrantStatus?: string;
  qdrantVectorSize?: number;
  configuredDimensions?: number;
  dimensionMatch?: boolean;
  qdrantDistance?: string;
  payloadIndexes?: string[];
  retrieveLimit?: number;
  retrieveScoreThreshold?: number;
}

export interface KbDocumentRow {
  docId: string;
  title: string;
  materialType: string;
  category?: string;
  chunkCount: number;
  sourceFile?: string;
  ingestedAt?: string;
  healthHint?: string;
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
  title?: string;
  category?: string;
  source?: string;
  sourceFile?: string;
  materialType?: string;
  payload?: Record<string, unknown>;
}

export interface OpsRetrieveRequest {
  query: string;
  limit?: number;
  scoreThreshold?: number;
  materialType?: string;
  category?: string;
  source?: string;
  docId?: string;
}

export interface OpsRetrieveResponse {
  query: string;
  limit: number;
  scoreThreshold: number;
  appliedFilters: Record<string, string>;
  hitCount: number;
  latencyMs: number;
  estimatedContextChars: number;
  hits: RetrievedChunk[];
}

export interface KbRetrieveSettings {
  knowledgeBaseId: string;
  defaultLimit: number;
  defaultScoreThreshold: number;
  retrieveLimit?: number;
  retrieveScoreThreshold?: number;
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
