package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.navigation.SettingsSection
import com.krtky.financetracker.ui.screens.settings.BanksSettingsContent
import com.krtky.financetracker.ui.screens.settings.BackupSettingsContent
import com.krtky.financetracker.ui.screens.settings.CategoriesSettingsContent
import com.krtky.financetracker.ui.screens.settings.DevSettingsContent
import com.krtky.financetracker.ui.screens.settings.GoogleAuthSettingsContent
import com.krtky.financetracker.ui.screens.settings.LlmSettingsContent
import com.krtky.financetracker.ui.screens.settings.LocationSettingsContent
import com.krtky.financetracker.ui.screens.settings.ProfileSettingsContent
import com.krtky.financetracker.ui.screens.settings.SheetsSettingsContent
import com.krtky.financetracker.ui.screens.settings.SmsSettingsContent
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsDetailScreen(
    section: String,
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val sectionEnum = SettingsSection.fromRoute(section)
    val status by vm.status.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val title = sectionEnum?.title ?: "Settings"
    val listSections = section == "categories" || section == "banks"
    var themePrimary by remember { mutableStateOf(state.themeCustomPrimary) }
    var themeSecondary by remember { mutableStateOf(state.themeCustomSecondary) }
    var themeTertiary by remember { mutableStateOf(state.themeCustomTertiary) }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            StackTopBar(title = title, onBack = onBack)
            if (status != null) {
                Text(status!!, color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            when (section) {
                "profile" -> ProfileSettingsContent(vm)
                "appearance" -> AppearanceSettingsContent(
                    state = state,
                    onDarkModeChange = vm::setDarkModePref,
                    onThemeModeChange = vm::setThemeMode,
                    onPresetChange = vm::setThemePreset,
                    onCustomColorsChange = { p, s, t ->
                        themePrimary = p
                        themeSecondary = s
                        themeTertiary = t
                        vm.setThemeCustomColors(p, s, t)
                    },
                    onSchemeStyleChange = vm::setThemeSchemeStyle,
                    onContrastChange = vm::setContrastLevel,
                    onTypographyModeChange = vm::setTypographyMode,
                    onOledModeChange = vm::setOledMode,
                    themePrimary = themePrimary,
                    themeSecondary = themeSecondary,
                    themeTertiary = themeTertiary,
                )
                "backup" -> BackupSettingsContent(vm)
                "llm" -> LlmSettingsContent(vm)
                "sms" -> SmsSettingsContent(vm)
                "location" -> LocationSettingsContent(vm)
                "sheets" -> SheetsSettingsContent(vm)
                "google_auth" -> GoogleAuthSettingsContent(vm)
                "categories" -> CategoriesSettingsContent(vm)
                "banks" -> BanksSettingsContent(vm)
                "dev" -> DevSettingsContent(vm)
                else -> Text("Unknown section", color = scheme.error)
            }
            Spacer(Modifier.height(if (listSections) 88.dp else 32.dp))
        }
    }
}
