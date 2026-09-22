package com.personal.inkpad.engine

import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType

/**
 * Light palm rejection helpers.
 * We only treat Stylus/Eraser/Mouse as "pen" pointers. Finger can still write
 * (many Android styluses report as Touch). While a pen stroke is active,
 * extra finger contacts are ignored for inking so the palm doesn't scribble.
 */
object PalmRejection {
    fun PointerInputChange.isInkPointer(): Boolean =
        type == PointerType.Stylus || type == PointerType.Eraser || type == PointerType.Mouse

    fun PointerInputChange.isFinger(): Boolean = type == PointerType.Touch
}
