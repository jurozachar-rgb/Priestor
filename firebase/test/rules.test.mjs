// Automatické testy bezpečnostných pravidiel Firestore (spúšťajú sa v GitHub Actions v emulátore).
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertSucceeds, assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs, query, where,
  serverTimestamp, arrayUnion, arrayRemove, deleteField, Timestamp,
} from 'firebase/firestore';

let env;
const DAY = 24 * 3600 * 1000;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-priestor',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env.cleanup(); });

const db = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

// Výchozí stav: oblasť "nakup" vlastní alice, je v nej aj bob. Carol je cudzia.
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    await setDoc(doc(f, 'spaces/nakup'), {
      name: 'Nákup', ownerId: 'alice', members: ['alice', 'bob'],
      memberNames: { alice: 'Alica', bob: 'Bob' }, sections: [], color: 1, createdAt: Timestamp.now(),
    });
    await setDoc(doc(f, 'spaces/nakup/tasks/t1'), {
      title: 'Mlieko', note: '', sectionId: null, dueAt: null, hasTime: false, done: false,
      doneAt: null, doneByName: null, createdBy: 'alice', createdByName: 'Alica', createdAt: Timestamp.now(),
    });
    await setDoc(doc(f, 'invites/ABCD2345'), {
      spaceId: 'nakup', spaceName: 'Nákup', createdBy: 'alice',
      createdAt: Timestamp.now(), expiresAt: Timestamp.fromMillis(Date.now() + 7 * DAY),
    });
    await setDoc(doc(f, 'invites/OLDX2345'), {
      spaceId: 'nakup', spaceName: 'Nákup', createdBy: 'alice',
      createdAt: Timestamp.now(), expiresAt: Timestamp.fromMillis(Date.now() - DAY),
    });
  });
});

const newTask = (uid, extra = {}) => ({
  title: 'Chlieb', note: '', sectionId: null, dueAt: null, hasTime: false, done: false,
  doneAt: null, doneByName: null, createdBy: uid, createdByName: uid, createdAt: serverTimestamp(), ...extra,
});

// ---------- Čítanie ----------

test('neprihlásený nevidí nič', async () => {
  await assertFails(getDoc(doc(anon(), 'spaces/nakup')));
  await assertFails(getDoc(doc(anon(), 'invites/ABCD2345')));
});

test('člen vidí oblasť a úlohy, cudzí nie', async () => {
  await assertSucceeds(getDoc(doc(db('bob'), 'spaces/nakup')));
  await assertSucceeds(getDocs(collection(db('bob'), 'spaces/nakup/tasks')));
  await assertFails(getDoc(doc(db('carol'), 'spaces/nakup')));
  await assertFails(getDocs(collection(db('carol'), 'spaces/nakup/tasks')));
});

test('zoznam oblastí iba s filtrom na seba', async () => {
  await assertSucceeds(getDocs(query(collection(db('carol'), 'spaces'), where('members', 'array-contains', 'carol'))));
  await assertFails(getDocs(collection(db('carol'), 'spaces')));
});

test('zoznam pozvánok iba vlastných', async () => {
  await assertSucceeds(getDocs(query(collection(db('alice'), 'invites'), where('createdBy', '==', 'alice'))));
  await assertFails(getDocs(collection(db('carol'), 'invites')));
});

test('iné kolekcie sú zakázané', async () => {
  await assertFails(setDoc(doc(db('alice'), 'users/alice'), { a: 1 }));
  await assertFails(getDoc(doc(db('alice'), 'users/alice')));
});

// ---------- Oblasti ----------

const newSpace = (uid, extra = {}) => ({
  name: 'Chata', ownerId: uid, members: [uid], memberNames: { [uid]: uid },
  sections: [{ id: 'a', name: 'Nakúpiť' }], color: 2, createdAt: serverTimestamp(), ...extra,
});

test('vytvorenie oblasti: iba sám pre seba', async () => {
  await assertSucceeds(setDoc(doc(db('carol'), 'spaces/s1'), newSpace('carol')));
  await assertFails(setDoc(doc(db('carol'), 'spaces/s2'), newSpace('carol', { ownerId: 'alice' })));
  await assertFails(setDoc(doc(db('carol'), 'spaces/s3'), newSpace('carol', { members: ['carol', 'alice'] })));
  await assertFails(setDoc(doc(db('carol'), 'spaces/s4'), newSpace('carol', { name: '' })));
  await assertFails(setDoc(doc(db('carol'), 'spaces/s5'), newSpace('carol', { name: 'x'.repeat(101) })));
  await assertFails(setDoc(doc(db('carol'), 'spaces/s6'), newSpace('carol', { admin: true })));
  await assertFails(setDoc(doc(anon(), 'spaces/s7'), newSpace('carol')));
});

