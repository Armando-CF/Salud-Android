import { useEffect, useMemo, useRef, useState } from 'react'
import { registerSW } from 'virtual:pwa-register'
import { HashRouter, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { CalendarDays, Droplet, LayoutGrid, NotebookPen, PersonStanding, Scale } from 'lucide-react'
import { dbFor } from './data/db'
import { PetSpecies } from './data/catalogs'
import { applyTheme, getSetting, setSetting, usePref, useSetting } from './data/prefs'
import { ProfileContext, initProfiles, toInfo, useActive, useActiveId, type ActiveProfile } from './data/profiles'
import { PawIcon, TabsContext, ToastProvider } from './ui'
import { HomePage, MorePage } from './modules/home'
import { SymptomEditPage, SymptomsPage } from './modules/symptoms'
import { SelfExamPage, WellbeingPage } from './modules/wellbeing'
import { MeasurementDetailPage, MeasurementsPage } from './modules/measure'
import { ParamTrendPage, StudiesPage, StudyDetailPage, StudyEditPage } from './modules/studies'
import { CycleDayPage, CyclePage, CycleSettingsPage, CycleStatsPage } from './modules/cycle'
import { AppointmentEditPage, AppointmentsPage, MedEditPage, MedsPage } from './modules/meds'
import { VaccineEditPage, VaccinesPage } from './modules/vaccines'
import { AtlasPage } from './modules/atlas'
import { LockGate, NewProfilePage, Onboarding, ProfilePage, ProfilesPage, SettingsPage } from './modules/profile'
import { ExportPage } from './modules/backup'
import { EnergyPage } from './modules/energy'

function Splash() {
  return (
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--header-bg)' }}>
      <img src="./favicon.svg" width={84} height={84} alt="Savia" />
    </div>
  )
}

export default function App() {
  const [ready, setReady] = useState(false)
  const activeId = useActiveId()
  const [onboarded] = useSetting('onboardingDone')

  useEffect(() => {
    let alive = true
    ;(async () => {
      await initProfiles()
      applyTheme(await getSetting('themeMode'))
      // Pide al navegador que no borre los datos locales por falta de espacio.
      try { await navigator.storage?.persist?.() } catch { /* no disponible */ }
      if (alive) setReady(true)
    })()
    // Cierra el teclado al tocar cualquier cosa que no sea un campo (en iPhone el teclado no se cierra solo).
    const dismissKeyboard = (e: MouseEvent) => {
      const active = document.activeElement as HTMLElement | null
      if (!active || !/^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName)) return
      const target = e.target as HTMLElement | null
      if (target && target.closest('input, textarea, select, label')) return
      active.blur()
    }
    document.addEventListener('click', dismissKeyboard, true)
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const onScheme = () => { getSetting('themeMode').then(applyTheme) }
    mq.addEventListener?.('change', onScheme)
    return () => { alive = false; mq.removeEventListener?.('change', onScheme); document.removeEventListener('click', dismissKeyboard, true) }
  }, [])

  return (
    <div className="app">
      <ToastProvider>
        <UpdatePrompt />
        {!ready || activeId === undefined || onboarded === undefined ? (
          <Splash />
        ) : !onboarded ? (
          <Onboarding onDone={() => { void setSetting('onboardingDone', true) }} />
        ) : (
          <LockGate>
            {/* Al cambiar de perfil se recrea todo: cada perfil tiene su propia base de datos. */}
            <ProfileShell key={activeId} id={activeId} />
          </LockGate>
        )}
      </ToastProvider>
    </div>
  )
}

/**
 * Registra el service worker y avisa cuando hay una versión nueva publicada.
 * La app revisa si hay actualización al abrirse, al volver a primer plano y cada hora.
 * Los datos (IndexedDB) no se tocan al actualizar.
 */
function UpdatePrompt() {
  const [ready, setReady] = useState(false)
  const update = useRef<(reload?: boolean) => Promise<void>>()
  useEffect(() => {
    let reg: ServiceWorkerRegistration | undefined
    update.current = registerSW({
      immediate: true,
      onNeedRefresh: () => setReady(true),
      onRegisteredSW: (_url, r) => { reg = r },
    })
    const check = () => { if (document.visibilityState === 'visible') reg?.update().catch(() => {}) }
    document.addEventListener('visibilitychange', check)
    const timer = window.setInterval(check, 60 * 60 * 1000)
    return () => { document.removeEventListener('visibilitychange', check); window.clearInterval(timer) }
  }, [])
  if (!ready) return null
  return (
    <div className="update-bar" role="status">
      <span className="grow">Hay una versión nueva de Savia.</span>
      <button className="btn small tonal" onClick={() => { void update.current?.(true) }}>Actualizar</button>
      <button className="btn small text" style={{ color: 'inherit' }} onClick={() => setReady(false)}>Luego</button>
    </div>
  )
}

