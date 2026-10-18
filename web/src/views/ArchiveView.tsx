import { formatDate } from '../format';
import { navigate, useTasks } from '../hooks';
import { type Space, sectionName } from '../model';
import * as Repo from '../repo';
import { IconButton } from '../ui';
import { TaskRow } from './SpaceView';

/** Archív urobených úloh. Odškrtnutím sa úloha vráti medzi otvorené. */
export function ArchiveView({ space }: { space: Space }) {
  const tasks = useTasks(space.id);
  const done = (tasks ?? []).filter((t) => t.done).sort((a, b) => (b.doneAt ?? Infinity) - (a.doneAt ?? Infinity));
  return (
    <div className="page">
      <header className="page-head">
        <IconButton icon="back" label="Späť" onClick={() => navigate(`/s/${space.id}`)} />
        <h1 className="page-title grow">Archív · {space.name}</h1>
      </header>
      {tasks === null ? (
        <div className="center-pane"><div className="spinner" aria-label="Načítavam" /></div>
      ) : done.length === 0 ? (
        <p className="empty">Zatiaľ nič urobené.</p>
      ) : (
        <ul className="tasks">
          {done.map((t) => (
            <TaskRow key={t.id} task={t}
              meta={[t.doneAt ? formatDate(t.doneAt) : null, t.doneByName, sectionName(space, t.sectionId)].filter(Boolean).join(' · ')}
              onCheck={() => Repo.setDone(t, false)}
              trailing={<IconButton icon="trash" label="Zmazať natrvalo" onClick={() => Repo.deleteTask(t)} />} />
          ))}
        </ul>
      )}
    </div>
  );
}
