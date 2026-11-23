// Guardar, compartir y elegir archivos en el navegador (pensado para Safari en iPhone).

/**
 * Entrega un archivo a la persona. En iPhone abre la hoja de compartir (Guardar en Archivos, AirDrop, Mail…);
 * donde no existe, lo descarga. Devuelve "shared", "downloaded" o "cancelled".
 * IMPORTANTE: llámalo como respuesta directa a un toque (iOS exige un gesto de la persona).
 */
export async function saveFile(name: string, content: Blob | string | ArrayBuffer | Uint8Array, mime: string): Promise<'shared' | 'downloaded' | 'cancelled'> {
  const blob = content instanceof Blob ? content : new Blob([content as BlobPart], { type: mime })
  const file = new File([blob], name, { type: mime })
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean }
  const touch = typeof matchMedia !== 'undefined' && matchMedia('(pointer: coarse)').matches
  if (touch && nav.share && nav.canShare?.({ files: [file] })) {
    try {
      await nav.share({ files: [file], title: name })
      return 'shared'
    } catch (e) {
      if ((e as DOMException)?.name === 'AbortError') return 'cancelled'
      // si compartir falla por otra razón, se descarga
    }
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
  return 'downloaded'
}

/** Abre el selector de archivos del sistema. `accept` p. ej. "application/json,.json" o "image/*,application/pdf". */
export function pickFiles(accept: string, multiple = false): Promise<File[]> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = accept
    input.multiple = multiple
    input.style.position = 'fixed'
    input.style.left = '-9999px'
    document.body.appendChild(input)
    let done = false
    const finish = (files: File[]) => { if (!done) { done = true; input.remove(); resolve(files) } }
    input.addEventListener('change', () => finish([...(input.files ?? [])]))
    input.addEventListener('cancel', () => finish([]))
    input.click()
  })
}

/** Nombre de archivo seguro a partir de un texto: "Firulais García" → "firulais-garcia". */
export function slug(text: string, max = 24): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, max) || 'savia'
}

/** ¿La app corre instalada (pantalla de inicio) y no dentro de Safari? */
export function isStandalone(): boolean {
  return (typeof matchMedia !== 'undefined' && matchMedia('(display-mode: standalone)').matches) || (navigator as Navigator & { standalone?: boolean }).standalone === true
}

/** ¿Es un iPhone/iPad? */
export function isIos(): boolean {
  const ua = navigator.userAgent
  return /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)
}
