import { useState } from 'react';
import type { Ctx } from '../App';
import {
  daysBetween, entriesCount, isoDate, longDate, longDateWithDay, monthTitle, relativeDays, spaceColor, spaceColors,
  timesCount, todayIso, weekdayIndex, weekdayShort, weekdaysShort,
} from '../format';
import { navigate, useEntries } from '../hooks';
import type { LogEntry, Space } from '../model';
import * as Repo from '../repo';
import { Chip, ColorPicker, ConfirmDialog, Dot, Icon, IconButton, Menu, Modal, TextInputDialog } from '../ui';
import { ColorDialog } from './SpaceView';

/**
 * Kalendár činností: vlastné činnosti (Kosenie…), ku každej záznamy s dátumom a poznámkou.
 * Vľavo mesačný kalendár s bodkami, vpravo (na úzkej obrazovke pod ním) súhrn vybranej činnosti.
 */
export function LogView({ ctx, selectedId }: { ctx: Ctx; selectedId: string | null }) {
  const activities = ctx.activities;
  const entries = useEntries((activities ?? []).map((a) => a.id));
  const today = todayIso();
  const [month, setMonth] = useState(() => { const d = new Date(); return { y: d.getFullYear(), m: d.getMonth() }; });
  const [day, setDay] = useState<string | null>(null);
  const [editing, setEditing] = useState<LogEntry | null>(null);
  const [dialog, setDialog] = useState<null | 'new' | 'rename' | 'color' | 'delete' | 'leave'>(null);

  if (activities === null) return <div className="center-pane"><div className="spinner" aria-label="Načítavam" /></div>;

  const selected = activities.find((a) => a.id === selectedId) ?? null;
  const byId = new Map(activities.map((a) => [a.id, a]));
  const visible = entries.filter((e) => byId.has(e.spaceId) && (!selected || e.spaceId === selected.id));
  const monthPrefix = `${month.y}-${String(month.m + 1).padStart(2, '0')}`;
  const select = (id: string | null) => navigate(id ? `/log/${id}` : '/log');
  const shift = (n: number) => setMonth(({ y, m }) => { const d = new Date(y, m + n, 1); return { y: d.getFullYear(), m: d.getMonth() }; });

  if (activities.length === 0) {
    return (
      <div className="center-pane narrow-text">
        <span className="big-icon"><Icon name="calendar" size={48} /></span>
        <h1 className="page-title">Kalendár činností</h1>
        <p className="muted">Zapisujte si, kedy ste čo robili – kosenie, polievanie, výmenu oleja. Ku každej činnosti potom uvidíte všetky termíny pod sebou a môžete ju zdieľať s rodinou.</p>
        <div className="button-col">
          <button type="button" className="primary" onClick={() => select(Repo.createActivity('Kosenie', 0))}>Vytvoriť „Kosenie“</button>
          <button type="button" className="outline" onClick={() => setDialog('new')}>Vytvoriť vlastnú činnosť</button>
        </div>
        {dialog === 'new' && <NewActivityDialog onClose={() => setDialog(null)} onCreate={(n, c) => { setDialog(null); select(Repo.createActivity(n, c)); }} />}
      </div>
    );
  }

  const dots = new Map<string, string[]>();
  visible.filter((e) => e.date.startsWith(monthPrefix)).forEach((e) => {
    dots.set(e.date, [...(dots.get(e.date) ?? []), spaceColor(byId.get(e.spaceId)?.color ?? 0)]);
  });

  return (
    <div className="page log">
      <header className="page-head">
        <h1 className="page-title grow">{selected ? selected.name : 'Kalendár činností'}</h1>
        <button type="button" className="primary" onClick={() => setDay(today)}><Icon name="add" size={20} /> Zapísať dnes</button>
        {selected && (
          <Menu items={[
            { label: 'Zdieľať / členovia', onClick: () => navigate(`/s/${selected.id}/members`) },
            { label: 'Premenovať', onClick: () => setDialog('rename') },
            { label: 'Zmeniť farbu', onClick: () => setDialog('color') },
            selected.ownerId === ctx.user.uid
              ? { label: 'Zmazať činnosť', onClick: () => setDialog('delete'), danger: true }
              : { label: 'Opustiť činnosť', onClick: () => setDialog('leave'), danger: true },
          ]} />
        )}
      </header>

      <div className="chips" role="group" aria-label="Činnosti">
        <Chip selected={!selected} onClick={() => select(null)}>Všetko</Chip>
        {activities.map((a) => (
          <Chip key={a.id} selected={a.id === selected?.id} onClick={() => select(a.id === selected?.id ? null : a.id)}>
            <Dot color={spaceColor(a.color)} />{a.name}
          </Chip>
        ))}
        <button type="button" className="chip add" onClick={() => setDialog('new')}><Icon name="add" size={16} />Nová činnosť</button>
      </div>

      <div className="log-grid">
        <section className="calendar" aria-label="Mesačný kalendár">
          <div className="cal-head">
            <IconButton icon="left" label="Predchádzajúci mesiac" onClick={() => shift(-1)} />
            <h2 className="cal-title">{monthTitle(month.y, month.m)}</h2>
            <IconButton icon="right" label="Ďalší mesiac" onClick={() => shift(1)} />
          </div>
          {!today.startsWith(monthPrefix) && (
            <button type="button" className="text cal-today" onClick={() => { const d = new Date(); setMonth({ y: d.getFullYear(), m: d.getMonth() }); }}>
              Späť na dnešok
            </button>
          )}
          <MonthGrid y={month.y} m={month.m} today={today} dots={dots} onDay={setDay} />
        </section>

        <section className="summary" aria-label="Súhrn">
          {selected ? (
            <ActivitySummary activity={selected} entries={visible} today={today} onOpen={setEditing} />
          ) : (
            <MonthList monthTitleText={monthTitle(month.y, month.m)}
              entries={visible.filter((e) => e.date.startsWith(monthPrefix))} byId={byId} onOpen={setEditing} />
          )}
        </section>
      </div>

      {day && (
        <DayDialog day={day} activities={activities}
          preselected={selected?.id ?? (activities.length === 1 ? activities[0].id : null)}
          dayEntries={entries.filter((e) => e.date === day && byId.has(e.spaceId))} byId={byId}
          onEdit={(e) => { setDay(null); setEditing(e); }} onClose={() => setDay(null)}
          onAdd={(spaceId, note) => { Repo.addEntry(spaceId, day, note); setDay(null); }} />
      )}
      {editing && (
        <EditEntryDialog entry={editing} activityName={byId.get(editing.spaceId)?.name ?? ''} onClose={() => setEditing(null)} />
      )}
      {dialog === 'new' && (
        <NewActivityDialog onClose={() => setDialog(null)} onCreate={(n, c) => { setDialog(null); select(Repo.createActivity(n, c)); }} />
      )}
      {dialog === 'rename' && selected && (
        <TextInputDialog title="Premenovať činnosť" initial={selected.name} confirm="Uložiť" onClose={() => setDialog(null)}
          onConfirm={(v) => { Repo.renameSpace(selected.id, v); setDialog(null); }} />
      )}
      {dialog === 'color' && selected && <ColorDialog space={selected} onClose={() => setDialog(null)} />}
      {dialog === 'delete' && selected && (
        <ConfirmDialog title={`Zmazať „${selected.name}“?`} text="Zmažú sa všetky záznamy tejto činnosti, aj pre ostatných členov."
          confirm="Zmazať" onClose={() => setDialog(null)}
          onConfirm={() => { setDialog(null); select(null); Repo.deleteSpace(selected.id).catch(console.error); }} />
      )}
      {dialog === 'leave' && selected && (
        <ConfirmDialog title={`Opustiť „${selected.name}“?`} text="Prestanete vidieť túto činnosť a jej záznamy."
          confirm="Opustiť" onClose={() => setDialog(null)}
          onConfirm={() => { setDialog(null); select(null); Repo.leaveSpace(selected.id).catch(console.error); }} />
      )}
    </div>
  );
}

