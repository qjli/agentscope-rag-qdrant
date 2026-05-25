import { useEffect, useState } from "react";
import { Gauge, Search, SlidersHorizontal } from "lucide-react";
import { api } from "../api/client";
import type { KbRetrieveSettings, OpsRetrieveResponse } from "../api/types";
import { useKb } from "../context/KbContext";

const PRESETS = [
  { name: "严格", limit: 3, scoreThreshold: 0.45 },
  { name: "标准", limit: 5, scoreThreshold: 0.35 },
  { name: "宽松", limit: 8, scoreThreshold: 0.25 },
];

export function RetrievePage() {
  const { selectedKbId } = useKb();
  const [query, setQuery] = useState("");
  const [limit, setLimit] = useState(5);
  const [scoreThreshold, setScoreThreshold] = useState(0.35);
  const [materialType, setMaterialType] = useState("");
  const [category, setCategory] = useState("");
  const [source, setSource] = useState("");
  const [docId, setDocId] = useState("");
  const [result, setResult] = useState<OpsRetrieveResponse | null>(null);
  const [settings, setSettings] = useState<KbRetrieveSettings | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveMsg, setSaveMsg] = useState<string | null>(null);

  useEffect(() => {
    api
      .getRetrieveSettings(selectedKbId)
      .then((s) => {
        setSettings(s);
        if (s.retrieveLimit != null) setLimit(s.retrieveLimit);
        if (s.retrieveScoreThreshold != null) setScoreThreshold(s.retrieveScoreThreshold);
      })
      .catch(() => setSettings(null));
  }, [selectedKbId]);

  async function runRetrieve() {
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const res = await api.retrieve(selectedKbId, {
        query: query.trim(),
        limit,
        scoreThreshold,
        materialType: materialType || undefined,
        category: category || undefined,
        source: source || undefined,
        docId: docId || undefined,
      });
      setResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : "检索失败");
    } finally {
      setLoading(false);
    }
  }

  async function saveSettings() {
    setSaveMsg(null);
    try {
      const updated = await api.updateRetrieveSettings(selectedKbId, {
        retrieveLimit: limit,
        retrieveScoreThreshold: scoreThreshold,
      });
      setSettings(updated);
      setSaveMsg("已保存为本知识库默认检索参数");
    } catch (e) {
      setSaveMsg(e instanceof Error ? e.message : "保存失败");
    }
  }

  return (
    <div className="space-y-6">
      <div className="rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200/60">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <Search className="h-5 w-5 text-teal-600" />
          检索调试（仅 Qdrant，不调用大模型）
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          用于调参和验收命中质量。当前知识库默认 limit=
          {settings?.retrieveLimit ?? settings?.defaultLimit ?? "—"}，threshold=
          {settings?.retrieveScoreThreshold ?? settings?.defaultScoreThreshold ?? "—"}
        </p>

        <div className="mt-4 flex flex-wrap gap-2">
          {PRESETS.map((p) => (
            <button
              key={p.name}
              type="button"
              onClick={() => {
                setLimit(p.limit);
                setScoreThreshold(p.scoreThreshold);
              }}
              className="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-medium hover:bg-teal-50"
            >
              {p.name} · K={p.limit} · θ={p.scoreThreshold}
            </button>
          ))}
        </div>

        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <label className="block text-sm lg:col-span-2">
            <span className="text-slate-600">查询</span>
            <textarea
              rows={3}
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="输入与对话页相同的问题…"
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">Top-K (limit)</span>
            <input
              type="number"
              min={1}
              max={100}
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2"
              value={limit}
              onChange={(e) => setLimit(Number(e.target.value))}
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">score 阈值</span>
            <input
              type="number"
              min={0}
              max={1}
              step={0.05}
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2"
              value={scoreThreshold}
              onChange={(e) => setScoreThreshold(Number(e.target.value))}
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">物料过滤 material_type</span>
            <select
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2"
              value={materialType}
              onChange={(e) => setMaterialType(e.target.value)}
            >
              <option value="">不限</option>
              <option value="TEXT">TEXT</option>
              <option value="WORD">WORD</option>
              <option value="PDF">PDF</option>
            </select>
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">category</span>
            <input
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              placeholder="如 FAQ 分类"
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">source</span>
            <input
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2"
              value={source}
              onChange={(e) => setSource(e.target.value)}
              placeholder="如 faq"
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">doc_id</span>
            <input
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 font-mono text-xs"
              value={docId}
              onChange={(e) => setDocId(e.target.value)}
            />
          </label>
        </div>

        <div className="mt-4 flex flex-wrap gap-3">
          <button
            type="button"
            disabled={loading || !query.trim()}
            onClick={runRetrieve}
            className="inline-flex items-center gap-2 rounded-2xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-700 disabled:opacity-50"
          >
            <Search className="h-4 w-4" />
            {loading ? "检索中…" : "执行检索"}
          </button>
          <button
            type="button"
            onClick={saveSettings}
            className="inline-flex items-center gap-2 rounded-2xl border border-slate-200 px-5 py-2.5 text-sm font-semibold hover:bg-slate-50"
          >
            <SlidersHorizontal className="h-4 w-4" />
            保存为库默认参数
          </button>
        </div>
        {saveMsg && <p className="mt-2 text-sm text-teal-700">{saveMsg}</p>}
        {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      </div>

      {result && (
        <div className="rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200/60">
          <div className="mb-4 flex flex-wrap items-center gap-4 text-sm">
            <span className="inline-flex items-center gap-1 rounded-lg bg-slate-100 px-2 py-1">
              <Gauge className="h-4 w-4" />
              {result.latencyMs} ms
            </span>
            <span>命中 {result.hitCount} 条</span>
            <span>约 {result.estimatedContextChars} 字符上下文</span>
            <span>
              limit={result.limit} · θ={result.scoreThreshold}
            </span>
            {Object.keys(result.appliedFilters).length > 0 && (
              <span className="text-teal-700">
                过滤 {JSON.stringify(result.appliedFilters)}
              </span>
            )}
          </div>
          {result.hits.length === 0 ? (
            <p className="text-slate-500">无命中，可尝试降低阈值或放宽过滤条件</p>
          ) : (
            <div className="space-y-3">
              {result.hits.map((h, i) => (
                <div
                  key={h.id ?? i}
                  className="rounded-2xl border border-slate-100 bg-slate-50/80 p-4 text-sm"
                >
                  <div className="mb-2 flex flex-wrap gap-2 text-xs text-slate-500">
                    {h.score != null && (
                      <span className="font-semibold text-teal-700">
                        score={h.score.toFixed(3)}
                      </span>
                    )}
                    {h.docId && <span className="font-mono">doc_id={h.docId}</span>}
                    {h.chunkId != null && <span>chunk={h.chunkId}</span>}
                    {h.title && <span>标题: {h.title}</span>}
                    {h.category && <span>分类: {h.category}</span>}
                    {h.source && <span>source: {h.source}</span>}
                    {h.sourceFile && <span>文件: {h.sourceFile}</span>}
                    {h.materialType && <span>物料: {h.materialType}</span>}
                  </div>
                  <p className="whitespace-pre-wrap leading-relaxed text-slate-800">
                    {h.content}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
