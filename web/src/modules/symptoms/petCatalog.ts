/**
 * Síntomas frecuentes en mascotas para sugerir al registrar (agrupados).
 * Se describen como lo que la persona OBSERVA en su animal, no como un diagnóstico.
 */
export const PetSymptomCatalog = {
  groups: [
    ['General', ['Decaimiento', 'No quiere comer', 'Fiebre', 'Bebe mucha agua', 'Bebe poca agua', 'Pérdida de peso', 'Aumento de peso', 'Temblores', 'Duerme más de lo normal']],
    ['Digestivo', ['Vómito', 'Diarrea', 'Estreñimiento', 'Gases', 'Sangre en heces', 'Panza inflamada', 'Arcadas sin vomitar', 'Come pasto u objetos']],
    ['Piel y pelaje', ['Comezón / se rasca', 'Caída de pelo', 'Herida', 'Pulgas o garrapatas', 'Bulto o bolita', 'Piel enrojecida', 'Costras o caspa', 'Se lame mucho una zona', 'Mal olor en la piel']],
    ['Movimiento', ['Cojera', 'Dolor al moverse', 'Rigidez', 'No quiere subir o brincar', 'Debilidad en las patas', 'Pierde el equilibrio', 'Arrastra el trasero']],
    ['Ojos, oídos y boca', ['Lagañas', 'Ojo rojo', 'Ojo cerrado o entrecerrado', 'Sacude la cabeza', 'Se rasca la oreja', 'Mal olor en el oído', 'Mal aliento', 'Babeo', 'Le cuesta masticar', 'Encías pálidas']],
    ['Respiratorio', ['Tos', 'Estornudos', 'Respiración agitada', 'Secreción nasal', 'Jadeo excesivo', 'Ronquido o silbido al respirar']],
    ['Urinario', ['Orina mucho', 'Orina poco', 'Sangre en la orina', 'Le cuesta orinar', 'Se orina dentro de casa', 'Se lame los genitales']],
    ['Conducta', ['Ansiedad', 'Agresividad', 'Se esconde', 'Llora o se queja', 'Desorientación', 'Inquietud por la noche']],
  ] as readonly (readonly [string, readonly string[]])[],

  /** Posibles desencadenantes para etiquetar lo que se observó. */
  triggers: ['Cambio de alimento', 'Comió algo raro', 'Paseo / parque', 'Baño', 'Calor', 'Estrés / viaje', 'Otros animales', 'Medicamento', 'Vacuna reciente', 'Golpe o caída'],

  /** Intensidad de la molestia tal como se observa en el animal (1..10). */
  intensityLabel: (i: number): string => (i <= 2 ? 'Apenas se nota' : i <= 4 ? 'Leve' : i <= 6 ? 'Moderado' : i <= 8 ? 'Fuerte' : 'Muy fuerte'),

  /** Señales por las que conviene acudir a urgencias veterinarias sin esperar (orientativo). */
  alarmSigns: [
    'le cuesta respirar o tiene las encías azuladas o muy pálidas',
    'tiene la panza inflamada y dura, o intenta vomitar sin sacar nada',
    'no puede orinar (sobre todo en gatos machos)',
    'convulsiona, se desmaya o no puede levantarse',
    'sangra y no para, o tuvo un golpe fuerte o un atropello',
    'pudo comer algo tóxico (chocolate, uvas, veneno, medicamentos)',
    'vomita o tiene diarrea muchas veces en un día, o con sangre',
  ],

  /** Aviso breve cuando se registra una molestia muy fuerte. */
  ALARM_NOTE:
    'Si lo ves muy mal, empeora rápido o no cede, llévalo a tu veterinaria o veterinario o a urgencias veterinarias. ' +
    'Esta app no sustituye una valoración profesional.',

  /** Lista de señales de alarma en una sola frase, para una nota informativa. */
  get alarmSummary(): string {
    return 'Acude a urgencias veterinarias si ' + PetSymptomCatalog.alarmSigns.slice(0, 4).join('; ') + '.'
  },

  /** Especies para las que existe figura en el atlas corporal ("Elegir en el cuerpo"). */
  hasBodyMap: (species: string): boolean => species === 'perro' || species === 'gato',

  get all(): string[] {
    return PetSymptomCatalog.groups.flatMap((g) => [...g[1]])
  },
}
