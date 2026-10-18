import { useCallback, useEffect, useState } from 'react';
import { JoinDialog, NewSpaceDialog } from './dialogs';
import { isLoading, navigate, useAllSpaces, useNarrow, usePath, useUser } from './hooks';
import { isLog, type Space, type User } from './model';
import * as Repo from './repo';
import { Icon, IconButton, Tile, Toast, type ToastData } from './ui';
import { ArchiveView } from './views/ArchiveView';
import { HomeView } from './views/HomeView';
import { LogView } from './views/LogView';
import { MembersView } from './views/MembersView';
import { SettingsView } from './views/SettingsView';
import { SignInView } from './views/SignInView';
import { SpaceView } from './views/SpaceView';

const PENDING_JOIN = 'priestor.joinCode';

/** Odkaz s pozvánkou /join/KOD si zapamätáme, aby prežil prihlásenie. */
function captureJoinLink() {
  const m = location.pathname.match(/^\/join\/([A-Za-z0-9]{4,12})\/?$/);
  if (m) {
    try { sessionStorage.setItem(PENDING_JOIN, m[1].toUpperCase()); } catch { /* nevadí */ }
    history.replaceState(null, '', '/');
  }
}
captureJoinLink();

export function App() {
  const user = useUser();
  if (isLoading(user)) return <div className="center-screen"><div className="spinner" aria-label="Načítavam" /></div>;
  if (!user) return <SignInView />;
  return <SignedIn key={user.uid} user={user} />;
}

function SignedIn({ user }: { user: User }) {
  const path = usePath();
  const all = useAllSpaces();
  const narrow = useNarrow();
  const [drawer, setDrawer] = useState(false);
  const [dialog, setDialog] = useState<null | 'newSpace' | 'join'>(null);
  const [joinCode, setJoinCode] = useState('');
  const [toast, setToast] = useState<ToastData | null>(null);
  const clearToast = useCallback(() => setToast(null), []);

  useEffect(() => {
    let code: string | null = null;
    try { code = sessionStorage.getItem(PENDING_JOIN); sessionStorage.removeItem(PENDING_JOIN); } catch { /* nevadí */ }
    if (code) { setJoinCode(code); setDialog('join'); }
  }, []);

  useEffect(() => { setDrawer(false); }, [path]);

  const areas = all?.filter((s) => !isLog(s)) ?? null;
  const activities = all?.filter(isLog) ?? null;

  const openJoin = () => { setJoinCode(''); setDialog('join'); };
  const ctx: Ctx = { user, all, areas, activities, toast: setToast, openNewSpace: () => setDialog('newSpace'), openJoin };

  const sidebar = (
    <Sidebar path={path} areas={areas} onNew={() => setDialog('newSpace')} onJoin={openJoin} user={user} />
  );

  return (
    <div className={`shell${narrow ? ' narrow' : ''}`}>
      {narrow ? (
        <>
          <header className="topbar">
            <IconButton icon="menu" label="Menu" onClick={() => setDrawer(true)} />
            <button type="button" className="brand-link" onClick={() => navigate('/')}>Priestor</button>
          </header>
          {drawer && (
            <div className="drawer-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) setDrawer(false); }}>
              <div className="drawer">{sidebar}</div>
            </div>
          )}
        </>
      ) : sidebar}
      <main className="main">
        <Route path={path} ctx={ctx} />
      </main>
      <Toast toast={toast} onDone={clearToast} />
      {dialog === 'newSpace' && (
        <NewSpaceDialog onClose={() => setDialog(null)} onCreate={(name, sections, color) => {
          setDialog(null);
          navigate(`/s/${Repo.createSpace(name, sections, color)}`);
        }} />
      )}
      {dialog === 'join' && (
        <JoinDialog initialCode={joinCode} onClose={() => setDialog(null)} onJoined={(spaceId) => {
          setDialog(null);
          // Typ (oblasť / činnosť) zistíme, až keď sa objaví v zozname.
          navigate(`/open/${spaceId}`);
        }} />
      )}
    </div>
  );
}

export interface Ctx {
  user: User;
  all: Space[] | null;
  areas: Space[] | null;
  activities: Space[] | null;
  toast: (t: ToastData) => void;
  openNewSpace: () => void;
  openJoin: () => void;
}

