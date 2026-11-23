// Copia el atlas corporal generado para la app de Android (fuente única) a la carpeta pública de la web.
import fs from 'node:fs'
const src = new URL('../../app/src/main/assets/atlas.json', import.meta.url)
const dst = new URL('../public/atlas.json', import.meta.url)
if (fs.existsSync(src)) {
  fs.copyFileSync(src, dst)
  console.log('atlas.json sincronizado')
} else if (!fs.existsSync(dst)) {
  console.error('No se encontró atlas.json'); process.exit(1)
}