function MonthGrid({ y, m, today, dots, onDay }: {
  y: number; m: number; today: string; dots: Map<string, string[]>; onDay: (iso: string) => void;
}) {
  const first = new Date(y, m, 1);
  const offset = weekdayIndex(first);
  const len = new Date(y, m + 1, 0).getDate();
  const cells: (string | null)[] = [
    ...Array.from({ length: offset }, () => null),
    ...Array.from({ length: len }, (_, i) => isoDate(new Date(y, m, i + 1))),
  ];
  while (cells.length % 7) cells.push(null);
  return (
    <div className="cal-grid" role="grid">
      {weekdaysShort.map((w) => <div key={w} className="cal-wd" role="columnheader">{w}</div>)}
      {cells.map((iso, i) => {
        if (!iso) return <div key={`e${i}`} className="cal-cell empty" />;
        const d = dots.get(iso) ?? [];
        const label = `${longDateWithDay(iso)}${d.length ? `, ${entriesCount(d.length)}` : ''}`;
        return (
          <button key={iso} type="button" role="gridcell" aria-label={label}
            className={`cal-cell${iso === today ? ' today' : ''}${d.length ? ' has' : ''}`} onClick={() => onDay(iso)}>
            <span className="cal-num">{Number(iso.slice(8))}</span>
            <span className="cal-dots">{d.slice(0, 4).map((c, j) => <Dot key={j} color={c} size={6} />)}</span>
          </button>
        );
      })}
    </div>
  );
}

