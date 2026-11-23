// Prueba en navegador (iPhone emulado) de la sección Cuerpo: vistas, selección al tocar, zoom, tinte y navegación.
// Uso (desde web/):  npm run build && node scripts/e2e-atlas.mjs
import { withApp, writeIdb } from './pw.mjs'

const fails = []
const check = (cond, msg) => { if (!cond) { fails.push(msg); console.error('FALLA:', msg) } }

const ready = async (page) => { await page.waitForSelector('.atlas-canvas svg path', { timeout: 15000 }); await page.waitForTimeout(250) }
const state = (page) => page.evaluate(() => {
  const root = document.querySelector('.atlas')
  return {
    view: root?.dataset.view ?? null,
    selected: root?.dataset.selected ?? null,
    title: document.querySelector('.atlas-panel h2')?.textContent ?? null,
    zoom: Number(document.querySelector('.atlas-canvas')?.dataset.zoom ?? 0),
    transform: document.querySelector('.atlas-canvas svg > g')?.getAttribute('transform') ?? '',
    groups: [...document.querySelectorAll('.atlas-groups .chip')].map((c) => c.textContent),
    sexSelector: !!document.querySelector('.atlas-sex'),
    panel: document.querySelector('.atlas-panel')?.textContent ?? '',
  }
})
const canvasBox = (page) => page.evaluate(() => { const r = document.querySelector('.atlas-canvas').getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height } })
const tapCanvas = async (page, fx, fy) => { const b = await canvasBox(page); await page.touchscreen.tap(b.x + b.w * fx, b.y + b.h * fy); await page.waitForTimeout(200) }
const clickLabel = async (page, label) => { await page.click(`[aria-label="${label}"]`); await page.waitForTimeout(150) }
const chip = async (page, text) => { await page.click(`.atlas-groups .chip:text-is("${text}")`); await page.waitForTimeout(250) }
/** Tamaño mínimo de los controles táctiles visibles del atlas. */
const smallTargets = (page) => page.evaluate(() => [...document.querySelectorAll('.atlas button')]
  .filter((el) => el.offsetParent !== null)
  .map((el) => { const r = el.getBoundingClientRect(); return { t: (el.getAttribute('aria-label') || el.textContent || '').trim().slice(0, 24), w: Math.round(r.width), h: Math.round(r.height) } })
  .filter((x) => x.h < 44 || x.w < 44))
/** Pellizco con dos dedos simulado con eventos de puntero. */
const pinch = (page, from, to) => page.evaluate(async ({ from, to }) => {
  const el = document.querySelector('.atlas-canvas')
  const r = el.getBoundingClientRect()
  const cx = r.x + r.width / 2, cy = r.y + r.height / 2
  const fire = (type, id, x, y) => el.dispatchEvent(new PointerEvent(type, { pointerId: id, pointerType: 'touch', clientX: x, clientY: y, bubbles: true, cancelable: true, isPrimary: id === 11 }))
  fire('pointerdown', 11, cx - from, cy); fire('pointerdown', 12, cx + from, cy)
  for (let i = 1; i <= 8; i++) {
    const d = from + ((to - from) * i) / 8
    fire('pointermove', 11, cx - d, cy); fire('pointermove', 12, cx + d, cy)
    await new Promise((res) => requestAnimationFrame(res))
  }
  fire('pointerup', 11, cx - to, cy); fire('pointerup', 12, cx + to, cy)
}, { from, to })

