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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import java.util.Locale

enum class DiagramDirection {
  TOP_DOWN,
  LEFT_RIGHT,
}

enum class DiagramNodeShape {
  RECTANGLE,
  ROUNDED,
  DIAMOND,
  CIRCLE,
  STADIUM,
}

@Immutable
data class DiagramNode(
  val id: String,
  val label: String,
  val shape: DiagramNodeShape = DiagramNodeShape.RECTANGLE,
)

@Immutable
data class DiagramEdge(
  val fromId: String,
  val toId: String,
  val label: String = "",
)

@Immutable
data class DiagramSpec(
  val title: String,
  val direction: DiagramDirection,
  val nodes: List<DiagramNode>,
  val edges: List<DiagramEdge>,
)

@Immutable
sealed class DiagramParseResult {
  @Immutable data class Valid(val spec: DiagramSpec) : DiagramParseResult()
  @Immutable
  data class Fallback(
    val reason: String,
    val fallbackSteps: List<String>,
    val rawSource: String,
  ) : DiagramParseResult()
}

/**
 * Safe, bounded Mermaid flowchart & structured diagram parser.
 * Supports `flowchart TD/TB/LR`, `graph TD/LR`, chained node definitions (`A[Start] --> B{Decision}`),
 * arrow labels (`-->|Yes|`, `-- No -->`), and bounded complexity limits.
 */
object AstrynDiagramParser {

  const val MAX_DIAGRAM_NODES = 24
  const val MAX_DIAGRAM_EDGES = 48

  private val HEADER_REGEX =
    Regex("""^(?:flowchart|graph)\s+(TD|TB|LR|RL)\b(.*)$""", RegexOption.IGNORE_CASE)

  private val ARROW_SPLIT_REGEX =
    Regex("""\s*(?:-->\|([^|]+)\||-\.\->\|([^|]+)\||--\s+([^->]+?)\s+-->|==>\|([^|]+)\||-->|-\.\->|==>|---)\s*""")

  private val NODE_STADIUM_REGEX = Regex("""^([A-Za-z0-9_-]+)\s*\(\[(.+?)\]\)$""")
  private val NODE_CIRCLE_REGEX = Regex("""^([A-Za-z0-9_-]+)\s*\(\((.+?)\)\)$""")
  private val NODE_DIAMOND_REGEX = Regex("""^([A-Za-z0-9_-]+)\s*\{(.+?)\}$""")
  private val NODE_ROUNDED_REGEX = Regex("""^([A-Za-z0-9_-]+)\s*\((.+?)\)$""")
  private val NODE_RECT_REGEX = Regex("""^([A-Za-z0-9_-]+)\s*\[(.+?)\]$""")
  private val NODE_BARE_REGEX = Regex("""^([A-Za-z0-9_-]+)$""")

  fun parseDiagram(rawSource: String): DiagramParseResult {
    val normalized = rawSource.replace("\r\n", "\n").replace("\r", "\n").trim()
    if (normalized.isEmpty()) {
      return DiagramParseResult.Fallback("Diagram source is empty.", emptyList(), rawSource)
    }

    val lines =
      normalized.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("%%") }

    if (lines.isEmpty()) {
      return DiagramParseResult.Fallback("No diagram statements found.", emptyList(), normalized)
    }

    var direction = DiagramDirection.TOP_DOWN
    var title = "Process Flowchart"
    var startIdx = 0

    val headerMatch = HEADER_REGEX.matchEntire(lines[0])
    if (headerMatch != null) {
      val dirToken = headerMatch.groupValues[1].uppercase(Locale.ROOT)
      direction = if (dirToken == "LR" || dirToken == "RL") DiagramDirection.LEFT_RIGHT else DiagramDirection.TOP_DOWN
      startIdx = 1
    }

    val nodesMap = LinkedHashMap<String, DiagramNode>()
    val edges = mutableListOf<DiagramEdge>()
    val fallbackLines = mutableListOf<String>()

    fun registerNode(token: String): DiagramNode? {
      val clean = token.trim().trimEnd(';')
      if (clean.isEmpty()) return null
      val parsed = parseSingleNodeToken(clean) ?: return null
      val existing = nodesMap[parsed.id]
      if (existing != null) {
        // Upgrade bare id placeholder if label/shape is discovered later
        val upgraded =
          DiagramNode(
            id = parsed.id,
            label = if (parsed.label != parsed.id) parsed.label else existing.label,
            shape = if (parsed.shape != DiagramNodeShape.RECTANGLE) parsed.shape else existing.shape,
          )
        nodesMap[parsed.id] = upgraded
        return upgraded
      }
      if (nodesMap.size >= MAX_DIAGRAM_NODES) return null
      nodesMap[parsed.id] = parsed
      return parsed
    }

