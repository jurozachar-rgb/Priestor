// Konfigurácia Firebase pre webovú aplikáciu.
// Firebase konzola → Project settings → Your apps → Web app (</>) → SDK setup and configuration → Config.
// Tieto hodnoty nie sú tajné – prístup k dátam chránia pravidlá vo firebase/firestore.rules.
export const firebaseConfig = {
  apiKey: 'AIzaSyAdoREJqe0ek_T-joQU823NoYTgHHQqCkg',
  authDomain: 'td-app-67ad6.firebaseapp.com',
  projectId: 'td-app-67ad6',
  storageBucket: 'td-app-67ad6.firebasestorage.app',
  messagingSenderId: '1022303725394',
  appId: '',
};

/** Adresa webu – používa sa v odkazoch s pozvánkou. */
export const SITE_URL = 'https://priestor.jurajzachar.sk';
