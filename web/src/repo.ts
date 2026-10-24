// Prístup k dátam vo Firestore – zrkadlo Android Repo.kt (rovnaká štruktúra a pravidlá).
//
//  spaces/{spaceId}                 – oblasť alebo činnosť (kind = 'log')
//  spaces/{spaceId}/tasks/{taskId}  – úloha
//  spaces/{spaceId}/entries/{id}    – záznam v kalendári činností
//  invites/{kód}                    – pozvánka, platí 7 dní
import { initializeApp } from 'firebase/app';
import {
  GoogleAuthProvider, getAuth, onAuthStateChanged, reauthenticateWithPopup, signInWithPopup,
  signOut as fbSignOut, deleteUser,
} from 'firebase/auth';
import {
  type DocumentSnapshot, Timestamp, arrayRemove, arrayUnion, collection, deleteDoc, deleteField, doc, getDoc,
  getDocs, getFirestore, onSnapshot, query, serverTimestamp, setDoc, terminate, updateDoc, where, writeBatch,
} from 'firebase/firestore';
import { firebaseConfig } from './config';
import { INVITE_LEN, KIND_LOG, type LogEntry, type Section, type Space, type TaskItem, type User } from './model';

const app = initializeApp(
  Object.fromEntries(Object.entries(firebaseConfig).filter(([, v]) => v !== '')) as typeof firebaseConfig,
);
const auth = getAuth(app);
auth.languageCode = 'sk';
const db = getFirestore(app);
const spaces = collection(db, 'spaces');
const invites = collection(db, 'invites');

const provider = new GoogleAuthProvider();
provider.setCustomParameters({ prompt: 'select_account' });

// ---------- Účet ----------

const toUser = (u: { uid: string; displayName: string | null; email: string | null; photoURL: string | null }): User =>
  ({ uid: u.uid, displayName: u.displayName, email: u.email, photoURL: u.photoURL });

export function currentUser(): User | null {
  return auth.currentUser ? toUser(auth.currentUser) : null;
}

export function onUser(cb: (u: User | null) => void): () => void {
  return onAuthStateChanged(auth, (u) => cb(u ? toUser(u) : null));
}

const uid = () => {
  const u = auth.currentUser;
  if (!u) throw new Error('Nie ste prihlásený');
  return u.uid;
};
const myName = () => {
  const u = auth.currentUser;
  return (u?.displayName?.trim() || u?.email || 'Ja');
};

export async function signIn(): Promise<void> {
  await signInWithPopup(auth, provider);
}

export async function signOut(): Promise<void> {
  await fbSignOut(auth);
}

// ---------- Prevod dokumentov ----------

const millis = (t: unknown): number | null => (t instanceof Timestamp ? t.toMillis() : null);

function toSpace(d: DocumentSnapshot): Space | null {
  const x = d.data();
  if (!x || typeof x.name !== 'string') return null;
  return {
    id: d.id,
    name: x.name,
    ownerId: x.ownerId ?? '',
    members: Array.isArray(x.members) ? x.members : [],
    memberNames: x.memberNames ?? {},
    sections: (Array.isArray(x.sections) ? x.sections : [])
      .filter((s: { id?: unknown }) => typeof s?.id === 'string')
      .map((s: { id: string; name?: string }) => ({ id: s.id, name: s.name ?? '' })),
    color: typeof x.color === 'number' ? x.color : 0,
    kind: typeof x.kind === 'string' ? x.kind : null,
  };
}

function toTask(d: DocumentSnapshot, spaceId: string): TaskItem | null {
  const x = d.data();
  if (!x || typeof x.title !== 'string') return null;
  return {
    id: d.id,
    spaceId,
    title: x.title,
    note: x.note ?? '',
    sectionId: x.sectionId ?? null,
    dueAt: millis(x.dueAt),
    hasTime: !!x.hasTime,
    done: !!x.done,
    doneAt: millis(x.doneAt),
    doneByName: x.doneByName ?? null,
    createdByName: x.createdByName ?? null,
    // pred potvrdením serverom je createdAt null – nové úlohy dáme hore
    createdAt: millis(x.createdAt) ?? Date.now(),
  };
}

function toEntry(d: DocumentSnapshot, spaceId: string): LogEntry | null {
  const x = d.data();
  if (!x || typeof x.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(x.date)) return null;
  return { id: d.id, spaceId, date: x.date, note: x.note ?? '', createdByName: x.createdByName ?? null };
}

const fail = (what: string) => (e: unknown) => console.error(`Zápis zlyhal: ${what}`, e);

// ---------- Oblasti a činnosti ----------

