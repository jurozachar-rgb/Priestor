import { useState } from 'react';
import * as Repo from '../repo';
import { Icon } from '../ui';

function explain(e: unknown): string | null {
  const code = (e as { code?: string })?.code ?? '';
  if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') return null;
  if (code === 'auth/popup-blocked') return 'Prehliadač zablokoval prihlasovacie okno. Povoľte vyskakovacie okná pre túto stránku a skúste znova.';
  if (code === 'auth/unauthorized-domain') return 'Táto adresa ešte nie je povolená vo Firebase (Authentication → Settings → Authorized domains).';
  if (code === 'auth/network-request-failed') return 'Nie ste pripojený na internet.';
  return `Prihlásenie zlyhalo (${code || (e as Error)?.message || 'neznáma chyba'}).`;
}

export function SignInView() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="signin">
      <div className="signin-card">
        <span className="brand-mark big" aria-hidden><Icon name="check" size={40} /></span>
        <h1>Priestor</h1>
        <p className="lede">Úlohy a nákupy pre domácnosť, rodinu aj dovolenku. Každú oblasť zdieľate len s tými, koho sa týka.</p>
        <button type="button" className="primary big" disabled={busy} onClick={async () => {
          setBusy(true); setError(null);
          try { await Repo.signIn(); } catch (e) { setError(explain(e)); } finally { setBusy(false); }
        }}>
          {busy ? 'Prihlasujem…' : 'Prihlásiť sa cez Google'}
        </button>
        {error && <p className="error" role="alert">{error}</p>}
        <p className="fine">Rovnaký účet ako v Android aplikácii – uvidíte tie isté oblasti. <a href="/privacy/">Zásady ochrany súkromia</a></p>
      </div>
    </div>
  );
}
