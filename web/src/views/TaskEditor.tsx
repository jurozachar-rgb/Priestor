import { useState } from 'react';
import { isoDate, parseIso, timeValue } from '../format';
import type { Space, TaskItem } from '../model';
import * as Repo from '../repo';
import { Chip, ConfirmDialog, Modal } from '../ui';

/** Pridanie alebo úprava úlohy. task == null → nová úloha. */
export function TaskEditor({ space, task, defaultSectionId, onClose }: {
  space: Space; task: TaskItem | null; defaultSectionId: string | null; onClose: () => void;
}) {
  const due = task?.dueAt != null ? new Date(task.dueAt) : null;
  const [title, setTitle] = useState(task?.title ?? '');
  const [note, setNote] = useState(task?.note ?? '');
  const [sectionId, setSectionId] = useState<string | null>(task?.sectionId ?? defaultSectionId);
  const [date, setDate] = useState(due ? isoDate(due) : '');
  const [time, setTime] = useState(due && task?.hasTime ? timeValue(due) : '');
  const [confirmDelete, setConfirmDelete] = useState(false);

  const save = () => {
    if (!title.trim()) return;
    let dueAt: number | null = null;
    if (date) {
      const d = parseIso(date);
      if (time) { const [h, m] = time.split(':').map(Number); d.setHours(h, m, 0, 0); }
      dueAt = d.getTime();
    }
    Repo.saveTask(space.id, task?.id ?? null, title, note, sectionId, dueAt, !!(date && time));
    onClose();
  };

  return (
    <>
      <Modal title={task ? 'Upraviť úlohu' : 'Nová úloha'} onClose={onClose} actions={<>
        {task && <button type="button" className="text danger push-left" onClick={() => setConfirmDelete(true)}>Zmazať</button>}
        <button type="button" className="text" onClick={onClose}>Zrušiť</button>
        <button type="submit" form="task-form" className="primary" disabled={!title.trim()}>Uložiť</button>
      </>}>
        <form id="task-form" className="stack" onSubmit={(e) => { e.preventDefault(); save(); }}
          onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) save(); }}>
          <div>
            <label className="label" htmlFor="t-title">Čo treba urobiť</label>
            <input id="t-title" className="field" value={title} maxLength={500} autoFocus
              onChange={(e) => setTitle(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="t-note">Poznámka (nepovinné)</label>
            <textarea id="t-note" className="field" rows={3} value={note} maxLength={5000}
              onChange={(e) => setNote(e.target.value)} />
          </div>
          {space.sections.length > 0 && (
            <div className="chips flat" role="group" aria-label="Sekcia">
              <Chip selected={sectionId == null} onClick={() => setSectionId(null)}>Bez sekcie</Chip>
              {space.sections.map((s) => (
                <Chip key={s.id} selected={sectionId === s.id} onClick={() => setSectionId(s.id)}>{s.name}</Chip>
              ))}
            </div>
          )}
          <div className="row wrap">
            <div>
              <label className="label" htmlFor="t-date">Termín</label>
              <input id="t-date" type="date" className="field" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div>
              <label className="label" htmlFor="t-time">Čas (nepovinné)</label>
              <input id="t-time" type="time" className="field" value={time} disabled={!date}
                onChange={(e) => setTime(e.target.value)} />
            </div>
            {date && <button type="button" className="text align-end" onClick={() => { setDate(''); setTime(''); }}>Bez termínu</button>}
          </div>
          {task?.createdByName && <p className="hint">Pridal(a): {task.createdByName}</p>}
        </form>
      </Modal>
      {confirmDelete && task && (
        <ConfirmDialog title="Zmazať úlohu?" text={task.title} confirm="Zmazať" onClose={() => setConfirmDelete(false)}
          onConfirm={() => { Repo.deleteTask(task); onClose(); }} />
      )}
    </>
  );
}