test('člen mení názov a sekcie, nie vlastníka ani členov', async () => {
  await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/nakup'), { name: 'Potraviny' }));
  await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/nakup'), { sections: [{ id: 'x', name: 'Drogéria' }] }));
  await assertFails(updateDoc(doc(db('bob'), 'spaces/nakup'), { ownerId: 'bob' }));
  await assertFails(updateDoc(doc(db('bob'), 'spaces/nakup'), { members: arrayUnion('carol') }));
  await assertFails(updateDoc(doc(db('bob'), 'spaces/nakup'), { members: arrayRemove('alice') }));
  await assertFails(updateDoc(doc(db('bob'), 'spaces/nakup'), { 'memberNames.alice': 'Hacker' }));
  await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/nakup'), { 'memberNames.bob': 'Bobo' }));
});

test('cudzí nemôže meniť ani mazať oblasť', async () => {
  await assertFails(updateDoc(doc(db('carol'), 'spaces/nakup'), { name: 'X' }));
  await assertFails(deleteDoc(doc(db('carol'), 'spaces/nakup')));
});

test('mazať oblasť môže iba vlastník', async () => {
  await assertFails(deleteDoc(doc(db('bob'), 'spaces/nakup')));
  await assertSucceeds(deleteDoc(doc(db('alice'), 'spaces/nakup')));
});

test('vlastník odoberie člena, člen nie', async () => {
  const remove = { members: arrayRemove('bob'), 'memberNames.bob': deleteField() };
  await assertFails(updateDoc(doc(db('carol'), 'spaces/nakup'), remove));
  await assertSucceeds(updateDoc(doc(db('alice'), 'spaces/nakup'), remove));
});

test('vlastník nemôže odobrať sám seba', async () => {
  await assertFails(updateDoc(doc(db('alice'), 'spaces/nakup'), {
    members: arrayRemove('alice'), 'memberNames.alice': deleteField(),
  }));
});

test('člen môže odísť', async () => {
  await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/nakup'), {
    members: arrayRemove('bob'), 'memberNames.bob': deleteField(),
  }));
});

// ---------- Pozvánky ----------

const join = (uid, code) => updateDoc(doc(db(uid), 'spaces/nakup'), {
  members: arrayUnion(uid), [`memberNames.${uid}`]: uid, lastJoinCode: code,
});

test('pripojenie s platnou pozvánkou', async () => {
  await assertSucceeds(getDoc(doc(db('carol'), 'invites/ABCD2345')));
  await assertSucceeds(join('carol', 'ABCD2345'));
});

test('pripojenie bez pozvánky, s neplatnou alebo prepadnutou zlyhá', async () => {
  await assertFails(updateDoc(doc(db('carol'), 'spaces/nakup'), { members: arrayUnion('carol') }));
  await assertFails(join('carol', 'ZZZZ9999'));
  await assertFails(join('carol', 'OLDX2345'));
});

test('pri pripojení sa nedá pridať niekto iný', async () => {
  await assertFails(updateDoc(doc(db('carol'), 'spaces/nakup'), {
    members: arrayUnion('carol', 'dave'), 'memberNames.carol': 'c', lastJoinCode: 'ABCD2345',
  }));
});

const invite = (uid, extra = {}) => ({
  spaceId: 'nakup', spaceName: 'Nákup', createdBy: uid, createdAt: serverTimestamp(),
  expiresAt: Timestamp.fromMillis(Date.now() + 7 * DAY), ...extra,
});

test('pozvánku vytvorí iba člen, max. 7 dní, kód 8 znakov', async () => {
  await assertSucceeds(setDoc(doc(db('bob'), 'invites/QWER2345'), invite('bob')));
  await assertFails(setDoc(doc(db('carol'), 'invites/QWER2346'), invite('carol')));
  await assertFails(setDoc(doc(db('bob'), 'invites/QWER2347'), invite('alice')));
  await assertFails(setDoc(doc(db('bob'), 'invites/QWER2348'),
    invite('bob', { expiresAt: Timestamp.fromMillis(Date.now() + 30 * DAY) })));
  await assertFails(setDoc(doc(db('bob'), 'invites/SHORT6'), invite('bob')));
});

test('existujúcu pozvánku nemožno prepísať', async () => {
  await assertFails(setDoc(doc(db('bob'), 'invites/ABCD2345'), invite('bob')));
});

// ---------- Úlohy ----------

