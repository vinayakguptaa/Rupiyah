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

public val Icons.Filled.VolunteerActivism: ImageVector
    get() {
        if (_volunteerActivism != null) {
            return _volunteerActivism!!
        }
        _volunteerActivism = materialIcon(name = "Filled.VolunteerActivism") {
            materialPath {
                moveTo(1.0f, 11.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(11.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
            materialPath {
                moveTo(16.0f, 3.25f)
                curveTo(16.65f, 2.49f, 17.66f, 2.0f, 18.7f, 2.0f)
                curveTo(20.55f, 2.0f, 22.0f, 3.45f, 22.0f, 5.3f)
                curveToRelative(0.0f, 2.27f, -2.91f, 4.9f, -6.0f, 7.7f)
                curveToRelative(-3.09f, -2.81f, -6.0f, -5.44f, -6.0f, -7.7f)
                curveTo(10.0f, 3.45f, 11.45f, 2.0f, 13.3f, 2.0f)
                curveTo(14.34f, 2.0f, 15.35f, 2.49f, 16.0f, 3.25f)
                close()
            }
            materialPath {
                moveTo(20.0f, 17.0f)
                horizontalLineToRelative(-7.0f)
                lineToRelative(-2.09f, -0.73f)
                lineToRelative(0.33f, -0.94f)
                lineTo(13.0f, 16.0f)
                horizontalLineToRelative(2.82f)
                curveToRelative(0.65f, 0.0f, 1.18f, -0.53f, 1.18f, -1.18f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.49f, -0.31f, -0.93f, -0.77f, -1.11f)
                lineTo(8.97f, 11.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(9.02f)
                lineTo(14.0f, 22.0f)
                lineToRelative(8.01f, -3.0f)
                verticalLineToRelative(0.0f)
                curveTo(22.0f, 17.9f, 21.11f, 17.0f, 20.0f, 17.0f)
                close()
            }
        }
        return _volunteerActivism!!
    }

private var _volunteerActivism: ImageVector? = null
