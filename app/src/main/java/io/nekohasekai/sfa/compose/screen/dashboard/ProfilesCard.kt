package io.nekohasekai.sfa.compose.screen.dashboard

import android.net.Uri
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.qr.QRCodeDialog
import io.nekohasekai.sfa.compose.component.qr.QRSDialog
import io.nekohasekai.sfa.compose.component.qr.QRScanSheet
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.screen.configuration.ProfileImportHandler
import io.nekohasekai.sfa.compose.screen.qrscan.QRScanResult
import io.nekohasekai.sfa.compose.util.ProfileIcons
import io.nekohasekai.sfa.compose.util.QRCodeGenerator
import io.nekohasekai.sfa.compose.util.RelativeTimeFormatter
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.shareProfile
import io.nekohasekai.sfa.utils.ProfileOverflowMenuModel
import io.nekohasekai.sfa.ktx.shareProfileAsJson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesCard(
    profiles: List<Profile>,
    selectedProfileId: Long,
    isLoading: Boolean,
    showAddProfileSheet: Boolean,
    updatingProfileIds: Set<Long> = emptySet(),
    deletingProfileIds: Set<Long> = emptySet(),
    updatedProfileId: Long? = null,
    onProfileSelected: (Long) -> Unit,
    onProfileEdit: (Profile) -> Unit,
    onProfileDelete: (Profile) -> Unit,
    onProfileShare: (Profile) -> Unit,
    onProfileShareURL: (Profile) -> Unit,
    onProfileUpdate: (Profile) -> Unit,
    onProfileMove: (Int, Int) -> Unit,
    onShowAddProfileSheet: () -> Unit,
    onHideAddProfileSheet: () -> Unit,
    onOpenNewProfile: (NewProfileArgs) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val importHandler = remember { ProfileImportHandler(context) }

    var clipboardImporting by remember { mutableStateOf(false) }

    var showQRCodeDialog by remember { mutableStateOf(false) }
    var qrCodeProfile by remember { mutableStateOf<Profile?>(null) }

    var showQRSDialog by remember { mutableStateOf(false) }
    var qrsProfile by remember { mutableStateOf<Profile?>(null) }
    var qrsProfileData by remember { mutableStateOf<ByteArray?>(null) }

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportName by remember { mutableStateOf<String?>(null) }
    var pendingQrsData by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    var showQRScanSheet by remember { mutableStateOf(false) }

    val importFromFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                coroutineScope.launch {
                    when (val parseResult = importHandler.parseUri(uri)) {
                        is ProfileImportHandler.UriParseResult.Success -> {
                            withContext(Dispatchers.Main) {
                                pendingImportName = parseResult.name
                                pendingImportUri = uri
                                showImportConfirmDialog = true
                            }
                        }
                        is ProfileImportHandler.UriParseResult.Error -> {
                            withContext(Dispatchers.Main) {
                                context.errorDialogBuilder(Exception(parseResult.message)).show()
                            }
                        }
                    }
                }
            }
        }

    var exportingProfileId by rememberSaveable { mutableStateOf(-1L) }
    val saveFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) {
                val selectedProfile = profiles.find { it.id == exportingProfileId }
                if (selectedProfile != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val profileData = createProfileContent(selectedProfile)
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(profileData)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                        context,
                                        context.getString(R.string.success_profile_saved),
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                        context,
                                        "${context.getString(R.string.failed_save_profile)}: ${e.message}",
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        }
                    }
                }
            }
        }

    val saveJsonFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                val selectedProfile = profiles.find { it.id == exportingProfileId }
                if (selectedProfile != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val jsonContent = File(selectedProfile.typed.path).readText()
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(jsonContent.toByteArray())
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                        context,
                                        context.getString(R.string.success_profile_saved),
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                        context,
                                        "${context.getString(R.string.failed_save_profile)}: ${e.message}",
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        }
                    }
                }
            }
        }

    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Всего подписок: ${profiles.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }


        }

        Spacer(modifier = Modifier.height(12.dp))

        if (profiles.isEmpty()) {
            Text(
                text = stringResource(R.string.no_profiles),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Flat list — no bottom-sheet picker
            profiles.forEach { profile ->
                val isSelected = profile.id == selectedProfileId
                val rowColor by
                    animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        label = "subscriptionSelection",
                    )
                Surface(
                    onClick = { onProfileSelected(profile.id) },
                    modifier =
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics {
                            selected = isSelected
                        },
                    shape = MaterialTheme.shapes.large,
                    color = rowColor,
                    border =
                        BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else androidx.compose.ui.graphics.Color.Transparent,
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector =
                                ProfileIcons.getIconById(profile.icon)
                                    ?: ProfileIcons.getDefaultIconForType(
                                        profile.typed.type == TypedProfile.Type.Remote
                                    ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                color =
                                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            SubscriptionMetadata(profile)
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ProfileActionRow(
                            profile = profile,
                            isUpdating = profile.id in updatingProfileIds,
                            isDeleting = profile.id in deletingProfileIds,
                            showUpdateSuccess = profile.id == updatedProfileId,
                            onEdit = { profile.let { onProfileEdit(it) } },
                            onDelete = { profile.let { onProfileDelete(it) } },
                            onUpdate = { profile.let { onProfileUpdate(it) } },
                            onShareFile = {
                                profile.let {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            context.shareProfile(it)
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                context.errorDialogBuilder(e).show()
                                            }
                                        }
                                    }
                                }
                            },
                            onSaveFile = {
                                profile.let {
                                    exportingProfileId = it.id
                                    saveFileLauncher.launch("${it.name}.bpf")
                                }
                            },
                            onSaveJson = {
                                profile.let {
                                    exportingProfileId = it.id
                                    saveJsonFileLauncher.launch("${it.name}.json")
                                }
                            },
                            onShareJson = {
                                profile.let {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            context.shareProfileAsJson(it)
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                context.errorDialogBuilder(e).show()
                                            }
                                        }
                                    }
                                }
                            },
                            onShareURL = {
                                profile.let {
                                    qrCodeProfile = it
                                    showQRCodeDialog = true
                                }
                            },
                            onShareQRS = {
                                profile.let { profile ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val data = createProfileContent(profile)
                                            withContext(Dispatchers.Main) {
                                                qrsProfile = profile
                                                qrsProfileData = data
                                                showQRSDialog = true
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                context.errorDialogBuilder(e).show()
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (clipboardImporting) AlertDialog(
        onDismissRequest = {},
        title = { Text("Импорт из буфера обмена") },
        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text("Загрузка и проверка серверов…")
        } },
        confirmButton = {},
    )

    if (showAddProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = onHideAddProfileSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.add_profile),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )

                ListItem(
                    modifier = Modifier.clickable(enabled = !clipboardImporting) {
                        onHideAddProfileSheet()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip?.let { clip ->
                            (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(context).toString() }
                        }.orEmpty().trim()
                        if (text.isBlank()) {
                            Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                        } else {
                            clipboardImporting = true
                            coroutineScope.launch {
                                try {
                                    when (val result = importHandler.importFromQRCode(text)) {
                                        is ProfileImportHandler.ImportResult.Success -> Unit
                                        is ProfileImportHandler.ImportResult.Error -> context.errorDialogBuilder(Exception(result.message)).show()
                                    }
                                } finally { clipboardImporting = false }
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Outlined.ContentPaste, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("Импорт из буфера обмена") },
                    supportingContent = { Text("Ссылка на подписку, ссылки на серверы или конфигурация") },
                )

                ListItem(
                    modifier =
                        Modifier.clickable {
                            onHideAddProfileSheet()
                            importFromFileLauncher.launch("*/*")
                        },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.profile_add_import_file)) },
                    supportingContent = {
                        Text(stringResource(R.string.import_from_file_description))
                    },
                )

                ListItem(
                    modifier =
                        Modifier.clickable {
                            onHideAddProfileSheet()
                            showQRScanSheet = true
                        },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.profile_add_scan_qr_code)) },
                    supportingContent = { Text(stringResource(R.string.scan_qr_code_description)) },
                )

                ListItem(
                    modifier =
                        Modifier.clickable {
                            onHideAddProfileSheet()
                            onOpenNewProfile(NewProfileArgs())
                        },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.profile_add_create_manually))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.create_new_profile_description))
                    },
                )
            }
        }
    }

    if (showQRCodeDialog && qrCodeProfile != null) {
        val profile = qrCodeProfile!!
        val link =
            remember(profile) {
                Libbox.generateRemoteProfileImportLink(profile.name, profile.typed.remoteURL)
            }
        val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
        val qrBitmap = QRCodeGenerator.rememberPrimaryBitmap(link, backgroundColor = surfaceColor)

        QRCodeDialog(
            bitmap = qrBitmap,
            onDismiss = {
                showQRCodeDialog = false
                qrCodeProfile = null
            },
        )
    }

    if (showQRSDialog && qrsProfile != null && qrsProfileData != null) {
        QRSDialog(
            profileData = qrsProfileData!!,
            profileName = qrsProfile!!.name,
            onDismiss = {
                showQRSDialog = false
                qrsProfile = null
                qrsProfileData = null
            },
        )
    }

    if (showImportConfirmDialog && pendingImportName != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportName = null
                pendingQrsData = null
                pendingImportUri = null
            },
            title = { Text(stringResource(R.string.import_profile_confirm_title)) },
            text = {
                Text(stringResource(R.string.import_profile_confirm_message, pendingImportName!!))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        val qrsData = pendingQrsData
                        val importUri = pendingImportUri
                        pendingImportName = null
                        pendingQrsData = null
                        pendingImportUri = null
                        coroutineScope.launch {
                            if (qrsData != null) {
                                when (val result = importHandler.importFromQRSData(qrsData)) {
                                    is ProfileImportHandler.ImportResult.Success -> {
                                        withContext(Dispatchers.Main) {
                                            onProfileEdit(result.profile)
                                        }
                                    }
                                    is ProfileImportHandler.ImportResult.Error -> {
                                        withContext(Dispatchers.Main) {
                                            context
                                                .errorDialogBuilder(Exception(result.message))
                                                .show()
                                        }
                                    }
                                }
                            } else if (importUri != null) {
                                when (val result = importHandler.importFromUri(importUri)) {
                                    is ProfileImportHandler.ImportResult.Success -> {
                                        withContext(Dispatchers.Main) {
                                            onProfileEdit(result.profile)
                                        }
                                    }
                                    is ProfileImportHandler.ImportResult.Error -> {
                                        withContext(Dispatchers.Main) {
                                            context
                                                .errorDialogBuilder(Exception(result.message))
                                                .show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.import_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        pendingImportName = null
                        pendingQrsData = null
                        pendingImportUri = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showQRScanSheet) {
        QRScanSheet(
            onDismiss = { showQRScanSheet = false },
            onScanResult = { result ->
                showQRScanSheet = false
                when (result) {
                    is QRScanResult.QRSData -> {
                        coroutineScope.launch {
                            when (val parseResult = importHandler.parseQRSData(result.data)) {
                                is ProfileImportHandler.QRSParseResult.Success -> {
                                    withContext(Dispatchers.Main) {
                                        pendingImportName = parseResult.name
                                        pendingQrsData = result.data
                                        showImportConfirmDialog = true
                                    }
                                }
                                is ProfileImportHandler.QRSParseResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        context
                                            .errorDialogBuilder(Exception(parseResult.message))
                                            .show()
                                    }
                                }
                            }
                        }
                    }
                    is QRScanResult.RemoteProfile -> {
                        coroutineScope.launch {
                            when (
                                val parseResult = importHandler.parseQRCode(result.uri.toString())
                            ) {
                                is ProfileImportHandler.QRCodeParseResult.RemoteProfile -> {
                                    withContext(Dispatchers.Main) {
                                        onOpenNewProfile(
                                            NewProfileArgs(
                                                importName = parseResult.name,
                                                importUrl = parseResult.url,
                                            )
                                        )
                                    }
                                }
                                is ProfileImportHandler.QRCodeParseResult.LocalProfile -> {
                                    when (
                                        val importResult =
                                            importHandler.importFromQRCode(result.uri.toString())
                                    ) {
                                        is ProfileImportHandler.ImportResult.Success -> {
                                            withContext(Dispatchers.Main) {
                                                onProfileEdit(importResult.profile)
                                            }
                                        }
                                        is ProfileImportHandler.ImportResult.Error -> {
                                            withContext(Dispatchers.Main) {
                                                context
                                                    .errorDialogBuilder(
                                                        Exception(importResult.message)
                                                    )
                                                    .show()
                                            }
                                        }
                                    }
                                }
                                is ProfileImportHandler.QRCodeParseResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        context
                                            .errorDialogBuilder(Exception(parseResult.message))
                                            .show()
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

private suspend fun createProfileContent(profile: Profile): ByteArray {
    val content = ProfileContent()
    content.name = profile.name
    when (profile.typed.type) {
        TypedProfile.Type.Local -> {
            content.type = Libbox.ProfileTypeLocal
        }
        TypedProfile.Type.Remote -> {
            content.type = Libbox.ProfileTypeRemote
        }
    }
    content.config = java.io.File(profile.typed.path).readText()
    content.remotePath = profile.typed.remoteURL
    content.autoUpdate = profile.typed.autoUpdate
    content.autoUpdateInterval = profile.typed.autoUpdateInterval
    content.lastUpdated = profile.typed.lastUpdated.time
    return content.encode()
}

@Composable
private fun ProfileActionRow(
    profile: Profile?,
    isUpdating: Boolean,
    isDeleting: Boolean,
    showUpdateSuccess: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
    onShareFile: () -> Unit,
    onSaveFile: () -> Unit,
    onSaveJson: () -> Unit,
    onShareJson: () -> Unit,
    onShareURL: () -> Unit,
    onShareQRS: () -> Unit,
) {
    if (profile == null) return

    Row(modifier = Modifier, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        if (profile.typed.type == TypedProfile.Type.Remote) {
            ActionButton(
                icon =
                    when {
                        showUpdateSuccess -> Icons.Default.Check
                        else -> Icons.Default.Refresh
                    },
                contentDescription = stringResource(R.string.update_profile),
                onClick = onUpdate,
                enabled = !isUpdating && !showUpdateSuccess,
                isLoading = isUpdating,
            )
        }

        ShareButton(
            profile = profile,
            isDeleting = isDeleting,
            onEdit = onEdit,
            onDelete = onDelete,
            onShareFile = onShareFile,
            onSaveFile = onSaveFile,
            onSaveJson = onSaveJson,
            onShareJson = onShareJson,
            onShareURL = onShareURL,
            onShareQRS = onShareQRS,
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                )
            }
        }
    }
}

@Composable
private fun ShareButton(
    profile: Profile,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareFile: () -> Unit,
    onSaveFile: () -> Unit,
    onSaveJson: () -> Unit,
    onShareJson: () -> Unit,
    onShareURL: () -> Unit,
    onShareQRS: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var shareExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isRemote = profile.typed.type == TypedProfile.Type.Remote

    Box {
        ActionButton(
            icon = Icons.Default.MoreVert,
            contentDescription = "Действия с подпиской",
            onClick = {
                if (isDeleting) return@ActionButton
                shareExpanded = false
                expanded = true
            },
            enabled = !isDeleting,
            isLoading = isDeleting,
        )

        DropdownMenu(
            expanded = expanded && !shareExpanded && !confirmDelete,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Изменить") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Поделиться") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                trailingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                onClick = { shareExpanded = true },
            )
            if (ProfileOverflowMenuModel.DELETE_ENABLED) {
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    enabled = !isDeleting,
                    onClick = {
                        expanded = false
                        confirmDelete = true
                    },
                )
            }
        }

        DropdownMenu(
            expanded = expanded && shareExpanded && !confirmDelete,
            onDismissRequest = {
                shareExpanded = false
                expanded = false
            },
        ) {
            for (action in ProfileOverflowMenuModel.shareActions(isRemote)) {
                when (action) {
                    ProfileOverflowMenuModel.ShareAction.SaveFile ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_as_file)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                shareExpanded = false
                                expanded = false
                                onSaveFile()
                            },
                        )
                    ProfileOverflowMenuModel.ShareAction.ShareFile ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_as_file)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.IosShare,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                shareExpanded = false
                                expanded = false
                                onShareFile()
                            },
                        )
                    ProfileOverflowMenuModel.ShareAction.SaveJson ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_content_json)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DataObject,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                shareExpanded = false
                                expanded = false
                                onSaveJson()
                            },
                        )
                    ProfileOverflowMenuModel.ShareAction.ShareJson ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_content_json)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DataObject,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                shareExpanded = false
                                expanded = false
                                onShareJson()
                            },
                        )
                    ProfileOverflowMenuModel.ShareAction.ShareUrl ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_share_url)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                shareExpanded = false
                                expanded = false
                                onShareURL()
                            },
                        )
                    ProfileOverflowMenuModel.ShareAction.ShareQrs ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_as_qrs)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                shareExpanded = false
                                expanded = false
                                onShareQRS()
                            },
                        )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) confirmDelete = false
            },
            title = { Text("Удалить подписку?") },
            text = {
                Text(
                    "«${profile.name}» будет удалена. Это действие нельзя отменить.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        if (isDeleting) return@TextButton
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { confirmDelete = false },
                ) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun SubscriptionMetadata(profile: Profile) {
    val context = LocalContext.current
    val count by
        produceState<Int?>(null, profile.id, profile.typed.lastUpdated.time, profile.typed.path) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            val outbounds =
                                org.json
                                    .JSONObject(File(profile.typed.path).readText())
                                    .optJSONArray("outbounds")
                            if (outbounds == null) null
                            else
                                (0 until outbounds.length()).count { index ->
                                    outbounds.optJSONObject(index)?.optString("type")?.let {
                                        it !in
                                            setOf("selector", "urltest", "direct", "block", "dns")
                                    } == true
                                }
                        }
                        .getOrNull()
                }
        }
    Text(
        listOfNotNull(
                if (profile.typed.type == TypedProfile.Type.Remote) "Подписка"
                else "Локальный профиль",
                count?.let { "Серверов: $it" },
            )
            .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Обновлено: " + RelativeTimeFormatter.format(context, profile.typed.lastUpdated),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
