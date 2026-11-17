package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AstrynChartType {
  BAR,
  LINE,
  PIE,
  COMPARISON,
  TIME_SERIES,
}

enum class ChartDataProvenance(val badgeText: String) {
  OBSERVED("Observed"),
  CALCULATED("Calculated"),
  FORECAST_OR_ESTIMATE("Forecast / Estimate"),
  UNSPECIFIED(""),
}

@Immutable
data class ChartSeries(
  val name: String,
  val values: List<Double>,
  val provenance: ChartDataProvenance = ChartDataProvenance.UNSPECIFIED,
)

@Immutable
data class AstrynChartSpec(
  val title: String,
  val type: AstrynChartType,
  val labels: List<String>,
  val series: List<ChartSeries>,
  val xAxisLabel: String = "",
  val yAxisLabel: String = "",
  val unit: String = "",
)

@Immutable
sealed class ChartParseResult {
  @Immutable data class Valid(val spec: AstrynChartSpec) : ChartParseResult()
  @Immutable data class InvalidOrEmpty(val reason: String, val rawFallback: String) : ChartParseResult()
}

/**
 * Deterministic chart specification parser with strict data-integrity validation:
 * - Never invents missing or non-numeric values.
 * - Rejects empty datasets, NaN/Infinite numbers, or invalid pie sums cleanly.
 * - Distinguishes observed/calculated series from forecast/estimate series when declared.
 */
object AstrynChartParser {

  private const val MAX_SERIES_COUNT = 6
  private const val MAX_DATA_POINTS = 48

  fun parseChartBlock(rawSource: String): ChartParseResult {
    val trimmed = rawSource.trim()
    if (trimmed.isEmpty()) {
      return ChartParseResult.InvalidOrEmpty(
        reason = "Chart specification is empty.",
        rawFallback = rawSource,
      )
    }

    return try {
      val root = JSONObject(trimmed)
      val title = root.optString("title", "").trim().ifBlank { "Data Visualization" }
      val rawType = root.optString("type", "bar").trim().lowercase(Locale.ROOT)
      val chartType =
        when (rawType) {
          "line" -> AstrynChartType.LINE
          "pie", "donut" -> AstrynChartType.PIE
          "comparison", "compare" -> AstrynChartType.COMPARISON
          "time_series", "timeseries", "trend" -> AstrynChartType.TIME_SERIES
          else -> AstrynChartType.BAR
        }

      val labelsArray = root.optJSONArray("labels")
      val labels =
        buildList {
          if (labelsArray != null) {
            val count = min(labelsArray.length(), MAX_DATA_POINTS)
            for (i in 0 until count) {
              add(labelsArray.optString(i, "Item ${i + 1}").trim().ifBlank { "Item ${i + 1}" })
            }
          }
        }

      val xAxis = root.optString("xAxis", root.optString("x_axis", "")).trim()
      val yAxis = root.optString("yAxis", root.optString("y_axis", "")).trim()
      val unit = root.optString("unit", "").trim()

      val defaultProvenance = parseProvenance(root.optString("provenance", ""))
      val seriesList = mutableListOf<ChartSeries>()

      val seriesArray = root.optJSONArray("series")
      if (seriesArray != null && seriesArray.length() > 0) {
        val count = min(seriesArray.length(), MAX_SERIES_COUNT)
        for (sIdx in 0 until count) {
          val obj = seriesArray.optJSONObject(sIdx) ?: continue
          val name = obj.optString("name", "Series ${sIdx + 1}").trim().ifBlank { "Series ${sIdx + 1}" }
          val vals = parseFiniteNumberArray(obj.optJSONArray("values"))
          if (vals == null || vals.isEmpty()) {
            return ChartParseResult.InvalidOrEmpty(
              reason = "Series '$name' contains non-numeric or missing values.",
              rawFallback = trimmed,
            )
          }
          val prov = parseProvenance(obj.optString("provenance", "")).let {
            if (it == ChartDataProvenance.UNSPECIFIED) defaultProvenance else it
          }
          seriesList.add(ChartSeries(name = name, values = vals, provenance = prov))
        }
      } else {
        val valuesArr = root.optJSONArray("values")
        val vals = parseFiniteNumberArray(valuesArr)
        if (vals == null || vals.isEmpty()) {
          return ChartParseResult.InvalidOrEmpty(
            reason = "Chart requires numeric values; none were found.",
            rawFallback = trimmed,
          )
        }
        val seriesName = root.optString("seriesName", yAxis.ifBlank { "Value" }).trim()
        seriesList.add(ChartSeries(name = seriesName, values = vals, provenance = defaultProvenance))
      }

      if (seriesList.isEmpty()) {
        return ChartParseResult.InvalidOrEmpty(
          reason = "No valid chart series present.",
          rawFallback = trimmed,
        )
      }

      val resolvedLabels =
        if (labels.isNotEmpty()) {
          labels
        } else {
          val pointCount = seriesList.maxOf { it.values.size }
          (1..pointCount).map { "P$it" }
        }

      // Normalize series length only against explicit labels if mismatch is unreasonable, or reject empty
      val minCount = seriesList.minOf { it.values.size }
      if (minCount == 0) {
        return ChartParseResult.InvalidOrEmpty(
          reason = "Series contains zero data points.",
          rawFallback = trimmed,
        )
      }

      if (chartType == AstrynChartType.PIE) {
        val pieValues = seriesList.first().values
        if (pieValues.any { it < 0.0 } || pieValues.sum() <= 0.0) {
          return ChartParseResult.InvalidOrEmpty(
            reason = "Pie chart values must be non-negative with a positive total.",
            rawFallback = trimmed,
          )
        }
      }

      ChartParseResult.Valid(
        AstrynChartSpec(
          title = title,
          type = chartType,
          labels = resolvedLabels,
          series = seriesList,
          xAxisLabel = xAxis,
          yAxisLabel = yAxis,
          unit = unit,
        )
      )
    } catch (_: Exception) {
      ChartParseResult.InvalidOrEmpty(
        reason = "Malformed chart specification; showing raw data safely.",
        rawFallback = trimmed,
      )
    }
  }

