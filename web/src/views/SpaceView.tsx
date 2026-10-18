import { type ReactNode, useState } from 'react';
import type { Ctx } from '../App';
import { EditSectionsDialog } from '../dialogs';
import { formatDue, isOverdue } from '../format';
import { navigate, useTasks } from '../hooks';
import { type Space, type TaskItem, taskOrder } from '../model';
import * as Repo from '../repo';
import { Chip, ColorPicker, ConfirmDialog, IconButton, Menu, Modal, TextInputDialog, Tile } from '../ui';
import { TaskEditor } from './TaskEditor';

const ALL = '__all__';

export function SpaceView({ space, ctx }: { space: Space; ctx: Ctx }) {
  const tasks = useTasks(space.id);
  const [filter, setFilter] = useState(ALL);
  const [editing, setEditing] = useState<TaskItem | null>(null);
  const [creating, setCreating] = useState(false);
  const [dialog, setDialog] = useState<null | 'sections' | 'rename' | 'color' | 'delete' | 'leave'>(null);
  const [quick, setQuick] = useState('');

  const isOwner = space.ownerId === ctx.user.uid;
  const validIds = new Set(space.sections.map((s) => s.id));
  const active = filter !== ALL && !validIds.has(filter) ? ALL : filter;
  const open = (tasks ?? []).filter((t) => !t.done);
  const shown = active === ALL ? open : open.filter((t) => t.sectionId === active);

  const complete = (t: TaskItem) => {
    Repo.setDone(t, true);
    ctx.toast({ text: `Hotovo: ${t.title}`, action: { label: 'Späť', run: () => Repo.setDone(t, false) } });
  };

  const addQuick = () => {
    if (!quick.trim()) return;
    Repo.saveTask(space.id, null, quick, '', active === ALL ? null : active, null, false);
    setQuick('');
  };

  const row = (t: TaskItem) => <TaskRow key={t.id} task={t} onCheck={() => complete(t)} onOpen={() => setEditing(t)} />;

  return (
    <div className="page">
      <header className="page-head">
        <Tile name={space.name} color={space.color} size={40} />
        <h1 className="page-title grow">{space.name}</h1>
        <IconButton icon="personAdd" label="Členovia a pozvánky" onClick={() => navigate(`/s/${space.id}/members`)} />
        <IconButton icon="archive" label="Archív" onClick={() => navigate(`/s/${space.id}/archive`)} />
        <Menu items={[
          { label: 'Upraviť sekcie', onClick: () => setDialog('sections') },
          { label: 'Premenovať', onClick: () => setDialog('rename') },
          { label: 'Zmeniť farbu', onClick: () => setDialog('color') },
          isOwner
            ? { label: 'Zmazať oblasť', onClick: () => setDialog('delete'), danger: true }
            : { label: 'Opustiť oblasť', onClick: () => setDialog('leave'), danger: true },
        ]} />
      </header>

      {space.sections.length > 0 && (
        <div className="chips" role="group" aria-label="Sekcie">
          <Chip selected={active === ALL} onClick={() => setFilter(ALL)}>Všetko</Chip>
          {space.sections.map((s) => {
            const n = open.filter((t) => t.sectionId === s.id).length;
            return (
              <Chip key={s.id} selected={active === s.id} onClick={() => setFilter(s.id)}>
                {s.name}{n > 0 && <span className="chip-count">{n}</span>}
              </Chip>
            );
          })}
        </div>
      )}

      <form className="quick-add" onSubmit={(e) => { e.preventDefault(); addQuick(); }}>
        <input className="field" value={quick} onChange={(e) => setQuick(e.target.value)} maxLength={500}
          placeholder={active === ALL ? 'Pridať úlohu…' : `Pridať do „${space.sections.find((s) => s.id === active)?.name}“…`}
          aria-label="Nová úloha" />
        <button type="submit" className="primary" disabled={!quick.trim()}>Pridať</button>
        <button type="button" className="outline" onClick={() => setCreating(true)}>S termínom…</button>
      </form>

      {tasks === null ? (
        <div className="center-pane"><div className="spinner" aria-label="Načítavam" /></div>
      ) : shown.length === 0 ? (
        <p className="empty">Nič tu nie je. Napíšte úlohu do poľa vyššie a stlačte Enter.</p>
      ) : active === ALL && space.sections.length > 0 ? (
        [...space.sections.map((s) => ({ key: s.id, title: s.name, list: shown.filter((t) => t.sectionId === s.id) })),
          { key: 'none', title: 'Bez sekcie', list: shown.filter((t) => !t.sectionId || !validIds.has(t.sectionId)) }]
          .filter((g) => g.list.length > 0)
          .map((g) => (
            <section key={g.key} className="group">
              <h2 className="group-title">{g.title}</h2>
              <ul className="tasks">{[...g.list].sort(taskOrder).map(row)}</ul>
            </section>
          ))
      ) : (
        <ul className="tasks">{[...shown].sort(taskOrder).map(row)}</ul>
      )}

      {(creating || editing) && (
        <TaskEditor space={space} task={editing}
          defaultSectionId={active === ALL ? null : active}
          onClose={() => { setCreating(false); setEditing(null); }} />
      )}

      {dialog === 'sections' && (
        <EditSectionsDialog initial={space.sections} onClose={() => setDialog(null)}
          onSave={(s) => { Repo.updateSections(space.id, s); setDialog(null); }} />
      )}
      {dialog === 'rename' && (
        <TextInputDialog title="Premenovať oblasť" initial={space.name} confirm="Uložiť" onClose={() => setDialog(null)}
          onConfirm={(v) => { Repo.renameSpace(space.id, v); setDialog(null); }} />
      )}
      {dialog === 'color' && <ColorDialog space={space} onClose={() => setDialog(null)} />}
      {dialog === 'delete' && (
        <ConfirmDialog title={`Zmazať „${space.name}“?`}
          text="Zmažú sa všetky úlohy aj archív pre všetkých členov. Nedá sa to vrátiť." confirm="Zmazať"
          onClose={() => setDialog(null)}
          onConfirm={() => { setDialog(null); navigate('/'); Repo.deleteSpace(space.id).catch(console.error); }} />
      )}
      {dialog === 'leave' && (
        <ConfirmDialog title={`Opustiť „${space.name}“?`}
          text="Oblasť zmizne z vášho zoznamu. Späť sa dostanete len cez novú pozvánku." confirm="Opustiť"
          onClose={() => setDialog(null)}
          onConfirm={() => { setDialog(null); navigate('/'); Repo.leaveSpace(space.id).catch(console.error); }} />
      )}
    </div>
  );
}