function Route({ path, ctx }: { path: string; ctx: Ctx }) {
  const seg = path.split('/').filter(Boolean);
  if (seg[0] === 's' && seg[1]) {
    const space = findSpace(ctx, seg[1]);
    if (space === undefined) return <Loading />;
    if (space === null) return <Missing />;
    if (seg[2] === 'archive') return <ArchiveView space={space} />;
    if (seg[2] === 'members') return <MembersView space={space} />;
    return <SpaceView key={space.id} space={space} ctx={ctx} />;
  }
  if (seg[0] === 'log') return <LogView ctx={ctx} selectedId={seg[1] ?? null} />;
  if (seg[0] === 'open' && seg[1]) return <OpenAfterJoin ctx={ctx} id={seg[1]} />;
  if (seg[0] === 'settings') return <SettingsView user={ctx.user} />;
  return <HomeView ctx={ctx} />;
}

/** undefined = načítava sa, null = neexistuje / nemám prístup */
function findSpace(ctx: Ctx, id: string): Space | null | undefined {
  if (!ctx.all) return undefined;
  return ctx.all.find((s) => s.id === id) ?? null;
}

function OpenAfterJoin({ ctx, id }: { ctx: Ctx; id: string }) {
  const s = ctx.all?.find((x) => x.id === id);
  useEffect(() => {
    if (s) navigate(isLog(s) ? `/log/${s.id}` : `/s/${s.id}`, true);
  }, [s]);
  return <Loading />;
}

const Loading = () => <div className="center-pane"><div className="spinner" aria-label="Načítavam" /></div>;

function Missing() {
  return (
    <div className="center-pane">
      <h1 className="page-title">Táto oblasť tu nie je</h1>
      <p className="muted">Bola zmazaná, alebo vás z nej odobrali.</p>
      <button type="button" className="primary" onClick={() => navigate('/')}>Späť na oblasti</button>
    </div>
  );
}

function Sidebar({ path, areas, onNew, onJoin, user }: {
  path: string; areas: Space[] | null; onNew: () => void; onJoin: () => void; user: User;
}) {
  const at = (p: string) => path === p || path.startsWith(`${p}/`);
  return (
    <nav className="sidebar" aria-label="Oblasti">
      <button type="button" className="brand" onClick={() => navigate('/')}>
        <span className="brand-mark" aria-hidden><Icon name="check" size={20} /></span>
        Priestor
      </button>

      <div className="side-group">
        <div className="side-head">
          <span>Oblasti</span>
          <IconButton icon="add" label="Nová oblasť" onClick={onNew} />
        </div>
        {areas === null && <div className="side-note">Načítavam…</div>}
        {areas?.length === 0 && <div className="side-note">Zatiaľ žiadne oblasti.</div>}
        {areas?.map((s) => (
          <button key={s.id} type="button" className={`side-item${at(`/s/${s.id}`) ? ' on' : ''}`}
            onClick={() => navigate(`/s/${s.id}`)} aria-current={at(`/s/${s.id}`) ? 'page' : undefined}>
            <Tile name={s.name} color={s.color} size={28} />
            <span className="side-name">{s.name}</span>
            {s.members.length > 1 && <span className="side-count" title="Zdieľané"><Icon name="people" size={16} />{s.members.length}</span>}
          </button>
        ))}
      </div>

      <div className="side-group">
        <button type="button" className={`side-item${at('/log') ? ' on' : ''}`} onClick={() => navigate('/log')}>
          <span className="side-icon"><Icon name="calendar" /></span>
          <span className="side-name">Kalendár činností</span>
        </button>
      </div>

      <div className="side-foot">
        <button type="button" className="side-item" onClick={onJoin}>
          <span className="side-icon"><Icon name="groupAdd" /></span>
          <span className="side-name">Pripojiť sa kódom</span>
        </button>
        <button type="button" className={`side-item${at('/settings') ? ' on' : ''}`} onClick={() => navigate('/settings')}>
          {user.photoURL
            ? <img className="avatar" src={user.photoURL} alt="" referrerPolicy="no-referrer" />
            : <span className="side-icon"><Icon name="settings" /></span>}
          <span className="side-name">{user.displayName ?? user.email ?? 'Účet'}</span>
        </button>
      </div>
    </nav>
  );
}
