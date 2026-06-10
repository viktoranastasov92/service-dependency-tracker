import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev (npm run dev): base='/' so assets load from http://localhost:5173/
// In prod build: base='/api/v1/' so assets load from http://host:8080/api/v1/
export default defineConfig(({ command }) => ({
  plugins: [react()],
  base: command === 'build' ? '/api/v1/' : '/',
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
}));
