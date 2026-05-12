package com.architectai.feature.build

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.architectai.core.designsystem.sound.LocalSoundEffectManager
import com.architectai.core.designsystem.sound.SoundEffectPlayer
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileType
import com.architectai.feature.build.canvas.CanvasState
import com.architectai.feature.build.canvas.CanvasTile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BuildCanvas(
    viewModel: BuildViewModel,
    modifier: Modifier = Modifier,
    soundEffectPlayer: SoundEffectPlayer = LocalSoundEffectManager.current
) {
    val uiState by viewModel.uiState.collectAsState()
    val canvasState = uiState.canvasState
    val dragState = uiState.dragState

    val gridUnit = 30f // 1 grid unit = 30dp at zoom 1.0

    // Local transform state for smooth zoom/pan
    var currentScale by remember { mutableFloatStateOf(canvasState.scale) }
    var currentOffset by remember { mutableStateOf(canvasState.offset) }

    // Track if a transform gesture is in progress (to suppress tap/drag)
    var isTransforming by remember { mutableStateOf(false) }

    // Haptic feedback
    val view = LocalView.current

    // --- Task 1: Staggered fadeIn + scaleIn animation for new tiles ---
    val animatedTiles = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }

    // Track previously seen tile IDs so we only animate newly added ones
    val currentTileIds = canvasState.tiles.map { it.id }.toSet()
    LaunchedEffect(currentTileIds) {
        val existingIds = animatedTiles.keys.toSet()
        val newIds = currentTileIds - existingIds
        // Remove stale entries (tiles that no longer exist)
        (existingIds - currentTileIds).forEach { animatedTiles.remove(it) }
        // Animate new tiles with staggered delay
        newIds.forEachIndexed { index, id ->
            val animatable = Animatable(0f)
            animatedTiles[id] = animatable
            launch {
                delay(index * 50L) // 50ms stagger per tile
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    // --- Task 2: Snap bounce animation for tiles that just settled ---
    val snapAnimations = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    // Track which tile was last dragged so we can trigger snap on it
    var lastDraggedTileId by remember { mutableStateOf<String?>(null) }

    // Detect when drag ends → start snap bounce animation
    val isDragging = dragState.isDragging
    LaunchedEffect(isDragging) {
        if (!isDragging && lastDraggedTileId != null) {
            val tileId = lastDraggedTileId!!
            val animatable = Animatable(0f)
            snapAnimations[tileId] = animatable
            launch {
                // Bounce: 0 → 1 overshoot then settle back
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.4f,
                        stiffness = Spring.StiffnessHigh
                    )
                )
                // Clean up after animation settles
                snapAnimations.remove(tileId)
            }
            lastDraggedTileId = null
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // --- Task 4: Accessibility content description ---
            .semantics {
                contentDescription = "Build canvas with ${canvasState.tiles.size} tiles. Pinch to zoom, drag to move tiles."
            }
            // Zoom/pan via pinch gesture (two-finger)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    isTransforming = true
                    val newScale = (currentScale * zoom).coerceIn(0.5f, 3f)
                    val newOffset = currentOffset + pan
                    currentScale = newScale
                    currentOffset = newOffset
                    viewModel.updateCanvasTransform(newScale, newOffset)
                }
            }
            // Drag gesture for tile dragging (long press to start, or tap to select)
            .pointerInput(canvasState.tiles) {
                var dragTileId by mutableStateOf<String?>(null)
                var isLongPressDrag by mutableStateOf(false)

                detectDragGestures(
                    onDragStart = { offset ->
                        if (isTransforming) return@detectDragGestures
                        val hitTile = hitTestTile(offset, canvasState.tiles, currentScale, currentOffset)
                        if (hitTile != null) {
                            dragTileId = hitTile.id
                            lastDraggedTileId = hitTile.id
                            isLongPressDrag = true
                            val transformedOffset = screenToCanvas(offset, currentScale, currentOffset)
                            viewModel.startDragging(hitTile.id, transformedOffset)
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragTileId != null) {
                            change.consume()
                            val transformedOffset = screenToCanvas(change.position, currentScale, currentOffset)
                            viewModel.updateDrag(transformedOffset)
                        }
                    },
                    onDragEnd = {
                        if (dragTileId != null) {
                            viewModel.endDrag()
                            // --- Task 3: Enhanced haptics — CONFIRM for snap ---
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            // Sound: tile placed
                            soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.TILE_PLACE)
                        }
                        dragTileId = null
                        isLongPressDrag = false
                        isTransforming = false
                    },
                    onDragCancel = {
                        dragTileId = null
                        isLongPressDrag = false
                        isTransforming = false
                    }
                )
            }
            // Tap gestures for select/delete
            .pointerInput(canvasState.tiles) {
                detectTapGestures(
                    onTap = { offset ->
                        if (isTransforming) {
                            isTransforming = false
                            return@detectTapGestures
                        }
                        val hitTile = hitTestTile(offset, canvasState.tiles, currentScale, currentOffset)
                        viewModel.selectTile(hitTile?.id)
                    }
                )
            }
    ) {
        clipRect {
            translate(left = currentOffset.x, top = currentOffset.y) {
                scale(scale = currentScale, pivot = Offset.Zero) {
                    // Draw grid lines
                    val gridColor = Color(0xFFE0D8CF).copy(alpha = 0.5f)
                    for (x in 0..30) {
                        drawLine(
                            color = gridColor,
                            start = Offset(x * gridUnit, 0f),
                            end = Offset(x * gridUnit, 30 * gridUnit),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0..30) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y * gridUnit),
                            end = Offset(30 * gridUnit, y * gridUnit),
                            strokeWidth = 1f
                        )
                    }

                    // Draw each tile
                    canvasState.tiles.forEach { tile ->
                        val isBeingDragged = dragState.isTileDrag && dragState.draggedTileId == tile.id
                        val placement = tile.placement
                        val bounds = tile.bounds

                        // --- Task 1: Staggered entry animation progress ---
                        val entryProgress = animatedTiles[tile.id]?.value ?: 1f
                        val entryScale = 0.5f + 0.5f * entryProgress // 0.5 → 1.0
                        val entryAlpha = entryProgress // 0 → 1

                        // --- Task 2: Snap bounce animation progress ---
                        val snapProgress = snapAnimations[tile.id]?.value ?: 1f
                        // Snap scale: overshoot to 1.1 then settle to 1.0
                        // Using a decaying bounce: at snapProgress=0.5 → 1.1, at 1.0 → 1.0
                        val snapBounce = if (snapProgress < 1f) {
                            1f + 0.1f * kotlin.math.sin(snapProgress * Math.PI.toFloat()).toFloat() * (1f - snapProgress)
                        } else 1f

                        val combinedScale = entryScale * snapBounce
                        val combinedAlpha = entryAlpha

                        val fillColor = try {
                            Color(android.graphics.Color.parseColor(placement.color.hex))
                        } catch (_: IllegalArgumentException) {
                            Color.Gray
                        }
                        val borderColor = Color(0xFF3D1410)
                        val tileWidth = placement.tileType.widthUnits * gridUnit
                        val tileHeight = placement.tileType.heightUnits * gridUnit

                        // When dragging, offset tile to follow drag position and add visual lift
                        val drawOffset = if (isBeingDragged && dragState.isDragging) {
                            val dragPos = dragState.dragPosition
                            val startBounds = tile.bounds
                            Offset(
                                dragPos.x - startBounds.width / 2,
                                dragPos.y - startBounds.height / 2
                            )
                        } else {
                            Offset(bounds.left, bounds.top)
                        }

                        // Draw shadow under dragged tile for lift effect
                        if (isBeingDragged && dragState.isDragging) {
                            drawTileShadow(
                                x = drawOffset.x + 4f,
                                y = drawOffset.y + 4f,
                                width = tileWidth,
                                height = tileHeight,
                                tileType = placement.tileType,
                                rotation = placement.rotation
                            )
                        }

                        // --- Apply entry/snap scale + alpha around tile center ---
                        val centerX = drawOffset.x + tileWidth / 2f
                        val centerY = drawOffset.y + tileHeight / 2f

                        // Use save/restore-like translate+scale for per-tile transform
                        val alpha = if (isBeingDragged) 0.85f * combinedAlpha else combinedAlpha

                        // Apply scale around tile center using translate + scale
                        translate(left = centerX, top = centerY) {
                            scale(scale = combinedScale, pivot = Offset.Zero) {
                                translate(left = -tileWidth / 2f, top = -tileHeight / 2f) {
                                    when (placement.tileType) {
                                        TileType.SOLID_SQUARE, TileType.WINDOW_SQUARE -> {
                                            val isTranslucent = placement.tileType == TileType.WINDOW_SQUARE

                                            drawRoundRect(
                                                color = if (isTranslucent) fillColor.copy(alpha = 0.6f * alpha) else fillColor.copy(alpha = alpha),
                                                topLeft = Offset.Zero,
                                                size = Size(bounds.width, bounds.height),
                                                cornerRadius = CornerRadius(4f)
                                            )
                                            drawRoundRect(
                                                color = borderColor.copy(alpha = alpha),
                                                style = Stroke(width = 3f),
                                                cornerRadius = CornerRadius(4f),
                                                topLeft = Offset.Zero,
                                                size = Size(bounds.width, bounds.height)
                                            )
                                            if (isTranslucent) {
                                                drawRoundRect(
                                                    color = Color.White.copy(alpha = 0.2f * alpha),
                                                    topLeft = Offset(8f, 8f),
                                                    size = Size(bounds.width - 16, bounds.height - 16),
                                                    cornerRadius = CornerRadius(2f)
                                                )
                                            }
                                        }

                                        TileType.EQUILATERAL_TRIANGLE, TileType.ISOSCELES_TRIANGLE -> {
                                            val path = equilateralTrianglePath(
                                                x = 0f,
                                                y = 0f,
                                                width = tileWidth,
                                                height = tileHeight,
                                                rotation = placement.rotation
                                            )
                                            drawPath(path = path, color = fillColor.copy(alpha = alpha))
                                            drawPath(path = path, color = borderColor.copy(alpha = alpha), style = Stroke(width = 3f))
                                        }

                                        TileType.RIGHT_TRIANGLE -> {
                                            val path = rightTrianglePath(
                                                x = 0f,
                                                y = 0f,
                                                width = tileWidth,
                                                height = tileHeight,
                                                rotation = placement.rotation
                                            )
                                            drawPath(path = path, color = fillColor.copy(alpha = alpha))
                                            drawPath(path = path, color = borderColor.copy(alpha = alpha), style = Stroke(width = 3f))
                                        }
                                    }
                                }
                            }
                        }

                        // Selection highlight
                        if (canvasState.selectedTileId == tile.id && !isBeingDragged) {
                            drawRoundRect(
                                color = Color(0xFFE0542E),
                                style = Stroke(width = 4f),
                                cornerRadius = CornerRadius(4f),
                                topLeft = Offset(bounds.left - 4, bounds.top - 4),
                                size = Size(bounds.width + 8, bounds.height + 8)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hit-test a screen-space offset against the tiles, accounting for canvas scale/offset.
 */
private fun hitTestTile(
    screenOffset: Offset,
    tiles: List<CanvasTile>,
    scale: Float,
    offset: Offset
): CanvasTile? {
    val canvasOffset = screenToCanvas(screenOffset, scale, offset)
    return tiles.firstOrNull { tile ->
        val bounds = tile.bounds
        canvasOffset.x >= bounds.left &&
            canvasOffset.x <= bounds.right &&
            canvasOffset.y >= bounds.top &&
            canvasOffset.y <= bounds.bottom
    }
}

/**
 * Convert screen coordinates to canvas coordinates.
 */
private fun screenToCanvas(screenOffset: Offset, scale: Float, offset: Offset): Offset {
    return Offset(
        (screenOffset.x - offset.x) / scale,
        (screenOffset.y - offset.y) / scale
    )
}

/**
 * Draw a shadow rect/triangle to simulate lift when dragging.
 */
private fun DrawScope.drawTileShadow(
    x: Float, y: Float,
    width: Float, height: Float,
    tileType: TileType,
    rotation: Rotation
) {
    val shadowColor = Color.Black.copy(alpha = 0.25f)
    when (tileType) {
        TileType.SOLID_SQUARE, TileType.WINDOW_SQUARE -> {
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = CornerRadius(4f)
            )
        }
        TileType.EQUILATERAL_TRIANGLE, TileType.ISOSCELES_TRIANGLE -> {
            val path = equilateralTrianglePath(x, y, width, height, rotation)
            drawPath(path = path, color = shadowColor)
        }
        TileType.RIGHT_TRIANGLE -> {
            val path = rightTrianglePath(x, y, width, height, rotation)
            drawPath(path = path, color = shadowColor)
        }
    }
}

/**
 * Equilateral/Isosceles triangle path with rotation support.
 *
 * R0:   apex UP    (flat bottom)
 * R90:  apex RIGHT (flat left)
 * R180: apex DOWN  (flat top)
 * R270: apex LEFT  (flat right)
 */
private fun equilateralTrianglePath(
    x: Float, y: Float,
    width: Float, height: Float,
    rotation: Rotation
): Path = Path().apply {
    when (rotation) {
        Rotation.R0 -> {
            // Apex up, flat bottom
            moveTo(x + width / 2, y)
            lineTo(x + width, y + height)
            lineTo(x, y + height)
        }
        Rotation.R90 -> {
            // Apex right, flat left
            moveTo(x, y)
            lineTo(x + width, y + height / 2)
            lineTo(x, y + height)
        }
        Rotation.R180 -> {
            // Apex down, flat top
            moveTo(x, y)
            lineTo(x + width, y)
            lineTo(x + width / 2, y + height)
        }
        Rotation.R270 -> {
            // Apex left, flat right
            moveTo(x + width, y)
            lineTo(x, y + height / 2)
            lineTo(x + width, y + height)
        }
    }
    close()
}

/**
 * Right triangle path with rotation support.
 *
 * R0:   right angle at TOP-LEFT    (flat TOP + RIGHT)
 * R90:  right angle at TOP-RIGHT   (flat RIGHT + BOTTOM)
 * R180: right angle at BOTTOM-RIGHT (flat BOTTOM + LEFT)
 * R270: right angle at BOTTOM-LEFT (flat LEFT + TOP)
 */
private fun rightTrianglePath(
    x: Float, y: Float,
    width: Float, height: Float,
    rotation: Rotation
): Path = Path().apply {
    when (rotation) {
        Rotation.R0 -> {
            // Right angle at top-left
            moveTo(x, y)
            lineTo(x + width, y)
            lineTo(x + width, y + height)
        }
        Rotation.R90 -> {
            // Right angle at top-right
            moveTo(x + width, y)
            lineTo(x + width, y + height)
            lineTo(x, y + height)
        }
        Rotation.R180 -> {
            // Right angle at bottom-right
            moveTo(x + width, y + height)
            lineTo(x, y + height)
            lineTo(x, y)
        }
        Rotation.R270 -> {
            // Right angle at bottom-left
            moveTo(x, y + height)
            lineTo(x, y)
            lineTo(x + width, y)
        }
    }
    close()
}
