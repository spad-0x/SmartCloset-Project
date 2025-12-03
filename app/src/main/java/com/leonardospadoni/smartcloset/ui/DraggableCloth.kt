package com.leonardospadoni.smartcloset.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leonardospadoni.smartcloset.model.Cloth
import kotlin.math.roundToInt

@Composable
fun DraggableCloth(
    cloth: Cloth,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f
) {
    // Stato per la posizione X e Y
    var offsetX by remember { mutableFloatStateOf(initialOffsetX) }
    var offsetY by remember { mutableFloatStateOf(initialOffsetY) }

    AsyncImage(
        model = cloth.image_url,
        contentDescription = cloth.category,
        contentScale = ContentScale.Fit, // Fit per vedere tutto il capo
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) } // Muove l'immagine
            .size(200.dp) // Dimensione fissa per l'editor
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    )
}
