// Dátové typy – rovnaké ako v Android aplikácii (data/Models.kt).

export interface Section { id: string; name: string }

/** Oblasť (Domov, Nákup…) alebo činnosť v kalendári činností (kind === 'log'). */
export interface Space {
  id: string;
  name: string;
  ownerId: string;
  members: string[];
  memberNames: Record<string, string>;
  sections: Section[];
  color: number;
  kind: string | null;
}

export const KIND_LOG = 'log';
export const isLog = (s: Space) => s.kind === KIND_LOG;

export interface TaskItem {
  id: string;
  spaceId: string;
  title: string;
  note: string;
  sectionId: string | null;
  /** Termín v ms. Ak hasTime je false, je to polnoc daného dňa (lokálny čas). */
  dueAt: number | null;
  hasTime: boolean;
  done: boolean;
  doneAt: number | null;
  doneByName: string | null;
  createdByName: string | null;
  createdAt: number;
  /** Ručné poradie (ťahanie myšou / prstom). null = úloha ešte nebola presúvaná. */
  order: number | null;
}

/** Záznam v kalendári činností. date je 'RRRR-MM-DD'. */
export interface LogEntry {
  id: string;
  spaceId: string;
  date: string;
  note: string;
  createdByName: string | null;
}

export interface User {
  uid: string;
  displayName: string | null;
  email: string | null;
  photoURL: string | null;
}

export const sectionName = (s: Space, id: string | null) => s.sections.find((x) => x.id === id)?.name ?? null;

/**
 * Poradie: najprv úlohy, ktoré ešte nikto ručne nepresúval (s termínom – najbližší hore, potom bez termínu –
 * najnovšie hore), potom úlohy v poradí, ako ich niekto poukladal.
 */
export const taskOrder = (a: TaskItem, b: TaskItem) => {
  if ((a.order == null) !== (b.order == null)) return a.order == null ? -1 : 1;
  if (a.order != null && b.order != null && a.order !== b.order) return a.order - b.order;
  if ((a.dueAt == null) !== (b.dueAt == null)) return a.dueAt == null ? 1 : -1;
  if (a.dueAt != null && b.dueAt != null && a.dueAt !== b.dueAt) return a.dueAt - b.dueAt;
  return b.createdAt - a.createdAt;
};

export const INVITE_LEN = 8;

/** Bez diakritiky a malými písmenami – „Pečivo“ → „pecivo“. */
export const normalizeForSearch = (s: string) => s.normalize('NFD').replace(/\p{Mn}+/gu, '').toLowerCase().trim();

/**
 * Návrhy názvov podľa toho, čo už v oblasti niekedy bolo (aj v archíve) – rovnaké ako v Androide.
 * Stačia 2 znaky; hľadá sa od začiatku názvu alebo slova, bez ohľadu na diakritiku.
 */
export function titleSuggestions(query: string, tasks: TaskItem[], limit = 6): TaskItem[] {
  const q = normalizeForSearch(query);
  if (q.length < 2) return [];
  const seen = new Set<string>();
  return tasks
    .map((t) => ({ t, n: normalizeForSearch(t.title) }))
    .filter(({ n }) => n !== q && (n.startsWith(q) || n.split(/[\s,\/-]+/).some((w) => w.startsWith(q))))
    .sort((a, b) =>
      Number(!a.n.startsWith(q)) - Number(!b.n.startsWith(q))
      || Number(a.t.done) - Number(b.t.done)
      || Math.max(b.t.doneAt ?? 0, b.t.createdAt) - Math.max(a.t.doneAt ?? 0, a.t.createdAt))
    .filter(({ n }) => (seen.has(n) ? false : (seen.add(n), true)))
    .slice(0, limit)
    .map(({ t }) => t);
}
