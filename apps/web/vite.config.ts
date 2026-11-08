import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // /api trafia do backendu – przeglądarka widzi jeden adres, więc ciasteczka i SameSite działają bez CORS
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});