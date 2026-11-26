import { type HTMLAttributes, type ReactNode, useState } from 'react';
import type { Ctx } from '../App';
import { EditSectionsDialog } from '../dialogs';
import { formatDue, isOverdue } from '../format';
import { navigate, useTasks } from '../hooks';
import { type Space, type TaskItem, taskOrder, titleSuggestions } from '../model';
import * as Repo from '../repo';
import { Chip, ColorPicker, ConfirmDialog, Icon, IconButton, Menu, Modal, Suggestions, TextInputDialog, Tile } from '../ui';
import { TaskEditor } from './TaskEditor';

const ALL = '__all__';

export function SpaceView({ space, ctx }: { space: Space; ctx: Ctx }) {
  const tasks = useTasks(space.id);
  const [filter, setFilter] = useState(ALL);
  const [editing, setEditing] = useState<TaskItem | null>(null);
  const [creating, setCreating] = useState(false);
  const [dialog, setDialog] = useState<null | 'sections' | 'rename' | 'color' | 'delete' | 'leave'>(null);
  const [quick, setQuick] = useState('');
  // Ťahanie myšou: ktorá úloha sa ťahá, v ktorej skupine a aktuálne (ešte neuložené) poradie skupiny.
  const [drag, setDrag] = useState<{ id: string; group: string; items: TaskItem[] } | null>(null);

  const isOwner = space.ownerId === ctx.user.uid;
  const validIds = new Set(space.sections.map((s) => s.id));
  const active = filter !== ALL && !validIds.has(filter) ? ALL : filter;
  const open = (tasks ?? []).filter((t) => !t.done);
  const shown = active === ALL ? open : open.filter((t) => t.sectionId === active);

  const complete = (t: TaskItem) => {
    Repo.setDone(t, true);
    ctx.toast({ text: `Hotovo: ${t.title}`, action: { label: 'Späť', run: () => Repo.setDone(t, false) } });
  };

  const addQuick = (title = quick, fromSectionId: string | null = null) => {
    if (!title.trim()) return;
    const section = active !== ALL ? active : fromSectionId && validIds.has(fromSectionId) ? fromSectionId : null;
    Repo.saveTask(space.id, null, title, '', section, null, false);
    setQuick('');
  };
  const suggestions = titleSuggestions(quick, tasks ?? []);

  /** Zoznam úloh jednej skupiny (sekcie), ktorý sa dá preusporiadať ťahaním myšou. */
  const list = (group: string, items: TaskItem[]) => {
    const sorted = drag?.group === group ? drag.items : [...items].sort(taskOrder);
    return (
      <ul className="tasks">
        {sorted.map((t) => (
          <TaskRow key={t.id} task={t} onCheck={() => complete(t)} onOpen={() => setEditing(t)}
            dragging={drag?.id === t.id}
            dnd={{
              draggable: true,
              onDragStart: (e) => {
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', t.id);
                setDrag({ id: t.id, group, items: sorted });
              },
              onDragOver: (e) => {
                if (!drag || drag.group !== group) return;
                e.preventDefault();
                if (drag.id === t.id) return;
                const from = drag.items.findIndex((x) => x.id === drag.id);
                const to = drag.items.findIndex((x) => x.id === t.id);
                if (from < 0 || to < 0) return;
                const next = [...drag.items];
                next.splice(to, 0, ...next.splice(from, 1));
                setDrag({ ...drag, items: next });
              },
              onDrop: (e) => e.preventDefault(),
              onDragEnd: () => {
                if (drag) Repo.reorderTasks(space.id, drag.items);
                setDrag(null);
              },
            }}
            trailing={<span className="drag-handle" title="Presunúť – chyťte a potiahnite"><Icon name="drag" /></span>} />
        ))}
      </ul>
    );
  };

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
          aria-label="Nová úloha" autoComplete="off" />
        <button type="submit" className="primary" disabled={!quick.trim()}>Pridať</button>
        <button type="button" className="outline" onClick={() => setCreating(true)}>S termínom…</button>
      </form>
      <Suggestions items={suggestions} onPick={(title) => {
        const from = suggestions.find((s) => s.title === title);
        addQuick(title, from?.sectionId ?? null);
      }} />

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
              {list(g.key, g.list)}
            </section>
          ))
      ) : (
        list(active, shown)
      )}

      {(creating || editing) && (
        <TaskEditor space={space} task={editing} allTasks={tasks ?? []}
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

export function TaskRow({ task, sectionName, onCheck, onOpen, meta, trailing, dnd, dragging }: {
  task: TaskItem; sectionName?: string | null; onCheck: () => void; onOpen?: () => void;
  meta?: string; trailing?: ReactNode; dnd?: HTMLAttributes<HTMLLIElement> & { draggable?: boolean }; dragging?: boolean;
}) {
  const parts = meta != null ? [meta] : [
    task.dueAt != null ? formatDue(task.dueAt, task.hasTime) : null,
    sectionName ?? null,
    task.note.trim() ? task.note.split('\n')[0] : null,
  ].filter(Boolean);
  const overdue = !task.done && task.dueAt != null && isOverdue(task.dueAt, task.hasTime);
  return (
    <li className={`task${dragging ? ' dragging' : ''}`} {...dnd}>
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
