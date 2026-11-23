// Datos de ejemplo para pruebas (equivalentes a DemoData de la app de Android).
const DAY = 86400000
const todayEpochDay = () => { const d = new Date(); return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / DAY) }
const ms = (day, minutes = 720) => { const u = new Date(day * DAY); const d = new Date(u.getUTCFullYear(), u.getUTCMonth(), u.getUTCDate()); d.setHours(Math.floor(minutes / 60), minutes % 60, 0, 0); return d.getTime() }
const profile = (p) => ({ id: 1, name: '', sex: 'X', birthEpochDay: null, heightCm: null, goalWeightKg: null, bloodType: '', allergies: '', conditions: '', emergencyName: '', emergencyPhone: '', doctorName: '', doctorPhone: '', insurance: '', notes: '', kind: 'person', species: '', breed: '', sterilized: null, microchip: '', markings: '', relation: '', avatarColor: 0, ...p })
const sym = (o) => ({ regionId: null, regionLabel: null, durationMin: null, tags: '', notes: '', intensity: 5, ...o })
const meas = (o) => ({ value2: null, tag: '', notes: '', ...o })
const cyc = (o) => ({ flow: 0, symptoms: '', moods: '', mucus: '', basalTemp: null, sex: 0, ovulationTest: 0, pregnancyTest: 0, contraceptive: false, notes: '', ...o })
const vac = (o) => ({ type: 'vacuna', dose: '', nextDueEpochDay: null, place: '', lot: '', notes: '', remind: true, ...o })
const med = (o) => ({ dose: '', instructions: '', times: '', daysMask: 127, endEpochDay: null, active: true, remind: true, notes: '', ...o })
const appt = (o) => ({ doctor: '', specialty: '', place: '', notes: '', remind: true, done: false, ...o })

