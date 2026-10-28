import React from 'react';
import ReactDOM from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import { App } from './screens/App.tsx';
import { loadRuntimeConfig } from './util/config.ts';
import './style.css';

// the runtime config must be in place before anything reads the API URLs
loadRuntimeConfig().finally(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
});
