// Textos del atlas: nombre, descripción, huesos (b), músculos (m), órganos/estructuras (o),
// cuándo conviene consultar (t) y vista relacionada (link). Contenido educativo, no diagnóstico.

const E = 'Si el dolor es muy intenso, aparece de golpe o viene con fiebre alta, falta de aire, desmayo o sangrado, busca atención médica de inmediato.';

/** Entradas base. g = género gramatical del nombre ('m'/'f') para "derecho/derecha". side=false → sin lado. */
export const BASE = {
  // ── Cabeza y cuello ──
  cabeza: { n: 'Cabeza (frente y cráneo)', d: 'Parte superior de la cabeza: frente, sienes y cuero cabelludo. Debajo está el cráneo, que protege al cerebro.', b: ['Frontal', 'Parietales', 'Temporales'], m: ['Frontal', 'Temporal'], o: ['Cerebro', 'Senos frontales'], t: 'Un dolor de cabeza súbito y muy intenso ("el peor de tu vida"), o acompañado de debilidad, dificultad para hablar, visión doble o rigidez de cuello, es una urgencia.' },
  cara: { n: 'Cara (ojos, nariz y mejillas)', d: 'Zona media de la cara: ojos, nariz, pómulos y senos paranasales.', b: ['Cigomático (pómulo)', 'Maxilar', 'Huesos nasales', 'Órbitas'], m: ['Orbicular de los ojos', 'Cigomáticos', 'Masetero'], o: ['Ojos', 'Nariz', 'Senos maxilares'], t: 'Pérdida repentina de visión, dolor intenso en un ojo o caída de un lado de la cara requieren atención urgente.' },
  mandibula: { n: 'Boca y mandíbula', d: 'Boca, labios, dientes, mentón y la articulación de la mandíbula (ATM), que está justo delante del oído.', b: ['Mandíbula', 'Maxilar'], m: ['Masetero', 'Orbicular de los labios', 'Pterigoideos'], o: ['Dientes y encías', 'Lengua', 'Glándulas salivales'], t: 'Un dolor en la mandíbula junto con dolor u opresión en el pecho puede ser señal de un problema del corazón: busca atención de inmediato.' },
  oido: { n: 'Oído', g: 'm', side: true, d: 'Oreja y conducto auditivo. Dentro están el tímpano, el oído medio y el oído interno (audición y equilibrio).', b: ['Temporal', 'Martillo, yunque y estribo'], o: ['Tímpano', 'Cóclea', 'Órgano del equilibrio'], t: 'Consulta si hay dolor con fiebre, salida de líquido, pérdida repentina de audición o vértigo intenso.' },
  cuello: { n: 'Cuello y garganta', d: 'Parte frontal del cuello: garganta, laringe (cuerdas vocales), tráquea, tiroides y ganglios.', b: ['Vértebras cervicales', 'Hioides'], m: ['Esternocleidomastoideo', 'Escalenos'], o: ['Laringe', 'Tráquea', 'Esófago', 'Tiroides', 'Ganglios linfáticos'], t: 'Dificultad para respirar o tragar, o una hinchazón que crece rápido, necesitan atención urgente.' },
  cabeza_post: { n: 'Cabeza (parte posterior)', d: 'Coronilla y parte de atrás de la cabeza (región occipital).', b: ['Occipital', 'Parietales'], m: ['Occipital'], o: ['Cerebro', 'Cerebelo'], t: 'Después de un golpe en la cabeza, vigila somnolencia, vómito repetido o confusión: son motivo de urgencia.' },
  nuca: { n: 'Nuca y cuello posterior', d: 'Parte de atrás del cuello. Aquí se acumula mucha tensión muscular por postura y estrés.', b: ['Vértebras cervicales (C1–C7)'], m: ['Trapecio (porción superior)', 'Esplenios', 'Elevador de la escápula'], o: ['Médula espinal cervical'], t: 'Rigidez de nuca con fiebre y dolor de cabeza intenso es una urgencia.' },
  // ── Tronco frontal ──
  pecho: { n: 'Pecho', g: 'm', side: true, d: 'Región pectoral: mama, músculo pectoral y costillas. Detrás están los pulmones (y el corazón, un poco hacia la izquierda).', b: ['Costillas', 'Clavícula'], m: ['Pectoral mayor', 'Pectoral menor', 'Intercostales', 'Serrato anterior'], o: ['Pulmón', 'Mama', 'Corazón (lado izquierdo)'], t: 'Dolor u opresión en el pecho que se extiende al brazo, cuello o mandíbula, con sudor frío, náuseas o falta de aire: llama a emergencias de inmediato.', link: 'organs' },
  esternon: { n: 'Centro del pecho (esternón)', d: 'Hueso plano en el centro del pecho donde se unen las costillas. Detrás están el corazón, la tráquea y el esófago.', b: ['Esternón', 'Cartílagos costales'], o: ['Corazón', 'Esófago', 'Tráquea', 'Timo'], t: 'Opresión en el centro del pecho con falta de aire, sudoración o dolor que se extiende al brazo o mandíbula es una emergencia. El ardor que sube tras comer suele ser reflujo.', link: 'organs' },
  abd_hipocondrio: { n: 'Hipocondrio', g: 'm', side: true, d: 'Parte alta y lateral del abdomen, bajo las costillas.', o: { der: ['Hígado', 'Vesícula biliar', 'Ángulo derecho del colon', 'Parte del riñón derecho'], izq: ['Bazo', 'Parte del estómago', 'Cola del páncreas', 'Ángulo izquierdo del colon', 'Parte del riñón izquierdo'] }, m: ['Oblicuos', 'Diafragma'], t: { der: 'Dolor aquí después de comidas grasosas, a veces hacia la espalda o el hombro derecho, puede venir de la vesícula. Con fiebre o piel/ojos amarillos, consulta pronto.', izq: 'Dolor intenso aquí después de un golpe requiere valoración urgente (bazo).' }, link: 'organs' },
  abd_epigastrio: { n: 'Epigastrio (boca del estómago)', d: 'Parte alta y central del abdomen, justo debajo del esternón.', o: ['Estómago', 'Parte del hígado', 'Páncreas', 'Duodeno', 'Aorta abdominal'], m: ['Recto abdominal'], t: 'El ardor o dolor aquí suele relacionarse con gastritis o reflujo. Si es muy intenso, se va hacia la espalda, o hay vómito con sangre o evacuaciones negras, busca atención urgente.', link: 'organs' },
  abd_flanco: { n: 'Flanco', g: 'm', side: true, d: 'Costado del abdomen, a la altura de la cintura.', o: { der: ['Colon ascendente', 'Riñón derecho', 'Intestino delgado'], izq: ['Colon descendente', 'Riñón izquierdo', 'Intestino delgado'] }, m: ['Oblicuo externo', 'Oblicuo interno', 'Transverso'], t: 'Dolor tipo cólico que va del costado hacia la ingle, a veces con sangre en la orina, puede ser un cálculo renal. Con fiebre y ardor al orinar, consulta pronto.', link: 'organs' },
  abd_mesogastrio: { n: 'Mesogastrio (región umbilical)', d: 'Centro del abdomen, alrededor del ombligo.', o: ['Intestino delgado', 'Colon transverso', 'Aorta abdominal'], m: ['Recto abdominal'], t: 'El dolor que empieza alrededor del ombligo y luego se mueve hacia abajo a la derecha puede ser apendicitis: no lo dejes pasar.', link: 'organs' },
  abd_fosa_iliaca: { n: 'Fosa ilíaca', g: 'f', side: true, d: 'Parte baja y lateral del abdomen, junto al hueso de la cadera.', o: { der: ['Apéndice', 'Ciego (inicio del colon)', 'Final del intestino delgado', 'Ovario y trompa derechos (en mujeres)'], izq: ['Colon sigmoide', 'Ovario y trompa izquierdos (en mujeres)'] }, m: ['Oblicuos', 'Psoas ilíaco'], t: { der: 'Dolor que aumenta al caminar, toser o al soltar la presión, con fiebre, náuseas o falta de apetito, puede ser apendicitis: acude a urgencias.', izq: 'Dolor con fiebre y cambios al evacuar puede venir del colon (diverticulitis). En mujeres, un dolor súbito e intenso puede ser de origen ginecológico: consulta pronto.' }, link: 'organs' },
  abd_hipogastrio: { n: 'Hipogastrio (bajo vientre)', d: 'Parte baja y central del abdomen, por encima del pubis.', o: ['Vejiga', 'Intestino delgado', 'Recto', 'Útero (en mujeres)', 'Próstata (en hombres)'], m: ['Recto abdominal', 'Piramidal'], t: 'Dolor con ardor al orinar o ganas frecuentes sugiere infección urinaria. Los cólicos menstruales también se sienten aquí; si son incapacitantes, vale la pena consultarlo.', link: 'organs' },
  ingle: { n: 'Ingle', g: 'f', side: true, d: 'Pliegue entre el abdomen y el muslo. Por aquí pasan vasos, nervios y ganglios, y es zona frecuente de hernias.', b: ['Pubis', 'Cabeza del fémur (articulación de la cadera)'], m: ['Aductores', 'Psoas ilíaco'], o: ['Ganglios inguinales', 'Arteria y vena femoral', 'Conducto inguinal'], t: 'Un bulto que aparece al hacer esfuerzo puede ser una hernia. Si el bulto duele mucho, se pone duro y no regresa, es una urgencia.' },
  pubis: { n: 'Pubis y genitales', d: 'Región púbica y genitales externos.', b: ['Pubis (sínfisis púbica)'], o: ['Vejiga y uretra', 'Genitales'], t: 'En hombres, un dolor testicular súbito e intenso es una urgencia. Consulta ante ardor, secreciones, llagas o bultos.' },
  // ── Espalda ──
  espalda_alta: { n: 'Espalda alta', g: 'f', side: true, d: 'Zona del omóplato (escápula) y parte alta de la espalda.', b: ['Escápula', 'Costillas', 'Vértebras dorsales'], m: ['Trapecio', 'Romboides', 'Infraespinoso', 'Dorsal ancho'], o: ['Pulmón (por dentro)'], t: 'Suele doler por tensión o mala postura. Un dolor súbito "desgarrante" entre los omóplatos con falta de aire es una urgencia.' },
  espalda_media: { n: 'Espalda media', g: 'f', side: true, d: 'Parte baja de las costillas por detrás.', b: ['Costillas inferiores', 'Vértebras dorsales bajas'], m: ['Dorsal ancho', 'Erectores de la columna', 'Serrato posterior'], o: ['Base del pulmón', 'Riñón (parte alta)'] },
  columna_dorsal: { n: 'Columna dorsal', d: 'Tramo de la columna a la altura del tórax (12 vértebras), donde se articulan las costillas.', b: ['Vértebras dorsales o torácicas (T1–T12)'], m: ['Erectores de la columna'], o: ['Médula espinal'], link: 'skeleton' },
  lumbar: { n: 'Zona lumbar', g: 'f', side: true, d: 'Espalda baja, entre las costillas y la cadera. Es la zona de dolor de espalda más frecuente.', b: ['Vértebras lumbares', 'Cresta ilíaca'], m: ['Cuadrado lumbar', 'Erectores de la columna', 'Dorsal ancho'], o: ['Riñón'], t: 'Consulta pronto si el dolor baja por la pierna con adormecimiento o debilidad, o si hay fiebre. Pérdida de control para orinar o evacuar es una urgencia.' },
  columna_lumbar: { n: 'Columna lumbar', d: 'Las 5 vértebras de la espalda baja; soportan la mayor parte del peso del cuerpo.', b: ['Vértebras lumbares (L1–L5)', 'Discos intervertebrales'], m: ['Multífidos', 'Erectores de la columna'], o: ['Raíces nerviosas (nervio ciático)'], t: 'Adormecimiento en la zona genital o de la silla de montar, o pérdida de control de esfínteres, requieren atención urgente.', link: 'skeleton' },
  sacro: { n: 'Sacro y cóccix', d: 'Base de la columna, entre los glúteos. El cóccix es la "colita".', b: ['Sacro', 'Cóccix', 'Articulaciones sacroilíacas'], m: ['Glúteo mayor (inserción)', 'Piso pélvico'] },
  gluteo: { n: 'Glúteo', g: 'm', side: true, d: 'Nalga. Debajo de los músculos glúteos pasa el nervio ciático.', b: ['Ilíaco', 'Isquion', 'Cabeza del fémur'], m: ['Glúteo mayor', 'Glúteo medio', 'Glúteo menor', 'Piriforme'], o: ['Nervio ciático'], t: 'El dolor que baja del glúteo por detrás de la pierna suele ser ciática.' },
  // ── Brazo ──
  hombro: { n: 'Hombro', g: 'm', side: true, d: 'Articulación más móvil del cuerpo: une el brazo con el tronco.', b: ['Húmero (cabeza)', 'Escápula (acromion)', 'Clavícula'], m: ['Deltoides', 'Manguito rotador (supraespinoso, infraespinoso, subescapular, redondo menor)'], t: 'Dolor en el hombro o brazo izquierdo junto con opresión en el pecho puede ser del corazón: emergencia.', link: 'skeleton' },
  brazo: { n: 'Brazo', g: 'm', side: true, d: 'Del hombro al codo (cara anterior).', b: ['Húmero'], m: ['Bíceps braquial', 'Braquial'], link: 'skeleton' },
  codo: { n: 'Codo', g: 'm', side: true, d: 'Articulación entre el brazo y el antebrazo (pliegue anterior).', b: ['Húmero', 'Radio', 'Cúbito'], m: ['Bíceps (tendón)', 'Braquiorradial', 'Flexores del antebrazo'], t: 'El dolor en el lado externo se conoce como "codo de tenista"; en el interno, "codo de golfista".', link: 'skeleton' },
  antebrazo: { n: 'Antebrazo', g: 'm', side: true, d: 'Del codo a la muñeca.', b: ['Radio (lado del pulgar)', 'Cúbito (lado del meñique)'], m: ['Flexores de la muñeca y los dedos', 'Pronadores'], link: 'skeleton' },
  muneca: { n: 'Muñeca', g: 'f', side: true, d: 'Une el antebrazo con la mano. Por el túnel del carpo pasan los tendones y el nervio mediano.', b: ['Radio y cúbito (extremo)', 'Huesos del carpo'], o: ['Nervio mediano', 'Túnel del carpo'], t: 'Hormigueo en pulgar, índice y medio, sobre todo de noche, sugiere síndrome del túnel del carpo.', link: 'hand' },
  mano: { n: 'Mano', g: 'f', side: true, d: 'Palma y dedos. Toca "Ver detalle" para conocer el nombre de cada dedo y hueso.', b: ['Carpo (8 huesos)', 'Metacarpianos (5)', 'Falanges (14)'], m: ['Músculos tenares e hipotenares', 'Interóseos', 'Lumbricales'], link: 'hand' },
  hombro_post: { n: 'Hombro (parte posterior)', g: 'm', side: true, sideMid: 'Hombro', d: 'Cara posterior del hombro.', b: ['Escápula', 'Húmero'], m: ['Deltoides posterior', 'Infraespinoso', 'Redondo menor'] },
  brazo_post: { n: 'Brazo (parte posterior)', g: 'm', side: true, sideMid: 'Brazo', d: 'Parte de atrás del brazo.', b: ['Húmero'], m: ['Tríceps braquial'] },
  codo_post: { n: 'Codo (punta)', g: 'm', side: true, sideMid: 'Codo', d: 'Punta del codo (olécranon). Por el canal interno pasa el nervio cubital: el "huesito de la risa".', b: ['Olécranon (cúbito)', 'Húmero'], m: ['Tríceps (tendón)'], o: ['Nervio cubital', 'Bolsa del olécranon'] },
  antebrazo_post: { n: 'Antebrazo (parte posterior)', g: 'm', side: true, sideMid: 'Antebrazo', d: 'Dorso del antebrazo.', b: ['Radio', 'Cúbito'], m: ['Extensores de la muñeca y los dedos'] },
  muneca_post: { n: 'Muñeca (dorso)', g: 'f', side: true, sideMid: 'Muñeca', d: 'Parte de atrás de la muñeca.', b: ['Huesos del carpo', 'Radio y cúbito'], o: ['Tendones extensores'], link: 'hand' },
  mano_dorso: { n: 'Dorso de la mano', g: 'f', side: true, sideMid: 'Dorso de la mano', d: 'Parte de atrás de la mano y los nudillos.', b: ['Metacarpianos', 'Falanges'], o: ['Tendones extensores'], link: 'hand' },
  // ── Pierna ──
  muslo: { n: 'Muslo', g: 'm', side: true, d: 'De la cadera a la rodilla (cara anterior).', b: ['Fémur'], m: ['Cuádriceps (recto femoral y vastos)', 'Sartorio', 'Aductores'], link: 'skeleton' },
  rodilla: { n: 'Rodilla', g: 'f', side: true, d: 'Articulación más grande del cuerpo: une el fémur con la tibia.', b: ['Fémur', 'Tibia', 'Rótula'], m: ['Tendón del cuádriceps', 'Tendón rotuliano'], o: ['Meniscos', 'Ligamentos cruzados y colaterales'], t: 'Consulta si se hincha rápido tras una torcedura, se "traba" o no puedes apoyar la pierna.', link: 'skeleton' },
  pierna: { n: 'Pierna (espinilla)', g: 'f', side: true, sideMid: 'Pierna', d: 'De la rodilla al tobillo, por delante.', b: ['Tibia', 'Peroné'], m: ['Tibial anterior', 'Extensores de los dedos'], link: 'skeleton' },
  tobillo: { n: 'Tobillo', g: 'm', side: true, d: 'Une la pierna con el pie. Los "huesitos" a los lados son los maléolos.', b: ['Tibia (maléolo interno)', 'Peroné (maléolo externo)', 'Astrágalo'], o: ['Ligamentos del tobillo'], t: 'Tras una torcedura, consulta si no puedes dar cuatro pasos o hay dolor al tocar el hueso.', link: 'foot' },
  pie: { n: 'Pie', g: 'm', side: true, d: 'Empeine y dedos. Toca "Ver detalle" para conocer cada dedo y hueso.', b: ['Tarso (7 huesos)', 'Metatarsianos (5)', 'Falanges (14)'], m: ['Extensores de los dedos', 'Músculos intrínsecos del pie'], link: 'foot' },
  muslo_post: { n: 'Muslo (parte posterior)', g: 'm', side: true, sideMid: 'Muslo', d: 'Parte de atrás del muslo.', b: ['Fémur'], m: ['Isquiotibiales (bíceps femoral, semitendinoso, semimembranoso)'], o: ['Nervio ciático'] },
  corva: { n: 'Corva (detrás de la rodilla)', g: 'f', side: true, sideMid: 'Corva', d: 'Hueco poplíteo: por aquí pasan vasos y nervios hacia la pierna.', b: ['Fémur', 'Tibia'], m: ['Isquiotibiales (tendones)', 'Gemelos (origen)', 'Poplíteo'], o: ['Arteria y vena poplíteas'] },
  pantorrilla: { n: 'Pantorrilla', g: 'f', side: true, d: 'Parte de atrás de la pierna.', b: ['Tibia', 'Peroné'], m: ['Gemelos', 'Sóleo'], t: 'Dolor con hinchazón, calor o enrojecimiento en una sola pantorrilla (sobre todo tras reposo prolongado o un viaje largo) necesita valoración pronto: puede ser un coágulo.' },
  aquiles: { n: 'Tendón de Aquiles', g: 'm', side: true, d: 'Tendón grueso que une la pantorrilla con el talón.', b: ['Calcáneo', 'Tibia y peroné (maléolos)'], m: ['Gemelos y sóleo (tendón de Aquiles)'], t: 'Un "chasquido" súbito con dolor, como una pedrada, sugiere rotura del tendón: acude a valoración.' },
  talon: { n: 'Talón y planta', g: 'm', side: true, sideMid: 'Talón', d: 'Talón y planta del pie.', b: ['Calcáneo'], o: ['Fascia plantar', 'Almohadilla grasa del talón'], t: 'El dolor en el talón al dar los primeros pasos del día suele ser fascitis plantar.', link: 'foot' },
  // ── Esqueleto ──
  h_craneo: { n: 'Cráneo', d: 'Conjunto de 8 huesos que protegen el cerebro (frontal, 2 parietales, 2 temporales, occipital, esfenoides y etmoides), más los huesos de la cara.' },
  h_mandibula: { n: 'Mandíbula', d: 'Único hueso móvil de la cabeza; sostiene los dientes inferiores y se articula con el temporal (ATM).' },
  h_cervicales: { n: 'Vértebras cervicales', d: 'Las 7 vértebras del cuello (C1–C7). Las dos primeras, atlas y axis, permiten girar e inclinar la cabeza.' },
  h_dorsales: { n: 'Vértebras dorsales', d: 'Las 12 vértebras torácicas (T1–T12); cada una se articula con un par de costillas.' },
  h_lumbares: { n: 'Vértebras lumbares', d: 'Las 5 vértebras de la espalda baja (L1–L5), las más grandes y las que más carga soportan.' },
  h_sacro: { n: 'Sacro y cóccix', d: 'El sacro son 5 vértebras fusionadas que unen la columna con la pelvis; el cóccix (3 a 5 vértebras pequeñas) es el extremo final.' },
  h_esternon: { n: 'Esternón', d: 'Hueso plano del centro del pecho (manubrio, cuerpo y apófisis xifoides). Protege el corazón.' },
  h_costillas: { n: 'Costillas', g: 'f', side: true, sidePlural: true, d: 'Son 12 por lado: 7 verdaderas (se unen al esternón), 3 falsas (se unen por cartílago) y 2 flotantes. Protegen pulmones y corazón.' },
  h_clavicula: { n: 'Clavícula', g: 'f', side: true, d: 'Hueso largo y delgado que une el esternón con el hombro. Es de los que más se fracturan.' },
  h_escapula: { n: 'Escápula (omóplato)', g: 'f', side: true, sideMid: 'Escápula', d: 'Hueso plano y triangular de la espalda alta; se articula con el húmero y la clavícula.' },
  h_humero: { n: 'Húmero', g: 'm', side: true, d: 'Hueso del brazo, del hombro al codo.' },
  h_radio: { n: 'Radio', g: 'm', side: true, d: 'Hueso del antebrazo del lado del pulgar. Gira sobre el cúbito para voltear la palma.' },
  h_cubito: { n: 'Cúbito (ulna)', g: 'm', side: true, sideMid: 'Cúbito', d: 'Hueso del antebrazo del lado del meñique; su extremo forma la punta del codo (olécranon).' },
  h_mano: { n: 'Huesos de la mano', g: 'f', side: true, sideMid: 'Huesos de la mano', d: '27 huesos: 8 del carpo (muñeca), 5 metacarpianos (palma) y 14 falanges (dedos).', link: 'hand' },
  h_coxal: { n: 'Hueso coxal (cadera)', g: 'm', side: true, sideMid: 'Hueso coxal', d: 'Formado por ilion, isquion y pubis. Los dos coxales y el sacro forman la pelvis.' },
  h_femur: { n: 'Fémur', g: 'm', side: true, d: 'Hueso del muslo: el más largo y fuerte del cuerpo. Su cabeza encaja en la cadera.' },
  h_rotula: { n: 'Rótula (patela)', g: 'f', side: true, sideMid: 'Rótula', d: 'Hueso redondeado al frente de la rodilla, dentro del tendón del cuádriceps.' },
  h_tibia: { n: 'Tibia', g: 'f', side: true, d: 'Hueso principal de la pierna (la espinilla); soporta el peso del cuerpo.' },
  h_perone: { n: 'Peroné (fíbula)', g: 'm', side: true, sideMid: 'Peroné', d: 'Hueso delgado del lado externo de la pierna; su extremo forma el maléolo externo del tobillo.' },
  h_pie: { n: 'Huesos del pie', g: 'm', side: true, sideMid: 'Huesos del pie', d: '26 huesos: 7 del tarso (incluidos calcáneo y astrágalo), 5 metatarsianos y 14 falanges.', link: 'foot' },
  // ── Órganos ──
  o_cerebro: { n: 'Cerebro', d: 'Centro de control del cuerpo: pensamiento, memoria, movimiento, sentidos y emociones.', t: 'Debilidad o adormecimiento de un lado del cuerpo, cara caída o dificultad para hablar: llama a emergencias de inmediato.' },
  o_tiroides: { n: 'Tiroides', d: 'Glándula en forma de mariposa en el cuello; sus hormonas regulan el metabolismo, la energía y la temperatura.' },
  o_traquea: { n: 'Tráquea', d: 'Conducto que lleva el aire de la garganta a los bronquios y pulmones.' },
  o_pulmon: { n: 'Pulmón', g: 'm', side: true, d: 'Órgano donde la sangre toma oxígeno y libera dióxido de carbono. El derecho tiene 3 lóbulos y el izquierdo 2.', t: 'Falta de aire en reposo, labios azulados o dolor al respirar hondo requieren atención urgente.' },
  o_corazon: { n: 'Corazón', d: 'Músculo del tamaño de un puño que bombea sangre a todo el cuerpo. Está en el centro del pecho, ligeramente hacia la izquierda.', t: 'Dolor u opresión en el pecho, falta de aire, sudor frío o palpitaciones con desmayo: llama a emergencias.' },
  o_higado: { n: 'Hígado', d: 'Órgano más grande del interior del cuerpo. Filtra la sangre, produce bilis y almacena energía. Está arriba a la derecha, bajo las costillas.' },
  o_vesicula: { n: 'Vesícula biliar', d: 'Pequeña bolsa bajo el hígado que guarda la bilis para digerir las grasas. Puede formar cálculos ("piedras").' },
  o_estomago: { n: 'Estómago', d: 'Bolsa muscular que mezcla los alimentos con jugos gástricos. Está arriba, ligeramente a la izquierda.' },
  o_bazo: { n: 'Bazo', d: 'Órgano del sistema inmune que filtra la sangre. Está arriba a la izquierda, protegido por las costillas.' },
  o_pancreas: { n: 'Páncreas', d: 'Glándula detrás del estómago: produce enzimas digestivas e insulina, que regula el azúcar en la sangre.' },
  o_rinon: { n: 'Riñón', g: 'm', side: true, d: 'Filtra la sangre y produce la orina. Los riñones están hacia la espalda, a los lados de la columna (por eso aquí se muestran translúcidos).', t: 'Dolor en el costado con fiebre y ardor al orinar, o cólico muy intenso hacia la ingle, requieren consulta pronto.' },
  o_intestino_delgado: { n: 'Intestino delgado', d: 'Tubo de unos 6 metros (duodeno, yeyuno e íleon) donde se absorbe la mayor parte de los nutrientes.' },
  o_intestino_grueso: { n: 'Intestino grueso (colon)', d: 'Marco que rodea al intestino delgado: ciego, colon ascendente (derecha), transverso, descendente (izquierda), sigmoide y recto. Absorbe agua y forma las heces.' },
  o_apendice: { n: 'Apéndice', d: 'Pequeña prolongación del inicio del colon, abajo a la derecha. Su inflamación es la apendicitis.', t: 'Dolor que empieza cerca del ombligo y se fija abajo a la derecha, con fiebre o náuseas: acude a urgencias.' },
  o_vejiga: { n: 'Vejiga', d: 'Bolsa muscular que almacena la orina hasta que se expulsa por la uretra.' },
  o_utero: { n: 'Útero', d: 'Órgano muscular en forma de pera invertida donde se desarrolla el embarazo; su capa interna (endometrio) se desprende en cada menstruación.' },
  o_ovario: { n: 'Ovario', g: 'm', side: true, d: 'Glándula que libera los óvulos (ovulación) y produce estrógenos y progesterona. Se conecta al útero por la trompa de Falopio.' },
  o_prostata: { n: 'Próstata', d: 'Glándula del tamaño de una nuez bajo la vejiga; produce parte del líquido del semen. Con la edad puede crecer y dificultar la salida de la orina.' },
  o_testiculo: { n: 'Testículo', g: 'm', side: true, d: 'Glándula que produce espermatozoides y testosterona.', t: 'Un dolor súbito e intenso en un testículo es una urgencia. Consulta si notas un bulto o cambio de tamaño.' },
  // ── Mano ──
  m_muneca: { n: 'Muñeca', d: 'Une el antebrazo (radio y cúbito) con la mano a través de los 8 huesos del carpo.' },
  m_dorso: { n: 'Dorso de la mano', d: 'Parte de atrás de la mano. Debajo están los 5 metacarpianos; sus cabezas forman los nudillos.' },
  m_radio: { n: 'Radio', d: 'Hueso del antebrazo del lado del pulgar; es el que más carga recibe al caer sobre la mano.' },
  m_cubito: { n: 'Cúbito (ulna)', d: 'Hueso del antebrazo del lado del meñique.' },
  m_escafoides: { n: 'Escafoides', d: 'Hueso del carpo del lado del pulgar. Es el hueso de la muñeca que más se fractura al caer con la mano abierta.' },
  m_semilunar: { n: 'Semilunar', d: 'Hueso del carpo con forma de media luna, en el centro de la fila cercana al antebrazo.' },
  m_piramidal: { n: 'Piramidal', d: 'Hueso del carpo del lado del meñique, en la fila cercana al antebrazo.' },
  m_pisiforme: { n: 'Pisiforme', d: 'Hueso pequeño y redondo, como un chícharo, que se palpa en la base de la palma del lado del meñique.' },
  m_trapecio: { n: 'Trapecio', d: 'Hueso del carpo en la base del pulgar; su articulación le da al pulgar su gran movilidad.' },
  m_trapezoide: { n: 'Trapezoide', d: 'Hueso pequeño del carpo, en la base del dedo índice.' },
  m_grande: { n: 'Hueso grande (capitado)', d: 'El hueso más grande del carpo, en el centro de la muñeca, en la base del dedo medio.' },
  m_ganchoso: { n: 'Ganchoso', d: 'Hueso del carpo con un gancho hacia la palma, en la base del anular y el meñique.' },
  // ── Pie ──
  p_talon: { n: 'Talón', d: 'Parte posterior del pie; apoya sobre el calcáneo.' },
  p_empeine: { n: 'Empeine', d: 'Parte superior del pie, entre el tobillo y los dedos. Debajo están los huesos del tarso y los metatarsianos.' },
  p_calcaneo: { n: 'Calcáneo', d: 'Hueso del talón, el más grande del pie. En él se inserta el tendón de Aquiles.' },
  p_astragalo: { n: 'Astrágalo (talo)', d: 'Hueso que une el pie con la pierna: se articula con la tibia y el peroné para formar el tobillo.' },
  p_navicular: { n: 'Navicular (escafoides del pie)', d: 'Hueso del lado interno del pie, pieza clave del arco plantar.' },
  p_cuboides: { n: 'Cuboides', d: 'Hueso del lado externo del pie, delante del calcáneo.' },
  p_cuneiforme_medial: { n: 'Cuneiforme medial', d: 'Primera cuña: hueso en la base del dedo gordo.' },
  p_cuneiforme_intermedio: { n: 'Cuneiforme intermedio', d: 'Segunda cuña: la más pequeña, en la base del segundo dedo.' },
  p_cuneiforme_lateral: { n: 'Cuneiforme lateral', d: 'Tercera cuña: en la base del tercer dedo.' },
};

