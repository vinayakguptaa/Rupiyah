/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Filled.WavingHand: ImageVector
    get() {
        if (_wavingHand != null) {
            return _wavingHand!!
        }
        _wavingHand = materialIcon(name = "Filled.WavingHand") {
            materialPath {
                moveTo(23.0f, 17.0f)
                curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                verticalLineToRelative(-1.5f)
                curveToRelative(2.48f, 0.0f, 4.5f, -2.02f, 4.5f, -4.5f)
                horizontalLineTo(23.0f)
                close()
                moveTo(1.0f, 7.0f)
                curveToRelative(0.0f, -3.31f, 2.69f, -6.0f, 6.0f, -6.0f)
                verticalLineToRelative(1.5f)
                curveTo(4.52f, 2.5f, 2.5f, 4.52f, 2.5f, 7.0f)
                horizontalLineTo(1.0f)
                close()
                moveTo(8.01f, 4.32f)
                lineToRelative(-4.6f, 4.6f)
                curveToRelative(-3.22f, 3.22f, -3.22f, 8.45f, 0.0f, 11.67f)
                reflectiveCurveToRelative(8.45f, 3.22f, 11.67f, 0.0f)
                lineToRelative(7.07f, -7.07f)
                curveToRelative(0.49f, -0.49f, 0.49f, -1.28f, 0.0f, -1.77f)
                curveToRelative(-0.49f, -0.49f, -1.28f, -0.49f, -1.77f, 0.0f)
                lineToRelative(-4.42f, 4.42f)
                lineToRelative(-0.71f, -0.71f)
                lineToRelative(6.54f, -6.54f)
                curveToRelative(0.49f, -0.49f, 0.49f, -1.28f, 0.0f, -1.77f)
                reflectiveCurveToRelative(-1.28f, -0.49f, -1.77f, 0.0f)
                lineToRelative(-5.83f, 5.83f)
                lineToRelative(-0.71f, -0.71f)
                lineToRelative(6.89f, -6.89f)
                curveToRelative(0.49f, -0.49f, 0.49f, -1.28f, 0.0f, -1.77f)
                reflectiveCurveToRelative(-1.28f, -0.49f, -1.77f, 0.0f)
                lineToRelative(-6.89f, 6.89f)
                lineTo(11.02f, 9.8f)
                lineToRelative(5.48f, -5.48f)
                curveToRelative(0.49f, -0.49f, 0.49f, -1.28f, 0.0f, -1.77f)
                reflectiveCurveToRelative(-1.28f, -0.49f, -1.77f, 0.0f)
                lineToRelative(-7.62f, 7.62f)
                curveToRelative(1.22f, 1.57f, 1.11f, 3.84f, -0.33f, 5.28f)
                lineToRelative(-0.71f, -0.71f)
                curveToRelative(1.17f, -1.17f, 1.17f, -3.07f, 0.0f, -4.24f)
                lineToRelative(-0.35f, -0.35f)
                lineToRelative(4.07f, -4.07f)
                curveToRelative(0.49f, -0.49f, 0.49f, -1.28f, 0.0f, -1.77f)
                curveTo(9.29f, 3.83f, 8.5f, 3.83f, 8.01f, 4.32f)
                close()
            }
        }
        return _wavingHand!!
    }

private var _wavingHand: ImageVector? = null