/** seed: 'empty' | 'person' | 'family' | 'pet' | 'kid'. Devuelve { nombreDeBase: { tabla: [filas] } }. */
export function demoData(seed) {
  const t = todayEpochDay()
  const out = { 'savia-meta': { kv: [{ key: 'onboardingDone', value: true }], profiles: [{ id: 1, createdAt: Date.now() }] } }
  if (seed === 'empty') { out['savia-p1'] = { profile: [profile({ name: '' })] }; return out }

  // ── Perfil principal ──
  const p1 = { profile: [profile({ name: 'Alex', sex: 'F', relation: 'Yo', birthEpochDay: t - 365 * 31 - 40, heightCm: 168, goalWeightKg: 64, bloodType: 'O+', allergies: 'Penicilina', conditions: 'Rinitis alérgica', emergencyName: 'Mamá', emergencyPhone: '55 1234 5678', doctorName: 'Dra. Rivera', doctorPhone: '55 8765 4321' })] }
  p1.prefs = [{ key: 'cycleEnabled', value: true }]
  const symptoms = [[0, 'Dolor de cabeza', 6], [1, 'Fatiga', 4], [3, 'Dolor abdominal', 7], [5, 'Dolor de cabeza', 5], [9, 'Tos', 3], [10, 'Dolor de garganta', 4], [14, 'Dolor de espalda', 5], [21, 'Dolor de cabeza', 7], [30, 'Náuseas', 3], [41, 'Migraña', 8], [55, 'Dolor de cabeza', 4], [70, 'Acidez / reflujo', 5]]
  p1.symptoms = symptoms.map(([ago, name, intensity], i) => sym({ id: i + 1, timestamp: Math.min(ms(t - ago, 540 + (i * 37) % 600), Date.now() - 600000), name, intensity, regionId: name === 'Dolor abdominal' ? 'abd_fosa_iliaca_der' : null, regionLabel: name === 'Dolor abdominal' ? 'Fosa ilíaca derecha' : null, durationMin: i % 2 === 0 ? 90 : null, tags: name.includes('cabeza') ? 'Estrés,Pantallas' : '', notes: i === 0 ? 'Empezó después de comer; mejoró con reposo.' : '' }))
  p1.measurements = []
  let mid = 1
  for (let i = 0; i <= 60; i++) p1.measurements.push(meas({ id: mid++, timestamp: ms(t - 180 + i * 3, 450), type: 'weight', value: Math.round((70.5 - i * 0.07 + [0, 0.4, -0.3, 0.2, -0.1][i % 5]) * 10) / 10 }))
  for (let i = 0; i <= 14; i++) {
    p1.measurements.push(meas({ id: mid++, timestamp: ms(t - 84 + i * 6, 480), type: 'bp', value: 114 + (i * 7) % 14, value2: 72 + (i * 5) % 11, tag: 'Por la mañana' }))
    p1.measurements.push(meas({ id: mid++, timestamp: ms(t - 84 + i * 6, 482), type: 'hr', value: 62 + (i * 3) % 12, tag: 'En reposo' }))
  }
  for (let i = 0; i <= 7; i++) p1.measurements.push(meas({ id: mid++, timestamp: ms(t - 70 + i * 10, 420), type: 'glucose', value: 86 + (i * 5) % 13, tag: 'En ayunas' }))
  p1.measurements.push(meas({ id: mid++, timestamp: ms(t - 2, 1200), type: 'temp', value: 36.7 }))
  p1.measurements.push(meas({ id: mid++, timestamp: ms(t - 12, 540), type: 'waist', value: 78.5 }))
  p1.dailyLogs = []
  for (let i = 0; i < 30; i++) p1.dailyLogs.push({ epochDay: t - i, mood: 2 + (i * 7) % 4, energy: 2 + (i * 5) % 4, stress: 1 + (i * 3) % 4, sleepHours: 6 + ((i * 3) % 5) * 0.5, waterGlasses: 4 + (i * 2) % 5, exerciseMin: i % 3 === 0 ? 40 : 0, notes: i === 0 ? 'Día tranquilo, caminé por la tarde.' : '' })
  p1.studies = []; p1.studyResults = []
  let rid = 1
  const lab = (id, ago, glu, col, tri, hb) => {
    p1.studies.push({ id, epochDay: t - ago, title: 'Química sanguínea y biometría', category: 'lab', place: 'Laboratorio Central', doctor: 'Dra. Rivera', status: col > 200 ? 'seguimiento' : 'normal', summary: col > 200 ? 'Colesterol ligeramente elevado; repetir en 6 meses.' : 'Todo dentro de rango.', followUpEpochDay: null })
    const r = (name, value, unit, refMin, refMax, textValue = '') => p1.studyResults.push({ id: rid++, studyId: id, name, value, textValue, unit, refMin, refMax, notes: '' })
    r('Glucosa', glu, 'mg/dL', 70, 99); r('Colesterol total', col, 'mg/dL', null, 200); r('Triglicéridos', tri, 'mg/dL', null, 150); r('Hemoglobina', hb, 'g/dL', 12, 16); r('Examen general de orina', null, '', null, null, 'Sin alteraciones')
  }
  lab(1, 20, 92, 212, 138, 13.4); lab(2, 200, 95, 198, 160, 13.1); lab(3, 560, 89, 185, 120, 12.8)
  p1.studies.push({ id: 4, epochDay: t - 90, title: 'Ultrasonido abdominal', category: 'imagen', place: 'Clínica San José', doctor: '', status: 'normal', summary: 'Hígado, vesícula y riñones sin alteraciones.', followUpEpochDay: null })
  p1.studies.push({ id: 5, epochDay: t - 150, title: 'Limpieza dental', category: 'dental', place: '', doctor: '', status: 'normal', summary: '', followUpEpochDay: t + 30 })
  p1.cycleDays = []
  const lengths = [28, 29, 27, 30, 28, 26, 29, 28]
  let start = t - lengths.reduce((a, b) => a + b, 0) - 11
  const byDay = new Map()
  const put = (o) => byDay.set(o.epochDay, { ...(byDay.get(o.epochDay) ?? cyc({ epochDay: o.epochDay })), ...o })
  ;[...lengths, 0].forEach((len, ci) => {
    const pl = 4 + (ci % 2)
    for (let d = 0; d < pl; d++) { if (start + d > t) break; put({ epochDay: start + d, flow: [3, 4, 3, 2, 2][d], symptoms: d < 2 ? 'colicos,fatiga' : d === 2 ? 'dolor_cabeza' : '', moods: d === 0 ? 'sensible,irritable' : '' }) }
    if (len > 0) { put({ epochDay: start + len - 15, mucus: 'clara', symptoms: 'dolor_ovulacion', sex: 1 }); put({ epochDay: start + len - 3, symptoms: 'senos,hinchazon,antojos', moods: 'cambios' }) }
    start += len
  })
  put({ epochDay: t, symptoms: 'fatiga', moods: 'tranquila', notes: 'Todo bien.' })
  put({ epochDay: t - 3, pregnancyTest: 1, sex: 2 })
  p1.cycleDays = [...byDay.values()]
  p1.medications = [
    med({ id: 1, name: 'Loratadina', dose: '10 mg', times: '08:00', startEpochDay: t - 40, instructions: 'Con el desayuno' }),
    med({ id: 2, name: 'Vitamina D', dose: '1 cápsula', times: '08:00,20:00', startEpochDay: t - 100 }),
    med({ id: 3, name: 'Paracetamol', dose: '500 mg', times: '', startEpochDay: t - 200, remind: false, instructions: 'Solo si hay dolor' }),
    med({ id: 4, name: 'Amoxicilina', dose: '500 mg', times: '08:00,16:00,23:00', startEpochDay: t - 90, endEpochDay: t - 83, active: false }),
  ]
  p1.medIntakes = [{ id: 1, medicationId: 1, epochDay: t, slot: '08:00', timestamp: ms(t, 485), status: 'taken' }, { id: 2, medicationId: 2, epochDay: t, slot: '08:00', timestamp: ms(t, 486), status: 'taken' }]
  p1.appointments = [
    appt({ id: 1, timestamp: ms(t + 6, 630), title: 'Revisión anual', doctor: 'Dra. Rivera', specialty: 'Medicina general', place: 'Consultorio 204, Hospital Ángeles' }),
    appt({ id: 2, timestamp: ms(t + 30, 1020), title: 'Limpieza dental', doctor: 'Dr. Soto', specialty: 'Odontología' }),
    appt({ id: 3, timestamp: ms(t - 20, 540), title: 'Entrega de resultados', doctor: 'Dra. Rivera', done: true }),
  ]
  p1.selfExams = [{ id: 1, epochDay: t - 33, type: 'mama', normal: true, notes: '' }, { id: 2, epochDay: t - 64, type: 'mama', normal: true, notes: '' }, { id: 3, epochDay: t - 15, type: 'piel', normal: false, notes: 'Lunar nuevo en el hombro izquierdo; vigilar.' }]
  p1.vaccinations = [vac({ id: 1, epochDay: t - 300, name: 'Influenza', dose: 'Anual', nextDueEpochDay: t + 65, place: 'Centro de salud' }), vac({ id: 2, epochDay: t - 900, name: 'Td (tétanos y difteria)', dose: 'Refuerzo' })]
  out['savia-p1'] = p1
  if (seed === 'person') return out

  // ── Familia: hijo (perfil 2) y perro (perfil 3) ──
  out['savia-meta'].profiles.push({ id: 2, createdAt: Date.now() }, { id: 3, createdAt: Date.now() })
  out['savia-meta'].kv.push({ key: 'nextProfileId', value: 4 })
  const kid = { profile: [profile({ name: 'Leo', sex: 'M', relation: 'Hijo', birthEpochDay: t - 365 * 7 - 100, heightCm: 122, avatarColor: 2, bloodType: 'O+', allergies: 'Polen' })] }
  kid.measurements = []
  for (let i = 0; i <= 8; i++) { kid.measurements.push(meas({ id: i * 2 + 1, timestamp: ms(t - 720 + i * 90, 540), type: 'weight', value: 19.5 + i * 0.6 })); kid.measurements.push(meas({ id: i * 2 + 2, timestamp: ms(t - 720 + i * 90, 541), type: 'height', value: 110 + i * 1.5 })) }
  kid.symptoms = [sym({ id: 1, timestamp: ms(t - 2, 1200), name: 'Fiebre', intensity: 5, notes: '38.2 °C por la noche' }), sym({ id: 2, timestamp: ms(t - 2, 480), name: 'Tos', intensity: 3 })]
  kid.vaccinations = [vac({ id: 1, epochDay: t - 400, name: 'Influenza', dose: 'Anual', nextDueEpochDay: t - 35, place: 'Centro de salud' }), vac({ id: 2, epochDay: t - 900, name: 'SRP (sarampión, rubéola, paperas)', dose: 'Refuerzo' }), vac({ id: 3, epochDay: t - 1500, name: 'DPT', dose: 'Refuerzo' })]
  kid.medications = [med({ id: 1, name: 'Paracetamol infantil', dose: '5 ml', times: '08:00,16:00', startEpochDay: t - 2, endEpochDay: t + 1 })]
  kid.medIntakes = [{ id: 1, medicationId: 1, epochDay: t, slot: '08:00', timestamp: ms(t, 490), status: 'taken' }]
  kid.appointments = [appt({ id: 1, timestamp: ms(t + 1, 1020), title: 'Pediatra', doctor: 'Dr. Méndez', specialty: 'Pediatría' })]
  out['savia-p2'] = kid
  const dog = { profile: [profile({ name: 'Firulais', kind: 'pet', species: 'perro', breed: 'Mestizo', sex: 'M', birthEpochDay: t - 365 * 4 - 60, sterilized: true, microchip: '985112003456789', markings: 'Café con pecho blanco', doctorName: 'Veterinaria Huellitas', doctorPhone: '55 2222 3333', avatarColor: 5, goalWeightKg: 17 })] }
  dog.measurements = []
  for (let i = 0; i <= 12; i++) dog.measurements.push(meas({ id: i + 1, timestamp: ms(t - 360 + i * 30, 600), type: 'weight', value: Math.round((19.4 - i * 0.15 + (i % 3) * 0.1) * 100) / 100 }))
  dog.measurements.push(meas({ id: 20, timestamp: ms(t - 20, 660), type: 'temp', value: 38.6 }))
  dog.symptoms = [sym({ id: 1, timestamp: ms(t - 1, 420), name: 'Vómito', intensity: 4, tags: 'Comió algo raro', notes: 'Una vez, después del paseo.' }), sym({ id: 2, timestamp: ms(t - 9, 1140), name: 'Comezón / se rasca mucho', intensity: 5 }), sym({ id: 3, timestamp: ms(t - 40, 540), name: 'Cojera', intensity: 6, regionId: 'pet_carpo_perro', regionLabel: 'Carpo (muñeca)' })]
  dog.vaccinations = [vac({ id: 1, epochDay: t - 360, name: 'Rabia', dose: 'Anual', nextDueEpochDay: t + 5, place: 'Veterinaria Huellitas', lot: 'RB-2231' }), vac({ id: 2, epochDay: t - 330, name: 'Múltiple (séxtuple)', dose: 'Refuerzo', nextDueEpochDay: t + 35, place: 'Veterinaria Huellitas' }), vac({ id: 3, epochDay: t - 100, type: 'desp_interna', name: 'Desparasitación interna', nextDueEpochDay: t - 10 }), vac({ id: 4, epochDay: t - 25, type: 'desp_externa', name: 'Pipeta antipulgas', nextDueEpochDay: t + 5 })]
  dog.medications = [med({ id: 1, name: 'Omeprazol', dose: '10 mg', times: '09:00', startEpochDay: t - 1, endEpochDay: t + 4, instructions: 'En ayunas' })]
  dog.appointments = [appt({ id: 1, timestamp: ms(t + 5, 660), title: 'Vacuna anual', doctor: 'Veterinaria Huellitas' })]
  dog.studies = [{ id: 1, epochDay: t - 200, title: 'Química sanguínea', category: 'lab', place: 'Laboratorio veterinario', doctor: '', status: 'normal', summary: '', followUpEpochDay: null }]
  dog.studyResults = [{ id: 1, studyId: 1, name: 'Creatinina', value: 1.1, textValue: '', unit: 'mg/dL', refMin: 0.5, refMax: 1.8, notes: '' }]
  out['savia-p3'] = dog
  if (seed === 'pet') out['savia-meta'].kv.push({ key: 'activeProfile', value: 3 })
  if (seed === 'kid') out['savia-meta'].kv.push({ key: 'activeProfile', value: 2 })
  return out
}