// ── Persona (tema claro) ──
const errorsPerson = await withApp({ seed: 'person' }, async ({ page, go, shot, auditInputs }) => {
  await go('/cuerpo'); await ready(page)
  let s = await state(page)
  check(s.view === 'front_F', `vista inicial ${s.view}`)
  check(JSON.stringify(s.groups) === JSON.stringify(['Frente', 'Espalda', 'Abdomen', 'Órganos', 'Esqueleto', 'Mano', 'Pie']), `grupos ${s.groups}`)
  check(s.sexSelector, 'falta selector Mujer/Hombre')
  check(/Toca cualquier parte/.test(s.panel) && /su lado derecho queda a tu izquierda/.test(s.panel), 'falta la ayuda inicial')
  // tinte por síntoma reciente (dolor abdominal en fosa ilíaca derecha)
  const tinted = await page.evaluate(() => document.querySelector('[data-id="abd_fosa_iliaca_der"]')?.getAttribute('style') ?? '')
  check(/--bad/.test(tinted), `la zona con síntomas no está teñida: ${tinted}`)
  await shot('atlas_front_f')
  check((await smallTargets(page)).length === 0, 'controles menores a 44px: ' + JSON.stringify(await smallTargets(page)))

  // tocar el centro de la figura selecciona una zona y muestra su nombre
  await tapCanvas(page, 0.5, 0.42)
  s = await state(page)
  check(!!s.selected && !!s.title, 'tocar el centro no seleccionó nada')
  console.log('toque al centro →', s.selected, '/', s.title)
  await shot('atlas_front_tap')
  // tocar el vacío quita la selección
  await tapCanvas(page, 0.06, 0.5)
  check((await state(page)).selected === '', 'tocar fuera no quitó la selección')

  // zoom con botones
  await clickLabel(page, 'Acercar'); await clickLabel(page, 'Acercar')
  s = await state(page)
  check(s.zoom > 2 && /scale\(2\.\d+\)/.test(s.transform), `zoom con botones: ${s.zoom} ${s.transform}`)
  await tapCanvas(page, 0.5, 0.5)
  s = await state(page)
  check(!!s.selected, 'con zoom, tocar no seleccionó')
  console.log('toque con zoom →', s.selected, '/', s.title)
  await shot('atlas_front_zoom')
  await clickLabel(page, 'Alejar')
  check((await state(page)).zoom < s.zoom, 'Alejar no redujo el zoom')
  await clickLabel(page, 'Restablecer zoom')
  s = await state(page)
  check(s.zoom === 1 && /^translate\(0\.00 0\.00\) scale\(1\.0000\)/.test(s.transform), `restablecer: ${s.zoom} ${s.transform}`)
  // pellizco con dos dedos
  await pinch(page, 40, 110)
  s = await state(page)
  check(s.zoom > 1.5, `pellizco: zoom ${s.zoom}`)
  await pinch(page, 120, 30)
  check((await state(page)).zoom < s.zoom, 'pellizco para alejar')
  await clickLabel(page, 'Restablecer zoom').catch(() => {})

  // ficha con síntomas registrados y navegación al editor
  await go('/cuerpo?g=front&sel=abd_fosa_iliaca_der'); await ready(page)
  s = await state(page)
  check(s.title === 'Fosa ilíaca derecha', `título ${s.title}`)
  check(/Has registrado 1 síntoma aquí · último: Dolor abdominal/.test(s.panel), 'falta el conteo de síntomas')
  check(/Cuándo consultar: /.test(s.panel) && /Apéndice/.test(s.panel) && /Ver órganos/.test(s.panel), 'ficha incompleta')
  await shot('atlas_front_selected')
  await page.click('button:has-text("Registrar síntoma aquí")'); await page.waitForTimeout(300)
  const hash = await page.evaluate(() => decodeURIComponent(window.location.hash))
  check(hash === '#/sintomas/nuevo?region=abd_fosa_iliaca_der&label=Fosa ilíaca derecha', `URL del editor: ${hash}`)
  const raw = await page.evaluate(() => window.location.hash)
  check(raw.includes('label=Fosa%20il%C3%ADaca%20derecha'), `sin codificar: ${raw}`)

  // enlace "Ver mano" desde la mano de la figura
  await go('/cuerpo?g=front&sel=mano_der'); await ready(page)
  await page.click('button:has-text("Ver mano")'); await page.waitForTimeout(300)
  check((await state(page)).view === 'hand', 'Ver mano no abrió la vista de la mano')

  // cada grupo con una zona seleccionada
  for (const [g, sel, name, title] of [
    ['abdomen', 'abd_fosa_iliaca_der', 'atlas_abdomen', 'Fosa ilíaca derecha'],
    ['skeleton', 'h_femur_izq', 'atlas_skeleton', 'Fémur izquierdo'],
    ['hand', 'm_falange_media_indice', 'atlas_hand', 'Falange media del dedo índice'],
    ['foot', 'p_calcaneo', 'atlas_foot', 'Calcáneo'],
    ['organs', 'o_corazon', 'atlas_organs_f', 'Corazón'],
  ]) {
    await go(`/cuerpo?g=${g}&sel=${sel}`); await ready(page)
    s = await state(page)
    check(s.title === title, `${g}: título "${s.title}"`)
    await shot(name)
  }
  // textos de las vistas (abdomen, mano)
  await go('/cuerpo?g=abdomen'); await ready(page)
  const labels = await page.evaluate(() => [...document.querySelectorAll('.atlas-text')].map((t) => t.textContent))
  check(labels.includes('Epigastrio') && labels.includes('Hipogastrio'), `etiquetas del abdomen: ${labels.slice(0, 4)}`)

  // modelo de hombre (se guarda en el ajuste del perfil)
  await chip(page, 'Espalda')
  await page.click('.atlas-sex button:text-is("Hombre")'); await page.waitForTimeout(300)
  s = await state(page)
  check(s.view === 'back_M', `vista ${s.view}`)
  check(/su lado derecho queda a tu derecha/.test(s.panel), 'nota de orientación de espalda')
  await tapCanvas(page, 0.58, 0.4)
  s = await state(page)
  console.log('espalda hombre, toque →', s.selected, '/', s.title)
  check(!!s.selected, 'espalda: sin selección')
  await shot('atlas_back_m')
  await go('/cuerpo?g=organs&sel=o_higado'); await ready(page)
  s = await state(page)
  check(s.view === 'organs_M' && s.title === 'Hígado', `órganos hombre: ${s.view} ${s.title}`)
  await shot('atlas_organs_m')

  // modo "elegir zona"
  await go('/cuerpo?pick=1&g=back&sel=lumbar_der'); await ready(page)
  s = await state(page)
  check(/Usar esta zona/.test(s.panel) && !/Registrar síntoma aquí/.test(s.panel), 'modo elegir')
  await shot('atlas_pick')
  await page.click('button:has-text("Usar esta zona")'); await page.waitForTimeout(300)
  check(decodeURIComponent(await page.evaluate(() => window.location.hash)).includes('region=lumbar_der&label=Zona lumbar derecha'), 'elegir zona no devolvió la zona')
  check((await auditInputs()).length === 0, 'campos con letra menor a 16px')
})
check(errorsPerson.length === 0, 'errores de consola (persona): ' + errorsPerson.slice(0, 3).join(' | '))

