package com.scanrift.android.ui.adaptive

import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier

/**
 * Gives the pane scaffold's focus request somewhere harmless to land.
 *
 * `ThreePaneScaffold` (adaptive-layout 1.2) calls `requestFocus()` on the current
 * destination's pane whenever the destination changes — at launch for the list, on every
 * card opened for the detail. That is meant for keyboard users, but a request on a pane
 * enters it and lands on its first focusable node, and in touch mode clickables are not
 * focusable while text fields always are. So the collection focused its search box, and
 * opening a card focused the quantity field in the floating stepper: the keyboard popped
 * up, and once the main thread got stuck redrawing that field until the app hit an ANR.
 *
 * Put this on the root of each pane's content: the pane itself becomes the first
 * focusable node, so the scaffold's request stops there and no field is focused until
 * it is tapped. Keyboard users land on the pane, as the scaffold intends, and Tab moves
 * on into it.
 *
 * Not `focusProperties { onEnter = { cancelFocusChange() } }`: a field the user taps
 * requests focus with the same `Enter` direction, so cancelling there makes every field
 * in the pane untappable.
 */
fun Modifier.paneFocusLanding(): Modifier = focusable()