function ActivitySummary({ activity, entries, today, onOpen }: {
  activity: Space; entries: LogEntry[]; today: string; onOpen: (e: LogEntry) => void;
}) {
  const sorted = [...entries].sort((a, b) => a.date.localeCompare(b.date));
  const past = sorted.filter((e) => e.date <= today);
  const last = past[past.length - 1];
  const years = new Set(sorted.map((e) => e.date.slice(0, 4)));
  const shared = activity.members.length > 1;
  return (
    <>
      <div className="summary-card">
        <div className="summary-top">
          <Dot color={spaceColor(activity.color)} size={12} />
          <span className="summary-name">{activity.name}</span>
          <span className="summary-count">{timesCount(sorted.length)}</span>
        </div>
        {sorted.length === 0 ? (
          <p className="muted">Zatiaľ žiadny záznam. Kliknite na deň v kalendári alebo na „Zapísať dnes“.</p>
        ) : (
          <>
            {last && <p>Naposledy {longDate(last.date)} ({relativeDays(last.date, today)})</p>}
            {sorted.length >= 2 && (
              <p className="hint">
                Priemerne každých {Math.round(daysBetween(sorted[0].date, sorted[sorted.length - 1].date) / (sorted.length - 1))} dní,
                od {longDate(sorted[0].date)}
              </p>
            )}
          </>
        )}
      </div>
      <ol className="entries">
        {sorted.map((e, i) => (
          <li key={e.id}>
            {years.size > 1 && (i === 0 || sorted[i - 1].date.slice(0, 4) !== e.date.slice(0, 4)) && (
              <h3 className="year">{e.date.slice(0, 4)}</h3>
            )}
            <EntryRow entry={e} showAuthor={shared} onOpen={() => onOpen(e)} />
          </li>
        ))}
      </ol>
    </>
  );
}

function MonthList({ monthTitleText, entries, byId, onOpen }: {
  monthTitleText: string; entries: LogEntry[]; byId: Map<string, Space>; onOpen: (e: LogEntry) => void;
}) {
  const sorted = [...entries].sort((a, b) => a.date.localeCompare(b.date)
    || (byId.get(a.spaceId)?.name ?? '').localeCompare(byId.get(b.spaceId)?.name ?? '', 'sk'));
  return (
    <>
      <h2 className="summary-title">{monthTitleText}: {entriesCount(sorted.length)}</h2>
      <p className="hint">Vyberte činnosť hore a uvidíte všetky jej termíny pod sebou.</p>
      <ol className="entries">
        {sorted.map((e) => {
          const a = byId.get(e.spaceId);
          return (
            <li key={e.id}>
              <EntryRow entry={e} activity={a} showAuthor={(a?.members.length ?? 1) > 1} onOpen={() => onOpen(e)} />
            </li>
          );
        })}
      </ol>
    </>
  );
}

function EntryRow({ entry, activity, showAuthor, onOpen }: {
  entry: LogEntry; activity?: Space; showAuthor: boolean; onOpen: () => void;
}) {
  const sub = [entry.note.trim() || null, showAuthor && entry.createdByName ? `zapísal(a) ${entry.createdByName}` : null]
    .filter(Boolean).join(' · ');
  return (
    <button type="button" className="entry" onClick={onOpen}>
      <span className="entry-wd">{weekdayShort(entry.date)}</span>
      <span className="entry-main">
        <span className="entry-date">{longDate(entry.date, false)}</span>
        {sub && <span className="entry-sub">{sub}</span>}
      </span>
      {activity && <span className="entry-act"><Dot color={spaceColor(activity.color)} />{activity.name}</span>}
    </button>
  );
}

