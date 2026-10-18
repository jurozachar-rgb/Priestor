import { useState } from 'react';
import type { User } from '../model';
import * as Repo from '../repo';
import { ConfirmDialog, Icon } from '../ui';

export function SettingsView({ user }: { user: User }) {
  const [confirm, setConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="page">
      <header className="page-head"><h1 className="page-title">Nastavenia</h1></header>
      <div className="account">
        {user.photoURL && <img className="avatar big" src={user.photoURL} alt="" referrerPolicy="no-referrer" />}
        <div>
          <div className="account-name">{user.displayName ?? 'Účet'}</div>
          <div className="muted">{user.email}</div>
        </div>
      </div>
      <ul className="settings">
        <li>
          <div className="setting-text">
            <span>Pripomienky</span>
            <span className="hint">Upozornenia na termíny chodia iba v Android aplikácii. Na webe sú úlohy po termíne vyznačené červenou.</span>
          </div>
        </li>
        <li><a className="setting-link" href="/privacy/">Zásady ochrany súkromia</a></li>
        <li>
          <button type="button" className="setting-link" onClick={() => Repo.signOut()}>
            <Icon name="logout" size={20} /> Odhlásiť sa
          </button>
        </li>
        <li>
          <button type="button" className="setting-link danger" disabled={deleting} onClick={() => setConfirm(true)}>
            <Icon name="trash" size={20} /> {deleting ? 'Mažem…' : 'Zmazať účet a všetky dáta'}
          </button>
          <span className="hint">Vaše oblasti a činnosti sa zmažú, z cudzích odídete.</span>
        </li>
      </ul>
      {error && <p className="error">{error}</p>}
      {confirm && (
        <ConfirmDialog title="Zmazať účet?"
          text="Zmažú sa všetky oblasti a činnosti, ktoré ste vytvorili, vrátane úloh ostatných členov. Z oblastí iných ľudí budete odobratý. Nedá sa to vrátiť. Pre potvrdenie sa ešte raz prihlásite cez Google."
          confirm="Zmazať" onClose={() => setConfirm(false)}
          onConfirm={async () => {
            setConfirm(false); setDeleting(true); setError(null);
            try { await Repo.deleteAccount(); } catch (e) {
              const code = (e as { code?: string })?.code;
              if (code !== 'auth/popup-closed-by-user') setError(`Mazanie zlyhalo: ${(e as Error)?.message ?? e}`);
            } finally { setDeleting(false); }
          }} />
      )}
    </div>
  );
}
