// Prueba en navegador (iPhone emulado) del módulo de perfiles: bienvenida, perfiles, fichas, ajustes y bloqueo con PIN.
// Uso (desde web/):  npm run build && node scripts/e2e-profile.mjs
import { withApp, writeIdb } from './pw.mjs'
import { demoData } from './demo-data.mjs'

let failures = 0
const check = (cond, msg) => { if (!cond) { failures++; console.error('FALLA:', msg) } else console.log('  ok:', msg) }

/** Lee de IndexedDB: una fila (key) o todas las filas de un almacén. */
const idb = (page, dbName, store, key) => page.evaluate(async ({ dbName, store, key }) => {
  const db = await new Promise((res, rej) => { const r = indexedDB.open(dbName); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error) })
  if (!db.objectStoreNames.contains(store)) { db.close(); return null }
  const out = await new Promise((res, rej) => {
    const s = db.transaction(store).objectStore(store)
    const r = key === undefined ? s.getAll() : s.get(key)
    r.onsuccess = () => res(r.result ?? null); r.onerror = () => rej(r.error)
  })
  db.close()
  return out
}, { dbName, store, key })

const kv = async (page, key) => (await idb(page, 'savia-meta', 'kv', key))?.value
const safeArea = (page) => page.addStyleTag({ content: ':root{--safe-top:47px !important;--safe-bottom:34px !important}' })
const noSmallInputs = async (auditInputs, where) => { const bad = await auditInputs(); check(bad.length === 0, `campos de al menos 16px en ${where}` + (bad.length ? ' ' + JSON.stringify(bad) : '')) }
const pin = async (page, digits, scope = '') => { for (const d of digits) await page.locator(`${scope} .pf-pad button`, { hasText: new RegExp(`^${d}$`) }).click() }

/** Las bases de los demás perfiles se crean cuando la app las abre (lista de perfiles); aquí se siembran sus datos. */
async function seedFamily(page, go) {
  await go('/perfiles')
  await page.waitForFunction(() => indexedDB.databases().then((d) => ['savia-p2', 'savia-p3'].every((n) => d.some((x) => x.name === n))), null, { timeout: 8000 })
  await page.waitForTimeout(300)
  const data = demoData('family')
  for (const name of ['savia-p2', 'savia-p3']) await writeIdb(page, name, data[name])
  await page.reload(); await page.waitForSelector('.app'); await safeArea(page)
  await page.waitForSelector('text=Firulais')
}

// ── A. Bienvenida completa ───────────────────────────────────────────────────────────────────────
console.log('A. Bienvenida')
const errA = await withApp({ seed: 'none' }, async ({ page, shot, auditInputs }) => {
  await page.waitForSelector('.pf-onb')
  await shot('profile_onboarding_1')
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.locator('.pf-onb input.input').first().fill('Mariana')
  // El caso reportado en Android: tras escribir el nombre, tocar un chip deja ver el resto del formulario.
  await page.getByRole('button', { name: 'Mujer', exact: true }).click()
  await noSmallInputs(auditInputs, 'la bienvenida')
  await shot('profile_onboarding_2')
  await page.getByRole('button', { name: 'Siguiente' }).click()
  check(await page.getByRole('switch').isChecked(), 'elegir Mujer propone el seguimiento del ciclo')
  await noSmallInputs(auditInputs, 'la bienvenida (módulos)')
  await shot('profile_onboarding_3', true)
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await shot('profile_onboarding_4', true)
  await page.getByRole('button', { name: 'Empezar' }).click()
  await page.waitForSelector('.tabbar', { timeout: 8000 })
  check((await page.evaluate(() => location.hash)).replace('#', '') === '/' || (await page.evaluate(() => location.hash)) === '', 'tras la bienvenida la app queda en Inicio')
  const p = await idb(page, 'savia-p1', 'profile', 1)
  check(p?.name === 'Mariana' && p?.sex === 'F' && p?.relation === 'Yo', `la ficha quedó guardada (${p?.name}, ${p?.sex}, ${p?.relation})`)
  check((await idb(page, 'savia-p1', 'prefs', 'cycleEnabled'))?.value === true, 'ciclo activado en los ajustes del perfil')
  check((await idb(page, 'savia-p1', 'prefs', 'atlasSex'))?.value === 'F', 'modelo del atlas según el sexo')
  check((await kv(page, 'onboardingDone')) === true, 'la bienvenida no vuelve a mostrarse')
  await page.reload(); await page.waitForSelector('.tabbar')
  check((await page.locator('.pf-onb').count()) === 0, 'al recargar entra directo a la app')
})
check(errA.length === 0, 'sin errores de consola en la bienvenida' + (errA.length ? ' ' + JSON.stringify(errA.slice(0, 3)) : ''))

