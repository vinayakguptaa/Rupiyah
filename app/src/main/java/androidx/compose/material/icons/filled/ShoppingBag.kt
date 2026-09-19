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

public val Icons.Filled.ShoppingBag: ImageVector
    get() {
        if (_shoppingBag != null) {
            return _shoppingBag!!
        }
        _shoppingBag = materialIcon(name = "Filled.ShoppingBag") {
            materialPath {
                moveTo(18.0f, 6.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveTo(8.0f, 3.79f, 8.0f, 6.0f)
                horizontalLineTo(6.0f)
                curveTo(4.9f, 6.0f, 4.0f, 6.9f, 4.0f, 8.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveTo(20.0f, 6.9f, 19.1f, 6.0f, 18.0f, 6.0f)
                close()
                moveTo(10.0f, 10.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(12.0f, 4.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                horizontalLineToRelative(-4.0f)
                curveTo(10.0f, 4.9f, 10.9f, 4.0f, 12.0f, 4.0f)
                close()
                moveTo(16.0f, 10.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(10.0f)
                close()
            }
        }
        return _shoppingBag!!
    }

private var _shoppingBag: ImageVector? = null
