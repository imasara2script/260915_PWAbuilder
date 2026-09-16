package com.example.pwabuilder.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    val isGenerating by viewModel.isGenerating.collectAsState()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    var showChat by remember { mutableStateOf(false) }
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

                        webViewClient = PwaWebViewClient(projectDir)
                        loadUrl("https://pwa.local/index.html")
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { }
            )

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
        }
    }

    if (showChat) {
        ModalBottomSheet(
            onDismissRequest = { showChat = false },
            sheetState = sheetState
        ) {
            ChatInterface(
                project = project,
                onSend = { instruction, imagePaths ->
                    viewModel.refinePwa(project.id, instruction, imagePaths)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showChat = false
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
    onSend: (String, List<String>) -> Unit
) {
    val context = LocalContext.current
    val viewModel = LocalPwaViewModel.current
    var text by remember { mutableStateOf("") }
    var selectedImagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }

    val activeSession = project.activeSession

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
        selectedImagePaths = selectedImagePaths + paths
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
                Text(
                    text = activeSession?.title ?: "New Conversation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = { viewModel.createNewSession(project.id) }) {
                Icon(Icons.Rounded.Add, contentDescription = "New Chat")
            }
            IconButton(onClick = { showHistory = !showHistory }) {
                Icon(Icons.Rounded.History, contentDescription = "History")
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
                        Text(
                            text = session.title,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
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
            items(messages.reversed()) { msg ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (msg.role == "user") Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Card(
                        modifier = Modifier.padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.role == "user") 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = msg.content,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                            onClick = { selectedImagePaths = selectedImagePaths - path },
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
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Instruction...") }
            )
            IconButton(
                onClick = {
                    if (text.isNotBlank() || selectedImagePaths.isNotEmpty()) {
                        onSend(text, selectedImagePaths)
                        text = ""
                        selectedImagePaths = emptyList()
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

class PwaWebViewClient(private val projectDir: File) : WebViewClient() {
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
            }
        }
        return super.shouldInterceptRequest(view, request)
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
