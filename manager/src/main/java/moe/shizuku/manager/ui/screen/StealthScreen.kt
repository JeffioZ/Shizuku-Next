package moe.shizuku.manager.ui.screen

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.shizuku.manager.R
import moe.shizuku.manager.stealth.Action
import moe.shizuku.manager.stealth.ApkType
import moe.shizuku.manager.stealth.StealthTutorialViewModel
import moe.shizuku.manager.stealth.UiState
import moe.shizuku.manager.stealth.validatePackageName
import moe.shizuku.manager.utils.ApkUtils.ORIGINAL_PACKAGE_NAME
import moe.shizuku.manager.utils.ApkUtils.buildApkFilename
import moe.shizuku.manager.utils.ApkUtils.installPackage
import moe.shizuku.manager.utils.ApkUtils.uninstallPackage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: StealthTutorialViewModel = viewModel()
    val state by vm.uiState.observeAsState(UiState.Idle(Action.HIDE))

    var packageName by remember { mutableStateOf("") }
    var outDir by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUninstall by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            outDir = tree
            vm.createApk(ApkType.CLONE)
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is UiState.Pending -> when (s.apkType) {
                ApkType.CLONE -> {
                    val dir = outDir
                    if (dir != null) {
                        runCatching { exportApk(context, dir, s.apk) }
                            .onFailure { error = it.message }
                            .onSuccess { pendingUninstall = true }
                    }
                    vm.refresh()
                }

                ApkType.STUB -> {
                    context.installPackage(s.apk) { ok, msg ->
                        vm.refresh()
                        if (!ok) error = msg ?: "Install failed"
                    }
                }
            }

            is UiState.Error -> {
                error = s.error.message
                vm.refresh()
            }

            else -> Unit
        }
    }

    val action = (state as? UiState.Idle)?.action ?: Action.HIDE
    val busy = state is UiState.Loading || state is UiState.Pending
    val packageNameError = packageName.validatePackageName()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tools_stealth)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        )

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.stealth_description),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (action == Action.HIDE) {
                item {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.stealth_package_name)) },
                        supportingText = {
                            Text(
                                packageNameError?.let { stringResource(it) }
                                    ?: stringResource(R.string.stealth_package_name_helper_text)
                            )
                        },
                        isError = packageNameError != null,
                        singleLine = true
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        when (action) {
                            Action.HIDE -> {
                                vm.setPackageName(packageName.ifEmpty { null })
                                picker.launch(null)
                            }

                            Action.UNHIDE -> vm.createApk(ApkType.STUB)

                            Action.REHIDE -> context.uninstallPackage(ORIGINAL_PACKAGE_NAME) { _, _ ->
                                vm.refresh()
                            }
                        }
                    },
                    enabled = !busy && (action != Action.HIDE || packageNameError == null),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        stringResource(
                            when (action) {
                                Action.HIDE -> R.string.stealth_hide
                                Action.UNHIDE -> R.string.stealth_unhide
                                else -> R.string.stealth_hide
                            }
                        )
                    )
                }
            }
        }
    }

    if (pendingUninstall) {
        AlertDialog(
            onDismissRequest = { pendingUninstall = false },
            title = { Text(stringResource(R.string.stealth_uninstall_required)) },
            text = { Text(stringResource(R.string.stealth_uninstall_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingUninstall = false
                    context.uninstallPackage(ORIGINAL_PACKAGE_NAME) { _, _ -> vm.refresh() }
                }) { Text(stringResource(R.string.stealth_uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { error = null }) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }
}

private fun exportApk(context: Context, outDir: Uri, apk: File) {
    val cr = context.contentResolver
    val docUri = DocumentsContract.buildDocumentUriUsingTree(
        outDir,
        DocumentsContract.getTreeDocumentId(outDir)
    )
    val doc = DocumentsContract.createDocument(
        cr, docUri, "application/vnd.android.package-archive", buildApkFilename()
    ) ?: throw Exception("Could not create file in the selected folder")

    cr.openOutputStream(doc)?.use { output ->
        apk.inputStream().use { input -> input.copyTo(output) }
    }
}
