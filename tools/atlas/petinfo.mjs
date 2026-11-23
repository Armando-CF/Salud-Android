// Textos del atlas de mascotas (perro y gato). Contenido educativo, no diagnóstico.
// Cada campo puede ser un texto/lista o un objeto { perro, gato } cuando difiere entre especies.
// n = nombre, d = descripción, b = huesos, m = músculos, o = órganos/estructuras,
// t = cuándo ir al veterinario, link = grupo de vista relacionado.

const COJERA = 'Una cojera en la que no apoya la pata, con hinchazón, deformidad o dolor intenso, o que dura más de uno o dos días, necesita revisión. No le des analgésicos de personas: el paracetamol y el ibuprofeno son tóxicos para perros y gatos.';

export const PET = {
  cabeza: {
    n: 'Cabeza',
    d: 'Cráneo y frente. Protege el cerebro; a los lados están los músculos de la masticación.',
    b: ['Cráneo'], m: ['Temporal', 'Masetero'], o: ['Cerebro'],
    t: 'Después de un golpe en la cabeza, o si hay convulsiones, cabeza ladeada, caminar en círculos o pérdida del equilibrio, acude al veterinario de inmediato.',
    link: 'pet_organs',
  },
  oreja: {
    n: 'Oreja y oído',
    d: {
      perro: 'Pabellón de la oreja y conducto auditivo, que es largo y en forma de "L". Las orejas caídas ventilan menos y se infectan con más facilidad.',
      gato: 'Pabellón de la oreja y conducto auditivo, que es largo y en forma de "L". Los gatos giran cada oreja por separado para ubicar sonidos.',
    },
    o: ['Conducto auditivo', 'Tímpano', 'Oído interno (equilibrio)'],
    t: 'Sacudir la cabeza, rascarse mucho, mal olor, secreción oscura o dolor al tocar sugieren otitis o ácaros. Una oreja hinchada como almohadilla también necesita revisión.',
  },
  ojo: {
    n: 'Ojo',
    d: 'Ojo, párpados y tercer párpado (una membrana en la esquina interna que normalmente casi no se ve).',
    o: ['Córnea', 'Pupila e iris', 'Tercer párpado', 'Conducto lagrimal'],
    t: {
      perro: 'Un ojo cerrado, rojo, con lagaña verde o amarilla, opaco o más grande de lo normal es motivo de consulta el mismo día: los problemas de ojos avanzan rápido.',
      gato: 'Un ojo cerrado, rojo, con lagaña verde o amarilla, opaco, o pupilas de distinto tamaño son motivo de consulta el mismo día: los problemas de ojos avanzan rápido.',
    },
  },
  hocico: {
    n: 'Hocico y nariz',
    d: 'Trufa (nariz), puente nasal y labios superiores. Dentro están las fosas nasales y el olfato.',
    b: ['Maxilar', 'Huesos nasales'], o: ['Fosas nasales', 'Bigotes (vibrisas)'],
    t: {
      perro: 'Sangrado nasal, secreción espesa (sobre todo de un solo lado), estornudos constantes o hinchazón de la cara requieren revisión. Una nariz seca o tibia por sí sola no indica enfermedad.',
      gato: 'Estornudos con ojos llorosos y falta de apetito suelen ser gripe felina: si deja de comer, llévalo pronto. Sangrado nasal o hinchazón de la cara también requieren revisión.',
    },
  },
  boca: {
    n: 'Boca, dientes y encías',
    d: 'Mandíbula, lengua, dientes y encías. Las encías sanas son rosadas y húmedas; al presionarlas recuperan el color en menos de 2 segundos.',
    b: ['Mandíbula'], o: ['Dientes', 'Lengua', 'Encías', 'Glándulas salivales'],
    t: {
      perro: 'Encías pálidas, blancas, azuladas o amarillas son una urgencia. Mal aliento fuerte, sarro, babeo o dolor al comer ameritan revisión dental. Si comió chocolate, uvas, xilitol, medicamentos o veneno, llama al veterinario de inmediato aunque se vea bien.',
      gato: 'Encías pálidas, blancas, azuladas o amarillas son una urgencia. Un gato que babea, tiene mal aliento o deja de comer por dolor en la boca debe revisarse pronto. Los lirios y muchos medicamentos de personas son muy tóxicos para los gatos.',
    },
  },
  cuello: {
    n: 'Cuello y garganta',
    d: 'Garganta, tráquea, esófago y vértebras cervicales. Aquí va el collar y se palpan ganglios bajo la mandíbula.',
    b: ['Vértebras cervicales'], m: ['Músculos del cuello'], o: ['Tráquea', 'Esófago', 'Tiroides', 'Ganglios linfáticos'],
    t: {
      perro: 'Dificultad para respirar, atragantamiento, tos seca como "graznido" (frecuente en razas pequeñas) o dolor al mover el cuello requieren atención. Un bulto que crece debe revisarse.',
      gato: 'Dificultad para respirar o tragar, o dolor al mover el cuello, requieren atención. En gatos mayores, un bultito en la garganta con pérdida de peso y mucho apetito puede ser hipertiroidismo.',
    },
    link: 'pet_organs',
  },
  lomo: {
    n: 'Cruz y lomo',
    d: 'La cruz es el punto más alto entre los omóplatos: ahí se mide la altura y suelen aplicarse inyecciones y pipetas. El lomo es la espalda sobre las costillas.',
    b: ['Vértebras torácicas', 'Costillas (parte alta)', 'Escápulas'], m: ['Trapecio', 'Dorsal ancho', 'Músculos de la columna'], o: ['Médula espinal'],
    t: {
      perro: 'Dolor al tocar la espalda, lomo encorvado o rechazo a subir escaleras deben revisarse. Un bulto donde se aplicó una inyección suele bajar solo en pocas semanas; si crece o no baja, consúltalo.',
      gato: 'Dolor al tocar la espalda o lomo encorvado deben revisarse. Un bulto donde se aplicó una inyección que sigue ahí después de 3 semanas, mide más de 2 cm o crece, debe valorarse sin esperar.',
    },
  },
  lumbar: {
    n: 'Zona lumbar',
    d: 'Espalda baja, entre las costillas y la cadera. Debajo de los músculos están los riñones.',
    b: ['Vértebras lumbares'], m: ['Músculos lumbares', 'Psoas'], o: ['Riñones', 'Médula espinal'],
    t: {
      perro: 'Si arrastra las patas traseras, camina tambaleándose o grita al cargarlo, es una urgencia (posible hernia de disco), sobre todo en perros de lomo largo como el salchicha.',
      gato: 'Parálisis súbita de las patas traseras con dolor intenso y patas frías es una urgencia inmediata. Dolor al acariciar la espalda baja o dejar de saltar puede ser artrosis, frecuente en gatos mayores.',
    },
    link: 'pet_organs',
  },
  grupa: {
    n: 'Grupa y cadera',
    d: 'Parte posterior del lomo sobre la pelvis, hasta el nacimiento de la cola.',
    b: ['Pelvis', 'Sacro', 'Articulación de la cadera (fémur)'], m: ['Glúteos'],
    t: {
      perro: 'Dificultad para levantarse, correr "como conejo" o rechazo a saltar puede indicar displasia o artrosis de cadera. Mucho rascado y pelo ralo en esta zona suele ser alergia a las pulgas.',
      gato: 'Dejar de saltar a lugares altos o de acicalarse esta zona puede indicar artrosis o sobrepeso. Mucho rascado, costras y pelo ralo aquí suele ser alergia a las pulgas.',
    },
  },
  cola: {
    n: 'Cola',
    d: {
      perro: 'Prolongación de la columna (vértebras caudales). Sirve para el equilibrio y la comunicación.',
      gato: 'Prolongación de la columna (vértebras caudales). Es clave para el equilibrio y expresa su estado de ánimo.',
    },
    b: ['Vértebras caudales'],
    t: 'Una cola caída que no mueve, con dolor o herida, o después de un jalón o machucón, necesita revisión: puede afectar el control para orinar y defecar.',
  },
  pecho: {
    n: 'Pecho y costillas (tórax)',
    d: 'Caja torácica: costillas y esternón que protegen el corazón y los pulmones. Debes poder sentir las costillas al tocar, sin que se vean marcadas.',
    b: ['Costillas', 'Esternón'], m: ['Pectorales', 'Intercostales'], o: ['Corazón', 'Pulmones'],
    t: {
      perro: 'Respirar con mucho esfuerzo o muy rápido en reposo, tos persistente, encías azuladas o desmayos: acude de inmediato. Jadeo extremo con babeo y debilidad en un día caluroso puede ser golpe de calor: es una urgencia.',
      gato: 'Un gato que respira con la boca abierta, con esfuerzo o muy rápido en reposo necesita atención inmediata: en gatos casi nunca es normal jadear.',
    },
    link: 'pet_organs',
  },
  abdomen: {
    n: 'Abdomen (vientre)',
    d: 'Parte baja del tronco, detrás de las costillas. En las hembras aquí están las mamas.',
    m: ['Recto abdominal', 'Oblicuos'], o: ['Hígado', 'Estómago', 'Bazo', 'Intestinos', 'Vejiga', 'Mamas'],
    t: {
      perro: 'Abdomen hinchado y duro, con intentos de vomitar sin sacar nada, babeo e inquietud es una URGENCIA inmediata (torsión de estómago), sobre todo en perros grandes. Vómito o diarrea con sangre, o que duran más de un día, también requieren consulta. Revisa las mamas: cualquier bolita debe valorarse.',
      gato: 'Vómito repetido, diarrea con sangre o abdomen hinchado requieren consulta. Un gato que no come en 24 a 48 horas debe revisarse: el ayuno prolongado daña su hígado. Revisa las mamas: cualquier bolita debe valorarse pronto.',
    },
    link: 'pet_organs',
  },
  flanco: {
    n: 'Flanco (ijar)',
    d: 'Costado blando entre la última costilla y el muslo. Aquí se nota bien la respiración.',
    m: ['Oblicuos'], o: ['Intestinos', 'Riñón (más arriba)', 'Bazo (lado izquierdo)'],
    t: 'Cuenta aquí las respiraciones cuando duerme: más de 40 por minuto de forma repetida amerita consulta. Flancos muy hundidos indican delgadez o deshidratación; un costado abultado o doloroso debe revisarse.',
    link: 'pet_organs',
  },
  genital: {
    n: 'Zona anal y genital',
    d: 'Bajo la cola están el ano y las glándulas anales; debajo, la vulva en las hembras o el escroto en los machos (el prepucio queda bajo el vientre).',
    o: ['Ano y glándulas anales', 'Vulva o escroto', 'Uretra'],
    t: {
      perro: 'Arrastrar el trasero suele deberse a glándulas anales llenas o a parásitos. En hembras sin esterilizar, secreción vaginal con decaimiento y mucha sed es una urgencia (infección de matriz). Pujar para orinar o defecar sin lograrlo requiere atención el mismo día.',
      gato: 'Un gato (sobre todo macho) que entra y sale del arenero, puja y no orina, o llora al intentarlo, es una URGENCIA inmediata: puede tener la uretra tapada. Secreción vaginal en hembras sin esterilizar también es urgente.',
    },
  },
  hombro: {
    n: 'Hombro y escápula',
    d: 'Omóplato (escápula) y articulación del hombro. En perros y gatos la pata delantera se une al tronco solo con músculos, sin clavícula funcional.',
    b: ['Escápula', 'Húmero (cabeza)'], m: ['Supraespinoso', 'Infraespinoso', 'Deltoides'],
    t: COJERA,
  },
  brazo: { n: 'Brazo', d: 'Entre el hombro y el codo; queda pegado al costado del pecho.', b: ['Húmero'], m: ['Tríceps', 'Bíceps'], t: COJERA },
  codo: {
    n: 'Codo',
    d: 'Articulación entre el brazo y el antebrazo; apunta hacia atrás, a la altura del pecho.',
    b: ['Húmero', 'Radio', 'Cúbito (olécranon)'],
    t: {
      perro: 'En perros medianos y grandes es frecuente la displasia de codo (cojera al levantarse) y los callos o bolsas de líquido por echarse en piso duro.',
      gato: COJERA,
    },
  },
  antebrazo: {
    n: 'Antebrazo',
    d: 'Del codo al carpo.',
    b: ['Radio', 'Cúbito'], m: ['Flexores y extensores del carpo y los dedos'],
    t: 'Después de una caída o un salto (frecuente en razas pequeñas y en gatos), si no apoya la pata o se ve deformada puede haber fractura: muévelo lo menos posible y llévalo a revisión.',
  },
  carpo: {
    n: 'Carpo (muñeca)',
    d: 'Equivale a nuestra muñeca. Por detrás tiene una almohadilla carpiana que casi no toca el suelo.',
    b: ['Huesos del carpo', 'Radio y cúbito (extremo)'],
    t: COJERA,
  },
  mano: {
    n: 'Mano: dedos, almohadillas y uñas',
    d: {
      perro: 'Los perros caminan sobre los dedos. Cada mano tiene 4 dedos de apoyo con su almohadilla, una almohadilla central y, más arriba por dentro, el espolón (el "pulgar").',
      gato: 'Los gatos caminan sobre los dedos. Cada mano tiene 4 dedos de apoyo y un pulgar pequeño por dentro, con uñas retráctiles y almohadillas muy sensibles.',
    },
    b: ['Metacarpianos', 'Falanges'], o: ['Almohadillas', 'Uñas', 'Espolón'],
    t: {
      perro: 'Lamerse mucho las patas, cojear, una uña rota o sangrante, espigas o cortadas entre los dedos, o almohadillas quemadas por el pavimento caliente ameritan revisión. Las uñas largas cambian la pisada: córtalas con regularidad.',
      gato: 'Una uña encarnada en la almohadilla (frecuente en gatos mayores), cojera, heridas por pelea o almohadillas hinchadas necesitan revisión.',
    },
  },
  muslo: { n: 'Muslo', d: 'De la cadera a la rodilla: los músculos más potentes para correr y saltar.', b: ['Fémur'], m: ['Cuádriceps', 'Bíceps femoral', 'Semitendinoso'], t: COJERA },
  rodilla: {
    n: 'Babilla (rodilla)',
    d: 'Rodilla de la pata trasera, con rótula, meniscos y ligamentos cruzados. Queda pegada al vientre.',
    b: ['Fémur', 'Tibia', 'Rótula'], o: ['Ligamentos cruzados', 'Meniscos'],
    t: {
      perro: 'Cojera súbita de una pata trasera al correr o saltar suele ser rotura del ligamento cruzado. En razas pequeñas, dar "brinquitos" con la pata levantada sugiere luxación de rótula.',
      gato: COJERA,
    },
  },
  pierna: { n: 'Pierna', d: 'De la babilla al corvejón.', b: ['Tibia', 'Peroné'], m: ['Gemelos (gastrocnemio)', 'Tibial craneal'], t: COJERA },
  corvejon: {
    n: 'Corvejón (tarso)',
    d: 'Equivale a nuestro tobillo y talón: la punta que sobresale hacia atrás es el calcáneo, donde se inserta el tendón de Aquiles.',
    b: ['Huesos del tarso', 'Calcáneo'], o: ['Tendón de Aquiles'],
    t: {
      perro: 'Si el corvejón baja hasta casi tocar el suelo al caminar, puede haber una lesión del tendón de Aquiles: consulta.',
      gato: 'Si camina apoyando los corvejones en el suelo, puede ser una complicación de la diabetes o una lesión del tendón: consulta.',
    },
  },
  pie: {
    n: 'Pie: dedos, almohadillas y uñas',
    d: 'Parte de la pata trasera que apoya en el suelo: metatarso, 4 dedos, almohadillas y uñas.',
    b: ['Metatarsianos', 'Falanges'], o: ['Almohadillas', 'Uñas'],
    t: {
      perro: 'Cojera, una uña rota, espigas o cortadas entre los dedos, o almohadillas quemadas por el pavimento caliente ameritan revisión. Algunos perros tienen espolones traseros: vigila que la uña no se encarne.',
      gato: 'Cojera, heridas por pelea, uñas encarnadas o almohadillas hinchadas necesitan revisión.',
    },
  },

  // ── Órganos ──
  o_cerebro: {
    n: 'Cerebro',
    d: 'Controla el movimiento, los sentidos y la conducta.',
    t: 'Convulsiones, desorientación, cabeza ladeada, caminar en círculos o cambios bruscos de conducta requieren atención pronta. Una convulsión de más de 5 minutos, o varias seguidas, es una urgencia.',
  },
  o_traquea: {
    n: 'Tráquea y esófago',
    d: 'La tráquea lleva el aire a los pulmones; el esófago, pegado a ella, lleva la comida al estómago.',
    t: 'Atragantamiento, arcadas constantes, babeo o regurgitar la comida sin digerir deben revisarse; si le cuesta respirar, es una urgencia.',
  },
  o_pulmones: {
    n: 'Pulmones',
    d: 'Llenan casi todo el tórax. En reposo, lo normal es respirar entre 15 y 30 veces por minuto.',
    t: {
      perro: 'Respiración con esfuerzo o de más de 40 por minuto en reposo, tos persistente o encías azuladas requieren atención inmediata.',
      gato: 'Respirar con la boca abierta, con esfuerzo o a más de 40 por minuto en reposo requiere atención inmediata.',
    },
  },
  o_corazon: {
    n: 'Corazón',
    d: {
      perro: 'Bombea la sangre a todo el cuerpo. Late entre 60 y 140 veces por minuto (más rápido en razas pequeñas y cachorros).',
      gato: 'Bombea la sangre a todo el cuerpo. Late entre 140 y 220 veces por minuto.',
    },
    t: {
      perro: 'Tos (sobre todo de noche), cansancio fácil, desmayos, abdomen hinchado o respiración rápida al dormir pueden indicar enfermedad cardiaca. La prevención del gusano del corazón es importante en zonas con mosquitos.',
      gato: 'La enfermedad cardiaca en gatos suele no dar señales hasta que aparece dificultad para respirar o parálisis de las patas traseras: ambas son urgencias.',
    },
  },
  o_higado: {
    n: 'Hígado',
    d: 'Procesa nutrientes, medicamentos y toxinas, y produce la bilis. Está justo detrás del diafragma.',
    t: {
      perro: 'Encías, ojos o piel amarillos (ictericia), vómito, mucha sed y decaimiento requieren atención pronta.',
      gato: 'Ojos, encías u orejas amarillos (ictericia) requieren atención pronta. Un gato que deja de comer varios días puede enfermar del hígado.',
    },
  },
  o_estomago: {
    n: 'Estómago',
    d: 'Recibe y empieza a digerir la comida. Queda detrás del hígado, hacia el lado izquierdo.',
    t: {
      perro: 'Intentos de vomitar sin sacar nada con el abdomen hinchado es una URGENCIA (torsión de estómago). Vomitar una vez y seguir animado suele ser leve; vómito repetido, con sangre o con decaimiento requiere consulta.',
      gato: 'Vomitar bolas de pelo de vez en cuando es común; hacerlo varias veces por semana, vomitar con sangre o dejar de comer requiere consulta.',
    },
  },
  o_bazo: {
    n: 'Bazo',
    d: 'Filtra la sangre y guarda una reserva de glóbulos rojos.',
    t: {
      perro: 'Debilidad súbita con encías pálidas y abdomen hinchado, sobre todo en perros mayores, puede ser sangrado interno: es una urgencia.',
      gato: 'Debilidad súbita con encías pálidas requiere atención inmediata.',
    },
  },
  o_rinones: {
    n: 'Riñones',
    d: 'Filtran la sangre y producen la orina. Están pegados a la espalda, bajo las vértebras lumbares.',
    t: {
      perro: 'Beber y orinar mucho más de lo normal, perder peso, vómito y mal aliento pueden indicar enfermedad renal.',
      gato: 'Beber y orinar más de lo normal, perder peso, vómito y mal aliento pueden indicar enfermedad renal, muy frecuente en gatos mayores: conviene un chequeo anual con análisis.',
    },
  },
  o_intestinos: {
    n: 'Intestinos',
    d: 'Intestino delgado (absorbe los nutrientes) e intestino grueso (forma las heces).',
    t: 'Vómito y diarrea persistentes, heces con sangre o negras, o la sospecha de que tragó un juguete, hueso, calcetín, hilo o liga requieren consulta pronta: puede haber obstrucción. Nunca jales un hilo que asome por la boca o el ano.',
  },
  o_vejiga: {
    n: 'Vejiga',
    d: 'Almacena la orina hasta que sale por la uretra.',
    t: {
      perro: 'Orinar con sangre, muchas veces en poca cantidad, con esfuerzo o en lugares donde no solía hacerlo sugiere infección o cálculos. Si puja y no sale nada, es una urgencia.',
      gato: 'Si entra y sale del arenero, puja y no orina o llora al intentarlo, es una URGENCIA inmediata (uretra tapada), sobre todo en machos. Orinar con sangre o fuera del arenero también requiere consulta.',
    },
  },
};

export const PET_NOTE = 'Si tu mascota respira con dificultad, tiene las encías pálidas o azuladas, convulsiona, no puede orinar, sangra mucho o tragó algo tóxico, acude a un veterinario de urgencias de inmediato.';

/** Ficha de un id `pet_<zona>_<especie>`; null si no existe. */
export function petInfoFor(id) {
  const m = id.match(/^pet_(\w+)_(perro|gato)$/);
  if (!m) return null;
  const base = PET[m[1]];
  if (!base) return null;
  const pick = (v) => (v && !Array.isArray(v) && typeof v === 'object' ? v[m[2]] : v);
  const out = { n: pick(base.n), d: pick(base.d) || '' };
  for (const k of ['b', 'm', 'o']) { const v = pick(base[k]); if (v && v.length) out[k] = v; }
  const t = pick(base.t);
  if (t) out.t = t;
  if (base.link) out.link = base.link;
  return out;
}