  private fun parseFiniteNumberArray(array: JSONArray?): List<Double>? {
    if (array == null || array.length() == 0) return null
    val out = ArrayList<Double>(min(array.length(), MAX_DATA_POINTS))
    val limit = min(array.length(), MAX_DATA_POINTS)
    for (i in 0 until limit) {
      val raw = array.opt(i) ?: return null
      val num =
        when (raw) {
          is Number -> raw.toDouble()
          is String -> raw.trim().replace(",", "").toDoubleOrNull() ?: return null
          else -> return null
        }
      if (!num.isFinite()) return null
      out.add(num)
    }
    return out
  }

  private fun parseProvenance(raw: String): ChartDataProvenance {
    return when (raw.trim().lowercase(Locale.ROOT)) {
      "observed", "measured", "actual", "verified" -> ChartDataProvenance.OBSERVED
      "calculated", "computed", "derived" -> ChartDataProvenance.CALCULATED
      "forecast", "estimate", "estimated", "projected", "approx" -> ChartDataProvenance.FORECAST_OR_ESTIMATE
      else -> ChartDataProvenance.UNSPECIFIED
    }
  }

  fun formatChartNumber(value: Double, unit: String = ""): String {
    val formatted =
      if (abs(value - value.roundToInt()) < 1e-6 && abs(value) < 1_000_000_000) {
        value.roundToInt().toString()
      } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
      }
    return if (unit.isNotBlank()) "$formatted$unit" else formatted
  }
}

private val CHART_PALETTE =
  listOf(
    Color(0xFF38BDF8), // Cyan
    Color(0xFF818CF8), // Indigo
    Color(0xFF34D399), // Emerald
    Color(0xFFFBBF24), // Amber
    Color(0xFFF472B6), // Pink
    Color(0xFFA78BFA), // Violet
  )

/**
 * Pure Jetpack Compose Canvas Chart Card for BAR, LINE, PIE, COMPARISON, and TIME_SERIES data.
 */
@Composable
fun AstrynChartCard(
  messageId: String,
  blockIndex: Int,
  parseResult: ChartParseResult,
  modifier: Modifier = Modifier,
) {
  when (parseResult) {
    is ChartParseResult.InvalidOrEmpty -> {
      AstrynChartFallbackCard(
        messageId = messageId,
        blockIndex = blockIndex,
        reason = parseResult.reason,
        rawFallback = parseResult.rawFallback,
        modifier = modifier,
      )
    }
    is ChartParseResult.Valid -> {
      AstrynValidChartCard(
        messageId = messageId,
        blockIndex = blockIndex,
        spec = parseResult.spec,
        modifier = modifier,
      )
    }
  }
}

