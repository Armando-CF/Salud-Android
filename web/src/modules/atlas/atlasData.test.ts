// El proyecto no incluye los tipos de Node; la prueba corre en Node (vitest) y solo necesita leer el archivo.
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import {
  HUMAN_GROUPS, clampPan, fitView, hitTest, parseAtlas, parsePolys, recentCounts, screenToAtlas, tintPercent, zoomAbout,
} from './atlasData'
import { epochDayToMillis, today } from '../../lib/dates'

const atlas = parseAtlas(JSON.parse(readFileSync(new URL('../../../public/atlas.json', import.meta.url), 'utf8')))

describe('atlas: datos', () => {
  it('lee todas las vistas y cada forma tocable tiene ficha', () => {
    expect(atlas.views.length).toBe(15)
    for (const v of atlas.views) for (const s of v.shapes) if (s.id) expect(atlas.info[s.id], `sin ficha: ${s.id}`).toBeTruthy()
    expect(atlas.info['abd_fosa_iliaca_der'].organs.some((o) => o.includes('Apéndice'))).toBe(true)
    expect(atlas.info['mano_der'].link).toBe('hand')
    expect(atlas.note).toContain('atención médica')
    expect(atlas.petNote).toContain('veterinario')
  })

  it('parsea polígonos con varios subtrazos', () => {
    const p = parsePolys('M1 2L3 4L5 6ZM10 10L20 10L20 20Z')
    expect(p.length).toBe(2)
    expect([...p[0]]).toEqual([1, 2, 3, 4, 5, 6])
    expect(p[1].length).toBe(6)
  })

  it('grupos según el perfil', () => {
    expect(atlas.groupsFor(false, '').map((g) => g[0])).toEqual(['front', 'back', 'abdomen', 'organs', 'skeleton', 'hand', 'foot'])
    expect(atlas.groupsFor(false, '')).toBe(HUMAN_GROUPS)
    expect(atlas.groupsFor(true, 'perro').map((g) => g[0])).toEqual(['pet_side', 'pet_organs'])
    expect(atlas.groupsFor(true, 'gato').map((g) => g[0])).toEqual(['pet_side', 'pet_organs'])
    expect(atlas.groupsFor(true, 'ave')).toEqual([])
    expect(atlas.view('front', 'F')!.species).toBeNull()
    expect(atlas.view('front', 'M')!.id).toBe('front_M')
    expect(atlas.view('pet_side', 'F')).toBeNull()
    expect(atlas.view('front', 'F', 'perro')).toBeNull()
    expect(atlas.view('pet_side', 'F', 'gato')!.id).toBe('pet_side_gato')
    expect(atlas.hasSexVariants('front')).toBe(true)
    expect(atlas.hasSexVariants('skeleton')).toBe(false)
    expect(atlas.hasSexVariants('pet_side', 'perro')).toBe(false)
    expect(atlas.groupLabel('pet_organs')).toBe('Órganos')
    expect(atlas.orientationNote('back')).toContain('derecha')
    expect(atlas.orientationNote('pet_side')).toContain('Vista de lado')
  })

  it('las figuras de mascota llevan la especie en cada id y ficha completa', () => {
    const pets = atlas.views.filter((v) => v.species)
    expect(new Set(pets.map((v) => v.id))).toEqual(new Set(['pet_side_perro', 'pet_organs_perro', 'pet_side_gato', 'pet_organs_gato']))
    for (const v of pets) {
      for (const s of v.shapes) {
        if (!s.id) continue
        expect(s.id.startsWith('pet_') && s.id.endsWith(`_${v.species}`)).toBe(true)
        const info = atlas.info[s.id]
        expect(info.name && info.description && info.tip).toBeTruthy()
      }
    }
    expect(atlas.info['pet_abdomen_perro'].tip).toContain('torsión')
    expect(atlas.info['pet_genital_gato'].tip).toContain('arenero')
  })
})

