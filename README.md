# Savia

**Savia** es una bitácora de salud personal para Android: privada, sin cuentas y sin internet.
Todo lo que anotas se guarda únicamente en tu teléfono, y puedes exportarlo cuando quieras.

Está pensada para usarse durante muchos años y por cualquier persona: el seguimiento del ciclo
menstrual es un módulo que se activa o desactiva en los ajustes.

## Qué incluye

| Módulo | Qué hace |
| --- | --- |
| **Hoy** | Resumen del día: accesos rápidos, estado del ciclo, medicamentos pendientes, próxima cita, peso y síntomas recientes. |
| **Síntomas** | Registro con intensidad (1–10), zona del cuerpo, duración, desencadenantes y notas. Historial con búsqueda y resumen de frecuencias. |
| **Cuerpo** | Atlas interactivo: figura de mujer y de hombre (frente y espalda), las 9 regiones del abdomen, órganos, esqueleto, y detalle de mano y pie con el nombre de cada dedo y hueso. Desde cualquier zona se puede registrar un síntoma. |
| **Ciclo menstrual** | Calendario, registro diario (flujo, síntomas, ánimo, flujo cervical, temperatura basal, relaciones, pruebas, anticonceptivo), predicción de periodo, ventana fértil y ovulación, estadísticas e historial de ciclos. |
| **Mediciones** | Peso con tendencia, media móvil, meta e IMC; presión arterial, frecuencia cardiaca, glucosa, temperatura, oxigenación, cintura, cadera y grasa corporal. |
| **Estudios y chequeos** | Resultados de laboratorio con rangos de referencia, evolución de cada parámetro en el tiempo, adjuntos (fotos/PDF) y fechas de seguimiento. |
| **Medicamentos y citas** | Tomas del día, adherencia, citas médicas y recordatorios con notificaciones. |
| **Bienestar diario** | Ánimo, energía, estrés, sueño, agua y ejercicio, con tendencias. |
| **Autoexploración** | Registro y guía de autoexploración mamaria, testicular y de piel. |
| **Mi ficha** | Datos médicos básicos: tipo de sangre, alergias, padecimientos, contacto de emergencia. |
| **Exportar y respaldo** | Excel (.xlsx), PDF, CSV y JSON, por módulo y rango de fechas; respaldo completo y restauración. |

Savia no da diagnósticos ni sustituye la atención médica. Las predicciones del ciclo son
estimaciones y no sirven como método anticonceptivo.

## Privacidad

- La app **no pide permiso de internet**: no puede enviar datos a ningún lado.
- Los datos viven en una base de datos local (Room/SQLite) dentro del teléfono.
- Bloqueo opcional con huella o PIN del teléfono.
- Como no hay nube propia, conviene crear un respaldo de vez en cuando (Más → Exportar y respaldo).

## Compilar

Requisitos: JDK 17 o superior y el SDK de Android (plataforma 36, build-tools 36).

```bash
export ANDROID_HOME=/ruta/al/android-sdk
./gradlew :app:assembleRelease      # APK en app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest    # pruebas unitarias y capturas de pantalla (app/build/shots/)
```

### Firma

Para que una versión nueva se instale **encima** de la anterior conservando los datos, todos los
APK deben firmarse con la misma llave. El proyecto busca `keystore/signing.properties`
(carpeta ignorada por git):

```properties
storeFile=keystore/savia.jks
storePassword=…
keyAlias=savia
keyPassword=…
```

Si ese archivo no existe, el APK de depuración se firma con la llave de depuración local.

## Estructura

```
app/src/main/java/app/savia/
  data/        Entidades Room, DAOs, catálogos y preferencias
  cycle/       Motor de predicción del ciclo (lógica pura, con pruebas)
  export/      Exportadores (xlsx, pdf, csv, json) y respaldo
  reminders/   Recordatorios y notificaciones
  ui/          Pantallas por módulo (Jetpack Compose + Material 3)
app/src/main/assets/atlas.json   Atlas corporal (generado)
tools/atlas/   Generador del atlas:  node tools/atlas/gen.mjs
```

Stack: Kotlin, Jetpack Compose, Material 3, Room, Navigation Compose. minSdk 26 (Android 8.0).
