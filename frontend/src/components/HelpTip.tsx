import { CircleHelp } from "lucide-react";
import { useCallback, useId, useState } from "react";
import { createPortal } from "react-dom";

const TOOLTIP_Z = 9999;

/** 标签旁的「?」，悬停/聚焦时在页面最顶层显示说明（Portal，避免被卡片或 overflow 裁切） */
export function HelpTip({ text }: { text: string }) {
  const tipId = useId();
  const [open, setOpen] = useState(false);
  const [anchor, setAnchor] = useState<{ top: number; left: number } | null>(
    null,
  );

  const show = useCallback((button: HTMLButtonElement) => {
    const r = button.getBoundingClientRect();
    setAnchor({
      top: r.top,
      left: r.left + r.width / 2,
    });
    setOpen(true);
  }, []);

  const hide = useCallback(() => {
    setOpen(false);
    setAnchor(null);
  }, []);

  const tooltip =
    open && anchor
      ? createPortal(
          <div
            id={tipId}
            role="tooltip"
            style={{
              position: "fixed",
              top: anchor.top - 8,
              left: anchor.left,
              transform: "translate(-50%, -100%)",
              zIndex: TOOLTIP_Z,
            }}
            className="pointer-events-none w-64 max-w-[min(16rem,calc(100vw-2rem))] rounded-xl bg-slate-800 px-3 py-2 text-left text-xs leading-relaxed text-white shadow-xl"
          >
            {text}
            <span className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-slate-800" />
          </div>,
          document.body,
        )
      : null;

  return (
    <>
      <button
        type="button"
        className="relative z-10 inline-flex shrink-0 rounded-full p-0.5 text-slate-400 transition-colors hover:z-20 hover:text-teal-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-500/50"
        aria-label={`说明：${text}`}
        aria-describedby={open ? tipId : undefined}
        onMouseEnter={(e) => show(e.currentTarget)}
        onMouseLeave={hide}
        onFocus={(e) => show(e.currentTarget)}
        onBlur={hide}
      >
        <CircleHelp className="h-3.5 w-3.5" aria-hidden />
      </button>
      <span className="sr-only">{text}</span>
      {tooltip}
    </>
  );
}

export function LabelWithHelp({
  label,
  help,
  className = "text-slate-500",
}: {
  label: string;
  help: string;
  className?: string;
}) {
  return (
    <dt className={`flex items-center gap-1 ${className}`}>
      <span>{label}</span>
      <HelpTip text={help} />
    </dt>
  );
}