/** Všetky moje oblasti aj činnosti (rozlíšené podľa kind). */
export function subscribeSpaces(cb: (list: Space[]) => void, onError?: (e: Error) => void): () => void {
  return onSnapshot(
    query(spaces, where('members', 'array-contains', uid())),
    (snap) => cb(
      snap.docs.map(toSpace).filter((s): s is Space => s != null)
        .sort((a, b) => a.name.localeCompare(b.name, 'sk')),
    ),
    (e) => { console.warn('spaces', e); onError?.(e); },
  );
}

export function subscribeSpace(spaceId: string, cb: (s: Space | null) => void): () => void {
  return onSnapshot(doc(spaces, spaceId), (d) => cb(toSpace(d)), (e) => { console.warn('space', e); cb(null); });
}

export function newId(): string {
  return crypto.randomUUID().slice(0, 8);
}

export function createSpace(name: string, sectionNames: string[], color: number, kind: string | null = null): string {
  const ref = doc(spaces);
  const me = uid();
  const data: Record<string, unknown> = {
    name: name.trim(),
    ownerId: me,
    members: [me],
    memberNames: { [me]: myName() },
    sections: sectionNames.map((s) => s.trim()).filter(Boolean).map((n) => ({ id: newId(), name: n })),
    color,
    createdAt: serverTimestamp(),
  };
  if (kind) data.kind = kind;
  setDoc(ref, data).catch(fail('createSpace'));
  return ref.id;
}

export const createActivity = (name: string, color: number) => createSpace(name, [], color, KIND_LOG);

export function renameSpace(spaceId: string, name: string) {
  updateDoc(doc(spaces, spaceId), { name: name.trim() }).catch(fail('renameSpace'));
}

export function setColor(spaceId: string, color: number) {
  updateDoc(doc(spaces, spaceId), { color }).catch(fail('setColor'));
}

export function updateSections(spaceId: string, sections: Section[]) {
  updateDoc(doc(spaces, spaceId), { sections: sections.map((s) => ({ id: s.id, name: s.name.trim() })) })
    .catch(fail('updateSections'));
}

/** Vlastník zmaže oblasť aj so všetkými úlohami, záznamami a pozvánkami. */
export async function deleteSpace(spaceId: string) {
  const ref = doc(spaces, spaceId);
  for (const sub of ['tasks', 'entries']) {
    const docs = (await getDocs(collection(ref, sub))).docs;
    for (let i = 0; i < docs.length; i += 400) {
      const b = writeBatch(db);
      docs.slice(i, i + 400).forEach((d) => b.delete(d.ref));
      await b.commit();
    }
  }
  const inv = await getDocs(query(invites, where('createdBy', '==', uid()), where('spaceId', '==', spaceId)));
  await Promise.all(inv.docs.map((d) => deleteDoc(d.ref)));
  await deleteDoc(ref);
}

export async function leaveSpace(spaceId: string) {
  const me = uid();
  await updateDoc(doc(spaces, spaceId), { members: arrayRemove(me), [`memberNames.${me}`]: deleteField() });
}

export function removeMember(spaceId: string, memberUid: string) {
  updateDoc(doc(spaces, spaceId), { members: arrayRemove(memberUid), [`memberNames.${memberUid}`]: deleteField() })
    .catch(fail('removeMember'));
}

// ---------- Pozvánky ----------

const INVITE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function newInviteCode(): string {
  const bytes = new Uint32Array(INVITE_LEN);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => INVITE_ALPHABET[b % INVITE_ALPHABET.length]).join('');
}

/** Vytvorí pozvánku platnú 7 dní a vráti jej kód. */
export async function createInvite(space: Space): Promise<string> {
  const code = newInviteCode();
  try {
    await setDoc(doc(invites, code), {
      spaceId: space.id,
      spaceName: space.name,
      createdBy: uid(),
      createdAt: serverTimestamp(),
      expiresAt: Timestamp.fromMillis(Date.now() + 7 * 24 * 3600 * 1000),
    });
  } catch (e) {
    console.warn('createInvite', e);
    const c = (e as { code?: string }).code;
    if (c === 'permission-denied') {
      throw new Error('Server pozvánku odmietol. Obnovte stránku (najnovšia verzia) a skontrolujte dátum a čas v počítači.');
    }
    if (c === 'unavailable' || c === 'deadline-exceeded') {
      throw new Error('Pozvánku sa nepodarilo vytvoriť. Ste pripojený na internet?');
    }
    throw new Error(`Pozvánku sa nepodarilo vytvoriť${c ? ` (${c})` : ''}.`);
  }
  return code;
}

