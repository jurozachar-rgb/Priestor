import { useState } from 'react';
import { SITE_URL } from '../config';
import { navigate } from '../hooks';
import { isLog, type Space } from '../model';
import * as Repo from '../repo';
import { ConfirmDialog, Icon, IconButton, Modal } from '../ui';

export function MembersView({ space }: { space: Space }) {
  const me = Repo.currentUser()?.uid;
  const isOwner = space.ownerId === me;
  const log = isLog(space);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [code, setCode] = useState<string | null>(null);
  const [removing, setRemoving] = useState<{ uid: string; name: string } | null>(null);
  const back = log ? `/log/${space.id}` : `/s/${space.id}`;

  return (
    <div className="page">
      <header className="page-head">
        <IconButton icon="back" label="Späť" onClick={() => navigate(back)} />
        <h1 className="page-title grow">Členovia · {space.name}</h1>
      </header>
      <p className="muted measure">
        {log
          ? 'Túto činnosť a jej záznamy vidia iba ľudia v zozname nižšie. Ostatné vaše činnosti ani oblasti sa im nezobrazia.'
          : 'Túto oblasť vidia iba ľudia v zozname nižšie. Ostatné vaše oblasti sa im nezobrazia.'}
      </p>
      <button type="button" className="primary" disabled={busy} onClick={async () => {
        setBusy(true); setError(null);
        try { setCode(await Repo.createInvite(space)); } catch { setError('Pozvánku sa nepodarilo vytvoriť. Ste pripojený na internet?'); }
        setBusy(false);
      }}>
        <Icon name="personAdd" size={20} /> {busy ? 'Vytváram…' : 'Pozvať ľudí'}
      </button>
      {error && <p className="error">{error}</p>}

      <ul className="members">
        {space.members.map((uid) => {
          const name = space.memberNames[uid] ?? 'Neznámy';
          return (
            <li key={uid} className="member">
              <span className="member-text">
                <span>{uid === me ? `${name} (vy)` : name}</span>
                {uid === space.ownerId && <span className="hint">Vlastník</span>}
              </span>
              {isOwner && uid !== me && (
                <IconButton icon="close" label={`Odobrať ${name}`} onClick={() => setRemoving({ uid, name })} />
              )}
            </li>
          );
        })}
      </ul>

      {code && <InviteDialog space={space} code={code} log={log} onClose={() => setCode(null)} />}
      {removing && (
        <ConfirmDialog title={`Odobrať ${removing.name}?`}
          text={log ? 'Prestane vidieť túto činnosť aj jej záznamy.' : 'Prestane vidieť túto oblasť aj jej úlohy.'}
          confirm="Odobrať" onClose={() => setRemoving(null)}
          onConfirm={() => { Repo.removeMember(space.id, removing.uid); setRemoving(null); }} />
      )}
    </div>
  );
}

function InviteDialog({ space, code, log, onClose }: { space: Space; code: string; log: boolean; onClose: () => void }) {
  const link = `${SITE_URL}/join/${code}`;
  const text = `Pridaj sa ${log ? 'k činnosti' : 'do oblasti'} „${space.name}“ v aplikácii Priestor.\n${link}\nKód: ${code}`;
  const [copied, setCopied] = useState(false);
  const canShare = typeof navigator.share === 'function';
  return (
    <Modal title={`Pozvánka do „${space.name}“`} onClose={onClose} actions={<>
      {canShare && (
        <button type="button" className="text" onClick={() => navigator.share({ text }).catch(() => {})}>
          <Icon name="share" size={18} /> Zdieľať
        </button>
      )}
      <button type="button" className="primary" data-autofocus onClick={onClose}>Hotovo</button>
    </>}>
      <p>Pošlite kód alebo odkaz. Platí 7 dní a môže ho použiť ktokoľvek, kto ho dostane.</p>
      <p className="invite-code">{code}</p>
      <div className="row">
        <input className="field" readOnly value={link} aria-label="Odkaz s pozvánkou" />
        <button type="button" className="outline" onClick={() => {
          navigator.clipboard.writeText(text).then(() => setCopied(true)).catch(() => {});
        }}>
          <Icon name={copied ? 'check' : 'copy'} size={18} /> {copied ? 'Skopírované' : 'Kopírovať'}
        </button>
      </div>
    </Modal>
  );
}
