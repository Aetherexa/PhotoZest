import { defineConfig } from 'vitest/config';
export default defineConfig({
  base: process.env.PUBLIC_BASE_PATH || '/',
  build: { outDir: 'dist', sourcemap: false },
  test: { include: ['src/**/*.test.ts'] }
});