test('člen pridá úlohu, cudzí nie', async () => {
  await assertSucceeds(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t2'), newTask('bob')));
  await assertFails(setDoc(doc(db('carol'), 'spaces/nakup/tasks/t3'), newTask('carol')));
});

test('autor úlohy sa nedá sfalšovať ani zmeniť', async () => {
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t4'), newTask('alice')));
  await assertFails(updateDoc(doc(db('bob'), 'spaces/nakup/tasks/t1'), { createdBy: 'bob' }));
  await assertFails(updateDoc(doc(db('bob'), 'spaces/nakup/tasks/t1'), { createdByName: 'Bob' }));
});

test('úloha musí mať platné polia a rozumnú veľkosť', async () => {
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t5'), newTask('bob', { title: '' })));
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t6'), newTask('bob', { title: 'x'.repeat(501) })));
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t7'), newTask('bob', { note: 'x'.repeat(5001) })));
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t8'), newTask('bob', { extra: 1 })));
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t9'), newTask('bob', { done: true })));
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t10'), newTask('bob', { dueAt: 'zajtra' })));
});

test('člen odškrtne, upraví a zmaže úlohu', async () => {
  await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/nakup/tasks/t1'),
    { done: true, doneAt: serverTimestamp(), doneByName: 'Bob' }));
  await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/nakup/tasks/t1'),
    { title: 'Mlieko 2x', dueAt: Timestamp.fromMillis(Date.now() + DAY), hasTime: true }));
  await assertSucceeds(deleteDoc(doc(db('bob'), 'spaces/nakup/tasks/t1')));
});

test('cudzí nemôže upraviť ani zmazať úlohu', async () => {
  await assertFails(updateDoc(doc(db('carol'), 'spaces/nakup/tasks/t1'), { done: true }));
  await assertFails(deleteDoc(doc(db('carol'), 'spaces/nakup/tasks/t1')));
});

test('odobratý člen stratí prístup', async () => {
  await updateDoc(doc(db('alice'), 'spaces/nakup'), { members: arrayRemove('bob'), 'memberNames.bob': deleteField() });
  await assertFails(getDoc(doc(db('bob'), 'spaces/nakup')));
  await assertFails(getDocs(collection(db('bob'), 'spaces/nakup/tasks')));
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/tasks/t11'), newTask('bob')));
});

// ---------- Kalendár činností ----------

const seedLog = async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    await setDoc(doc(f, 'spaces/kosenie'), {
      name: 'Kosenie', ownerId: 'alice', members: ['alice', 'bob'], memberNames: { alice: 'Alica', bob: 'Bob' },
      sections: [], color: 0, createdAt: Timestamp.now(), kind: 'log',
    });
    await setDoc(doc(f, 'spaces/kosenie/entries/e1'), {
      date: '2026-11-01', note: '', createdBy: 'alice', createdByName: 'Alica', createdAt: Timestamp.now(),
    });
  });
};
const newEntry = (uid, extra = {}) => ({
  date: '2026-12-20', note: 'aj za plotom', createdBy: uid, createdByName: uid, createdAt: serverTimestamp(), ...extra,
});

test('činnosť: vytvorenie s kind log, iný kind nie, kind sa nedá zmeniť', async () => {
  await assertSucceeds(setDoc(doc(db('carol'), 'spaces/l1'), newSpace('carol', { sections: [], kind: 'log' })));
  await assertFails(setDoc(doc(db('carol'), 'spaces/l2'), newSpace('carol', { kind: 'admin' })));
  await assertFails(updateDoc(doc(db('carol'), 'spaces/l1'), { kind: deleteField() }));
  await assertFails(updateDoc(doc(db('alice'), 'spaces/nakup'), { kind: 'log' }));
});

test('člen činnosti číta, zapisuje, upravuje a maže záznamy, cudzí nie', async () => {
  await seedLog();
  await assertSucceeds(getDocs(collection(db('bob'), 'spaces/kosenie/entries')));
  await assertSucceeds(setDoc(doc(db('bob'), 'spaces/kosenie/entries/e2'), newEntry('bob')));
  await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/kosenie/entries/e1'), { date: '2026-11-02', note: 'x' }));
  await assertSucceeds(deleteDoc(doc(db('bob'), 'spaces/kosenie/entries/e1')));
  await assertFails(getDocs(collection(db('carol'), 'spaces/kosenie/entries')));
  await assertFails(setDoc(doc(db('carol'), 'spaces/kosenie/entries/e3'), newEntry('carol')));
  await assertFails(deleteDoc(doc(db('carol'), 'spaces/kosenie/entries/e2')));
});

test('záznam: platné polia, autor sa nedá sfalšovať ani zmeniť', async () => {
  await seedLog();
  await assertFails(setDoc(doc(db('bob'), 'spaces/kosenie/entries/a'), newEntry('bob', { createdBy: 'alice' })));
  await assertFails(setDoc(doc(db('bob'), 'spaces/kosenie/entries/b'), newEntry('bob', { date: '20.12.2026' })));
  await assertFails(setDoc(doc(db('bob'), 'spaces/kosenie/entries/c'), newEntry('bob', { note: 'x'.repeat(1001) })));
  await assertFails(setDoc(doc(db('bob'), 'spaces/kosenie/entries/d'), newEntry('bob', { extra: 1 })));
  await assertFails(updateDoc(doc(db('bob'), 'spaces/kosenie/entries/e1'), { createdBy: 'bob' }));
});

test('záznamy sa nedajú zapísať do bežnej oblasti', async () => {
  await assertFails(setDoc(doc(db('bob'), 'spaces/nakup/entries/e1'), newEntry('bob')));
});