@Composable
private fun AstrynValidChartCard(
  messageId: String,
  blockIndex: Int,
  spec: AstrynChartSpec,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  var selectedPointIndex by remember(spec) { mutableIntStateOf(-1) }
  val scrollState = rememberScrollState()

  val icon =
    when (spec.type) {
      AstrynChartType.PIE -> Icons.Outlined.PieChart
      AstrynChartType.LINE, AstrynChartType.TIME_SERIES -> Icons.AutoMirrored.Outlined.ShowChart
      AstrynChartType.BAR, AstrynChartType.COMPARISON -> Icons.Outlined.BarChart
    }

  val provenanceBadge =
    spec.series.map { it.provenance }.firstOrNull { it != ChartDataProvenance.UNSPECIFIED }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(glass.surfaceElevated.copy(alpha = 0.78f))
        .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(14.dp))
        .padding(AppSpacing.md)
        .semantics {
          contentDescription = "${spec.type.name.lowercase()} chart: ${spec.title}"
        }
        .testTag("chart_block_${messageId}_$blockIndex"),
    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = spec.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = glass.textPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (provenanceBadge != null && provenanceBadge.badgeText.isNotBlank()) {
        Text(
          text = provenanceBadge.badgeText,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          fontWeight = FontWeight.SemiBold,
          color =
            if (provenanceBadge == ChartDataProvenance.FORECAST_OR_ESTIMATE) glass.statusWarning
            else glass.accentCyan,
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .background(glass.surfaceStandard.copy(alpha = 0.7f))
              .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(6.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }

    // Selected data point readout (interactive inspection)
    val pointCount = min(spec.labels.size, spec.series.minOf { it.values.size })
    if (selectedPointIndex in 0 until pointCount) {
      val label = spec.labels[selectedPointIndex]
      val summaryText =
        spec.series.joinToString("  •  ") { s ->
          "${s.name}: ${AstrynChartParser.formatChartNumber(s.values[selectedPointIndex], spec.unit)}"
        }
      Text(
        text = "$label — $summaryText",
        style = MaterialTheme.typography.labelSmall,
        color = glass.accentCyan,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("chart_selection_readout_${messageId}_$blockIndex"),
      )
    }

    if (spec.type == AstrynChartType.PIE) {
      PieChartBody(
        spec = spec,
        selectedIndex = selectedPointIndex,
        onSelectSlice = { idx -> selectedPointIndex = if (selectedPointIndex == idx) -1 else idx },
      )
    } else {
      val needsHorizontalScroll = pointCount > 7
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .then(if (needsHorizontalScroll) Modifier.horizontalScroll(scrollState) else Modifier),
      ) {
        val chartMinWidth = if (needsHorizontalScroll) (pointCount * 54).dp else 280.dp
        CartesianChartBody(
          spec = spec,
          pointCount = pointCount,
          selectedIndex = selectedPointIndex,
          onSelectPoint = { idx -> selectedPointIndex = if (selectedPointIndex == idx) -1 else idx },
          modifier =
            if (needsHorizontalScroll) Modifier.width(chartMinWidth) else Modifier.fillMaxWidth(),
        )
      }
    }

    // Legend for multi-series Cartesian charts
    if (spec.type != AstrynChartType.PIE && spec.series.size > 1) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        spec.series.forEachIndexed { idx, s ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Box(
              modifier =
                Modifier.size(9.dp)
                  .clip(CircleShape)
                  .background(CHART_PALETTE[idx % CHART_PALETTE.size])
            )
            Text(
              text = s.name,
              style = MaterialTheme.typography.labelSmall,
              color = glass.textSecondary,
            )
          }
        }
      }
    }

    // Axis description footer when provided
    if (spec.xAxisLabel.isNotBlank() || spec.yAxisLabel.isNotBlank()) {
      val axisLine =
        listOfNotNull(
          spec.xAxisLabel.takeIf { it.isNotBlank() }?.let { "X: $it" },
          spec.yAxisLabel.takeIf { it.isNotBlank() }?.let { "Y: $it" },
        ).joinToString("   |   ")
      Text(
        text = axisLine,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
        color = glass.textMuted,
      )
    }
  }
}

