package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.IconType
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import app.lawnchair.util.requireSystemService
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SelectIconPreference(
    componentKey: ComponentKey,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = remember(componentKey) {
        val launcherApps: LauncherApps = context.requireSystemService()
        val intent = Intent().setComponent(componentKey.componentName)
        val activity = launcherApps.resolveActivity(intent, componentKey.user)
        activity?.label?.toString() ?: run {
            // Deep shortcuts encode the shortcut id as the class name and won't resolve to an activity.
            // Fall back to the source app's label.
            runCatching {
                val ai = context.packageManager.getApplicationInfo(componentKey.componentName.packageName, 0)
                context.packageManager.getApplicationLabel(ai).toString()
            }.getOrDefault(componentKey.componentName.packageName)
        }
    }
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val repo = IconOverrideRepository.INSTANCE.get(context)

    val applyIcon: (IconPickerItem) -> Unit = { item ->
        scope.launch {
            repo.setOverride(componentKey, item)
            (context as Activity).let {
                it.setResult(Activity.RESULT_OK)
                it.finish()
            }
        }
    }

    OnResult<IconPickerItem> { item -> applyIcon(item) }

    // Find icons from all installed icon packs whose appfilter.xml maps this component
    val suggestedIcons by produceState<List<Pair<String, IconPickerItem>>>(
        initialValue = emptyList(),
        key1 = iconPacks,
    ) {
        value = withContext(Dispatchers.IO) {
            val iconPackProvider = IconPackProvider.INSTANCE.get(context)
            iconPacks.mapNotNull { packInfo ->
                if (packInfo.packageName.isEmpty()) return@mapNotNull null
                val pack = iconPackProvider.getIconPack(packInfo.packageName) ?: return@mapNotNull null
                pack.load()
                val entry = pack.getIcon(componentKey.componentName) ?: return@mapNotNull null
                val item = IconPickerItem(
                    packPackageName = entry.packPackageName,
                    drawableName = entry.name,
                    label = entry.name,
                    type = IconType.Normal,
                )
                packInfo.name to item
            }
        }
    }

    val overrideItem by repo.observeTarget(componentKey).collectAsStateWithLifecycle(initialValue = null)
    val hasOverride = overrideItem != null

    PreferenceLayoutLazyColumn(label = label, modifier = modifier) {
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = true) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        scope.launch {
                            repo.deleteOverride(componentKey)
                            (context as Activity).let {
                                it.setResult(Activity.RESULT_OK)
                                it.finish()
                            }
                        }
                    },
                )
            }
        }
        if (suggestedIcons.isNotEmpty()) {
            val iconPackProvider = IconPackProvider.INSTANCE.get(context)
            preferenceGroupItems(
                count = 1,
                isFirstChild = !hasOverride,
                heading = { stringResource(id = R.string.icon_picker_suggested) },
            ) {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(suggestedIcons, key = { it.second.packPackageName }) { (_, item) ->
                        val pack = remember(item.packPackageName) {
                            iconPackProvider.getIconPack(item.packPackageName)
                        }
                        if (pack != null) {
                            IconPreview(iconPack = pack, iconItem = item, modifier = Modifier.size(64.dp)) {
                                applyIcon(item)
                            }
                        }
                    }
                }
            }
        }
        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = !hasOverride && suggestedIcons.isEmpty(),
            heading = { stringResource(id = R.string.pick_icon_from_label) },
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    if (iconPack.packageName.isEmpty()) {
                        navController.navigate(IconPicker())
                    } else {
                        navController.navigate(IconPicker(iconPack.packageName))
                    }
                },
            )
        }
    }
}
