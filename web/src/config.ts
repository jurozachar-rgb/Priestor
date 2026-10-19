// Konfigurácia Firebase pre webovú aplikáciu.
// Firebase konzola → Project settings → Your apps → Web app (</>) → SDK setup and configuration → Config.
// Tieto hodnoty nie sú tajné – prístup k dátam chránia pravidlá vo firebase/firestore.rules.
export const firebaseConfig = {
  apiKey: 'AIzaSyD1u7H-A1RwWg8tSS-1JGDcbiJJTj1tr5k',
  authDomain: 'td-app-67ad6.firebaseapp.com',
  projectId: 'td-app-67ad6',
  storageBucket: 'td-app-67ad6.firebasestorage.app',
  messagingSenderId: '1022303725394',
  appId: '1:1022303725394:web:03028f655afdc4ac5e3c8b',
};

/** Adresa webu – používa sa v odkazoch s pozvánkou. */
export const SITE_URL = 'https://priestor.jurajzachar.sk';
