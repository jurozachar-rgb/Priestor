// Formátovanie dátumov po slovensky a farby oblastí – rovnaké ako ui/Format.kt v Androide.

const monthsNom = ['január', 'február', 'marec', 'apríl', 'máj', 'jún', 'júl', 'august', 'september', 'október', 'november', 'december'];
const monthsGen = ['januára', 'februára', 'marca', 'apríla', 'mája', 'júna', 'júla', 'augusta', 'septembra', 'októbra', 'novembra', 'decembra'];
export const weekdaysShort = ['po', 'ut', 'st', 'št', 'pi', 'so', 'ne'];
const weekdaysLong = ['pondelok', 'utorok', 'streda', 'štvrtok', 'piatok', 'sobota', 'nedeľa'];

/** Farby oblastí (index sa ukladá do databázy). */
export const spaceColors = ['#2F6B4F', '#3B6EA8', '#B5643C', '#8A5A9E', '#B8912F', '#4F8C8C', '#A8485E', '#6B6B6B'];
export const spaceColor = (i: number) => spaceColors[((i % spaceColors.length) + spaceColors.length) % spaceColors.length];

// ---------- Dátumy ako 'RRRR-MM-DD' (lokálny deň) ----------

const pad = (n: number) => String(n).padStart(2, '0');
export const isoDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const parseIso = (s: string) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
export const todayIso = () => isoDate(new Date());
/** Deň v týždni 0 = pondelok … 6 = nedeľa */
export const weekdayIndex = (d: Date) => (d.getDay() + 6) % 7;
const dayDiff = (a: Date, b: Date) =>
  Math.round((Date.UTC(b.getFullYear(), b.getMonth(), b.getDate()) - Date.UTC(a.getFullYear(), a.getMonth(), a.getDate())) / 86400000);

export const monthTitle = (year: number, month0: number) => `${monthsNom[month0][0].toUpperCase()}${monthsNom[month0].slice(1)} ${year}`;

/** „1. novembra 2026“ alebo bez roka „1. novembra“ */
export const longDate = (iso: string, withYear = true) => {
  const d = parseIso(iso);
  return `${d.getDate()}. ${monthsGen[d.getMonth()]}${withYear ? ` ${d.getFullYear()}` : ''}`;
};

/** „Streda 18. októbra 2026“ */
export const longDateWithDay = (iso: string) => {
  const w = weekdaysLong[weekdayIndex(parseIso(iso))];
  return `${w[0].toUpperCase()}${w.slice(1)} ${longDate(iso)}`;
};

export const weekdayShort = (iso: string) => weekdaysShort[weekdayIndex(parseIso(iso))];

/** „dnes“, „včera“, „pred 5 dňami“, „o 3 dni“ */
export function relativeDays(iso: string, today = todayIso()): string {
  const n = dayDiff(parseIso(iso), parseIso(today));
  if (n === 0) return 'dnes';
  if (n === 1) return 'včera';
  if (n === -1) return 'zajtra';
  if (n > 1) return `pred ${n} dňami`;
  return -n <= 4 ? `o ${-n} dni` : `o ${-n} dní`;
}

export const daysBetween = (a: string, b: string) => dayDiff(parseIso(a), parseIso(b));

export const timesCount = (n: number) => (n === 1 ? '1 raz' : n >= 2 && n <= 4 ? `${n} razy` : `${n}-krát`);

export const entriesCount = (n: number) =>
  n === 0 ? 'žiadne záznamy' : n === 1 ? '1 záznam' : n <= 4 ? `${n} záznamy` : `${n} záznamov`;

// ---------- Termíny úloh ----------

const shortDay = (d: Date) => `${weekdaysShort[weekdayIndex(d)]} ${d.getDate()}. ${d.getMonth() + 1}.`;

/** „dnes 14:00“, „zajtra“, „pi 17. 10.“ */
export function formatDue(ms: number, hasTime: boolean): string {
  const d = new Date(ms);
  const now = new Date();
  const diff = dayDiff(now, d);
  let day: string;
  if (diff === 0) day = 'dnes';
  else if (diff === 1) day = 'zajtra';
  else if (diff === -1) day = 'včera';
  else day = d.getFullYear() === now.getFullYear() ? shortDay(d) : `${d.getDate()}. ${d.getMonth() + 1}. ${d.getFullYear()}`;
  return hasTime ? `${day} ${d.getHours()}:${pad(d.getMinutes())}` : day;
}

export const formatDate = (ms: number) => {
  const d = new Date(ms);
  return `${d.getDate()}. ${d.getMonth() + 1}. ${d.getFullYear()}`;
};

/** Úloha bez času je po termíne až od nasledujúceho dňa. */
export function isOverdue(ms: number, hasTime: boolean): boolean {
  if (hasTime) return ms < Date.now();
  return dayDiff(new Date(ms), new Date()) > 0;
}

/** Hodnoty pre <input type="date"> a <input type="time"> */
export const timeValue = (d: Date) => `${pad(d.getHours())}:${pad(d.getMinutes())}`;
