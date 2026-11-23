package app.savia

import app.savia.data.Appointment
import app.savia.data.CycleDay
import app.savia.data.DailyLog
import app.savia.data.Measurement
import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.data.Profile
import app.savia.data.SelfExam
import app.savia.data.Study
import app.savia.data.StudyResult
import app.savia.data.SymptomEntry
import app.savia.util.epochDayToMillis
import app.savia.util.today
import kotlinx.coroutines.runBlocking

/** Datos de ejemplo realistas para capturas de pantalla y pruebas. */
object DemoData {
    fun seed(cycle: Boolean = true) = runBlocking {
        val db = Savia.db
        val t = today()
        db.profile().save(
            Profile(
                name = "Alex", sex = if (cycle) "F" else "M", birthEpochDay = t - 365L * 31 - 40, heightCm = 168.0, goalWeightKg = 64.0,
                bloodType = "O+", allergies = "Penicilina", conditions = "Rinitis alérgica",
                emergencyName = "Mamá", emergencyPhone = "55 1234 5678", doctorName = "Dra. Rivera", doctorPhone = "55 8765 4321",
            ),
        )
        Savia.prefs.cycleEnabled.value = cycle
        Savia.prefs.onboardingDone.value = true

        // Síntomas
        val symptoms = listOf(
            Triple(0L, "Dolor de cabeza", 6), Triple(1L, "Fatiga", 4), Triple(3L, "Dolor abdominal", 7),
            Triple(5L, "Dolor de cabeza", 5), Triple(9L, "Tos", 3), Triple(10L, "Dolor de garganta", 4),
            Triple(14L, "Dolor de espalda", 5), Triple(21L, "Dolor de cabeza", 7), Triple(30L, "Náuseas", 3),
            Triple(41L, "Migraña", 8), Triple(55L, "Dolor de cabeza", 4), Triple(70L, "Acidez / reflujo", 5),
        )
        symptoms.forEachIndexed { i, (ago, name, inten) ->
            db.symptoms().insert(
                SymptomEntry(
                    timestamp = minOf(epochDayToMillis(t - ago, 9 * 60 + i * 37 % 600), System.currentTimeMillis() - 600_000L), name = name, intensity = inten,
                    regionId = if (name == "Dolor abdominal") "abd_fosa_iliaca_der" else null,
                    regionLabel = if (name == "Dolor abdominal") "Fosa ilíaca derecha" else null,
                    durationMin = if (i % 2 == 0) 90 else null,
                    tags = if (name.contains("cabeza")) "Estrés,Pantallas" else "",
                    notes = if (i == 0) "Empezó después de comer; mejoró con reposo." else "",
                ),
            )
        }

        // Mediciones: peso 6 meses (tendencia a la baja con ruido), presión, glucosa, pulso
        for (i in 0..60) {
            val day = t - 180 + i * 3
            val w = 70.5 - i * 0.07 + listOf(0.0, 0.4, -0.3, 0.2, -0.1)[i % 5]
            db.measurements().insert(Measurement(timestamp = epochDayToMillis(day, 7 * 60 + 30), type = "weight", value = Math.round(w * 10) / 10.0))
        }
        for (i in 0..14) {
            val day = t - 84 + i * 6
            db.measurements().insert(Measurement(timestamp = epochDayToMillis(day, 8 * 60), type = "bp", value = 114.0 + (i * 7) % 14, value2 = 72.0 + (i * 5) % 11, tag = "Por la mañana"))
            db.measurements().insert(Measurement(timestamp = epochDayToMillis(day, 8 * 60 + 2), type = "hr", value = 62.0 + (i * 3) % 12, tag = "En reposo"))
        }
        for (i in 0..7) {
            db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 70 + i * 10, 7 * 60), type = "glucose", value = 86.0 + (i * 5) % 13, tag = "En ayunas"))
        }
        db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 2, 20 * 60), type = "temp", value = 36.7))
        db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 12, 9 * 60), type = "waist", value = 78.5))

        // Bienestar diario
        for (i in 0..29) {
            db.dailyLogs().save(
                DailyLog(
                    epochDay = t - i, mood = 2 + (i * 7) % 4, energy = 2 + (i * 5) % 4, stress = 1 + (i * 3) % 4,
                    sleepHours = 6.0 + ((i * 3) % 5) * 0.5, waterGlasses = 4 + (i * 2) % 5, exerciseMin = if (i % 3 == 0) 40 else 0,
                    notes = if (i == 0) "Día tranquilo, caminé por la tarde." else "",
                ),
            )
        }

        // Estudios
        fun lab(ago: Long, glu: Double, col: Double, tri: Double, hb: Double): Long = runBlocking {
            val id = db.studies().insert(
                Study(epochDay = t - ago, title = "Química sanguínea y biometría", category = "lab", place = "Laboratorio Central", doctor = "Dra. Rivera",
                    status = if (col > 200) "seguimiento" else "normal", summary = if (col > 200) "Colesterol ligeramente elevado; repetir en 6 meses." else "Todo dentro de rango."),
            )
            db.studies().insertResults(
                listOf(
                    StudyResult(studyId = id, name = "Glucosa", value = glu, unit = "mg/dL", refMin = 70.0, refMax = 99.0),
                    StudyResult(studyId = id, name = "Colesterol total", value = col, unit = "mg/dL", refMax = 200.0),
                    StudyResult(studyId = id, name = "Triglicéridos", value = tri, unit = "mg/dL", refMax = 150.0),
                    StudyResult(studyId = id, name = "Hemoglobina", value = hb, unit = "g/dL", refMin = 12.0, refMax = 16.0),
                    StudyResult(studyId = id, name = "Examen general de orina", textValue = "Sin alteraciones"),
                ),
            )
            id
        }
        lab(20, 92.0, 212.0, 138.0, 13.4)
        lab(200, 95.0, 198.0, 160.0, 13.1)
        lab(560, 89.0, 185.0, 120.0, 12.8)
        db.studies().insert(Study(epochDay = t - 90, title = "Ultrasonido abdominal", category = "imagen", place = "Clínica San José", status = "normal", summary = "Hígado, vesícula y riñones sin alteraciones."))
        db.studies().insert(Study(epochDay = t - 150, title = "Limpieza dental", category = "dental", status = "normal", followUpEpochDay = t + 30))
        db.studies().insert(Study(epochDay = t - 300, title = "Influenza", category = "vacuna", place = "Centro de salud"))

        // Ciclo: 9 ciclos con duraciones variables
        if (cycle) {
            val lengths = listOf(28, 29, 27, 30, 28, 26, 29, 28)
            var start = t - lengths.sum() - 11
            for ((ci, len) in (lengths + 0).withIndex()) {
                val pl = 4 + ci % 2
                for (d in 0 until pl) {
                    if (start + d > t) break
                    db.cycle().save(
                        CycleDay(
                            epochDay = start + d, flow = listOf(3, 4, 3, 2, 2)[d],
                            symptoms = if (d < 2) "colicos,fatiga" else if (d == 2) "dolor_cabeza" else "",
                            moods = if (d == 0) "sensible,irritable" else "",
                        ),
                    )
                }
                if (len > 0) {
                    db.cycle().save(CycleDay(epochDay = start + len - 15, mucus = "clara", symptoms = "dolor_ovulacion", sex = 1))
                    db.cycle().save(CycleDay(epochDay = start + len - 3, symptoms = "senos,hinchazon,antojos", moods = "cambios"))
                }
                start += len
            }
            db.cycle().save(CycleDay(epochDay = t, symptoms = "fatiga", moods = "tranquila", notes = "Todo bien."))
        }

        // Medicamentos y tomas
        val med1 = db.medications().insert(Medication(name = "Loratadina", dose = "10 mg", times = "08:00", startEpochDay = t - 40, instructions = "Con el desayuno"))
        val med2 = db.medications().insert(Medication(name = "Vitamina D", dose = "1 cápsula", times = "08:00,20:00", startEpochDay = t - 100))
        db.medications().insert(Medication(name = "Paracetamol", dose = "500 mg", times = "", startEpochDay = t - 200, remind = false, instructions = "Solo si hay dolor"))
        db.medications().insert(Medication(name = "Amoxicilina", dose = "500 mg", times = "08:00,16:00,23:00", startEpochDay = t - 90, endEpochDay = t - 83, active = false))
        db.medications().insertIntake(MedIntake(medicationId = med1, epochDay = t, slot = "08:00", timestamp = epochDayToMillis(t, 8 * 60 + 5)))
        db.medications().insertIntake(MedIntake(medicationId = med2, epochDay = t, slot = "08:00", timestamp = epochDayToMillis(t, 8 * 60 + 6)))

        // Citas
        db.appointments().insert(Appointment(timestamp = epochDayToMillis(t + 6, 10 * 60 + 30), title = "Revisión anual", doctor = "Dra. Rivera", specialty = "Medicina general", place = "Consultorio 204, Hospital Ángeles"))
        db.appointments().insert(Appointment(timestamp = epochDayToMillis(t + 30, 17 * 60), title = "Limpieza dental", doctor = "Dr. Soto", specialty = "Odontología"))
        db.appointments().insert(Appointment(timestamp = epochDayToMillis(t - 20, 9 * 60), title = "Entrega de resultados", doctor = "Dra. Rivera", done = true))

        // Autoexploración
        db.selfExams().insert(SelfExam(epochDay = t - 33, type = if (cycle) "mama" else "testicular", normal = true))
        db.selfExams().insert(SelfExam(epochDay = t - 64, type = if (cycle) "mama" else "testicular", normal = true))
        db.selfExams().insert(SelfExam(epochDay = t - 15, type = "piel", normal = false, notes = "Lunar nuevo en el hombro izquierdo; vigilar."))
    }

    /**
     * Además del perfil principal (con [seed]), crea una familia de ejemplo: un hijo de 7 años y un perro,
     * cada uno con su historial. Devuelve (idHijo, idPerro). El perfil activo sigue siendo el 1.
     */
    fun seedFamily(): Pair<Int, Int> = runBlocking {
        seed()
        val t = today()
        val kid = Savia.createProfile(Profile(name = "Leo", sex = "M", relation = "Hijo", birthEpochDay = t - 365L * 7 - 100, heightCm = 122.0, avatarColor = 2, bloodType = "O+", allergies = "Polen"))
        Savia.dbFor(kid).let { db ->
            for (i in 0..8) {
                val day = t - 720 + i * 90
                db.measurements().insert(Measurement(timestamp = epochDayToMillis(day, 9 * 60), type = "weight", value = 19.5 + i * 0.6))
                db.measurements().insert(Measurement(timestamp = epochDayToMillis(day, 9 * 60 + 1), type = "height", value = 110.0 + i * 1.5))
            }
            db.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 2, 20 * 60), name = "Fiebre", intensity = 5, notes = "38.2 °C por la noche"))
            db.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 2, 8 * 60), name = "Tos", intensity = 3))
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = t - 400, name = "Influenza", dose = "Anual", nextDueEpochDay = t - 35, place = "Centro de salud"))
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = t - 900, name = "SRP (sarampión, rubéola, paperas)", dose = "Refuerzo"))
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = t - 1500, name = "DPT", dose = "Refuerzo"))
            val med = db.medications().insert(Medication(name = "Paracetamol infantil", dose = "5 ml", times = "08:00,16:00", startEpochDay = t - 2, endEpochDay = t + 1))
            db.medications().insertIntake(MedIntake(medicationId = med, epochDay = t, slot = "08:00", timestamp = epochDayToMillis(t, 8 * 60 + 10)))
            db.appointments().insert(Appointment(timestamp = epochDayToMillis(t + 1, 17 * 60), title = "Pediatra", doctor = "Dr. Méndez", specialty = "Pediatría"))
        }
        val dog = Savia.createProfile(Profile(name = "Firulais", kind = "pet", species = "perro", breed = "Mestizo", sex = "M", birthEpochDay = t - 365L * 4 - 60, sterilized = true, microchip = "985112003456789", markings = "Café con pecho blanco", doctorName = "Veterinaria Huellitas", doctorPhone = "55 2222 3333", avatarColor = 5, goalWeightKg = 17.0))
        Savia.dbFor(dog).let { db ->
            for (i in 0..12) db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 360 + i * 30, 10 * 60), type = "weight", value = Math.round((19.4 - i * 0.15 + (i % 3) * 0.1) * 100) / 100.0))
            db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 20, 11 * 60), type = "temp", value = 38.6))
            db.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 1, 7 * 60), name = "Vómito", intensity = 4, tags = "Comió algo raro", notes = "Una vez, después del paseo."))
            db.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 9, 19 * 60), name = "Comezón / se rasca mucho", intensity = 5))
            db.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 40, 9 * 60), name = "Cojera", intensity = 6, regionId = "pet_carpo", regionLabel = "Carpo (muñeca)"))
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = t - 360, name = "Rabia", dose = "Anual", nextDueEpochDay = t + 5, place = "Veterinaria Huellitas", lot = "RB-2231"))
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = t - 330, name = "Múltiple (séxtuple)", dose = "Refuerzo", nextDueEpochDay = t + 35, place = "Veterinaria Huellitas"))
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = t - 100, type = "desp_interna", name = "Desparasitación interna", nextDueEpochDay = t - 10))
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = t - 25, type = "desp_externa", name = "Pipeta antipulgas", nextDueEpochDay = t + 5))
            db.medications().insert(Medication(name = "Omeprazol", dose = "10 mg", times = "09:00", startEpochDay = t - 1, endEpochDay = t + 4, instructions = "En ayunas"))
            db.appointments().insert(Appointment(timestamp = epochDayToMillis(t + 5, 11 * 60), title = "Vacuna anual", doctor = "Veterinaria Huellitas"))
            val st = db.studies().insert(Study(epochDay = t - 200, title = "Química sanguínea", category = "lab", place = "Laboratorio veterinario", status = "normal"))
            db.studies().insertResults(listOf(StudyResult(studyId = st, name = "Creatinina", value = 1.1, unit = "mg/dL", refMin = 0.5, refMax = 1.8)))
        }
        Pair(kid, dog)
    }
}
