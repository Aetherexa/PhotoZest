# PhotoZest Web

Standalone Vite + React adaptation of the PhotoFlow browser SPA.
Users select local Gallery files or a Folder; classification, metadata extraction and optional OCR operate in browser memory. Model assets may be downloaded at first use; images are not uploaded to an Aetherexa endpoint.

```bash
npm install
npm test
npm run build
npm run dev
```
Use `PUBLIC_BASE_PATH=/tools/photozest/` for deployment under an existing site prefix.
Web virtual collections do **not** move original files.
