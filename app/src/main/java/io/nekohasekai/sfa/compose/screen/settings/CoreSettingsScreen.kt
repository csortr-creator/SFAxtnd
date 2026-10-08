package io.nekohasekai.sfa.compose.screen.settings

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.PowerManager
import android.os.Build
import android.net.Uri
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.clipboardText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CoreSettingsScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.core)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dataSize by remember { mutableStateOf("") }
    val version = remember { Libbox.version() }
    var showVersionMenu by remember { mutableStateOf(false) }

    // Calculate data size on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
            val size =
                filesDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
            val formattedSize = Libbox.formatBytes(size)
            dataSize = formattedSize
        }
    }

    val scaffoldPadding = LocalScaffoldPadding.current

    var isBatteryOptimizationIgnored by remember { mutableStateOf(false) }

    fun refreshBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(PowerManager::class.java)
            isBatteryOptimizationIgnored =
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            isBatteryOptimizationIgnored = true
        }
    }

    val requestBatteryOptimizationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            refreshBatteryOptimizationStatus()
        }

    LaunchedEffect(Unit) { refreshBatteryOptimizationStatus() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(
                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 8.dp,
            ),
    ) {
        // Background / battery optimization — always visible status + action (not a fake switch)
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.allow_background_work),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                supportingContent = {
                    Text(
                        text =
                            if (isBatteryOptimizationIgnored)
                                stringResource(R.string.battery_optimization_ignored)
                            else
                                stringResource(R.string.battery_optimization_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.BatteryChargingFull,
                        contentDescription = null,
                        tint =
                            if (isBatteryOptimizationIgnored)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.tertiary,
                    )
                },
                modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { openBackgroundWorkSettings(context, requestBatteryOptimizationLauncher) },
                colors =
                ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
            )
        }

        NetworkOptionsContent(core = true)

        // Core Information Card
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                // Version Info
                Box {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.core_version_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                version,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showVersionMenu = true },
                            ),
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        DropdownMenu(
                            expanded = showVersionMenu,
                            onDismissRequest = { showVersionMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.per_app_proxy_action_copy)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    clipboardText = version
                                    Toast.makeText(
                                        context,
                                        R.string.copied_to_clipboard,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    showVersionMenu = false
                                },
                            )
                        }
                    }
                }

                // Data Size
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.core_data_size),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            dataSize.ifEmpty { stringResource(R.string.calculating) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier =
                    Modifier.clip(
                        RoundedCornerShape(
                            bottomStart = 12.dp,
                            bottomEnd = 12.dp,
                        ),
                    ),
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        }

        if (version.contains("-")) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.beta_settings),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )

            Card(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
            }
        }

        // Working Directory Section
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.working_directory),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        // Working Directory Card
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            // Browse
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.browse),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier =
                Modifier
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .clickable {
                        openInFileManager(context)
                    },
                colors =
                ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
            )

            // Destroy
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.destroy),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier =
                Modifier
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .clickable {
                        scope.launch(Dispatchers.IO) {
                            val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
                            filesDir.deleteRecursively()
                            filesDir.mkdirs()

                            // Recalculate data size
                            val newSize =
                                filesDir.walkTopDown()
                                    .filter { it.isFile }
                                    .map { it.length() }
                                    .sum()
                            val formattedSize = Libbox.formatBytes(newSize)
                            dataSize = formattedSize
                        }
                    },
                colors =
                ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}


private fun openBackgroundWorkSettings(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        Toast.makeText(context, R.string.battery_optimization_ignored, Toast.LENGTH_SHORT).show()
        return
    }
    val packageUri = Uri.parse("package:${context.packageName}")
    val candidates =
        listOf(
            Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                packageUri,
            ),
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                packageUri,
            ),
        )
    for (intent in candidates) {
        try {
            launcher.launch(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // try next
        } catch (_: SecurityException) {
            // try next
        }
    }
    Toast.makeText(context, R.string.battery_settings_unavailable, Toast.LENGTH_SHORT).show()
}

private fun openInFileManager(context: Context) {
    val authority = "${context.packageName}.workingdir"
    val rootUri = DocumentsContract.buildRootUri(authority, "working_directory")

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(rootUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.no_file_manager),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
