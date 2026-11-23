package app.savia

import androidx.navigation.compose.rememberNavController
import app.savia.data.Appointment
import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.data.Profile
import app.savia.data.Study
import app.savia.data.StudyResult
import app.savia.ui.meds.AppointmentEditScreen
import app.savia.ui.meds.AppointmentsScreen
import app.savia.ui.meds.MedEditScreen
import app.savia.ui.meds.MedsScreen
import app.savia.ui.studies.StudiesScreen
import app.savia.ui.studies.StudyDetailScreen
import app.savia.ui.studies.StudyEditScreen
import app.savia.util.epochDayToMillis
import app.savia.util.today
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.annotation.Config

/** Capturas de estudios, medicamentos y citas con un perfil de MASCOTA activo (app/build/shots/pets_*.png). */
class PetsStudiesShots : ScreenshotBase() {

    /** Crea a Firulais, lo activa y siembra datos; devuelve (id del estudio con resultados, id de la cita). */
    private fun seedPet(): Pair<Long, Long> = runBlocking {
        PetsStudiesTest.createAndActivate(Profile(name = "Firulais", kind = "pet", species = "perro", sex = "M"))
        val db = Savia.db
        val t = today()
        val lab = db.studies().insert(
            Study(epochDay = t - 12, title = "Química sanguínea", category = "lab", place = "Clínica Patitas", doctor = "MVZ Rivera",
                status = "seguimiento", summary = "Creatinina en el límite alto; repetir en 3 meses.", followUpEpochDay = t + 78),
        )
        db.studies().insertResults(
            listOf(
                StudyResult(studyId = lab, name = "Glucosa", value = 96.0, unit = "mg/dL", refMin = 65.0, refMax = 118.0),
                StudyResult(studyId = lab, name = "Creatinina", value = 1.5, unit = "mg/dL", refMin = 0.5, refMax = 1.5),
                StudyResult(studyId = lab, name = "TGP (ALT)", value = 132.0, unit = "U/L", refMin = 10.0, refMax = 118.0),
            ),
        )
        db.studies().insert(Study(epochDay = t - 12, title = "Consulta veterinaria", category = "chequeo", place = "Clínica Patitas", doctor = "MVZ Rivera", status = "normal"))
        db.studies().insert(Study(epochDay = t - 200, title = "Radiografía de cadera", category = "imagen", place = "Hospital Veterinario del Sur", status = "normal"))
        db.studies().insert(Study(epochDay = t - 380, title = "Rabia", category = "vacuna", place = "Clínica Patitas"))

        val med = db.medications().insert(Medication(name = "Amoxicilina", dose = "1/2 tableta", times = "08:00,20:00", startEpochDay = t - 3, endEpochDay = t + 4, instructions = "Con la comida"))
        db.medications().insert(Medication(name = "Antipulgas", dose = "1 pipeta", times = "", startEpochDay = t - 60, remind = false))
        db.medications().insertIntake(MedIntake(medicationId = med, epochDay = t, slot = "08:00", timestamp = epochDayToMillis(t, 8 * 60 + 4)))

        val appt = db.appointments().insert(Appointment(timestamp = epochDayToMillis(t + 9, 17 * 60), title = "Vacuna anual", doctor = "MVZ Rivera", place = "Clínica Patitas"))
        db.appointments().insert(Appointment(timestamp = epochDayToMillis(t - 12, 11 * 60), title = "Revisión", doctor = "MVZ Rivera", done = true))
        lab to appt
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1400dp-xhdpi")
    fun studiesList() {
        seedPet()
        shotWhen("pets_studies_list", "Consulta o revisión") { StudiesScreen(rememberNavController()) }
    }

    @Test
    fun studiesListEmpty() {
        PetsStudiesTest.createAndActivate(Profile(name = "Garfield", kind = "pet", species = "gato"))
        shotWhen("pets_studies_empty", "consultas veterinarias") { StudiesScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2300dp-xhdpi")
    fun studyEditNew() {
        seedPet()
        shotWhen("pets_study_edit_new", "Veterinaria o veterinario") { StudyEditScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h3000dp-xhdpi")
    fun studyEditExisting() {
        val (lab, _) = seedPet()
        shotWhen("pets_study_edit_existing", "TGP (ALT)") { StudyEditScreen(rememberNavController(), lab) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun studyDetailDark() {
        val (lab, _) = seedPet()
        shotWhen("pets_study_detail_dark", "TGP (ALT)", dark = true) { StudyDetailScreen(rememberNavController(), lab) }
    }

    /** Persona con un estudio antiguo de categoría "Vacuna": se sigue viendo, con el aviso hacia la sección nueva. */
    @Test
    fun vaccineStudyShowsMovedNote() {
        DemoData.seed()
        val id = runBlocking { Savia.db.studies().getAll().first { it.category == "vacuna" }.id }
        shotWhen("studies_vaccine_moved_note", "Las vacunas ahora tienen su propia sección") { StudyDetailScreen(rememberNavController(), id) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun medsList() {
        seedPet()
        shotWhen("pets_meds_list", "Citas veterinarias") { MedsScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
    fun medEditNew() {
        seedPet()
        shotWhen("pets_med_edit_new", "cuando termine el tratamiento") { MedEditScreen(rememberNavController(), null) }
    }

    @Test
    fun appointments() {
        seedPet()
        shotWhen("pets_appointments", "Vacuna anual") { AppointmentsScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun appointmentEditNew() {
        seedPet()
        shotWhen("pets_appointment_edit_new", "Veterinaria o veterinario") { AppointmentEditScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun appointmentEditExisting() {
        val (_, appt) = seedPet()
        shotWhen("pets_appointment_edit", "MVZ Rivera") { AppointmentEditScreen(rememberNavController(), appt) }
    }
}
