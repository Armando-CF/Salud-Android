# Savia Web (PWA)

Versión web de Savia pensada para **instalarse en iPhone** desde Safari y publicarse gratis en
**GitHub Pages**. Es la misma bitácora de salud que la app de Android: perfiles para la familia y
las mascotas, síntomas, atlas del cuerpo, ciclo menstrual, mediciones, estudios, medicamentos,
citas, vacunas, bienestar, autoexploración y respaldos.

## Cómo se instala en el iPhone

1. Abre la dirección de la app en **Safari**.
2. Toca el botón **Compartir** y elige **“Agregar a inicio”**.
3. Ábrela desde el icono: funciona a pantalla completa y **sin conexión**.

## Actualizaciones

Cuando se publica una versión nueva, la app instalada la detecta al abrirse (y al volver a primer
plano) y muestra **“Hay una versión nueva · Actualizar”**. Si no se toca, la versión nueva entra sola
la próxima vez que la app se abre desde cero. Los datos no se tocan al actualizar.

## Privacidad y datos

- No hay servidores, cuentas ni rastreo: todo se guarda en **IndexedDB**, dentro del dispositivo.
- Como los datos viven solo en ese dispositivo y navegador, la app incluye en **Ajustes → Respaldo**
  los botones **Exportar respaldo (.json)** e **Importar respaldo (.json)**. Conviene guardar un
  respaldo de vez en cuando (Archivos / iCloud Drive / correo).
- Los respaldos son compatibles con la app de Android (se puede importar un respaldo de Android).

## Diferencias con la app de Android

| Android | Web |
| --- | --- |
| Recordatorios con notificaciones | iOS no permite avisos locales a una PWA: se ofrece **“Agregar al calendario”** (.ics) para citas, vacunas y medicamentos |
| Bloqueo con huella | Bloqueo opcional con **PIN** |
| Exportar PDF | **Imprimir → guardar como PDF** desde la hoja de compartir |

## Requisitos técnicos cubiertos

- **PWA para iOS:** `manifest.json`, `apple-touch-icon`, metas `apple-mobile-web-app-capable`,
  `apple-mobile-web-app-status-bar-style` y `viewport-fit=cover`; zonas seguras con
  `env(safe-area-inset-*)`; **service worker** que deja toda la app disponible sin conexión.
- **Safari táctil:** todos los `input`, `textarea` y `select` usan al menos `font-size: 16px`
  (sin zoom automático); el teclado se cierra al tocar fuera de un campo.
- **Almacenamiento local:** IndexedDB (Dexie), una base por perfil.
- **GitHub Pages:** rutas relativas (`base: './'`) y navegación con `#`, así que funciona en
  `https://usuario.github.io/<repositorio>/` sin pantalla en blanco; el flujo
  `.github/workflows/pages.yml` compila y publica en cada push a `main`.

## Publicar en GitHub Pages

1. GitHub Pages gratis requiere que el repositorio sea **público** (en privados necesita un plan de pago).
2. En el repositorio: **Settings → Pages → Build and deployment → Source: “GitHub Actions”**.
3. Cada push a `main` que toque `web/` compila y publica. También se puede lanzar a mano en la
   pestaña **Actions** (“Run workflow”).

## Desarrollo

```bash
cd web
npm ci
npm run dev        # servidor de desarrollo
npm test           # pruebas de lógica (vitest)
npm run build      # compila a web/dist
node scripts/e2e-all.mjs   # recorrido completo en un iPhone emulado (requiere Playwright/Chromium)
```

Estructura: `src/data` (modelo, IndexedDB, perfiles, ajustes), `src/ui` (componentes y gráficas),
`src/lib` (fechas, archivos), `src/modules/*` (una carpeta por sección), `public/atlas.json`
(atlas corporal, el mismo que usa Android; se copia desde `app/src/main/assets`).