/** Pripojí ma do oblasti podľa kódu. Vráti ID oblasti, alebo vyhodí chybu so slovenskou správou. */
export async function joinByCode(rawCode: string): Promise<string> {
  const code = rawCode.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (code.length !== INVITE_LEN) throw new Error(`Kód má mať ${INVITE_LEN} znakov.`);
  let invite;
  try {
    invite = await getDoc(doc(invites, code));
  } catch {
    throw new Error('Nepodarilo sa overiť kód. Skontrolujte internet.');
  }
  if (!invite.exists()) throw new Error('Pozvánka neexistuje.');
  const expires = invite.get('expiresAt');
  if (expires instanceof Timestamp && expires.toMillis() <= Date.now()) throw new Error('Platnosť pozvánky vypršala.');
  const spaceId = invite.get('spaceId');
  if (typeof spaceId !== 'string') throw new Error('Neplatná pozvánka.');
  const me = uid();
  await updateDoc(doc(spaces, spaceId), {
    members: arrayUnion(me),
    [`memberNames.${me}`]: myName(),
    lastJoinCode: code,
  });
  return spaceId;
}

// ---------- Úlohy ----------

export function subscribeTasks(spaceId: string, cb: (t: TaskItem[]) => void): () => void {
  return onSnapshot(
    collection(spaces, spaceId, 'tasks'),
    (snap) => cb(snap.docs.map((d) => toTask(d, spaceId)).filter((t): t is TaskItem => t != null)),
    (e) => console.warn('tasks', e),
  );
}

export function saveTask(
  spaceId: string, taskId: string | null, title: string, note: string,
  sectionId: string | null, dueAt: number | null, hasTime: boolean,
) {
  const col = collection(spaces, spaceId, 'tasks');
  const data: Record<string, unknown> = {
    title: title.trim(),
    note: note.trim(),
    sectionId,
    dueAt: dueAt != null ? Timestamp.fromMillis(dueAt) : null,
    hasTime: dueAt != null && hasTime,
  };
  if (taskId == null) {
    Object.assign(data, {
      done: false, doneAt: null, doneByName: null,
      createdBy: uid(), createdByName: myName(), createdAt: serverTimestamp(),
    });
    setDoc(doc(col), data).catch(fail('addTask'));
  } else {
    updateDoc(doc(col, taskId), data).catch(fail('updateTask'));
  }
}

export function setDone(task: TaskItem, done: boolean) {
  updateDoc(doc(spaces, task.spaceId, 'tasks', task.id), {
    done,
    doneAt: done ? serverTimestamp() : null,
    doneByName: done ? myName() : null,
  }).catch(fail('setDone'));
}

export function deleteTask(task: TaskItem) {
  deleteDoc(doc(spaces, task.spaceId, 'tasks', task.id)).catch(fail('deleteTask'));
}

// ---------- Kalendár činností ----------

export function subscribeEntries(spaceId: string, cb: (e: LogEntry[]) => void): () => void {
  return onSnapshot(
    collection(spaces, spaceId, 'entries'),
    (snap) => cb(snap.docs.map((d) => toEntry(d, spaceId)).filter((e): e is LogEntry => e != null)),
    (e) => console.warn('entries', e),
  );
}

export function addEntry(spaceId: string, date: string, note: string) {
  setDoc(doc(collection(spaces, spaceId, 'entries')), {
    date, note: note.trim(), createdBy: uid(), createdByName: myName(), createdAt: serverTimestamp(),
  }).catch(fail('addEntry'));
}

export function updateEntry(entry: LogEntry, date: string, note: string) {
  updateDoc(doc(spaces, entry.spaceId, 'entries', entry.id), { date, note: note.trim() }).catch(fail('updateEntry'));
}

export function deleteEntry(entry: LogEntry) {
  deleteDoc(doc(spaces, entry.spaceId, 'entries', entry.id)).catch(fail('deleteEntry'));
}

// ---------- Zmazanie účtu ----------

/** Zmaže všetky moje dáta (vlastné oblasti zmaže, z cudzích odíde) aj účet. Najprv sa znova prihlási. */
export async function deleteAccount(): Promise<void> {
  const u = auth.currentUser;
  if (!u) return;
  await reauthenticateWithPopup(u, provider);
  const me = u.uid;
  const mine = (await getDocs(query(spaces, where('members', 'array-contains', me)))).docs
    .map(toSpace).filter((s): s is Space => s != null);
  for (const s of mine) {
    if (s.ownerId === me) await deleteSpace(s.id); else await leaveSpace(s.id);
  }
  const inv = await getDocs(query(invites, where('createdBy', '==', me)));
  await Promise.all(inv.docs.map((d) => deleteDoc(d.ref)));
  await deleteUser(u);
  await terminate(db).catch(() => {});
  location.assign('/');
}