// ── Persona (tema oscuro) ──
const errorsDark = await withApp({ seed: 'person', dark: true }, async ({ page, go, shot }) => {
  await go('/cuerpo?g=organs&sel=o_estomago'); await ready(page); await shot('atlas_organs_dark')
  await go('/cuerpo?g=foot&sel=p_calcaneo'); await ready(page); await shot('atlas_foot_dark')
  await go('/cuerpo?g=front&sel=rodilla_izq'); await ready(page)
  check((await state(page)).title === 'Rodilla izquierda', 'oscuro: rodilla')
  await shot('atlas_front_dark')
})
check(errorsDark.length === 0, 'errores de consola (oscuro): ' + errorsDark.slice(0, 3).join(' | '))

// ── Mascotas: perro, gato y una especie sin figura ──
const errorsPet = await withApp({ seed: 'pet' }, async ({ page, go, shot }) => {
  await go('/cuerpo'); await ready(page)
  let s = await state(page)
  check(s.view === 'pet_side_perro', `vista de perro: ${s.view}`)
  check(JSON.stringify(s.groups) === JSON.stringify(['Cuerpo', 'Órganos']), `grupos de mascota: ${s.groups}`)
  check(!s.sexSelector, 'la mascota no debe tener selector Mujer/Hombre')
  check(/Vista de lado/.test(s.panel), 'nota de orientación de mascota')
  // La cojera del carpo fue hace 40 días: ya no tiñe la zona (solo cuentan 30 días), pero sí aparece en su ficha.
  const tinted = await page.evaluate(() => document.querySelector('[data-id="pet_carpo_perro"]')?.getAttribute('style') ?? '')
  check(!/--bad/.test(tinted), 'el carpo no debería estar teñido por un síntoma de hace 40 días')
  await go('/cuerpo?g=pet_side&sel=pet_carpo_perro'); await ready(page)
  check(/Has registrado 1 síntoma aquí · último: Cojera/.test((await state(page)).panel), 'perro: falta el conteo de síntomas del carpo')
  await go('/cuerpo'); await ready(page)
  await tapCanvas(page, 0.5, 0.5)
  s = await state(page)
  console.log('perro, toque al centro →', s.selected, '/', s.title)
  check(/^pet_.*_perro$/.test(s.selected ?? ''), 'perro: sin selección al tocar')
  check(/Cuándo ir al veterinario: /.test(s.panel) && /revisión veterinaria/.test(s.panel), 'textos de mascota')
  await shot('atlas_dog_tap')
  await go('/cuerpo?g=pet_side&sel=pet_abdomen_perro'); await ready(page)
  s = await state(page)
  check(s.title === 'Abdomen (vientre)' && /torsión/.test(s.panel), `perro abdomen: ${s.title}`)
  await shot('atlas_dog')
  await chip(page, 'Órganos')
  await tapCanvas(page, 0.45, 0.5)
  s = await state(page)
  console.log('perro órganos, toque →', s.selected, '/', s.title)
  check(s.view === 'pet_organs_perro', 'órganos del perro')
  await shot('atlas_dog_organs')
  await go('/cuerpo?pick=1'); await ready(page)
  check(/Toca la zona donde notas la molestia/.test((await state(page)).panel), 'texto de elegir zona para mascota')

  // gato
  const dog = await page.evaluate(async () => {
    const db = await new Promise((res, rej) => { const r = indexedDB.open('savia-p3'); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error) })
    const row = await new Promise((res) => { const q = db.transaction('profile').objectStore('profile').get(1); q.onsuccess = () => res(q.result) })
    db.close()
    return row
  })
  await writeIdb(page, 'savia-p3', { profile: [{ ...dog, name: 'Garfield', species: 'gato' }] })
  await page.reload(); await page.waitForSelector('.app'); await page.addStyleTag({ content: ':root{--safe-top:47px !important;--safe-bottom:34px !important}' })
  await go('/cuerpo?g=pet_side&sel=pet_genital_gato'); await ready(page)
  s = await state(page)
  check(s.view === 'pet_side_gato' && /arenero/.test(s.panel), `gato: ${s.view} ${s.title}`)
  await shot('atlas_cat')
  // ave: sin figura
  await writeIdb(page, 'savia-p3', { profile: [{ ...dog, name: 'Piolín', species: 'ave' }] })
  await page.reload(); await page.waitForSelector('.app'); await page.addStyleTag({ content: ':root{--safe-top:47px !important;--safe-bottom:34px !important}' })
  await go('/cuerpo'); await page.waitForSelector('.atlas .empty', { timeout: 10000 }).catch(() => check(false, 'ave: falta el estado vacío'))
  const empty = await page.evaluate(() => document.querySelector('.atlas')?.textContent ?? '')
  check(/Aún no tenemos una figura para esta especie/.test(empty) && /Registrar un síntoma/.test(empty), 'ave: texto del estado vacío')
  await shot('atlas_bird_empty')
})
check(errorsPet.length === 0, 'errores de consola (mascota): ' + errorsPet.slice(0, 3).join(' | '))

// ── Perro en tema oscuro ──
const errorsPetDark = await withApp({ seed: 'pet', dark: true }, async ({ page, go, shot }) => {
  await go('/cuerpo?g=pet_organs&sel=pet_o_corazon_perro'); await ready(page)
  check((await state(page)).title === 'Corazón', 'perro oscuro: corazón')
  await shot('atlas_dog_organs_dark')
  await go('/cuerpo?g=pet_side&sel=pet_muslo_perro'); await ready(page); await shot('atlas_dog_dark')
})
check(errorsPetDark.length === 0, 'errores de consola (mascota oscuro): ' + errorsPetDark.slice(0, 3).join(' | '))

if (fails.length) { console.error(`CON FALLAS (${fails.length})`); process.exitCode = 1 } else console.log('OK')
