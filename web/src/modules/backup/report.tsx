// Reporte para imprimir o guardar como PDF: una vista HTML limpia de las tablas exportadas.

import { Fmt } from '../../lib/dates'
import { dataIsPet, dataProfileName, type ExportData } from './exportData'
import { cycleStatPairs, display, kindLabel, profilePairs, rangeLabel, type ExportTable } from './tables'

/** Tablas que el reporte muestra como lista "campo: valor" en lugar de tabla. */
const AS_PAIRS = new Set(['ficha', 'ciclo_estadisticas'])

export function ReportView(props: { data: ExportData; tables: ExportTable[] }) {
  const { data, tables } = props
  const pet = dataIsPet(data)
  const name = dataProfileName(data)
  const ficha = tables.some((t) => t.id === 'ficha') ? profilePairs(data, true) : []
  const stats = tables.some((t) => t.id === 'ciclo_estadisticas') ? cycleStatPairs(data).filter(([, v]) => v !== null && v !== undefined) : []
  const rest = tables.filter((t) => !AS_PAIRS.has(t.id) && t.rows.length > 0)
  const empty = tables.filter((t) => !AS_PAIRS.has(t.id) && t.rows.length === 0)
  return (
    <article className="bk-report">
      <header className="bk-report-head">
        <div className="bk-report-brand">Savia — Reporte de salud</div>
        <h2>{name || (pet ? 'Mascota' : 'Sin nombre')}</h2>
        <div className="bk-report-meta">
          {data.profile ? kindLabel(data.profile.kind, data.profile.species) + ' · ' : ''}
          {rangeLabel(data.options)} · Generado el {Fmt.dateTime(data.generatedAt)}
        </div>
      </header>

      {ficha.length > 0 && (
        <section>
          <h3>{pet ? 'Ficha' : 'Ficha médica'}</h3>
          <dl className="bk-pairs">
            {ficha.map(([k, v]) => (
              <div key={k}><dt>{k}</dt><dd>{display(v)}</dd></div>
            ))}
          </dl>
        </section>
      )}

      {stats.length > 0 && (
        <section>
          <h3>Ciclo menstrual — resumen</h3>
          <dl className="bk-pairs">
            {stats.map(([k, v]) => (
              <div key={k}><dt>{k}</dt><dd>{display(v)}</dd></div>
            ))}
          </dl>
        </section>
      )}

      {rest.map((t) => (
        <section key={t.id}>
          <h3>{t.title} <span className="bk-count">({t.rows.length})</span></h3>
          <div className="bk-table-wrap">
            <table>
              <thead>
                <tr>{t.columns.map((c) => <th key={c.title} className={c.wrap ? 'bk-wrap' : undefined}>{c.title}</th>)}</tr>
              </thead>
              <tbody>
                {t.rows.map((row, i) => (
                  <tr key={i}>{t.columns.map((c, j) => <td key={j} className={c.wrap ? 'bk-wrap' : 'bk-nw'}>{display(row[j])}</td>)}</tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}

      {empty.length > 0 && <p className="bk-report-meta">Sin registros en este periodo: {empty.map((t) => t.title).join(', ')}.</p>}

      <footer className="bk-report-foot">
        Reporte generado por Savia a partir de lo que registró la persona. Es informativo y no sustituye una valoración médica{pet ? ' veterinaria' : ''}.
      </footer>
    </article>
  )
}
