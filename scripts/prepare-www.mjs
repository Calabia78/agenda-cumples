// Prepara la carpeta www/ que empaqueta la APK: copia src/index.html,
// sustituye las fuentes de Google por fuentes incluidas en la app (sin red)
// y copia los .woff2 desde node_modules.
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, rmSync, existsSync } from 'node:fs';

rmSync('www', { recursive: true, force: true });
mkdirSync('www/fonts', { recursive: true });

const fonts = [
  ['@fontsource-variable/bricolage-grotesque/files/bricolage-grotesque-latin-wght-normal.woff2', 'bricolage.woff2'],
  ['@fontsource-variable/schibsted-grotesk/files/schibsted-grotesk-latin-wght-normal.woff2', 'schibsted.woff2'],
  ['@fontsource/ibm-plex-mono/files/ibm-plex-mono-latin-400-normal.woff2', 'plexmono-400.woff2'],
  ['@fontsource/ibm-plex-mono/files/ibm-plex-mono-latin-500-normal.woff2', 'plexmono-500.woff2'],
];
for (const [from, to] of fonts) {
  const p = `node_modules/${from}`;
  if (!existsSync(p)) throw new Error('Falta la fuente ' + p);
  copyFileSync(p, `www/fonts/${to}`);
}
writeFileSync('www/fonts/fonts.css', `
@font-face{font-family:'Bricolage Grotesque';font-style:normal;font-weight:200 800;font-display:swap;src:url(bricolage.woff2) format('woff2-variations'),url(bricolage.woff2) format('woff2')}
@font-face{font-family:'Schibsted Grotesk';font-style:normal;font-weight:400 900;font-display:swap;src:url(schibsted.woff2) format('woff2-variations'),url(schibsted.woff2) format('woff2')}
@font-face{font-family:'IBM Plex Mono';font-style:normal;font-weight:400;font-display:swap;src:url(plexmono-400.woff2) format('woff2')}
@font-face{font-family:'IBM Plex Mono';font-style:normal;font-weight:500;font-display:swap;src:url(plexmono-500.woff2) format('woff2')}
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
