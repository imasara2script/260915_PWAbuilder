package com.example.pwabuilder.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import coil.compose.AsyncImage
import com.example.pwabuilder.LocalPwaViewModel
import com.example.pwabuilder.data.PwaProject
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PwaPreviewScreen(
    project: PwaProject,
    projectDir: File,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = LocalPwaViewModel.current
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadStatus by viewModel.uploadStatus.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    var showChat by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var chatInputText by remember { mutableStateOf("") }
    var chatSelectedImagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var showMenu by remember { mutableStateOf(false) }
    var showJsonViewer by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val jsonSheetState = rememberModalBottomSheetState()
    val updateSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedExportFiles by remember(project) { 
        mutableStateOf(project.files.map { it.name }.toSet()) 
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = project.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Projects"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showChat = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Refine with AI"
                        )
                    }
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh Preview") },
                                onClick = {
                                    webViewInstance?.reload()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Home Screen") },
                                onClick = {
                                    viewModel.installPwaShortcut(context, project)
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Rounded.AddHome, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share for AI Analysis") },
                                onClick = {
                                    viewModel.shareProjectForAi(context, project)
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("View/Copy Project JSON") },
                                onClick = {
                                    showJsonViewer = true
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Update Project from JSON") },
                                onClick = {
                                    showUpdateSheet = true
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { 
                                    if (isUploading) Text("Uploading...") else Text("Upload to GitHub")
                                },
                                onClick = {
                                    if (!isUploading) {
                                        viewModel.uploadToGithub(context, project)
                                    }
                                    showMenu = false
                                },
                                leadingIcon = {
                                    if (isUploading) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                                    }
                                },
                                enabled = !isUploading
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                        }

                        webViewClient = PwaWebViewClient(projectDir) { previewError = it }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                    previewError = "JS Error: ${consoleMessage.message()} (Line ${consoleMessage.lineNumber()})"
                                }
                                return super.onConsoleMessage(consoleMessage)
                            }

                            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                AlertDialog.Builder(context)
                                    .setTitle("Feature Not Available in Preview")
                                    .setMessage("window.confirm() is not supported in this in-app preview.\nPlease upload to GitHub to preview in your full browser app.")
                                    .setPositiveButton("OK") { _, _ -> result?.cancel() }
                                    .setOnCancelListener { result?.cancel() }
                                    .show()
                                return true
                            }

                            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                                AlertDialog.Builder(context)
                                    .setTitle("Feature Not Available in Preview")
                                    .setMessage("window.prompt() is not supported in this in-app preview.\nPlease upload to GitHub to preview in your full browser app.")
                                    .setPositiveButton("OK") { _, _ -> result?.cancel() }
                                    .setOnCancelListener { result?.cancel() }
                                    .show()
                                return true
                            }
                        }
                        loadUrl("https://pwa.local/index.html")
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { }
            )

            if (previewError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopCenter),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preview Runtime Error",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = previewError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = { previewError = null }) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Refining PWA...")
                        }
                    }
                }
            }

            if (isUploading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(uploadStatus ?: "Uploading to GitHub...")
                        }
                    }
                }
            }
        }
    }

    if (showChat) {
        ModalBottomSheet(
            onDismissRequest = { showChat = false },
            sheetState = sheetState
        ) {
            ChatInterface(
                project = project,
                chatInputText = chatInputText,
                onChatInputChanged = { chatInputText = it },
                selectedImagePaths = chatSelectedImagePaths,
                onSelectedImagePathsChanged = { chatSelectedImagePaths = it },
                onSend = { instruction, imagePaths ->
                    viewModel.refinePwa(project.id, instruction, imagePaths)
                    chatInputText = ""
                    chatSelectedImagePaths = emptyList()
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showChat = false
                        }
                    }
                },
                onReset = { index, instruction ->
                    if (project.activeSession != null) {
                        viewModel.resetToMessage(project.id, project.activeSession!!.id, index, instruction)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showChat = false
                            }
                        }
                    }
                }
            )
        }
    }

    if (showJsonViewer) {
        ModalBottomSheet(
            onDismissRequest = { showJsonViewer = false },
            sheetState = jsonSheetState
        ) {
            val json = remember(project, selectedExportFiles) { 
                viewModel.getProjectJson(project, selectedExportFiles.toList()) 
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(600.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Project JSON", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        clipboardManager.setText(AnnotatedString(json))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Select Files to Include:", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(project.files) { file ->
                        FilterChip(
                            selected = file.name in selectedExportFiles,
                            onClick = {
                                selectedExportFiles = if (file.name in selectedExportFiles) {
                                    selectedExportFiles - file.name
                                } else {
                                    selectedExportFiles + file.name
                                }
                            },
                            label = { Text(file.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        item {
                            Text(
                                text = json,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUpdateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showUpdateSheet = false },
            sheetState = updateSheetState
        ) {
            var updateJsonText by remember { mutableStateOf("") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(500.dp)
            ) {
                Text("Update Project from AI JSON", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Paste JSON from AI to update or add files in this project.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = updateJsonText,
                    onValueChange = { updateJsonText = it },
                    label = { Text("AI JSON Content") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text("{ \"files\": [...] }") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.updateProjectWithJson(project.id, updateJsonText)
                        scope.launch { updateSheetState.hide() }.invokeOnCompletion {
                            if (!updateSheetState.isVisible) showUpdateSheet = false
                        }
                    },
                    enabled = updateJsonText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Updates")
                }
            }
        }
    }
}

@Composable
fun ChatInterface(
    project: PwaProject,
    chatInputText: String,
    onChatInputChanged: (String) -> Unit,
    selectedImagePaths: List<String>,
    onSelectedImagePathsChanged: (List<String>) -> Unit,
    onSend: (String, List<String>) -> Unit,
    onReset: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val viewModel = LocalPwaViewModel.current
    var showHistory by remember { mutableStateOf(false) }
    var showImportChat by remember { mutableStateOf(false) }
    
    val availableModels by viewModel.availableModels.collectAsState()
    val globalSelectedModel by viewModel.selectedModel.collectAsState()
    var expandedModelDropdown by remember { mutableStateOf(false) }

    val activeSession = project.activeSession
    val currentSessionModel = activeSession?.selectedModel ?: project.selectedModel ?: globalSelectedModel
    
    val clipboardManager = LocalClipboardManager.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val paths = uris.mapNotNull { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@mapNotNull null
                val extension = context.contentResolver.getType(uri)?.substringAfter("/") ?: "jpg"
                val file = File(context.cacheDir, "chat_refine_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
                file.outputStream().use { outputStream ->
                    inputStream.use { it.copyTo(outputStream) }
                }
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        onSelectedImagePathsChanged(selectedImagePaths + paths)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(500.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Refine PWA", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = activeSession?.title ?: "New Conversation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (activeSession != null) {
                        Box {
                            TextButton(
                                onClick = { expandedModelDropdown = true }
                            ) {
                                Text(
                                    text = currentSessionModel,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            DropdownMenu(
                                expanded = expandedModelDropdown,
                                onDismissRequest = { expandedModelDropdown = false }
                            ) {
                                availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            viewModel.setSessionModel(project.id, activeSession.id, model)
                                            expandedModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            IconButton(onClick = { viewModel.createNewSession(project.id) }) {
                Icon(Icons.Rounded.Add, contentDescription = "New Chat")
            }
            IconButton(onClick = { showImportChat = !showImportChat }) {
                Icon(Icons.Default.UploadFile, contentDescription = "Import Chat")
            }
            IconButton(onClick = { showHistory = !showHistory }) {
                Icon(Icons.Rounded.History, contentDescription = "History")
            }
        }
        
        if (showImportChat) {
            var importJson by remember { mutableStateOf("") }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedTextField(
                    value = importJson,
                    onValueChange = { importJson = it },
                    label = { Text("Paste Chat JSON") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        viewModel.importSession(project.id, importJson)
                        showImportChat = false
                    },
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    enabled = importJson.isNotBlank()
                ) {
                    Text("Import")
                }
            }
        }

        if (activeSession != null && activeSession.lastTokenCount > 0) {
            val limit = viewModel.getTokenLimit(currentSessionModel)
            val usage = activeSession.lastTokenCount.toFloat() / limit
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Token Usage: ${activeSession.lastTokenCount} / $limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (usage > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(usage * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (usage > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                }
                LinearProgressIndicator(
                    progress = { usage },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraSmall),
                    color = if (usage > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        val todayUsage = viewModel.getTodayModelUsage(currentSessionModel)
        val rpdLimit = viewModel.getModelRpd(currentSessionModel)
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Model RPD ($currentSessionModel): $todayUsage / $rpdLimit used today",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (todayUsage >= rpdLimit && rpdLimit > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        if (showHistory) {
            Text("Previous Conversations", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.height(150.dp)) {
                items(project.chatSessions) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { 
                            viewModel.switchSession(project.id, session.id)
                            showHistory = false
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (session.id == activeSession?.id)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = session.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(
                                onClick = {
                                    val json = viewModel.getSessionJson(session)
                                    clipboardManager.setText(AnnotatedString(json))
                                    Toast.makeText(context, "Chat JSON copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy, 
                                    contentDescription = "Copy JSON",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteSession(project.id, session.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete, 
                                    contentDescription = "Delete", 
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        val messages = activeSession?.messages ?: emptyList()
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            itemsIndexed(messages.reversed()) { revIndex, msg ->
                val index = messages.size - 1 - revIndex
                var isEditing by remember { mutableStateOf(false) }
                var editText by remember { mutableStateOf(msg.content) }
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (msg.role == "user") Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Column(horizontalAlignment = if (msg.role == "user") Alignment.End else Alignment.Start) {
                        Card(
                            modifier = Modifier.padding(vertical = 4.dp),
                            onClick = { if (msg.role == "user") isEditing = !isEditing },
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.role == "user") 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            if (isEditing) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    OutlinedTextField(
                                        value = editText,
                                        onValueChange = { editText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodyMedium
                                    )
                                    
                                    if (msg.snapshot != null && msg.snapshot.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Snapshot: ${msg.snapshot.size} files",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row {
                                                TextButton(onClick = {
                                                    val json = viewModel.getSnapshotJson(msg.snapshot)
                                                    clipboardManager.setText(AnnotatedString(json))
                                                    Toast.makeText(context, "Snapshot JSON copied", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Text("Export", style = MaterialTheme.typography.labelSmall)
                                                }
                                                TextButton(onClick = {
                                                    if (project.activeSession != null) {
                                                        viewModel.deleteSnapshot(project.id, project.activeSession!!.id, index)
                                                        Toast.makeText(context, "Snapshot deleted", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { isEditing = false }) { Text("Cancel") }
                                        TextButton(onClick = { 
                                            onReset(index, editText)
                                            isEditing = false
                                        }) { Text("Reset from here", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = msg.content,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (msg.snapshot != null && msg.snapshot.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "📦 Snapshot (${msg.snapshot.size} files)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            TextButton(
                                                onClick = {
                                                    val json = viewModel.getSnapshotJson(msg.snapshot)
                                                    clipboardManager.setText(AnnotatedString(json))
                                                    Toast.makeText(context, "Snapshot JSON copied", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text("Export", style = MaterialTheme.typography.labelSmall)
                                            }
                                            TextButton(
                                                onClick = {
                                                    if (project.activeSession != null) {
                                                        viewModel.deleteSnapshot(project.id, project.activeSession!!.id, index)
                                                        Toast.makeText(context, "Snapshot deleted", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedImagePaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selectedImagePaths) { path ->
                    Box {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Selected Image",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onSelectedImagePathsChanged(selectedImagePaths - path) },
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(Icons.Rounded.Image, contentDescription = "Add Image")
            }
            OutlinedTextField(
                value = chatInputText,
                onValueChange = onChatInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Instruction...") }
            )
            IconButton(
                onClick = {
                    if (chatInputText.isNotBlank() || selectedImagePaths.isNotEmpty()) {
                        onSend(chatInputText, selectedImagePaths)
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

class PwaWebViewClient(
    private val projectDir: File,
    private val onError: (String) -> Unit
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url ?: return null
        if (url.scheme == "https" && url.host == "pwa.local") {
            var path = url.path ?: ""
            if (path.isEmpty() || path == "/") {
                path = "/index.html"
            }
            
            val file = File(projectDir, path.removePrefix("/"))
            if (file.exists() && file.isFile) {
                try {
                    val mimeType = getMimeType(file.name)
                    val encoding = "UTF-8"
                    val inputStream = FileInputStream(file)
                    return WebResourceResponse(mimeType, encoding, inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                onError("Resource not found: $path")
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val description = error?.description?.toString() ?: "Unknown error"
            val errorCode = error?.errorCode ?: 0
            onError("Page Load Error ($errorCode): $description")
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true) {
            val statusCode = errorResponse?.statusCode ?: 0
            val reason = errorResponse?.reasonPhrase ?: "Unknown"
            onError("HTTP Error $statusCode: $reason")
        }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".html", ignoreCase = true) -> "text/html"
            fileName.endsWith(".css", ignoreCase = true) -> "text/css"
            fileName.endsWith(".js", ignoreCase = true) -> "text/javascript"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
            else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        }
    }
}
