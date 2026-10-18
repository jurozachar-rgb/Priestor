import { type ReactNode, useEffect, useRef, useState } from 'react';
import { spaceColor, spaceColors } from './format';

// ---------- Ikony (Material Symbols Rounded, vložené ako SVG) ----------

const paths = {
  add: 'M11 13H6q-.42 0-.71-.29T5 12t.29-.71T6 11h5V6q0-.42.29-.71T12 5t.71.29T13 6v5h5q.42 0 .71.29T19 12t-.29.71T18 13h-5v5q0 .42-.29.71T12 19t-.71-.29T11 18z',
  back: 'm7.83 13 4.9 4.9q.3.3.29.7t-.31.7q-.3.27-.7.28t-.7-.28l-6.6-6.6q-.15-.15-.21-.33T4.43 12t.07-.38.21-.32l6.6-6.6q.28-.28.7-.28t.7.28q.3.3.3.71t-.3.71L7.83 11H19q.42 0 .71.29T20 12t-.29.71T19 13z',
  more: 'M12 20q-.82 0-1.41-.59T10 18t.59-1.41T12 16t1.41.59T14 18t-.59 1.41T12 20m0-6q-.82 0-1.41-.59T10 12t.59-1.41T12 10t1.41.59T14 12t-.59 1.41T12 14m0-6q-.82 0-1.41-.59T10 6t.59-1.41T12 4t1.41.59T14 6t-.59 1.41T12 8',
  people: 'M1 18.2q0-.85.44-1.56t1.16-1.09q1.55-.77 3.15-1.16T9 14t3.25.39 3.15 1.16q.72.38 1.16 1.09T17 18.2v.8q0 .82-.59 1.41T15 21H3q-.82 0-1.41-.59T1 19zM18.45 21q.27-.45.41-.96T19 19v-1q0-1.1-.61-2.11T16.65 14.15q1.27.15 2.4.51t2.1.89q.9.5 1.38 1.11T23 18v1q0 .82-.59 1.41T21 21zM9 12q-1.65 0-2.82-1.18T5 8t1.18-2.82T9 4t2.83 1.18T13 8t-1.17 2.83T9 12m10-4q0 1.65-1.17 2.83T15 12q-.28 0-.7-.06t-.7-.14q.68-.8 1.04-1.78T15 8t-.36-2.02T13.6 4.2q.35-.12.7-.16T15 4q1.65 0 2.83 1.18T19 8',
  personAdd: 'M18 11h-2q-.42 0-.71-.29T15 10t.29-.71T16 9h2V7q0-.42.29-.71T19 6t.71.29T20 7v2h2q.42 0 .71.29T23 10t-.29.71T22 11h-2v2q0 .42-.29.71T19 14t-.71-.29T18 13zm-9 1q-1.65 0-2.82-1.18T5 8t1.18-2.82T9 4t2.83 1.18T13 8t-1.17 2.83T9 12m-8 7v-.8q0-.85.44-1.56t1.16-1.09q1.55-.77 3.15-1.16T9 14t3.25.39 3.15 1.16q.72.38 1.16 1.09T17 18.2v.8q0 .82-.59 1.41T15 21H3q-.82 0-1.41-.59T1 19',
  archive: 'M5 21q-.82 0-1.41-.59T3 19V6.53q0-.35.11-.68t.34-.6l1.25-1.52q.27-.35.68-.54T6.25 3h11.5q.45 0 .86.19t.69.54l1.25 1.52q.23.27.34.6t.11.68V19q0 .82-.59 1.41T19 21zm.4-15h13.2l-.85-1H6.25zM12 10.4l-1.9-1.9q-.28-.27-.7-.27t-.7.27-.28.7.28.7l2.6 2.6q.3.3.7.3t.7-.3l2.6-2.6q.27-.27.27-.7t-.27-.7-.7-.27-.7.27z',
  calendar: 'M12 14q-.42 0-.71-.29T11 13t.29-.71T12 12t.71.29T13 13t-.29.71T12 14m-4 0q-.42 0-.71-.29T7 13t.29-.71T8 12t.71.29T9 13t-.29.71T8 14m8 0q-.42 0-.71-.29T15 13t.29-.71T16 12t.71.29T17 13t-.29.71T16 14m-4 4q-.42 0-.71-.29T11 17t.29-.71T12 16t.71.29T13 17t-.29.71T12 18m-4 0q-.42 0-.71-.29T7 17t.29-.71T8 16t.71.29T9 17t-.29.71T8 18m8 0q-.42 0-.71-.29T15 17t.29-.71T16 16t.71.29T17 17t-.29.71T16 18M5 22q-.82 0-1.41-.59T3 20V6q0-.82.59-1.41T5 4h1V3q0-.42.29-.71T7 2t.71.29T8 3v1h8V3q0-.42.29-.71T17 2t.71.29T18 3v1h1q.82 0 1.41.59T21 6v14q0 .82-.59 1.41T19 22zm0-2h14V10H5z',
  settings: 'M10.83 22q-.68 0-1.17-.45t-.6-1.1l-.22-1.65q-.33-.13-.61-.3t-.56-.38l-1.55.65q-.62.27-1.25.05t-.97-.8l-1.18-2.05q-.35-.57-.2-1.22t.67-1.05l1.33-1q-.03-.18-.03-.34v-.68q0-.16.03-.34l-1.33-1q-.53-.4-.67-1.05t.2-1.22L3.9 5.98q.35-.58.98-.8t1.25.05l1.55.65q.28-.2.58-.37t.6-.31l.22-1.65q.08-.65.58-1.1T10.83 2h2.35q.67 0 1.17.45t.6 1.1l.22 1.65q.33.13.61.3t.56.38l1.55-.65q.62-.27 1.25-.05t.97.8l1.18 2.05q.35.58.2 1.22t-.67 1.05l-1.33 1q.03.18.03.34v.68q0 .16-.05.34l1.33 1q.52.4.67 1.05t-.2 1.22l-1.2 2.05q-.35.58-.98.8t-1.25-.05l-1.5-.65q-.28.2-.58.37t-.6.31l-.22 1.65q-.07.65-.57 1.1t-1.18.45zm1.22-6.5q1.45 0 2.48-1.02T15.55 12t-1.02-2.47T12.05 8.5q-1.47 0-2.49 1.03T8.55 12t1.01 2.48 2.49 1.02',
  close: 'm12 13.4-4.9 4.9q-.27.28-.7.28t-.7-.28-.28-.7.28-.7l4.9-4.9-4.9-4.9q-.28-.27-.28-.7t.28-.7.7-.28.7.28l4.9 4.9 4.9-4.9q.27-.28.7-.28t.7.28.28.7-.28.7L13.4 12l4.9 4.9q.28.27.28.7t-.28.7-.7.28-.7-.28z',
  trash: 'M7 21q-.82 0-1.41-.59T5 19V6q-.42 0-.71-.29T4 5t.29-.71T5 4h4q0-.42.29-.71T10 3h4q.42 0 .71.29T15 4h4q.42 0 .71.29T20 5t-.29.71T19 6v13q0 .82-.59 1.41T17 21zm3-4q.42 0 .71-.29T11 16V9q0-.42-.29-.71T10 8t-.71.29T9 9v7q0 .42.29.71T10 17m4 0q.42 0 .71-.29T15 16V9q0-.42-.29-.71T14 8t-.71.29T13 9v7q0 .42.29.71T14 17',
  left: 'm10.8 12 3.9 3.9q.28.27.28.7t-.28.7-.7.28-.7-.28l-4.6-4.6q-.15-.15-.21-.32T8.43 12t.06-.38.21-.32l4.6-4.6q.27-.28.7-.28t.7.28.28.7-.28.7z',
  right: 'M12.6 12 8.7 8.1q-.28-.27-.28-.7t.28-.7.7-.28.7.28l4.6 4.6q.15.15.21.33t.07.37-.07.38-.21.32l-4.6 4.6q-.27.28-.7.28t-.7-.28-.28-.7.28-.7z',
  groupAdd: 'M12.5 11.95q.73-.8 1.11-1.83T14 8t-.39-2.12-1.11-1.83q1.5.2 2.5 1.33T16 8t-1 2.62-2.5 1.33M17.45 20q.27-.45.41-.96T18 18v-1q0-.9-.4-1.71t-1.05-1.44q1.27.45 2.36 1.16T20 17v1q0 .82-.59 1.41T18 20zM20 10h-1q-.42 0-.71-.29T18 9t.29-.71T19 8h1V7q0-.42.29-.71T21 6t.71.29T22 7v1h1q.42 0 .71.29T24 9t-.29.71T23 10h-1v1q0 .42-.29.71T21 12t-.71-.29T20 11zM8 12q-1.65 0-2.82-1.18T4 8t1.18-2.82T8 4t2.83 1.18T12 8t-1.17 2.83T8 12m-8 6v-.8q0-.85.44-1.56t1.16-1.09q1.55-.77 3.15-1.16T8 13t3.25.39 3.15 1.16q.72.38 1.16 1.09T16 17.2v.8q0 .82-.59 1.41T14 20H2q-.82 0-1.41-.59T0 18',
  logout: 'M5 21q-.82 0-1.41-.59T3 19V5q0-.82.59-1.41T5 3h6q.42 0 .71.29T12 4t-.29.71T11 5H5v14h6q.42 0 .71.29T12 20t-.29.71T11 21zm12.18-8H10q-.42 0-.71-.29T9 12t.29-.71T10 11h7.18l-1.88-1.87q-.27-.28-.27-.7t.27-.7q.28-.28.71-.29t.72.27l3.57 3.6q.3.3.3.7t-.3.7l-3.57 3.6q-.28.28-.7.27t-.73-.29q-.27-.3-.26-.71t.29-.69z',
  menu: 'M4 18q-.42 0-.71-.29T3 17t.29-.71T4 16h16q.42 0 .71.29T21 17t-.29.71T20 18zm0-5q-.42 0-.71-.29T3 12t.29-.71T4 11h16q.42 0 .71.29T21 12t-.29.71T20 13zm0-5q-.42 0-.71-.29T3 7t.29-.71T4 6h16q.42 0 .71.29T21 7t-.29.71T20 8z',
  share: 'M17 22q-1.25 0-2.12-.87T14 19q0-.15.08-.7l-7.05-4.1q-.4.38-.92.59T5 15q-1.25 0-2.12-.87T2 12t.88-2.12T5 9q.6 0 1.12.21t.93.59l7.05-4.1q-.05-.18-.06-.34T14 5q0-1.25.88-2.12T17 2t2.13.88T20 5t-.87 2.13T17 8q-.6 0-1.12-.21t-.93-.59L7.9 11.3q.05.18.06.34t.04.36-.04.36-.06.34l7.05 4.1q.4-.38.92-.59T17 16q1.25 0 2.13.88T20 19t-.87 2.13T17 22',
  copy: 'M9 18q-.82 0-1.41-.59T7 16V4q0-.82.59-1.41T9 2h9q.82 0 1.41.59T20 4v12q0 .82-.59 1.41T18 18zm-4 4q-.82 0-1.41-.59T3 20V7q0-.42.29-.71T4 6t.71.29T5 7v13h10q.42 0 .71.29T16 21t-.29.71T15 22z',
  check: 'm9.55 15.15 8.48-8.48q.3-.3.7-.3t.7.3.3.71-.3.71l-9.18 9.2q-.3.3-.7.3t-.7-.3L4.55 13q-.3-.3-.29-.71t.31-.71.71-.3.71.3z',
} as const;

