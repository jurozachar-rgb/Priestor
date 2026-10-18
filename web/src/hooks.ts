import { useEffect, useState, useSyncExternalStore } from 'react';
import type { LogEntry, Space, TaskItem, User } from './model';
import * as Repo from './repo';

const LOADING = Symbol('loading');
export type Loading = typeof LOADING;
export const isLoading = (v: unknown): v is Loading => v === LOADING;

export function useUser(): User | null | Loading {
  const [u, setU] = useState<User | null | Loading>(LOADING);
  useEffect(() => Repo.onUser(setU), []);
  return u;
}

/** Všetky moje oblasti aj činnosti. null = ešte sa načítava. */
export function useAllSpaces(): Space[] | null {
  const [list, setList] = useState<Space[] | null>(null);
  useEffect(() => Repo.subscribeSpaces(setList), []);
  return list;
}

export function useSpace(spaceId: string): Space | null | Loading {
  const [s, setS] = useState<Space | null | Loading>(LOADING);
  useEffect(() => { setS(LOADING); return Repo.subscribeSpace(spaceId, setS); }, [spaceId]);
  return s;
}

export function useTasks(spaceId: string): TaskItem[] | null {
  const [t, setT] = useState<TaskItem[] | null>(null);
  useEffect(() => { setT(null); return Repo.subscribeTasks(spaceId, setT); }, [spaceId]);
  return t;
}

/** Záznamy zo všetkých zadaných činností. */
export function useEntries(spaceIds: string[]): LogEntry[] {
  const key = spaceIds.join(',');
  const [byId, setById] = useState<Record<string, LogEntry[]>>({});
  useEffect(() => {
    const ids = key ? key.split(',') : [];
    const unsubs = ids.map((id) => Repo.subscribeEntries(id, (list) => setById((m) => ({ ...m, [id]: list }))));
    return () => unsubs.forEach((u) => u());
  }, [key]);
  return spaceIds.flatMap((id) => byId[id] ?? []);
}

// ---------- Jednoduchá navigácia cez adresu v prehliadači ----------

const listeners = new Set<() => void>();
window.addEventListener('popstate', () => listeners.forEach((l) => l()));

export function navigate(path: string, replace = false) {
  if (path === location.pathname) return;
  if (replace) history.replaceState(null, '', path); else history.pushState(null, '', path);
  listeners.forEach((l) => l());
  window.scrollTo(0, 0);
}

export function usePath(): string {
  return useSyncExternalStore(
    (cb) => { listeners.add(cb); return () => listeners.delete(cb); },
    () => location.pathname,
  );
}

/** Šírka okna pod hranicou → mobilné rozloženie. */
export function useNarrow(px = 820): boolean {
  const mq = `(max-width: ${px}px)`;
  return useSyncExternalStore(
    (cb) => { const m = window.matchMedia(mq); m.addEventListener('change', cb); return () => m.removeEventListener('change', cb); },
    () => window.matchMedia(mq).matches,
  );
}
