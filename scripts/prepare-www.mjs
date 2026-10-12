// Prepara la carpeta www/ que empaqueta la APK: copia src/index.html,
// sustituye las fuentes de Google por fuentes incluidas en la app (sin red)
// y copia los .woff2 desde node_modules.
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, rmSync, existsSync } from 'node:fs';

rmSync('www', { recursive: true, force: true });
mkdirSync('www/fonts', { recursive: true });

const fonts = [
  ['@fontsource-variable/bitter/files/bitter-latin-wght-normal.woff2', 'bitter.woff2'],
  ['@fontsource-variable/source-sans-3/files/source-sans-3-latin-wght-normal.woff2', 'sourcesans.woff2'],
];
for (const [from, to] of fonts) {
  const p = `node_modules/${from}`;
  if (!existsSync(p)) throw new Error('Falta la fuente ' + p);
  copyFileSync(p, `www/fonts/${to}`);
}
writeFileSync('www/fonts/fonts.css', `
@font-face{font-family:'Bitter';font-style:normal;font-weight:100 900;font-display:swap;src:url(bitter.woff2) format('woff2-variations'),url(bitter.woff2) format('woff2')}
@font-face{font-family:'Source Sans 3';font-style:normal;font-weight:200 900;font-display:swap;src:url(sourcesans.woff2) format('woff2-variations'),url(sourcesans.woff2) format('woff2')}
`);

let html = readFileSync('src/index.html', 'utf8');
const before = html;
html = html
  .replace(/<link rel="preconnect"[^>]*>\s*/g, '')
  .replace(/<link rel="stylesheet" href="https:\/\/fonts\.googleapis\.com[^>]*>/, '<link rel="stylesheet" href="fonts/fonts.css">');
if (html === before || html.includes('fonts.googleapis.com')) throw new Error('No se pudieron sustituir las fuentes');
// El archivo fuente es un fragmento (lo envuelve la plataforma de publicación): aquí se completa.
if (!/<html/i.test(html)) {
  html = '<!doctype html>\n<html lang="es">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">\n'
    + html.replace('<main class="wrap" id="view">', '</head>\n<body>\n<main class="wrap" id="view">') + '\n</body>\n</html>\n';
}
writeFileSync('www/index.html', html);
console.log('www/ listo');
