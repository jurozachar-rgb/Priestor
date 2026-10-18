import type { Ctx } from '../App';
import { navigate } from '../hooks';
import * as Repo from '../repo';
import { Icon, Tile } from '../ui';

/** Úvod: zoznam oblastí (na úzkej obrazovke je to hlavná navigácia). */
export function HomeView({ ctx }: { ctx: Ctx }) {
  const areas = ctx.areas;
  if (areas === null) return <div className="center-pane"><div className="spinner" aria-label="Načítavam" /></div>;
  if (areas.length === 0) {
    return (
      <div className="center-pane narrow-text">
        <h1 className="page-title">Zatiaľ žiadne oblasti</h1>
        <p className="muted">Oblasť je zoznam úloh pre jednu časť života – domov, nákup, chata, dovolenka. Každú môžete zdieľať s inými ľuďmi.</p>
        <div className="button-col">
          <button type="button" className="primary" onClick={() => {
            Repo.createSpace('Domov', [], 0);
            Repo.createSpace('Nákup', [], 2);
            Repo.createSpace('Chata', ['Nakúpiť', 'Urobiť'], 1);
            Repo.createSpace('Rodičia', [], 3);
            Repo.createSpace('Dieťa', [], 4);
          }}>Vytvoriť základné oblasti</button>
          <button type="button" className="outline" onClick={ctx.openNewSpace}>Vytvoriť vlastnú</button>
          <button type="button" className="text" onClick={ctx.openJoin}>Mám kód pozvánky</button>
        </div>
      </div>
    );
  }
  return (
    <div className="page">
      <header className="page-head">
        <h1 className="page-title">Oblasti</h1>
        <button type="button" className="primary" onClick={ctx.openNewSpace}><Icon name="add" size={20} /> Nová oblasť</button>
      </header>
      <div className="cards">
        {areas.map((s) => (
          <button key={s.id} type="button" className="card" onClick={() => navigate(`/s/${s.id}`)}>
            <Tile name={s.name} color={s.color} size={44} />
            <span className="card-text">
              <span className="card-title">{s.name}</span>
              {s.sections.length > 0 && <span className="card-sub">{s.sections.map((x) => x.name).join(', ')}</span>}
            </span>
            {s.members.length > 1 && <span className="side-count" title="Zdieľané"><Icon name="people" size={16} />{s.members.length}</span>}
          </button>
        ))}
        <button type="button" className="card card-log" onClick={() => navigate('/log')}>
          <span className="card-icon"><Icon name="calendar" size={26} /></span>
          <span className="card-text">
            <span className="card-title">Kalendár činností</span>
            <span className="card-sub">Kedy ste kosili, polievali, menili olej</span>
          </span>
        </button>
      </div>
    </div>
  );
}
