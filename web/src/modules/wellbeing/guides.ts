import { SelfExamTypes } from '../../data/catalogs'
import type { SelfExam } from '../../data/types'

export interface SelfExamGuide { whenToDo: string; steps: string[]; consultIf: string; note: string }

const guides: Record<string, SelfExamGuide> = {
  mama: {
    whenToDo: 'Una vez al mes. Si menstrúas, unos días después de que termine el periodo (las mamas están menos sensibles); si no, elige un día fijo.',
    steps: [
      'Frente al espejo, con los brazos a los lados, luego en alto y después con las manos en la cintura, observa si hay cambios de forma o tamaño, piel hundida, enrojecida o con aspecto de cáscara de naranja, o cambios en el pezón.',
      'En la regadera o recostándote, usa las yemas de los tres dedos medios y recorre toda la mama con pequeños círculos, con presión suave, media y firme.',
      'Cubre toda la zona: de la clavícula a la parte baja de la mama y del esternón a la axila. Revisa también la axila.',
      'Presiona suavemente el pezón y observa si sale alguna secreción.',
    ],
    consultIf: 'Un bulto o endurecimiento nuevo, cambios en la piel o en el pezón, secreción, o dolor persistente en un punto.',
    note: 'La mayoría de los bultos no son graves, pero siempre conviene revisarlos. La autoexploración no sustituye la revisión clínica ni los estudios (mastografía o ultrasonido) que te indiquen.',
  },
  testicular: {
    whenToDo: 'Una vez al mes, durante o después de un baño caliente, cuando la piel del escroto está relajada.',
    steps: [
      'Revisa un testículo a la vez, usando ambas manos.',
      'Hazlo rodar suavemente entre el pulgar y los demás dedos. Busca bultos duros o cambios de tamaño, forma o consistencia.',
      'Es normal que uno sea un poco más grande o cuelgue más, y sentir atrás un conducto blando (el epidídimo).',
      'Compara con lo que sentiste el mes anterior.',
    ],
    consultIf: 'Un bulto (aunque no duela), aumento de tamaño, sensación de pesadez, o dolor sordo en la ingle o en la parte baja del abdomen.',
    note: 'Detectar cambios a tiempo hace una gran diferencia. Ante cualquier duda, pide una valoración médica.',
  },
  piel: {
    whenToDo: 'Una vez al mes, con buena luz, un espejo de cuerpo completo y uno de mano.',
    steps: [
      'Revisa todo el cuerpo: cuero cabelludo, cara, cuello, espalda, axilas, entre los dedos, plantas de los pies y uñas.',
      'En cada lunar o mancha aplica la regla ABCDE:',
      'A · Asimetría: una mitad no se parece a la otra.',
      'B · Bordes: irregulares, borrosos o dentados.',
      'C · Color: disparejo, con varios tonos (café, negro, rojo, blanco o azul).',
      'D · Diámetro: mayor de 6 mm (como la goma de un lápiz).',
      'E · Evolución: cambia de tamaño, forma o color, da comezón o sangra.',
    ],
    consultIf: 'Un lunar que cumple alguna letra del ABCDE, uno nuevo que se ve distinto a los demás, o una herida que no cicatriza.',
    note: 'Tomar fotos de tus lunares te ayuda a comparar con el tiempo. Solo un profesional puede valorar una lesión.',
  },
}

/** Guías breves y prudentes de autoexploración (orientativas; no sustituyen la revisión médica). */
export const SelfExamGuides = {
  /** Días tras los cuales se sugiere repetir la autoexploración. */
  REPEAT_DAYS: 30,

  of: (type: string): SelfExamGuide | undefined => guides[type],

  /** Título completo, p. ej. "Autoexploración mamaria". */
  title: (type: string): string =>
    type === 'mama' ? 'Autoexploración mamaria' : type === 'testicular' ? 'Autoexploración testicular' : type === 'piel' ? 'Revisión de piel y lunares' : SelfExamTypes.label(type),

  /** Tipos ordenados con el más relevante según el sexo del perfil primero (todos disponibles para cualquiera). */
  orderedTypes(sex: string | null | undefined): string[] {
    const all = SelfExamTypes.all.map((o) => o[0])
    const first = sex === 'F' ? 'mama' : sex === 'M' ? 'testicular' : null
    return first === null ? all : [first, ...all.filter((t) => t !== first)]
  },

  /** Última fecha registrada por tipo. */
  lastByType(exams: SelfExam[]): Record<string, number> {
    const out: Record<string, number> = {}
    for (const e of exams) out[e.type] = Math.max(out[e.type] ?? -Infinity, e.epochDay)
    return out
  },

  /** ¿Toca repetir este tipo? (solo si ya hay al menos un registro). */
  isDue: (lastDay: number | null | undefined, today: number): boolean => lastDay != null && today - lastDay >= SelfExamGuides.REPEAT_DAYS,

  /** Fecha sugerida de la próxima revisión (un mes después de la última), o null si aún no hay registros. */
  nextSuggested: (lastDay: number | null | undefined): number | null => (lastDay == null ? null : lastDay + SelfExamGuides.REPEAT_DAYS),

  /** Los renglones "A · …" de la regla ABCDE no se numeran. */
  isLettered: (step: string): boolean => step.length > 3 && step[1] === ' ' && step[2] === '·',
}
