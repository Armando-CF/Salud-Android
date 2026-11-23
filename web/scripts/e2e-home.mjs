// Inicio y Más: capturas para persona, familia y mascota; cambio de perfil desde Inicio.
import { withApp } from './pw.mjs'
const fail = (m) => { console.error('FALLA:', m); process.exitCode = 1 }
for (const [seed, name, dark] of [['person', 'home_person', false], ['family', 'home_family', false], ['pet', 'home_pet', true], ['empty', 'home_empty', false]]) {
  const errors = await withApp({ seed, dark }, async ({ page, go, shot }) => {
    await page.waitForSelector('.tabbar')
    await shot(name, true)
    await go('/mas'); await shot(name.replace('home', 'more'), true)
    if (seed === 'person') {
      // el teclado (foco) se cierra al tocar fuera de un campo
      await go('/'); await page.waitForSelector('.home-quick')
    }
  })
  if (errors.length) fail(`${seed}: ${errors.slice(0, 3).join(' | ')}`)
}
console.log(process.exitCode ? 'CON FALLAS' : 'OK')