function DayDialog({ day, activities, preselected, dayEntries, byId, onEdit, onClose, onAdd }: {
  day: string; activities: Space[]; preselected: string | null; dayEntries: LogEntry[]; byId: Map<string, Space>;
  onEdit: (e: LogEntry) => void; onClose: () => void; onAdd: (spaceId: string, note: string) => void;
}) {
  const [chosen, setChosen] = useState(preselected);
  const [note, setNote] = useState('');
  return (
    <Modal title={longDateWithDay(day)} onClose={onClose} actions={<>
      <button type="button" className="text" onClick={onClose}>Zavrieť</button>
      <button type="submit" form="day-form" className="primary" disabled={!chosen}>Zapísať</button>
    </>}>
      {dayEntries.length > 0 && (
        <div className="day-entries">
          <h3 className="label">Zapísané</h3>
          {dayEntries.map((e) => {
            const a = byId.get(e.spaceId);
            return (
              <button key={e.id} type="button" className="entry compact" onClick={() => onEdit(e)}>
                <Dot color={spaceColor(a?.color ?? 0)} />
                <span className="entry-main">
                  <span className="entry-date">{a?.name}</span>
                  {e.note && <span className="entry-sub">{e.note}</span>}
                </span>
                <span className="hint">Upraviť</span>
              </button>
            );
          })}
        </div>
      )}
      <form id="day-form" className="stack" onSubmit={(e) => { e.preventDefault(); if (chosen) onAdd(chosen, note); }}>
        <div>
          <h3 className="label">Zapísať činnosť</h3>
          <div className="chips flat" role="radiogroup" aria-label="Činnosť">
            {activities.map((a) => (
              <Chip key={a.id} selected={a.id === chosen} onClick={() => setChosen(a.id)}>
                <Dot color={spaceColor(a.color)} />{a.name}
              </Chip>
            ))}
          </div>
        </div>
        <div>
          <label className="label" htmlFor="d-note">Poznámka (nepovinné)</label>
          <input id="d-note" className="field" value={note} maxLength={1000} placeholder="napr. aj za plotom"
            onChange={(e) => setNote(e.target.value)} />
        </div>
      </form>
    </Modal>
  );
}

function EditEntryDialog({ entry, activityName, onClose }: { entry: LogEntry; activityName: string; onClose: () => void }) {
  const [date, setDate] = useState(entry.date);
  const [note, setNote] = useState(entry.note);
  const [confirm, setConfirm] = useState(false);
  return (
    <>
      <Modal title={activityName} onClose={onClose} actions={<>
        <button type="button" className="text danger push-left" onClick={() => setConfirm(true)}>Zmazať</button>
        <button type="button" className="text" onClick={onClose}>Zrušiť</button>
        <button type="submit" form="entry-form" className="primary" disabled={!date}>Uložiť</button>
      </>}>
        <form id="entry-form" className="stack" onSubmit={(e) => {
          e.preventDefault();
          if (date) { Repo.updateEntry(entry, date, note); onClose(); }
        }}>
          <div>
            <label className="label" htmlFor="e-date">Dátum</label>
            <input id="e-date" type="date" className="field" value={date} required onChange={(e) => setDate(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="e-note">Poznámka</label>
            <input id="e-note" className="field" value={note} maxLength={1000} onChange={(e) => setNote(e.target.value)} />
          </div>
          {entry.createdByName && <p className="hint">Zapísal(a) {entry.createdByName}</p>}
        </form>
      </Modal>
      {confirm && (
        <ConfirmDialog title="Zmazať záznam?" text={`${activityName}, ${longDate(entry.date)}`} confirm="Zmazať"
          onClose={() => setConfirm(false)} onConfirm={() => { Repo.deleteEntry(entry); onClose(); }} />
      )}
    </>
  );
}

const SUGGESTIONS = ['Kosenie', 'Polievanie', 'Strihanie plota', 'Hnojenie', 'Výmena oleja'];

function NewActivityDialog({ onClose, onCreate }: { onClose: () => void; onCreate: (name: string, color: number) => void }) {
  const [name, setName] = useState('');
  const [color, setColor] = useState(() => Math.floor(Math.random() * spaceColors.length));
  return (
    <Modal title="Nová činnosť" onClose={onClose} actions={<>
      <button type="button" className="text" onClick={onClose}>Zrušiť</button>
      <button type="submit" form="new-act" className="primary" disabled={!name.trim()}>Vytvoriť</button>
    </>}>
      <form id="new-act" className="stack" onSubmit={(e) => { e.preventDefault(); if (name.trim()) onCreate(name, color); }}>
        <div>
          <label className="label" htmlFor="na-name">Názov</label>
          <input id="na-name" className="field" value={name} maxLength={100} placeholder="napr. Kosenie" autoFocus
            onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="chips flat">
          {SUGGESTIONS.map((s) => <Chip key={s} selected={name === s} onClick={() => setName(s)}>{s}</Chip>)}
        </div>
        <ColorPicker value={color} onChange={setColor} />
      </form>
    </Modal>
  );
}