// "Omitir" también funciona y deja la app usable.
const errA2 = await withApp({ seed: 'none' }, async ({ page }) => {
  await page.waitForSelector('.pf-onb')
  await page.getByRole('button', { name: 'Omitir' }).click()
  await page.waitForSelector('.tabbar', { timeout: 8000 })
  check((await kv(page, 'onboardingDone')) === true, 'Omitir termina la bienvenida')
})
check(errA2.length === 0, 'sin errores de consola al omitir')

// ── B. Perfiles, altas, fichas, eliminar y borrar datos ──────────────────────────────────────────
console.log('B. Perfiles y fichas')
const errB = await withApp({ seed: 'family' }, async ({ page, go, shot, auditInputs }) => {
  await seedFamily(page, go)
  await shot('profile_perfiles')
  check((await page.locator('.pf-active').count()) === 1 && (await page.locator('.pf-active').innerText()).includes('Alex'), 'el perfil activo (Alex) está marcado')
  check((await page.locator('.card .name').allInnerTexts()).join(',') === 'Alex,Leo,Firulais', 'lista: personas primero y luego mascotas')
  await page.getByRole('button', { name: 'Opciones de Leo' }).click()
  await page.waitForSelector('.sheet')
  await page.waitForTimeout(400)
  await shot('profile_perfiles_menu')
  await page.getByRole('button', { name: 'Cerrar' }).click()

  // Alta de persona (solo captura y validación)
  await go('/perfiles/nuevo/person')
  await page.getByRole('button', { name: 'Crear perfil', exact: true }).click()
  check(await page.getByText('Falta el nombre.').isVisible(), 'el alta exige nombre')
  await page.locator('input.input').first().fill('Sofía')
  await page.getByRole('button', { name: 'Hija', exact: true }).click()
  await page.getByRole('button', { name: 'Mujer', exact: true }).click()
  await noSmallInputs(auditInputs, 'el alta de persona')
  await shot('profile_nueva_persona', true)

  // Alta de mascota por la interfaz
  await go('/perfiles/nuevo/pet')
  await page.locator('input.input').first().fill('Garfield')
  await page.getByRole('button', { name: 'Gato', exact: true }).click()
  await page.getByRole('button', { name: 'Macho', exact: true }).click()
  await page.getByRole('switch').click({ force: true }) // el interruptor se activa a través de su etiqueta
  await page.locator('input[inputmode="numeric"]').first().fill('3')
  await page.locator('input[inputmode="numeric"]').nth(1).fill('2')
  await noSmallInputs(auditInputs, 'el alta de mascota')
  await shot('profile_nueva_mascota', true)
  await page.getByRole('button', { name: 'Crear perfil de mascota' }).click()
  await page.waitForFunction(() => location.hash === '#/', null, { timeout: 8000 })
  await page.waitForSelector('.tabbar')
  const newId = await kv(page, 'activeProfile')
  check(newId === 4, `la mascota nueva queda como perfil activo (id ${newId})`)
  const cat = await idb(page, 'savia-p4', 'profile', 1)
  check(cat?.name === 'Garfield' && cat?.kind === 'pet' && cat?.species === 'gato' && cat?.sex === 'M' && cat?.birthEpochDay > 0, 'ficha de la mascota creada con especie, sexo y edad aproximada')
  check((await idb(page, 'savia-p4', 'prefs', 'cycleEnabled'))?.value === false, 'las mascotas no llevan ciclo')
  check((await page.evaluate(() => location.hash)) === '#/', 'tras crear el perfil la app arranca en Inicio')
  await safeArea(page)
  await go('/ficha')
  await page.waitForSelector('text=Ficha de Garfield')
  await shot('profile_ficha_mascota_nueva', true)
  await go('/ajustes')
  check((await page.getByText('Ciclo menstrual', { exact: true }).count()) === 0, 'los ajustes de una mascota no muestran el ciclo')
  await shot('profile_ajustes_mascota', true)

  // Eliminar el perfil recién creado (siendo el activo): confirmación doble escribiendo ELIMINAR
  await go('/perfiles')
  check((await page.getByRole('button', { name: 'Opciones de Garfield' }).count()) === 1, 'Garfield aparece en Mascotas')
  await page.getByRole('button', { name: 'Opciones de Alex' }).click()
  check((await page.locator('.sheet').getByRole('button', { name: 'Eliminar perfil' }).count()) === 0, 'el perfil principal no se puede eliminar')
  await page.getByRole('button', { name: 'Cerrar' }).click()
  await page.getByRole('button', { name: 'Opciones de Garfield' }).click()
  await page.locator('.sheet').getByRole('button', { name: 'Eliminar perfil' }).click()
  await page.getByRole('button', { name: 'Continuar' }).click()
  const del = page.locator('.dialog').getByRole('button', { name: 'Eliminar perfil' })
  check(await del.isDisabled(), 'no se puede eliminar sin escribir ELIMINAR')
  await page.locator('.dialog input.input').fill('eliminar')
  await noSmallInputs(auditInputs, 'el diálogo de eliminación')
  await page.waitForTimeout(400)
  await shot('profile_eliminar_dialogo')
  await del.click()
  await page.waitForFunction(() => indexedDB.databases().then((d) => !d.some((x) => x.name === 'savia-p4')), null, { timeout: 8000 })
  check((await idb(page, 'savia-meta', 'profiles')).map((p) => p.id).join(',') === '1,2,3', 'el perfil eliminado sale de la lista')
  check((await kv(page, 'activeProfile')) === 1, 'al eliminar el perfil activo se regresa al perfil principal')
  await page.waitForSelector('.pf-active')
  check((await page.locator('.pf-active').innerText()).includes('Alex'), 'la lista muestra de nuevo a Alex como activo')
  check(!(await page.locator('.card .name').allInnerTexts()).includes('Garfield'), 'Garfield ya no aparece en la lista')
  const gone = await page.waitForSelector('.toast', { timeout: 3000 }).then((t) => t.innerText()).catch(() => '(sin aviso)')
  check(gone.includes('Se eliminó el perfil de Garfield'), `avisa que el perfil se eliminó («${gone}»)`)

  // Cambiar a la mascota con datos y editar su ficha
  await page.locator('.card', { hasText: 'Firulais' }).click()
  await page.waitForFunction(() => location.hash === '#/')
  check((await kv(page, 'activeProfile')) === 3, 'tocar un perfil cambia a ese perfil y va a Inicio')
  await safeArea(page)
  await go('/ficha')
  await page.waitForSelector('text=Ficha de Firulais')
  await noSmallInputs(auditInputs, 'la ficha de mascota')
  await shot('profile_ficha_mascota', true)
  check(await page.locator('.topbar .text-btn').isDisabled(), 'Guardar deshabilitado sin cambios')
  await page.locator('textarea.input').first().fill('Pollo')
  await page.locator('.topbar .text-btn').click()
  await page.waitForTimeout(300)
  check((await idb(page, 'savia-p3', 'profile', 1))?.allergies === 'Pollo', 'la ficha de la mascota se guarda')
  check(await page.locator('.pf-summary').innerText().then((t) => t.includes('Pollo')), 'la tarjeta resumen refleja lo escrito')

  // «Editar ficha» de otro perfil: cambia de perfil y aterriza en su ficha
  await go('/perfiles')
  await page.getByRole('button', { name: 'Opciones de Leo' }).click()
  await page.locator('.sheet').getByRole('button', { name: 'Editar ficha' }).click()
  await page.waitForFunction(() => location.hash === '#/ficha')
  await page.waitForSelector('text=Ficha de Leo')
  check((await kv(page, 'activeProfile')) === 2, '«Editar ficha» de otro perfil cambia a ese perfil y abre su ficha')
  await safeArea(page)

  // Borrar los datos del perfil activo (Leo) escribiendo BORRAR
  await go('/ajustes')
  check((await idb(page, 'savia-p2', 'symptoms')).length === 2, 'Leo tiene datos antes del borrado')
  await page.getByRole('button', { name: /Borrar los datos de este perfil/ }).click()
  await page.getByRole('button', { name: 'Continuar' }).click()
  const wipe = page.locator('.dialog').getByRole('button', { name: 'Borrar datos' })
  check(await wipe.isDisabled(), 'no se puede borrar sin escribir BORRAR')
  await page.locator('.dialog input.input').fill('BORRAR')
  await wipe.click()
  await page.waitForTimeout(500)
  const leo = await idb(page, 'savia-p2', 'profile', 1)
  check((await idb(page, 'savia-p2', 'symptoms')).length === 0 && (await idb(page, 'savia-p2', 'vaccinations')).length === 0 && (await idb(page, 'savia-p2', 'measurements')).length === 0, 'se borran los datos del perfil')
  check(leo?.name === 'Leo' && leo?.relation === 'Hijo' && leo?.avatarColor === 2 && leo?.birthEpochDay === null, 'se conservan nombre, parentesco y color; la ficha queda limpia')
  check((await idb(page, 'savia-p1', 'symptoms')).length === 12 && (await idb(page, 'savia-p3', 'symptoms')).length === 3, 'los demás perfiles no se tocan')

  // Ficha y ajustes del perfil principal
  await go('/perfiles')
  await page.locator('.card', { hasText: 'Alex' }).click()
  await page.waitForFunction(() => location.hash === '#/')
  await safeArea(page)
  await go('/ficha')
  await page.waitForSelector('text=Tarjeta de emergencia')
  await noSmallInputs(auditInputs, 'la ficha de persona')
  await shot('profile_ficha_persona', true)
  // cambios sin guardar → aviso al regresar
  await page.locator('input.input').first().fill('Alexa')
  await page.getByRole('button', { name: 'Regresar' }).click()
  check(await page.getByText('Cambios sin guardar').isVisible(), 'avisa de cambios sin guardar al regresar')
  await page.locator('.dialog').getByRole('button', { name: 'Cancelar' }).click()
  await page.locator('input.input').first().fill('Alex')
  await go('/ajustes')
  await page.waitForSelector('text=Respaldo')
  check((await page.getByText('Ciclo menstrual', { exact: true }).count()) === 1, 'los ajustes de una persona muestran el ciclo')
  check((await page.locator('.pf-backup').count()) === 1, 'Ajustes incluye la sección Respaldo con los botones de respaldo')
  await noSmallInputs(auditInputs, 'ajustes')
  await shot('profile_ajustes', true)
  // tema oscuro desde ajustes
  await page.getByRole('button', { name: 'Oscuro', exact: true }).click()
  await page.waitForTimeout(200)
  check((await page.evaluate(() => document.documentElement.dataset.theme)) === 'dark' && (await kv(page, 'themeMode')) === 'dark', 'el tema oscuro se aplica y se guarda')
  await page.getByRole('button', { name: 'Sistema', exact: true }).click()
  // ciclo: apagar y encender
  const sw = page.getByRole('switch').first()
  await sw.click({ force: true }); await page.waitForTimeout(200)
  check((await idb(page, 'savia-p1', 'prefs', 'cycleEnabled'))?.value === false, 'apagar el ciclo se guarda en el perfil')
  await sw.click({ force: true }); await page.waitForTimeout(200)
  check((await idb(page, 'savia-p1', 'prefs', 'cycleEnabled'))?.value === true, 'y volver a encenderlo también')
})
check(errB.length === 0, 'sin errores de consola en perfiles y fichas' + (errB.length ? ' ' + JSON.stringify(errB.slice(0, 3)) : ''))