const FINGERS = { pulgar: 'pulgar', indice: 'índice', medio: 'medio', anular: 'anular', menique: 'meñique' };
const TOES = { gordo: 'dedo gordo (hallux)', segundo: 'segundo dedo', tercero: 'tercer dedo', cuarto: 'cuarto dedo', quinto: 'quinto dedo (dedo pequeño)' };
const ORD = ['', 'Primer', 'Segundo', 'Tercer', 'Cuarto', 'Quinto'];
const FINGER_OF = ['', 'pulgar', 'índice', 'medio', 'anular', 'meñique'];
const TOE_OF = ['', 'dedo gordo', 'segundo dedo', 'tercer dedo', 'cuarto dedo', 'quinto dedo'];
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

/** Información para un id de forma; null si no hay. */
export function infoFor(id) {
  let m;
  if ((m = id.match(/^m_dedo_(\w+)$/))) {
    const f = FINGERS[m[1]];
    const thumb = m[1] === 'pulgar';
    return { n: thumb ? 'Pulgar' : `Dedo ${f}`, d: thumb ? 'Primer dedo de la mano. Tiene solo dos falanges y puede oponerse a los demás dedos, lo que permite agarrar.' : `${cap(f)}: tiene tres falanges (proximal, media y distal) unidas por dos articulaciones.`, b: thumb ? ['Falange proximal', 'Falange distal', 'Primer metacarpiano'] : ['Falange proximal', 'Falange media', 'Falange distal'] };
  }
  if ((m = id.match(/^m_falange_(proximal|media|distal)_(\w+)$/))) {
    const where = { proximal: 'la más cercana a la palma', media: 'la del centro del dedo', distal: 'la de la punta, donde está la uña' }[m[1]];
    return { n: `Falange ${m[1]} del ${m[2] === 'pulgar' ? 'pulgar' : 'dedo ' + FINGERS[m[2]]}`, d: `Hueso del dedo: ${where}.` };
  }
  if ((m = id.match(/^m_metacarpiano_(\d)$/))) {
    return { n: `${ORD[m[1]]} metacarpiano`, d: `Hueso de la palma que conecta el carpo con el ${FINGER_OF[m[1]]}. Su cabeza forma el nudillo.` };
  }
  if ((m = id.match(/^p_dedo_(\w+)$/))) {
    const big = m[1] === 'gordo';
    return { n: cap(TOES[m[1]]), d: big ? 'Primer dedo del pie. Tiene dos falanges y es clave para el equilibrio y el impulso al caminar.' : 'Tiene tres falanges pequeñas (proximal, media y distal).', b: big ? ['Falange proximal', 'Falange distal'] : ['Falange proximal', 'Falange media', 'Falange distal'] };
  }
  if ((m = id.match(/^p_falange_(proximal|media|distal)_(\w+)$/))) {
    return { n: `Falange ${m[1]} del ${TOES[m[2]].replace(/ \(.*/, '')}`, d: 'Hueso de un dedo del pie.' };
  }
  if ((m = id.match(/^p_metatarsiano_(\d)$/))) {
    return { n: `${ORD[m[1]]} metatarsiano`, d: `Hueso largo del pie que conecta el tarso con el ${TOE_OF[m[1]]}. Las cabezas de los metatarsianos forman la "bola" del pie.` };
  }
  const sideMatch = id.match(/^(.*)_(der|izq)$/);
  const baseId = sideMatch && BASE[sideMatch[1]] ? sideMatch[1] : id;
  const base = BASE[baseId];
  if (!base) return null;
  const side = sideMatch && BASE[sideMatch[1]] ? sideMatch[2] : null;
  const pick = (v) => (v && !Array.isArray(v) && typeof v === 'object' ? v[side] : v);
  let name = base.n;
  if (side) {
    const fem = base.g === 'f';
    const word = base.sidePlural ? (side === 'der' ? (fem ? 'derechas' : 'derechos') : fem ? 'izquierdas' : 'izquierdos') : side === 'der' ? (fem ? 'derecha' : 'derecho') : fem ? 'izquierda' : 'izquierdo';
    name = base.sideMid ? base.n.replace(base.sideMid, `${base.sideMid} ${word}`) : `${base.n} ${word}`;
  }
  const out = { n: name, d: base.d };
  for (const k of ['b', 'm', 'o']) { const v = pick(base[k]); if (v && v.length) out[k] = v; }
  const t = pick(base.t);
  if (t) out.t = t;
  if (base.link) out.link = base.link;
  return out;
}

export const GENERAL_NOTE = E;