    for (i in startIdx until lines.size) {
      val line = lines[i]
      if (line.startsWith("subgraph ", ignoreCase = true)) {
        val sgTitle = line.removePrefix("subgraph").trim().ifBlank { "Stage" }
        if (title == "Process Flowchart") title = sgTitle
        continue
      }
      if (line.equals("end", ignoreCase = true) || line.startsWith("classDef ", ignoreCase = true) ||
        line.startsWith("style ", ignoreCase = true)
      ) {
        continue
      }
      if (line.startsWith("title:", ignoreCase = true)) {
        title = line.substringAfter(":").trim().ifBlank { title }
        continue
      }

      fallbackLines.add(line)

      val arrowMatches = ARROW_SPLIT_REGEX.findAll(line).toList()
      if (arrowMatches.isEmpty()) {
        registerNode(line)
        continue
      }

      // Support chained edges on one line: A --> B --> C
      var cursor = 0
      var prevNode: DiagramNode? = null
      for (m in arrowMatches) {
        val leftSeg = line.substring(cursor, m.range.first).trim()
        val leftNode = if (leftSeg.isNotEmpty()) registerNode(leftSeg) else prevNode
        val edgeLabel =
          listOfNotNull(
            m.groups[1]?.value,
            m.groups[2]?.value,
            m.groups[3]?.value,
            m.groups[4]?.value,
          ).firstOrNull()?.trim().orEmpty()

        val nextBound =
          arrowMatches.firstOrNull { it.range.first > m.range.last }?.range?.first ?: line.length
        val rightSeg = line.substring(m.range.last + 1, nextBound).trim()
        val rightNode = registerNode(rightSeg)

        if (leftNode != null && rightNode != null && edges.size < MAX_DIAGRAM_EDGES) {
          edges.add(DiagramEdge(fromId = leftNode.id, toId = rightNode.id, label = edgeLabel))
        }
        prevNode = rightNode
        cursor = nextBound
      }
    }

    if (nodesMap.isEmpty()) {
      return DiagramParseResult.Fallback(
        reason = "Unsupported or malformed diagram syntax; displaying structured steps.",
        fallbackSteps = fallbackLines.ifEmpty { lines },
        rawSource = normalized,
      )
    }

    return DiagramParseResult.Valid(
      DiagramSpec(
        title = title,
        direction = direction,
        nodes = nodesMap.values.toList(),
        edges = edges,
      )
    )
  }

  private fun parseSingleNodeToken(raw: String): DiagramNode? {
    fun unquote(s: String) = s.trim().removeSurrounding("\"").removeSurrounding("'")
    NODE_STADIUM_REGEX.matchEntire(raw)?.let {
      return DiagramNode(it.groupValues[1], unquote(it.groupValues[2]), DiagramNodeShape.STADIUM)
    }
    NODE_CIRCLE_REGEX.matchEntire(raw)?.let {
      return DiagramNode(it.groupValues[1], unquote(it.groupValues[2]), DiagramNodeShape.CIRCLE)
    }
    NODE_DIAMOND_REGEX.matchEntire(raw)?.let {
      return DiagramNode(it.groupValues[1], unquote(it.groupValues[2]), DiagramNodeShape.DIAMOND)
    }
    NODE_ROUNDED_REGEX.matchEntire(raw)?.let {
      return DiagramNode(it.groupValues[1], unquote(it.groupValues[2]), DiagramNodeShape.ROUNDED)
    }
    NODE_RECT_REGEX.matchEntire(raw)?.let {
      return DiagramNode(it.groupValues[1], unquote(it.groupValues[2]), DiagramNodeShape.RECTANGLE)
    }
    NODE_BARE_REGEX.matchEntire(raw)?.let {
      return DiagramNode(it.groupValues[1], it.groupValues[1], DiagramNodeShape.RECTANGLE)
    }
    return null
  }

  /**
   * Assigns deterministic topological layers safely (handling cycles with a visited guard).
   */
  fun computeNodeLayers(spec: DiagramSpec): List<List<DiagramNode>> {
    if (spec.nodes.isEmpty()) return emptyList()
    val inDegree = HashMap<String, Int>()
    val adjacency = HashMap<String, MutableList<String>>()
    spec.nodes.forEach { n ->
      inDegree[n.id] = 0
      adjacency[n.id] = mutableListOf()
    }
    spec.edges.forEach { e ->
      if (inDegree.containsKey(e.toId) && adjacency.containsKey(e.fromId) && e.fromId != e.toId) {
        adjacency[e.fromId]!!.add(e.toId)
        inDegree[e.toId] = (inDegree[e.toId] ?: 0) + 1
      }
    }

    val nodeById = spec.nodes.associateBy { it.id }
    val visited = HashSet<String>()
    val layers = mutableListOf<List<DiagramNode>>()

    var frontier = spec.nodes.filter { (inDegree[it.id] ?: 0) == 0 }
    if (frontier.isEmpty()) {
      frontier = listOf(spec.nodes.first())
    }

    while (frontier.isNotEmpty() && layers.size < MAX_DIAGRAM_NODES) {
      val layer = frontier.filter { visited.add(it.id) }
      if (layer.isEmpty()) break
      layers.add(layer)

      val nextCandidates = linkedSetOf<DiagramNode>()
      for (n in layer) {
        for (targetId in adjacency[n.id].orEmpty()) {
          inDegree[targetId] = (inDegree[targetId] ?: 1) - 1
          if (targetId !in visited && (inDegree[targetId] ?: 0) <= 0) {
            nodeById[targetId]?.let { nextCandidates.add(it) }
          }
        }
      }
      frontier =
        if (nextCandidates.isNotEmpty()) {
          nextCandidates.toList()
        } else {
          spec.nodes.firstOrNull { it.id !in visited }?.let { listOf(it) } ?: emptyList()
        }
    }
    return layers
  }
}

