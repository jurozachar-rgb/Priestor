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

/** Poradie: najprv s termínom (najbližší hore), potom bez termínu (najnovšie hore). */
export const taskOrder = (a: TaskItem, b: TaskItem) => {
  if ((a.dueAt == null) !== (b.dueAt == null)) return a.dueAt == null ? 1 : -1;
  if (a.dueAt != null && b.dueAt != null && a.dueAt !== b.dueAt) return a.dueAt - b.dueAt;
  return b.createdAt - a.createdAt;
};

export const INVITE_LEN = 8;