function ProfileShell({ id }: { id: number }) {
  const db = useMemo(() => dbFor(id), [id])
  const loaded = useLiveQuery(async () => ({ row: await db.profile.get(1) }), [db])
  const value = useMemo<ActiveProfile | null>(() => {
    if (!loaded) return null
    const info = toInfo(id, loaded.row)
    return { info, db, isPet: info.kind === 'pet' }
  }, [loaded, db, id])
  if (!value) return <Splash />
  return (
    <ProfileContext.Provider value={value}>
      <HashRouter>
        <Shell />
      </HashRouter>
    </ProfileContext.Provider>
  )
}

function Shell() {
  const { info, isPet } = useActive()
  const [cycleEnabled] = usePref('cycleEnabled')
  const loc = useLocation()
  const hasAtlas = !isPet || PetSpecies.withAtlas.includes(info.species)
  const tabs = useMemo(
    () => [
      { to: '/', label: 'Hoy', icon: <CalendarDays size={22} /> },
      { to: '/sintomas', label: 'Síntomas', icon: <NotebookPen size={22} /> },
      cycleEnabled && !isPet
        ? { to: '/ciclo', label: 'Ciclo', icon: <Droplet size={22} /> }
        : { to: '/mediciones', label: 'Mediciones', icon: <Scale size={22} /> },
      ...(hasAtlas ? [{ to: '/cuerpo', label: 'Cuerpo', icon: isPet ? <PawIcon size={22} /> : <PersonStanding size={22} /> }] : []),
      { to: '/mas', label: 'Más', icon: <LayoutGrid size={22} /> },
    ],
    [cycleEnabled, isPet, hasAtlas],
  )
  const tabPaths = useMemo(() => tabs.map((t) => t.to), [tabs])
  const showTabs = tabPaths.includes(loc.pathname)
  return (
    <TabsContext.Provider value={tabPaths}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/mas" element={<MorePage />} />
        <Route path="/sintomas" element={<SymptomsPage />} />
        <Route path="/sintomas/nuevo" element={<SymptomEditPage />} />
        <Route path="/sintomas/:id" element={<SymptomEditPage />} />
        <Route path="/cuerpo" element={<AtlasPage />} />
        <Route path="/ciclo" element={<CyclePage />} />
        <Route path="/ciclo/dia/:day" element={<CycleDayPage />} />
        <Route path="/ciclo/estadisticas" element={<CycleStatsPage />} />
        <Route path="/ciclo/ajustes" element={<CycleSettingsPage />} />
        <Route path="/mediciones" element={<MeasurementsPage />} />
        <Route path="/mediciones/:type" element={<MeasurementDetailPage />} />
        <Route path="/estudios" element={<StudiesPage />} />
        <Route path="/estudios/nuevo" element={<StudyEditPage />} />
        <Route path="/estudios/:id" element={<StudyDetailPage />} />
        <Route path="/estudios/:id/editar" element={<StudyEditPage />} />
        <Route path="/parametro/:name" element={<ParamTrendPage />} />
        <Route path="/medicamentos" element={<MedsPage />} />
        <Route path="/medicamentos/nuevo" element={<MedEditPage />} />
        <Route path="/medicamentos/:id" element={<MedEditPage />} />
        <Route path="/citas" element={<AppointmentsPage />} />
        <Route path="/citas/nueva" element={<AppointmentEditPage />} />
        <Route path="/citas/:id" element={<AppointmentEditPage />} />
        <Route path="/vacunas" element={<VaccinesPage />} />
        <Route path="/vacunas/nueva" element={<VaccineEditPage />} />
        <Route path="/vacunas/:id" element={<VaccineEditPage />} />
        <Route path="/bienestar" element={<WellbeingPage />} />
        <Route path="/autoexploracion" element={<SelfExamPage />} />
        <Route path="/ficha" element={<ProfilePage />} />
        <Route path="/perfiles" element={<ProfilesPage />} />
        <Route path="/perfiles/nuevo/:kind" element={<NewProfilePage />} />
        <Route path="/energia" element={<EnergyPage />} />
        <Route path="/exportar" element={<ExportPage />} />
        <Route path="/ajustes" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      {showTabs && (
        <nav className="tabbar" aria-label="Secciones">
          {tabs.map((t) => (
            <NavLink key={t.to} to={t.to} end replace className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="pill">{t.icon}</span>
              <span>{t.label}</span>
            </NavLink>
          ))}
        </nav>
      )}
    </TabsContext.Provider>
  )
}