/**
 * Interactive native Jetpack Compose Flowchart & Diagram Card with zoom controls,
 * node shapes (process, decision diamond, rounded, circle), labeled directional connectors,
 * horizontal scrolling, and safe text fallback.
 */
@Composable
fun AstrynDiagramCard(
  messageId: String,
  blockIndex: Int,
  parseResult: DiagramParseResult,
  modifier: Modifier = Modifier,
) {
  when (parseResult) {
    is DiagramParseResult.Fallback -> {
      DiagramFallbackCard(
        messageId = messageId,
        blockIndex = blockIndex,
        fallback = parseResult,
        modifier = modifier,
      )
    }
    is DiagramParseResult.Valid -> {
      ValidDiagramCard(
        messageId = messageId,
        blockIndex = blockIndex,
        spec = parseResult.spec,
        modifier = modifier,
      )
    }
  }
}

@Composable
private fun ValidDiagramCard(
  messageId: String,
  blockIndex: Int,
  spec: DiagramSpec,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  var zoomScale by remember(spec) { mutableFloatStateOf(1.0f) }
  val layers = remember(spec) { AstrynDiagramParser.computeNodeLayers(spec) }
  val scrollState = rememberScrollState()

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(glass.surfaceElevated.copy(alpha = 0.78f))
        .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(14.dp))
        .padding(AppSpacing.md)
        .semantics {
          contentDescription = "Flowchart diagram: ${spec.title}"
        }
        .testTag("diagram_block_${messageId}_$blockIndex"),
    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
  ) {
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
          imageVector = Icons.Outlined.AccountTree,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = spec.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = glass.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.ZoomOut,
          contentDescription = "Zoom out diagram",
          tint = glass.textSecondary,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(6.dp))
              .clickable { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.75f) }
              .padding(4.dp)
              .size(15.dp),
        )
        Icon(
          imageVector = Icons.Outlined.ZoomIn,
          contentDescription = "Zoom in diagram",
          tint = glass.accentCyan,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(6.dp))
              .clickable { zoomScale = (zoomScale + 0.15f).coerceAtMost(1.45f) }
              .padding(4.dp)
              .size(15.dp),
        )
      }
    }

    Box(
      modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(vertical = 4.dp),
    ) {
      if (spec.direction == DiagramDirection.LEFT_RIGHT) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          layers.forEachIndexed { layerIdx, layerNodes ->
            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              layerNodes.forEach { node -> DiagramNodeChip(node = node, zoom = zoomScale) }
            }
            if (layerIdx < layers.lastIndex) {
              val edgeLabel =
                spec.edges.firstOrNull { e ->
                  layerNodes.any { it.id == e.fromId } &&
                    layers[layerIdx + 1].any { it.id == e.toId } &&
                    e.label.isNotBlank()
                }?.label.orEmpty()
              DiagramConnectorBadge(isHorizontal = true, label = edgeLabel)
            }
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          layers.forEachIndexed { layerIdx, layerNodes ->
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              layerNodes.forEach { node -> DiagramNodeChip(node = node, zoom = zoomScale) }
            }
            if (layerIdx < layers.lastIndex) {
              val edgeLabel =
                spec.edges.firstOrNull { e ->
                  layerNodes.any { it.id == e.fromId } &&
                    layers[layerIdx + 1].any { it.id == e.toId } &&
                    e.label.isNotBlank()
                }?.label.orEmpty()
              DiagramConnectorBadge(isHorizontal = false, label = edgeLabel)
            }
          }
        }
      }
    }

    // Concise branch legend if conditional/cyclic edges exist
    val branchEdges = spec.edges.filter { it.label.isNotBlank() }
    if (branchEdges.isNotEmpty()) {
      val nodeById = spec.nodes.associateBy { it.id }
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(glass.surfaceSubtle.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        branchEdges.take(6).forEach { edge ->
          val fromLabel = nodeById[edge.fromId]?.label ?: edge.fromId
          val toLabel = nodeById[edge.toId]?.label ?: edge.toId
          Text(
            text = "$fromLabel  —[${edge.label}]→  $toLabel",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = glass.textSecondary,
          )
        }
      }
    }
  }
}