@Composable
private fun CartesianChartBody(
  spec: AstrynChartSpec,
  pointCount: Int,
  selectedIndex: Int,
  onSelectPoint: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val allValues = spec.series.flatMap { it.values.take(pointCount) }
  val rawMax = allValues.maxOrNull() ?: 1.0
  val rawMin = min(0.0, allValues.minOrNull() ?: 0.0)
  val span = (rawMax - rawMin).let { if (it < 1e-9) 1.0 else it }

  val gridColor = glass.borderSubtle.copy(alpha = 0.5f)

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().height(176.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Y-Axis Ticks
      Column(
        modifier = Modifier.height(164.dp).padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
      ) {
        Text(
          text = AstrynChartParser.formatChartNumber(rawMax, spec.unit),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
          color = glass.textMuted,
        )
        Text(
          text = AstrynChartParser.formatChartNumber((rawMax + rawMin) / 2.0, spec.unit),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
          color = glass.textMuted,
        )
        Text(
          text = AstrynChartParser.formatChartNumber(rawMin, spec.unit),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
          color = glass.textMuted,
        )
      }

      Canvas(
        modifier =
          Modifier.weight(1f)
            .height(164.dp)
            .pointerInput(pointCount) {
              detectTapGestures { tapOffset ->
                if (pointCount > 0 && size.width > 0) {
                  val slotWidth = size.width.toFloat() / pointCount
                  val idx = (tapOffset.x / slotWidth).toInt().coerceIn(0, pointCount - 1)
                  onSelectPoint(idx)
                }
              }
            },
      ) {
        val w = size.width
        val h = size.height
        val topPad = 12.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val chartH = max(1f, h - topPad - bottomPad)

        // Horizontal Gridlines
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        for (step in 0..2) {
          val y = topPad + (chartH * step / 2f)
          drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (step < 2) dash else null,
          )
        }

        val zeroFraction = ((0.0 - rawMin) / span).toFloat().coerceIn(0f, 1f)
        val zeroY = topPad + chartH * (1f - zeroFraction)

        if (spec.type == AstrynChartType.LINE || spec.type == AstrynChartType.TIME_SERIES) {
          val slotW = w / max(1, pointCount)
          spec.series.forEachIndexed { sIdx, series ->
            val color = CHART_PALETTE[sIdx % CHART_PALETTE.size]
            val pts =
              (0 until pointCount).map { i ->
                val norm = ((series.values[i] - rawMin) / span).toFloat().coerceIn(0f, 1f)
                Offset(
                  x = (i + 0.5f) * slotW,
                  y = topPad + chartH * (1f - norm),
                )
              }
            if (pts.size >= 2) {
              val linePath = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (p in pts.drop(1)) lineTo(p.x, p.y)
              }
              if (spec.series.size == 1) {
                val fillPath = Path().apply {
                  addPath(linePath)
                  lineTo(pts.last().x, zeroY)
                  lineTo(pts.first().x, zeroY)
                  close()
                }
                drawPath(
                  path = fillPath,
                  brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.28f), Color.Transparent),
                    startY = topPad,
                    endY = zeroY,
                  ),
                )
              }
              drawPath(
                path = linePath,
                color = color,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
              )
            }
            pts.forEachIndexed { i, pt ->
              val isSel = i == selectedIndex
              drawCircle(
                color = if (isSel) Color.White else color,
                radius = if (isSel) 5.5.dp.toPx() else 3.8.dp.toPx(),
                center = pt,
              )
              if (isSel) {
                drawCircle(
                  color = color,
                  radius = 8.dp.toPx(),
                  center = pt,
                  style = Stroke(width = 1.5.dp.toPx()),
                )
              }
            }
          }
        } else {
          // BAR / COMPARISON grouped bars
          val slotW = w / max(1, pointCount)
          val groupPadding = slotW * 0.16f
          val usableGroupW = max(4f, slotW - groupPadding * 2f)
          val barW = max(4f, usableGroupW / max(1, spec.series.size) - 2.dp.toPx())

          for (i in 0 until pointCount) {
            val isSel = i == selectedIndex
            val groupStart = i * slotW + groupPadding
            spec.series.forEachIndexed { sIdx, series ->
              val v = series.values[i]
              val norm = ((v - rawMin) / span).toFloat().coerceIn(0f, 1f)
              val valY = topPad + chartH * (1f - norm)
              val barTop = min(valY, zeroY)
              val barHeight = max(2.dp.toPx(), abs(zeroY - valY))
              val color = CHART_PALETTE[sIdx % CHART_PALETTE.size]
              val x = groupStart + sIdx * (barW + 2.dp.toPx())
              drawRoundRect(
                color = if (isSel) color.copy(alpha = 1f) else color.copy(alpha = 0.85f),
                topLeft = Offset(x, barTop),
                size = Size(barW, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
              )
            }
          }
        }
      }
    }

    // X-Axis Labels + quick top values
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 34.dp),
      horizontalArrangement = Arrangement.SpaceAround,
    ) {
      for (i in 0 until pointCount) {
        val isSel = i == selectedIndex
        Column(
          modifier =
            Modifier.weight(1f)
              .clip(RoundedCornerShape(6.dp))
              .clickable { onSelectPoint(i) }
              .padding(vertical = 2.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = spec.labels[i],
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
            color = if (isSel) glass.accentCyan else glass.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (spec.series.size == 1) {
            Text(
              text = AstrynChartParser.formatChartNumber(spec.series[0].values[i], spec.unit),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
              color = glass.textMuted,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PieChartBody(
  spec: AstrynChartSpec,
  selectedIndex: Int,
  onSelectSlice: (Int) -> Unit,
) {
  val glass = AppThemeTokens.glass
  val values = spec.series.first().values
  val count = min(spec.labels.size, values.size)
  val total = values.take(count).sum().coerceAtLeast(1e-9)

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
  ) {
    Canvas(modifier = Modifier.size(132.dp).padding(4.dp)) {
      var startAngle = -90f
      for (i in 0 until count) {
        val sweep = ((values[i] / total) * 360.0).toFloat()
        val color = CHART_PALETTE[i % CHART_PALETTE.size]
        val isSel = i == selectedIndex
        drawArc(
          color = color,
          startAngle = startAngle,
          sweepAngle = sweep,
          useCenter = true,
          topLeft = if (isSel) Offset(-3f, -3f) else Offset.Zero,
          size = if (isSel) Size(size.width + 6f, size.height + 6f) else size,
        )
        startAngle += sweep
      }
      // Subtle center donut cutout for Glassmorphism polish
      drawCircle(
        color = if (glass.isDark) Color(0xFF0D1426) else Color(0xFFF8FAFC),
        radius = size.minDimension * 0.24f,
      )
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      for (i in 0 until count) {
        val pct = (values[i] / total) * 100.0
        val color = CHART_PALETTE[i % CHART_PALETTE.size]
        val isSel = i == selectedIndex
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSel) color.copy(alpha = 0.14f) else Color.Transparent)
              .clickable { onSelectSlice(i) }
              .padding(horizontal = 6.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
            Text(
              text = spec.labels[i],
              style = MaterialTheme.typography.labelSmall,
              color = glass.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${AstrynChartParser.formatChartNumber(values[i], spec.unit)} (${String.format(Locale.US, "%.1f", pct)}%)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = glass.textSecondary,
          )
        }
      }
    }
  }
}

@Composable
private fun AstrynChartFallbackCard(
  messageId: String,
  blockIndex: Int,
  reason: String,
  rawFallback: String,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(glass.surfaceSubtle.copy(alpha = 0.72f))
        .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(12.dp))
        .padding(AppSpacing.sm)
        .testTag("chart_fallback_${messageId}_$blockIndex"),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = null,
        tint = glass.statusWarning,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = reason,
        style = MaterialTheme.typography.labelSmall,
        color = glass.statusWarning,
        fontWeight = FontWeight.SemiBold,
      )
    }
    if (rawFallback.isNotBlank()) {
      Text(
        text = rawFallback,
        style = MaterialTheme.typography.bodySmall,
        color = glass.textSecondary,
      )
    }
  }
}