// ── C. Bloqueo con PIN y tema oscuro ─────────────────────────────────────────────────────────────
console.log('C. Bloqueo con PIN (tema oscuro)')
const errC = await withApp({ seed: 'person', dark: true }, async ({ page, go, shot }) => {
  await go('/ficha')
  await page.waitForSelector('text=Tarjeta de emergencia')
  await shot('profile_ficha_persona_dark', true)
  await go('/perfiles')
  await shot('profile_perfiles_dark')
  await go('/ajustes')
  await shot('profile_ajustes_dark', true)

  // Activar: PIN de 4 dígitos, confirmación que no coincide y luego correcta
  await page.getByRole('button', { name: 'Activar bloqueo' }).click()
  await page.waitForSelector('.pf-pinsheet')
  await page.waitForTimeout(400)
  await shot('profile_pin_activar')
  await pin(page, '2468', '.sheet'); await page.locator('.sheet .pf-pad button', { hasText: 'OK' }).click()
  await pin(page, '2469', '.sheet'); await page.locator('.sheet .pf-pad button', { hasText: 'OK' }).click()
  check(await page.getByText('Los PIN no coinciden').isVisible(), 'si la confirmación no coincide se empieza de nuevo')
  await pin(page, '2468', '.sheet'); await page.locator('.sheet .pf-pad button', { hasText: 'OK' }).click()
  await pin(page, '2468', '.sheet'); await page.locator('.sheet .pf-pad button', { hasText: 'OK' }).click()
  await page.waitForSelector('text=Bloqueo con PIN activado')
  const hash = await kv(page, 'pinHash'), salt = await kv(page, 'pinSalt')
  check(/^[0-9a-f]{64}$/.test(hash) && /^4:[0-9a-f]{32}$/.test(salt), 'solo se guarda un hash SHA-256 con sal (no el PIN)')
  check((await page.locator('.pf-lock').count()) === 0, 'activar el PIN no bloquea la sesión en curso')
  check(await page.getByRole('button', { name: 'Cambiar PIN' }).isVisible(), 'con PIN activo se ofrece cambiarlo o quitarlo')

  // Al recargar se pide el PIN
  await page.reload(); await page.waitForSelector('.pf-lock'); await safeArea(page)
  check((await page.locator('.tabbar, .page-body').count()) === 0, 'con la app bloqueada no se muestra el contenido')
  await page.waitForTimeout(400)
  await shot('profile_bloqueo_dark')
  await pin(page, '1111', '.pf-lock')
  await page.waitForSelector('text=PIN incorrecto')
  check((await page.locator('.pf-lock').count()) === 1, 'un PIN incorrecto no desbloquea')
  await page.waitForTimeout(400)
  await shot('profile_bloqueo_incorrecto')
  await pin(page, '2468', '.pf-lock')
  await page.waitForSelector('.pf-lock', { state: 'detached' })
  await page.waitForSelector('.page-body')
  check(true, 'el PIN correcto desbloquea')

  // Volver tras más de un minuto en segundo plano vuelve a bloquear
  await page.evaluate(() => {
    const real = Date.now
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    Date.now = () => real() + 61_000
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' })
    document.dispatchEvent(new Event('visibilitychange'))
    Date.now = real
  })
  await page.waitForSelector('.pf-lock')
  check(true, 'tras más de 60 s en segundo plano vuelve a pedir el PIN')
  await pin(page, '2468', '.pf-lock')
  await page.waitForSelector('.pf-lock', { state: 'detached' })
  check((await page.evaluate(() => location.hash)) === '#/ajustes', 'al desbloquear sigue en la misma pantalla')

  // Cambiar el PIN (pide el actual) a uno de 6 dígitos
  await page.getByRole('button', { name: 'Cambiar PIN' }).click()
  await pin(page, '0000', '.sheet')
  await page.waitForSelector('.sheet >> text=PIN incorrecto')
  await pin(page, '2468', '.sheet')
  await page.waitForSelector('text=Elige un PIN')
  await pin(page, '135790', '.sheet'); await page.locator('.sheet .pf-pad button', { hasText: 'OK' }).click()
  await pin(page, '135790', '.sheet'); await page.locator('.sheet .pf-pad button', { hasText: 'OK' }).click()
  await page.waitForSelector('text=PIN actualizado')
  check((await kv(page, 'pinSalt')).startsWith('6:') && (await kv(page, 'pinHash')) !== hash, 'cambiar el PIN pide el actual y guarda el nuevo')
  await page.reload(); await page.waitForSelector('.pf-lock')
  await pin(page, '246800', '.pf-lock')
  await page.waitForSelector('text=PIN incorrecto')
  await pin(page, '135790', '.pf-lock')
  await page.waitForSelector('.pf-lock', { state: 'detached' })
  check(true, 'el PIN nuevo (6 dígitos) desbloquea y el anterior ya no')

  // Quitar el bloqueo (pide el actual)
  await page.getByRole('button', { name: 'Quitar', exact: true }).click()
  await pin(page, '135790', '.sheet')
  await page.waitForSelector('text=Se quitó el bloqueo con PIN')
  check((await kv(page, 'pinHash')) === '', 'quitar el bloqueo borra el hash')
  await page.reload(); await page.waitForSelector('.page-body')
  check((await page.locator('.pf-lock').count()) === 0, 'sin PIN la app abre directo')
})
check(errC.length === 0, 'sin errores de consola con el bloqueo' + (errC.length ? ' ' + JSON.stringify(errC.slice(0, 3)) : ''))

// Pantalla de bloqueo en tema claro
await withApp({ seed: 'person' }, async ({ page, go, shot }) => {
  await go('/ajustes')
  await page.getByRole('button', { name: 'Activar bloqueo' }).click()
  await pin(page, '2468', '.sheet'); await page.locator('.sheet .pf-pad button', { hasText: 'OK' }).click()
  await pin(page, '2468', '.sheet'); await page.locator('.sheet .pf-pad button', { hasText: 'OK' }).click()
  await page.waitForSelector('text=Bloqueo con PIN activado')
  await page.reload(); await page.waitForSelector('.pf-lock'); await safeArea(page)
  await pin(page, '24', '.pf-lock')
  await page.waitForTimeout(400)
  await shot('profile_bloqueo')
})

console.log(failures ? `CON FALLAS (${failures})` : 'OK')
process.exitCode = failures ? 1 : 0
