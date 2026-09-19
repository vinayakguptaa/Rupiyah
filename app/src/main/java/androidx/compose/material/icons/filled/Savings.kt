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

public val Icons.Filled.Savings: ImageVector
    get() {
        if (_savings != null) {
            return _savings!!
        }
        _savings = materialIcon(name = "Filled.Savings") {
            materialPath {
                moveTo(19.83f, 7.5f)
                lineToRelative(-2.27f, -2.27f)
                curveToRelative(0.07f, -0.42f, 0.18f, -0.81f, 0.32f, -1.15f)
                curveTo(17.96f, 3.9f, 18.0f, 3.71f, 18.0f, 3.5f)
                curveTo(18.0f, 2.67f, 17.33f, 2.0f, 16.5f, 2.0f)
                curveToRelative(-1.64f, 0.0f, -3.09f, 0.79f, -4.0f, 2.0f)
                lineToRelative(-5.0f, 0.0f)
                curveTo(4.46f, 4.0f, 2.0f, 6.46f, 2.0f, 9.5f)
                reflectiveCurveTo(4.5f, 21.0f, 4.5f, 21.0f)
                lineToRelative(5.5f, 0.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                lineToRelative(5.5f, 0.0f)
                lineToRelative(1.68f, -5.59f)
                lineTo(22.0f, 14.47f)
                verticalLineTo(7.5f)
                horizontalLineTo(19.83f)
                close()
                moveTo(13.0f, 9.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(5.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(16.0f, 11.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                curveTo(17.0f, 10.55f, 16.55f, 11.0f, 16.0f, 11.0f)
                close()
            }
        }
        return _savings!!
    }

private var _savings: ImageVector? = null