export function ColorDialog({ space, onClose }: { space: Space; onClose: () => void }) {
  const [c, setC] = useState(space.color);
  return (
    <Modal title="Farba" onClose={onClose} actions={<>
      <button type="button" className="text" onClick={onClose}>Zrušiť</button>
      <button type="button" className="primary" onClick={() => { Repo.setColor(space.id, c); onClose(); }}>Uložiť</button>
    </>}>
      <ColorPicker value={c} onChange={setC} />
    </Modal>
  );
}

export function TaskRow({ task, sectionName, onCheck, onOpen, meta, trailing }: {
  task: TaskItem; sectionName?: string | null; onCheck: () => void; onOpen?: () => void;
  meta?: string; trailing?: ReactNode;
}) {
  const parts = meta != null ? [meta] : [
    task.dueAt != null ? formatDue(task.dueAt, task.hasTime) : null,
    sectionName ?? null,
    task.note.trim() ? task.note.split('\n')[0] : null,
  ].filter(Boolean);
  const overdue = !task.done && task.dueAt != null && isOverdue(task.dueAt, task.hasTime);
  return (
    <li className="task">
      <input type="checkbox" className="check" checked={task.done} onChange={onCheck}
        aria-label={task.done ? `Vrátiť medzi otvorené: ${task.title}` : `Hotovo: ${task.title}`} />
      <button type="button" className="task-body" onClick={onOpen} disabled={!onOpen}>
        <span className="task-title">{task.title}</span>
        {parts.length > 0 && <span className={`task-meta${overdue ? ' overdue' : ''}`}>{parts.join(' · ')}</span>}
      </button>
      {trailing}
    </li>
  );
}