describe('atlas: búsqueda por punto (mismos casos que Android)', () => {
  it('figura humana', () => {
    const front = atlas.view('front', 'F')!
    expect(hitTest(front, 200, 340, 4)?.id).toBe('abd_mesogastrio')
    // lado derecho de la persona = izquierda de la pantalla
    expect(hitTest(front, 165, 385, 4)?.id).toBe('abd_fosa_iliaca_der')
    expect(hitTest(front, 238, 606, 4)?.id).toBe('rodilla_izq')
    expect(hitTest(atlas.view('back', 'M')!, 235, 350, 4)?.id).toBe('lumbar_der')
    expect(hitTest(atlas.view('organs', 'M')!, 212, 240, 4)?.id).toBe('o_corazon')
    expect(hitTest(front, 75, 30, 4)).toBeNull()
  })

  it('perro', () => {
    const side = atlas.view('pet_side', 'F', 'perro')!
    expect(hitTest(side, 270, 215, 3)?.id).toBe('pet_pecho_perro')
    expect(hitTest(side, 330, 226, 3)?.id).toBe('pet_abdomen_perro')
    expect(hitTest(side, 270, 140, 3)?.id).toBe('pet_lomo_perro')
    expect(hitTest(side, 469, 170, 3)?.id).toBe('pet_cola_perro')
    expect(hitTest(side, 395, 205, 3)?.id).toBe('pet_muslo_perro')
    expect(hitTest(side, 110, 120, 3)?.id).toBe('pet_ojo_perro')
    expect(hitTest(side, 116, 107, 3)?.id).toBe('pet_cabeza_perro')
    expect(hitTest(side, 300, 330, 3)).toBeNull() // aire bajo el vientre
    expect(hitTest(atlas.view('pet_organs', 'F', 'perro')!, 234, 220, 3)?.id).toBe('pet_o_corazon_perro')
  })

  it('gato', () => {
    const side = atlas.view('pet_side', 'F', 'gato')!
    expect(hitTest(side, 255, 225, 3)?.id).toBe('pet_pecho_gato')
    expect(hitTest(side, 335, 236, 3)?.id).toBe('pet_abdomen_gato')
    expect(hitTest(side, 270, 160, 3)?.id).toBe('pet_lomo_gato')
    expect(hitTest(side, 470, 162, 3)?.id).toBe('pet_cola_gato')
    expect(hitTest(side, 86, 149, 3)?.id).toBe('pet_ojo_gato')
    expect(hitTest(side, 122, 152, 3)?.id).toBe('pet_cabeza_gato')
    expect(hitTest(atlas.view('pet_organs', 'F', 'gato')!, 250, 175, 3)?.id).toBe('pet_o_pulmones_gato')
  })

  it('tolera toques cerca de huesos delgados', () => {
    const hand = atlas.view('hand', 'F')!
    const bone = hand.shapes.find((s) => s.id === 'm_falange_media_indice')!
    const [l, t, r, b] = bone.bounds
    expect(hitTest(hand, (l + r) / 2, (t + b) / 2, 6)?.id).toBe('m_falange_media_indice')
  })
})

describe('atlas: zoom, desplazamiento y tinte', () => {
  const box: [number, number, number, number] = [70, 20, 260, 846]

  it('centra la vista y convierte pantalla ↔ atlas', () => {
    const { s0, mx, my } = fitView(box, 390, 500)
    expect(s0).toBeCloseTo((500 - 16) / 846, 5)
    expect(my).toBeCloseTo(8, 5)
    expect(mx).toBeGreaterThan(8)
    const [ax, ay] = screenToAtlas(195, 250, box, 390, 500, 1, 0, 0)
    expect(ax).toBeCloseTo(200, 3) // centro horizontal de la figura
    expect(ay).toBeCloseTo(443, 0)
  })

  it('el zoom mantiene fijo el punto bajo el dedo y respeta los límites', () => {
    const before = screenToAtlas(120, 300, box, 390, 500, 1, 0, 0)
    const z = zoomAbout(1, 0, 0, 2, 120, 300, 390, 500)
    expect(z.zoom).toBe(2)
    const after = screenToAtlas(120, 300, box, 390, 500, z.zoom, z.panX, z.panY)
    expect(after[0]).toBeCloseTo(before[0], 3)
    expect(after[1]).toBeCloseTo(before[1], 3)
    expect(zoomAbout(5, 0, 0, 4, 0, 0, 390, 500).zoom).toBe(6)
    const out = zoomAbout(2, -100, -100, 0.1, 195, 250, 390, 500)
    expect(out.zoom).toBe(1)
    expect(Math.abs(out.panX)).toBe(0)
    expect(Math.abs(out.panY)).toBe(0)
    expect(clampPan(50, -9999, 2, 390, 500)).toEqual([0, -500])
  })

  it('cuenta síntomas recientes por zona y calcula el tinte', () => {
    const t = today()
    const counts = recentCounts([
      { regionId: 'abd_mesogastrio', timestamp: epochDayToMillis(t - 1) },
      { regionId: 'abd_mesogastrio', timestamp: epochDayToMillis(t - 29) },
      { regionId: 'abd_mesogastrio', timestamp: epochDayToMillis(t - 31) },
      { regionId: null, timestamp: epochDayToMillis(t) },
      { regionId: 'rodilla_izq', timestamp: epochDayToMillis(t) },
    ], t)
    expect(counts).toEqual({ abd_mesogastrio: 2, rodilla_izq: 1 })
    expect(tintPercent(0)).toBe(0)
    expect(tintPercent(1)).toBe(40)
    expect(tintPercent(9)).toBe(70)
  })
})