@Composable
private fun DiagramNodeChip(
  node: DiagramNode,
  zoom: Float,
) {
  val glass = AppThemeTokens.glass
  val isDecision = node.shape == DiagramNodeShape.DIAMOND
  val shape =
    when (node.shape) {
      DiagramNodeShape.ROUNDED, DiagramNodeShape.STADIUM -> RoundedCornerShape(99.dp)
      DiagramNodeShape.CIRCLE -> CircleShape
      DiagramNodeShape.DIAMOND, DiagramNodeShape.RECTANGLE -> RoundedCornerShape(10.dp)
    }

  val borderColor = if (isDecision) glass.accentPrimary else glass.accentCyan.copy(alpha = 0.7f)
  val bgColor =
    if (isDecision) glass.accentPrimary.copy(alpha = 0.16f)
    else glass.surfaceStandard.copy(alpha = 0.85f)

  Box(
    modifier =
      Modifier.widthIn(min = (88f * zoom).dp, max = (200f * zoom).dp)
        .clip(shape)
        .background(bgColor)
        .border(1.dp, borderColor, shape)
        .padding(horizontal = (12f * zoom).dp, vertical = (7f * zoom).dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      if (isDecision) {
        Canvas(modifier = Modifier.size(10.dp)) {
          val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
          }
          drawPath(path, color = glass.accentPrimary, style = Stroke(width = 1.5.dp.toPx()))
        }
      }
      Text(
        text = node.label,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = (12f * zoom).sp),
        fontWeight = if (isDecision) FontWeight.Bold else FontWeight.SemiBold,
        color = glass.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun DiagramConnectorBadge(
  isHorizontal: Boolean,
  label: String,
) {
  val glass = AppThemeTokens.glass
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    modifier = Modifier.padding(2.dp),
  ) {
    if (label.isNotBlank()) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
        color = glass.accentCyan,
        modifier =
          Modifier.clip(RoundedCornerShape(4.dp))
            .background(glass.surfaceSubtle.copy(alpha = 0.8f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
      )
    }
    Icon(
      imageVector = if (isHorizontal) Icons.AutoMirrored.Outlined.ArrowForward else Icons.Outlined.ArrowDownward,
      contentDescription = null,
      tint = glass.accentCyan,
      modifier = Modifier.size(15.dp),
    )
  }
}

@Composable
private fun DiagramFallbackCard(
  messageId: String,
  blockIndex: Int,
  fallback: DiagramParseResult.Fallback,
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
        .testTag("diagram_fallback_${messageId}_$blockIndex"),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = fallback.reason,
      style = MaterialTheme.typography.labelSmall,
      color = glass.textSecondary,
      fontWeight = FontWeight.SemiBold,
    )
    fallback.fallbackSteps.take(16).forEach { step ->
      Text(
        text = "• $step",
        style = MaterialTheme.typography.bodySmall,
        color = glass.textPrimary,
      )
    }
  }
}
