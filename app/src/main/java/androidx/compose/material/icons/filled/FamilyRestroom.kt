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

public val Icons.Filled.FamilyRestroom: ImageVector
    get() {
        if (_familyRestroom != null) {
            return _familyRestroom!!
        }
        _familyRestroom = materialIcon(name = "Filled.FamilyRestroom") {
            materialPath {
                moveTo(16.0f, 4.0f)
                curveToRelative(0.0f, -1.11f, 0.89f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.89f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.89f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveTo(16.0f, 5.11f, 16.0f, 4.0f)
                close()
                moveTo(20.0f, 22.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(2.5f)
                lineToRelative(-2.54f, -7.63f)
                curveTo(19.68f, 7.55f, 18.92f, 7.0f, 18.06f, 7.0f)
                horizontalLineToRelative(-0.12f)
                curveToRelative(-0.86f, 0.0f, -1.63f, 0.55f, -1.9f, 1.37f)
                lineToRelative(-0.86f, 2.58f)
                curveTo(16.26f, 11.55f, 17.0f, 12.68f, 17.0f, 14.0f)
                verticalLineToRelative(8.0f)
                horizontalLineTo(20.0f)
                close()
                moveTo(12.5f, 11.5f)
                curveToRelative(0.83f, 0.0f, 1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveToRelative(-0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveTo(11.0f, 9.17f, 11.0f, 10.0f)
                reflectiveCurveTo(11.67f, 11.5f, 12.5f, 11.5f)
                close()
                moveTo(5.5f, 6.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, -0.89f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.89f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.89f, -2.0f, 2.0f)
                reflectiveCurveTo(4.39f, 6.0f, 5.5f, 6.0f)
                close()
                moveTo(7.5f, 22.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineTo(9.0f)
                verticalLineTo(9.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(4.0f)
                curveTo(2.9f, 7.0f, 2.0f, 7.9f, 2.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(7.0f)
                horizontalLineTo(7.5f)
                close()
                moveTo(14.0f, 22.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.82f, -0.68f, -1.5f, -1.5f, -1.5f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.82f, 0.0f, -1.5f, 0.68f, -1.5f, 1.5f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(4.0f)
                horizontalLineTo(14.0f)
                close()
            }
        }
        return _familyRestroom!!
    }

private var _familyRestroom: ImageVector? = null
