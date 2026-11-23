// Textos del módulo de medicamentos y citas según el tipo de perfil activo (persona o mascota).
// La lógica es la misma para ambos; solo cambian etiquetas y ejemplos.
export interface MedsText {
  isPet: boolean
  appointmentsTitle: string
  appointmentDefault: string
  appointmentsEmpty: string
  reasonPlaceholder: string
  doctorLabel: string
  doctorPlaceholder: string
  placePlaceholder: string
  notesPlaceholder: string
  medsEmpty: string
  noScheduledDoses: string
  medNamePlaceholder: string
  dosePlaceholder: string
  instructionsPlaceholder: string
  inactiveHint: string
  /** Quién indicó el tratamiento. */
  prescriber: string
  medNotesPlaceholder: string
}

export function medsText(isPet: boolean): MedsText {
  return isPet
    ? {
        isPet,
        appointmentsTitle: 'Citas veterinarias',
        appointmentDefault: 'Cita veterinaria',
        appointmentsEmpty: 'Anota sus consultas, vacunas y estudios programados. Puedes pasarlas al Calendario del iPhone para que te avise.',
        reasonPlaceholder: 'p. ej. Vacuna anual, revisión, desparasitación',
        doctorLabel: 'Veterinaria o veterinario',
        doctorPlaceholder: 'p. ej. MVZ Rivera',
        placePlaceholder: 'Clínica u hospital veterinario',
        notesPlaceholder: 'Cambios que has notado, dudas, cartilla o estudios que debes llevar…',
        medsEmpty: 'Agrega sus medicamentos o tratamientos para llevar el control de las tomas.',
        noScheduledDoses: 'No hay tomas con horario. Registra abajo las que le des según se necesiten.',
        medNamePlaceholder: 'p. ej. Amoxicilina, antipulgas',
        dosePlaceholder: 'p. ej. 1/2 tableta, 2 ml, 1 pipeta',
        instructionsPlaceholder: 'p. ej. con la comida, escondido en un premio',
        inactiveHint: 'Desactívalo cuando termine el tratamiento; su historial se conserva',
        prescriber: 'tu veterinaria o veterinario',
        medNotesPlaceholder: 'p. ej. quién lo recetó, cómo lo tolera',
      }
    : {
        isPet,
        appointmentsTitle: 'Citas médicas',
        appointmentDefault: 'Cita médica',
        appointmentsEmpty: 'Anota tus consultas y estudios programados. Puedes pasarlas al Calendario del iPhone para que te avise.',
        reasonPlaceholder: 'p. ej. Revisión anual',
        doctorLabel: 'Profesional de salud',
        doctorPlaceholder: 'p. ej. Dra. Rivera',
        placePlaceholder: 'Consultorio, clínica u hospital',
        notesPlaceholder: 'Síntomas que quieres comentar, dudas, estudios que debes llevar…',
        medsEmpty: 'Agrega tus medicamentos o tratamientos para llevar el control de tus tomas.',
        noScheduledDoses: 'No tienes tomas con horario. Registra abajo las que tomes según las necesites.',
        medNamePlaceholder: 'p. ej. Loratadina',
        dosePlaceholder: 'p. ej. 500 mg, 1 tableta, 5 ml',
        instructionsPlaceholder: 'p. ej. con alimentos',
        inactiveHint: 'Desactívalo si dejaste de tomarlo; su historial se conserva',
        prescriber: 'tu profesional de salud',
        medNotesPlaceholder: 'p. ej. quién lo recetó, efectos que notes',
      }
}

/** Por qué Savia usa el calendario en lugar de notificaciones propias. */
export const CALENDAR_NOTE =
  'Una app web en iPhone no puede programar avisos por sí sola. Por eso Savia te da un archivo de calendario: ' +
  'al abrirlo, el Calendario del iPhone guarda el evento y es él quien te avisa a la hora.'
