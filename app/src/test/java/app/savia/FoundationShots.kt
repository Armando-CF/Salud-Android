package app.savia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.savia.ui.components.BarChart
import app.savia.ui.components.BarEntry
import app.savia.ui.components.ChartHLine
import app.savia.ui.components.ChartPoint
import app.savia.ui.components.ChartRange
import app.savia.ui.components.ChartRangeSelector
import app.savia.ui.components.ChartSeries
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.DateField
import app.savia.ui.components.DateTimeField
import app.savia.ui.components.HBarList
import app.savia.ui.components.InfoNote
import app.savia.ui.components.LevelSlider
import app.savia.ui.components.LineChart
import app.savia.ui.components.NavRow
import app.savia.ui.components.NumberField
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.StatTile
import app.savia.ui.theme.SaviaTheme
import app.savia.util.nowMillis
import app.savia.util.today
import org.junit.Test

class FoundationShots : ScreenshotBase() {
    @Test
    fun components() = shot("foundation_components") { Demo() }

    @Test
    fun componentsDark() = shot("foundation_components_dark", dark = true) { Demo() }

    @androidx.compose.runtime.Composable
    private fun Demo() {
        SaviaScaffold("Componentes", onBack = {}, subtitle = "Vista de prueba") { pad ->
            Column(
                Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(title = "Peso") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Actual", "72.4 kg", Modifier.weight(1f), sub = "hoy")
                        StatTile("Cambio", "−1.2 kg", Modifier.weight(1f), sub = "30 días", valueColor = SaviaTheme.colors.good)
                    }
                    var r by remember { mutableStateOf(ChartRange.M3) }
                    ChartRangeSelector(r, { r = it })
                    val t = today()
                    val pts = (0..20).map { ChartPoint((t - 60 + it * 3).toDouble(), 74.0 - it * 0.1 + (it % 3) * 0.3) }
                    LineChart(
                        listOf(ChartSeries("Peso", pts, MaterialTheme.colorScheme.primary, fill = true)),
                        hLines = listOf(ChartHLine(71.0, SaviaTheme.colors.good, "Meta 71")),
                        unit = "kg",
                    )
                }
                SectionCard(title = "Formulario") {
                    var d by remember { mutableStateOf(today()) }
                    var ms by remember { mutableStateOf(nowMillis()) }
                    var n by remember { mutableStateOf("72.5") }
                    var lv by remember { mutableStateOf(6) }
                    var c by remember { mutableStateOf<String?>("b") }
                    DateField("Fecha", d, { d = it })
                    DateTimeField(ms, { ms = it })
                    NumberField(n, { n = it }, "Peso", suffix = "kg")
                    LevelSlider("Intensidad", lv, { lv = it }, color = SaviaTheme.colors.intensity(lv))
                    ChoiceChips(listOf("a" to "Ligero", "b" to "Moderado", "c" to "Abundante"), c, { c = it })
                    Pill("Requiere seguimiento", SaviaTheme.colors.warn)
                    InfoNote("Esto es orientativo y no sustituye una consulta médica.")
                }
                SectionCard(title = "Barras") {
                    BarChart(listOf(BarEntry("ene", 28.0), BarEntry("feb", 30.0), BarEntry("mar", 27.0), BarEntry("abr", 29.0)), reference = 28.5)
                    HBarList(listOf("Dolor de cabeza" to 8, "Fatiga" to 5, "Náuseas" to 2))
                    NavRow(Icons.Filled.Favorite, "Presión arterial", {}, subtitle = "120/80 mmHg · ayer")
                }
                Text("fin")
            }
        }
    }
}
