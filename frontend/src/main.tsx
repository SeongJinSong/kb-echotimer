import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'

// Noto Sans KR 폰트 로드
const link = document.createElement('link');
link.href = 'https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@300;400;500;700&display=swap';
link.rel = 'stylesheet';
document.head.appendChild(link);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
