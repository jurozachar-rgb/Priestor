import { useState } from 'react';
import { INVITE_LEN, type Section } from './model';
import * as Repo from './repo';
import { ColorPicker, IconButton, Modal } from './ui';
import { spaceColors } from './format';

export function JoinDialog({ initialCode, onClose, onJoined }: {
  initialCode: string; onClose: () => void; onJoined: (spaceId: string) => void;
}) {
  const [code, setCode] = useState(initialCode);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true); setError(null);
    try {
      onJoined(await Repo.joinByCode(code));
    } catch (e) {
      setError(e instanceof Error && e.message ? e.message : 'Nepodarilo sa pripojiť. Skontrolujte internet.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="Pripojiť sa kódom" onClose={onClose} actions={<>
      <button type="button" className="text" onClick={onClose}>Zrušiť</button>
      <button type="submit" form="join" className="primary" disabled={busy || !code.trim()}>{busy ? 'Pripájam…' : 'Pripojiť'}</button>
    </>}>
      <form id="join" onSubmit={(e) => { e.preventDefault(); if (!busy && code.trim()) submit(); }}>
        <p>Zadajte kód pozvánky do oblasti alebo činnosti, ktorý vám niekto poslal.</p>
        <label className="label" htmlFor="join-code">Kód</label>
        <input id="join-code" className={`field code-field${error ? ' invalid' : ''}`} value={code} autoFocus
          maxLength={12} autoComplete="off" spellCheck={false} placeholder={'X'.repeat(INVITE_LEN)}
          onChange={(e) => { setCode(e.target.value.toUpperCase()); setError(null); }}
          aria-invalid={!!error} aria-describedby={error ? 'join-err' : undefined} />
        {error && <p id="join-err" className="error">{error}</p>}
      </form>
    </Modal>
  );
}

export function NewSpaceDialog({ onClose, onCreate }: {
  onClose: () => void; onCreate: (name: string, sections: string[], color: number) => void;
}) {
  const [name, setName] = useState('');
  const [sections, setSections] = useState('');
  const [color, setColor] = useState(() => Math.floor(Math.random() * spaceColors.length));
  return (
    <Modal title="Nová oblasť" onClose={onClose} actions={<>
      <button type="button" className="text" onClick={onClose}>Zrušiť</button>
      <button type="submit" form="new-space" className="primary" disabled={!name.trim()}>Vytvoriť</button>
    </>}>
      <form id="new-space" className="stack" onSubmit={(e) => {
        e.preventDefault();
        if (name.trim()) onCreate(name, sections.split(',').map((s) => s.trim()).filter(Boolean), color);
      }}>
        <div>
          <label className="label" htmlFor="ns-name">Názov</label>
          <input id="ns-name" className="field" value={name} maxLength={100} placeholder="napr. Chata" autoFocus
            onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="ns-sections">Sekcie (nepovinné)</label>
          <input id="ns-sections" className="field" value={sections} placeholder="Nakúpiť, Urobiť"
            onChange={(e) => setSections(e.target.value)} />
          <p className="hint">Oddeľte čiarkou. Dajú sa zmeniť aj neskôr.</p>
        </div>
        <ColorPicker value={color} onChange={setColor} />
      </form>
    </Modal>
  );
}

/** Úprava sekcií oblasti: premenovať, pridať, odstrániť. */
export function EditSectionsDialog({ initial, onClose, onSave }: {
  initial: Section[]; onClose: () => void; onSave: (s: Section[]) => void;
}) {
  const [items, setItems] = useState<Section[]>(initial);
  return (
    <Modal title="Sekcie" onClose={onClose} actions={<>
      <button type="button" className="text" onClick={onClose}>Zrušiť</button>
      <button type="button" className="primary" onClick={() => onSave(items.filter((s) => s.name.trim()))}>Uložiť</button>
    </>}>
      <p className="hint">Úlohy z odstránenej sekcie zostanú v oblasti bez sekcie.</p>
      <div className="stack-tight">
        {items.map((s, i) => (
          <div key={s.id} className="row">
            <input className="field" value={s.name} maxLength={100} aria-label={`Sekcia ${i + 1}`}
              onChange={(e) => setItems(items.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} />
            <IconButton icon="close" label="Odstrániť sekciu" onClick={() => setItems(items.filter((_, j) => j !== i))} />
          </div>
        ))}
      </div>
      <button type="button" className="text" onClick={() => setItems([...items, { id: Repo.newId(), name: '' }])}>
        + Pridať sekciu
      </button>
    </Modal>
  );
}