export type IconName = keyof typeof paths;

export function Icon({ name, size = 22, label }: { name: IconName; size?: number; label?: string }) {
  return (
    <svg className="icon" width={size} height={size} viewBox="0 0 24 24" aria-hidden={label ? undefined : true}
      role={label ? 'img' : undefined} aria-label={label}>
      <path d={paths[name]} fill="currentColor" />
    </svg>
  );
}

export function IconButton({ icon, label, onClick, danger }: { icon: IconName; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button type="button" className={`icon-btn${danger ? ' danger' : ''}`} onClick={onClick} title={label} aria-label={label}>
      <Icon name={icon} />
    </button>
  );
}

// ---------- Dialóg ----------

export function Modal({ title, onClose, children, actions, wide }: {
  title: ReactNode; onClose: () => void; children?: ReactNode; actions?: ReactNode; wide?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    const first = ref.current?.querySelector<HTMLElement>('[data-autofocus], input, textarea, button.primary, button');
    first?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); prev?.focus?.(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div ref={ref} className={`modal${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={typeof title === 'string' ? title : undefined}>
        <h2 className="modal-title">{title}</h2>
        <div className="modal-body">{children}</div>
        {actions && <div className="modal-actions">{actions}</div>}
      </div>
    </div>
  );
}

export function ConfirmDialog({ title, text, confirm, onClose, onConfirm }: {
  title: string; text: string; confirm: string; onClose: () => void; onConfirm: () => void;
}) {
  return (
    <Modal title={title} onClose={onClose} actions={<>
      <button type="button" className="text" onClick={onClose}>Zrušiť</button>
      <button type="button" className="text danger" onClick={onConfirm}>{confirm}</button>
    </>}>
      <p>{text}</p>
    </Modal>
  );
}

export function TextInputDialog({ title, initial, confirm, onClose, onConfirm }: {
  title: string; initial: string; confirm: string; onClose: () => void; onConfirm: (v: string) => void;
}) {
  const [v, setV] = useState(initial);
  return (
    <Modal title={title} onClose={onClose} actions={<>
      <button type="button" className="text" onClick={onClose}>Zrušiť</button>
      <button type="submit" form="text-input" className="primary" disabled={!v.trim()}>{confirm}</button>
    </>}>
      <form id="text-input" onSubmit={(e) => { e.preventDefault(); if (v.trim()) onConfirm(v); }}>
        <input className="field" value={v} maxLength={100} onChange={(e) => setV(e.target.value)} autoFocus />
      </form>
    </Modal>
  );
}

export function ColorPicker({ value, onChange }: { value: number; onChange: (i: number) => void }) {
  return (
    <div className="colors" role="radiogroup" aria-label="Farba">
      {spaceColors.map((c, i) => (
        <button key={c} type="button" role="radio" aria-checked={i === value} aria-label={`Farba ${i + 1}`}
          className={`swatch${i === value ? ' on' : ''}`} style={{ background: c }} onClick={() => onChange(i)} />
      ))}
    </div>
  );
}

export function Tile({ name, color, size = 36 }: { name: string; color: number; size?: number }) {
  return (
    <span className="tile" style={{ background: spaceColor(color), width: size, height: size, fontSize: size * 0.45 }} aria-hidden>
      {name.slice(0, 1).toUpperCase()}
    </span>
  );
}

export function Dot({ color, size = 10 }: { color: string; size?: number }) {
  return <span className="dot" style={{ background: color, width: size, height: size }} aria-hidden />;
}

export function Chip({ selected, onClick, children }: { selected?: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" className={`chip${selected ? ' on' : ''}`} aria-pressed={!!selected} onClick={onClick}>
      {selected && <Icon name="check" size={16} />}{children}
    </button>
  );
}

export function Menu({ items }: { items: { label: string; onClick: () => void; danger?: boolean }[] }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
  }, [open]);
  return (
    <div className="menu-wrap" ref={ref}>
      <IconButton icon="more" label="Ďalšie možnosti" onClick={() => setOpen((o) => !o)} />
      {open && (
        <div className="menu" role="menu">
          {items.map((it) => (
            <button key={it.label} type="button" role="menuitem" className={it.danger ? 'danger' : ''}
              onClick={() => { setOpen(false); it.onClick(); }}>{it.label}</button>
          ))}
        </div>
      )}
    </div>
  );
}

/** Krátke oznámenie dole s možnosťou „Späť“. */
export interface ToastData { text: string; action?: { label: string; run: () => void } }

export function Toast({ toast, onDone }: { toast: ToastData | null; onDone: () => void }) {
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(onDone, 5000);
    return () => clearTimeout(t);
  }, [toast, onDone]);
  if (!toast) return null;
  return (
    <div className="toast" role="status">
      <span>{toast.text}</span>
      {toast.action && (
        <button type="button" className="text" onClick={() => { toast.action!.run(); onDone(); }}>{toast.action.label}</button>
      )}
      <IconButton icon="close" label="Zavrieť" onClick={onDone} />
    </div>
  );
}
