package com.example.pwabuilder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.pwabuilder.data.PwaFile
import com.example.pwabuilder.data.PwaProject
import com.example.pwabuilder.data.PwaStorage
import com.example.pwabuilder.ui.PwaDestinations
import com.example.pwabuilder.ui.PwaPreviewScreen
import com.example.pwabuilder.ui.PwaViewModel
import com.example.pwabuilder.ui.theme.PWABuilderTheme
import java.io.File
import java.util.UUID

val LocalPwaViewModel = staticCompositionLocalOf<PwaViewModel> {
    error("No PwaViewModel provided")
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: PwaViewModel

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = PwaStorage(applicationContext)
        viewModel = PwaViewModel(storage)

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            PWABuilderTheme {
                val initialProjectId = remember { intent.getStringExtra("projectId") }
                val backStack = if (initialProjectId != null) {
                    rememberNavBackStack(PwaDestinations.ProjectDashboard, PwaDestinations.PwaPreview(initialProjectId))
                } else {
                    rememberNavBackStack(PwaDestinations.ProjectDashboard)
                }
                
                // Navigate to AI Editor if shared images are received
                val sharedImages by viewModel.sharedImagePaths.collectAsState()
                LaunchedEffect(sharedImages) {
                    if (sharedImages.isNotEmpty() && backStack.last() is PwaDestinations.ProjectDashboard) {
                        backStack.add(PwaDestinations.AiEditor)
                    }
                }
                
                val windowAdaptiveInfo = currentWindowAdaptiveInfo()
                val directive = calculatePaneScaffoldDirective(windowAdaptiveInfo)
                val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

                CompositionLocalProvider(LocalPwaViewModel provides viewModel) {
                    val context = LocalContext.current
                    LaunchedEffect(Unit) {
                        viewModel.navigationEvents.collect { destination ->
                            if (backStack.last() != destination) {
                                backStack.add(destination)
                            }
                        }
                    }
                    LaunchedEffect(Unit) {
                        viewModel.successEvents.collect {
                            Toast.makeText(context, "Operation Successful", Toast.LENGTH_SHORT).show()
                        }
                    }
                    LaunchedEffect(Unit) {
                        viewModel.errorEvents.collect { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    }

                    val lastError by viewModel.lastError.collectAsState()
                    if (lastError != null) {
                        AlertDialog(
                            onDismissRequest = { viewModel.clearError() },
                            title = { Text("Error Details") },
                            text = { Text(lastError!!) },
                            confirmButton = {
                                TextButton(onClick = { viewModel.clearError() }) {
                                    Text("OK")
                                }
                            }
                        )
                    }

                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        sceneStrategy = listDetailStrategy,
                        entryProvider = entryProvider {
                            entry<PwaDestinations.ProjectDashboard>(
                                metadata = ListDetailSceneStrategy.listPane(
                                    detailPlaceholder = {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Select a project to preview or create a new one")
                                        }
                                    }
                                )
                            ) {
                                ProjectDashboardScreen(
                                    onPreviewProject = { project ->
                                        backStack.add(PwaDestinations.PwaPreview(project.id))
                                    },
                                    onProjectSettings = { project ->
                                        backStack.add(PwaDestinations.ProjectSettings(project.id))
                                    },
                                    onAddProject = {
                                        backStack.add(PwaDestinations.AiEditor)
                                    },
                                    onOpenSettings = {
                                        backStack.add(PwaDestinations.Settings)
                                    },
                                    onImportProject = {
                                        backStack.add(PwaDestinations.ImportProject)
                                    }
                                )
                            }
                            entry<PwaDestinations.ProjectSettings>(
                                metadata = ListDetailSceneStrategy.detailPane()
                            ) { key ->
                                val projectList by viewModel.projects.collectAsState()
                                val project = projectList.find { it.id == key.projectId }
                                if (project != null) {
                                    ProjectSettingsScreen(
                                        project = project,
                                        onBack = { backStack.removeLastOrNull() },
                                        onDeleted = { backStack.removeLastOrNull() }
                                    )
                                } else {
                                    Text("Project not found")
                                }
                            }
                            entry<PwaDestinations.ImportProject>(
                                metadata = ListDetailSceneStrategy.detailPane()
                            ) {
                                ImportProjectScreen(
                                    onProjectImported = {
                                        backStack.removeLastOrNull()
                                    },
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<PwaDestinations.Settings>(
                                metadata = ListDetailSceneStrategy.detailPane()
                            ) {
                                SettingsScreen(
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<PwaDestinations.ModelSettings>(
                                metadata = ListDetailSceneStrategy.detailPane()
                            ) {
                                ModelSettingsScreen(
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<PwaDestinations.AiEditor>(
                                metadata = ListDetailSceneStrategy.detailPane()
                            ) {
                                AiEditorScreen(
                                    onProjectGenerated = {
                                        backStack.removeLastOrNull()
                                    }
                                )
                            }
                            entry<PwaDestinations.PwaPreview>(
                                metadata = ListDetailSceneStrategy.detailPane()
                            ) { key ->
                                val projectList by viewModel.projects.collectAsState()
                                val project = projectList.find { it.id == key.projectId }
                                if (project != null) {
                                    PwaPreviewScreen(
                                        project = project,
                                        projectDir = storage.getProjectDir(project.id),
                                        onBack = { backStack.removeLastOrNull() }
                                    )
                                } else {
                                    Text("Project not found")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        val projectId = intent.getStringExtra("projectId")
        if (projectId != null) {
            viewModel.navigateTo(PwaDestinations.PwaPreview(projectId))
        }

        if (type == "application/json" && Intent.ACTION_SEND == action) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                try {
                    val jsonString = contentResolver.openInputStream(uri)?.use { 
                        it.bufferedReader().readText() 
                    }
                    if (jsonString != null) {
                        viewModel.importProjectFromJson(jsonString)
                        Toast.makeText(this, "Importing PWA project...", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to read project file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (type?.startsWith("image/") == true) {
            val paths = mutableListOf<String>()
            if (Intent.ACTION_SEND == action) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    val file = copyUriToInternalStorage(uri)
                    if (file != null) {
                        paths.add(file.absolutePath)
                    }
                }
            } else if (Intent.ACTION_SEND_MULTIPLE == action) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris != null) {
                    for (uri in uris) {
                        val file = copyUriToInternalStorage(uri)
                        if (file != null) {
                            paths.add(file.absolutePath)
                        }
                    }
                }
            }
            if (paths.isNotEmpty()) {
                viewModel.addSharedImages(paths)
                Toast.makeText(this, "Received ${paths.size} shared image(s)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyUriToInternalStorage(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val extension = contentResolver.getType(uri)?.substringAfter("/") ?: "jpg"
            val file = File(cacheDir, "shared_image_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
            file.outputStream().use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDashboardScreen(
    onPreviewProject: (PwaProject) -> Unit,
    onProjectSettings: (PwaProject) -> Unit,
    onAddProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalPwaViewModel.current
    val projects by viewModel.projects.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("PWA Projects") },
                actions = {
                    IconButton(onClick = onImportProject) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import Project")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProject) {
                Icon(Icons.Default.Add, contentDescription = "New PWA")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(projects) { project ->
                ProjectItem(
                    project = project,
                    onPreviewClick = { onPreviewProject(project) },
                    onSettingsClick = { onProjectSettings(project) }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsScreen(
    project: PwaProject,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalPwaViewModel.current
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var projectName by remember(project) { mutableStateOf(project.name) }
    var githubRepoName by remember(project) { 
        mutableStateOf(project.githubRepoName ?: project.name.lowercase().replace(Regex("[^a-z0-9]"), "-").take(100)) 
    }
    
    val availableModels by viewModel.availableModels.collectAsState()
    val globalSelectedModel by viewModel.selectedModel.collectAsState()
    val isFetchingModels by viewModel.isFetchingModels.collectAsState()
    
    var expandedModelDropdown by remember { mutableStateOf(false) }
    val currentProjectModel = project.selectedModel ?: globalSelectedModel

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Project") },
            text = { Text("Are you sure you want to delete '${project.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(project.id)
                        showDeleteConfirm = false
                        onDeleted()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Project Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("Project Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { viewModel.renameProject(project.id, projectName) },
                enabled = projectName.isNotBlank() && projectName != project.name,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Name")
            }

            Spacer(modifier = Modifier.height(16.dp))

            val storageSize = remember(project.id) { viewModel.getProjectStorageSizeFormatted(project.id) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Storage Usage:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(storageSize, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = githubRepoName,
                onValueChange = { githubRepoName = it },
                label = { Text("GitHub Repository Name") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Used as the repository name when uploading to GitHub") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            var showRenameRemoteDialog by remember { mutableStateOf(false) }

            Button(
                onClick = { 
                    if (!project.githubRepoName.isNullOrBlank() && project.githubRepoName != githubRepoName) {
                        showRenameRemoteDialog = true
                    } else {
                        viewModel.updateProjectGithubRepoName(context, project.id, githubRepoName, renameRemote = false)
                    }
                },
                enabled = githubRepoName.isNotBlank() && githubRepoName != project.githubRepoName,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save GitHub Repository Name")
            }

            if (showRenameRemoteDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameRemoteDialog = false },
                    title = { Text("GitHubリポジトリ名の変更") },
                    text = { Text("GitHub上のリポジトリ名も '${project.githubRepoName}' から '$githubRepoName' へ変更（リネーム）しますか？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRenameRemoteDialog = false
                                viewModel.updateProjectGithubRepoName(context, project.id, githubRepoName, renameRemote = true)
                            }
                        ) {
                            Text("GitHub側も変更する")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    showRenameRemoteDialog = false
                                    viewModel.updateProjectGithubRepoName(context, project.id, githubRepoName, renameRemote = false)
                                }
                            ) {
                                Text("アプリ内のみ変更")
                            }
                            TextButton(onClick = { showRenameRemoteDialog = false }) {
                                Text("キャンセル")
                            }
                        }
                    }
                )
            }

            var allowGithubPush by remember(project) { mutableStateOf(project.allowGithubPush) }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allowGithubPush,
                        onCheckedChange = { checked ->
                            allowGithubPush = checked
                            viewModel.updateProjectAllowGithubPush(project.id, checked)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "githubのリポジトリへのpushを許可",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "オフにするとGitHubへのアップロードがブロックされます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var showCloneConfirmDialog by remember { mutableStateOf(false) }
            val context = LocalContext.current

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Git Clone (GitHubから取得/同期)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "プロジェクトのGitHubリポジトリ（${githubRepoName.ifBlank { "未設定" }}）から最新コードをクローン（ダウンロード）し、現在のファイルを更新します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showCloneConfirmDialog = true },
                        enabled = githubRepoName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("'${githubRepoName.ifBlank { "リポジトリ" }}' から Git Clone を実行")
                    }
                }
            }

            if (showCloneConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showCloneConfirmDialog = false },
                    title = { Text("Git Clone の確認") },
                    text = { Text("GitHubリポジトリ '$githubRepoName' から最新コードを取得して、現在のプロジェクトのファイルを上書き更新します。よろしいですか？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCloneConfirmDialog = false
                                viewModel.cloneRepoToProject(context, project.id, githubRepoName)
                            }
                        ) {
                            Text("Git Clone 実行")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCloneConfirmDialog = false }) {
                            Text("キャンセル")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("AI Configuration", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = if (isFetchingModels) "Fetching models..." else currentProjectModel,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("AI Model for this Project") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Button(onClick = { expandedModelDropdown = true }) {
                                Text("Change")
                            }
                        }
                    }
                )
                DropdownMenu(
                    expanded = expandedModelDropdown,
                    onDismissRequest = { expandedModelDropdown = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                viewModel.setProjectModel(project.id, model)
                                expandedModelDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(text = "Stats", style = MaterialTheme.typography.titleMedium)
            Text(text = "${project.files.size} files", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Delete Project")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportProjectScreen(
    onProjectImported: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalPwaViewModel.current
    var jsonText by remember { mutableStateOf("") }
    var repoUrlInput by remember { mutableStateOf("") }
    val isGenerating by viewModel.isGenerating.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    jsonText = inputStream.bufferedReader().readText()
                    Toast.makeText(context, "JSON file loaded successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.successEvents.collect {
            onProjectImported()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Import Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Import from GitHub Repository", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = repoUrlInput,
                onValueChange = { repoUrlInput = it },
                label = { Text("GitHub Repo URL or owner/repo") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. username/my-pwa or https://github.com/username/my-pwa") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (repoUrlInput.isNotBlank()) {
                        viewModel.importProjectFromGitHub(repoUrlInput)
                    }
                },
                enabled = repoUrlInput.isNotBlank() && !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isGenerating) "Importing..." else "Import from GitHub")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text("Import from JSON", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { filePickerLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select JSON File")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Or paste the project JSON text below:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                label = { Text("Project JSON") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("{ \"projectName\": \"...\", \"files\": [...] }") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (jsonText.isNotBlank()) {
                        viewModel.importProjectFromJson(jsonText)
                    }
                },
                enabled = jsonText.isNotBlank() && !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Project from JSON")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalPwaViewModel.current
    val apiKey by viewModel.apiKey.collectAsState()
    val githubToken by viewModel.githubToken.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isFetchingModels by viewModel.isFetchingModels.collectAsState()

    var expandedModelDropdown by remember { mutableStateOf(false) }

    var isRevealed by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showSetPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSetPasswordDialog = false
                passwordInput = ""
                confirmPasswordInput = ""
            },
            title = { Text("Set Master Password") },
            text = {
                Column {
                    Text("Set a master password to protect and reveal your API credentials.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = { confirmPasswordInput = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Passwords do not match or empty", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (passwordInput.isNotBlank() && passwordInput == confirmPasswordInput) {
                            viewModel.saveMasterPassword(passwordInput)
                            showSetPasswordDialog = false
                            passwordInput = ""
                            confirmPasswordInput = ""
                            passwordError = false
                            isRevealed = true
                            Toast.makeText(context, "Master password set successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            passwordError = true
                        }
                    }
                ) {
                    Text("Set & Reveal")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSetPasswordDialog = false
                    passwordInput = ""
                    confirmPasswordInput = ""
                    passwordError = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                passwordInput = ""
                passwordError = false
            },
            title = { Text("Enter Master Password") },
            text = {
                Column {
                    Text("Enter your master password to reveal API credentials.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Incorrect password", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val savedPass = viewModel.getMasterPassword()
                        if (passwordInput == savedPass) {
                            showPasswordDialog = false
                            passwordInput = ""
                            passwordError = false
                            isRevealed = true
                        } else {
                            passwordError = true
                        }
                    }
                ) {
                    Text("Reveal")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPasswordDialog = false
                    passwordInput = ""
                    passwordError = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Global Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val uriHandler = LocalUriHandler.current

            Text("API Credentials", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Required for AI generation and GitHub deployment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation()
            )
            TextButton(
                onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    "Get Gemini API Key (Google AI Studio) ↗",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            OutlinedTextField(
                value = githubToken,
                onValueChange = { viewModel.updateGithubToken(it) },
                label = { Text("GitHub Token (PAT)") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation()
            )
            TextButton(
                onClick = { uriHandler.openUri("https://github.com/settings/tokens/new?scopes=repo,workflow&description=PWABuilder") },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    "Generate GitHub Personal Access Token (PAT) ↗",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isRevealed) {
                    Button(
                        onClick = {
                            val savedPass = viewModel.getMasterPassword()
                            if (savedPass.isBlank()) {
                                showSetPasswordDialog = true
                            } else {
                                showPasswordDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reveal with Password")
                    }
                } else {
                    Button(
                        onClick = { isRevealed = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Hide Keys")
                    }
                }

                OutlinedButton(
                    onClick = { showSetPasswordDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Change Password")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("AI Generation Prompt Template", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Customize the prompt template sent to Gemini. Use {prompt} where your input description will be inserted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val promptTemplate by viewModel.generationPromptTemplate.collectAsState()
            var editableTemplate by remember(promptTemplate) { mutableStateOf(promptTemplate) }

            OutlinedTextField(
                value = editableTemplate,
                onValueChange = { editableTemplate = it },
                label = { Text("Prompt Template") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                textStyle = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.updateGenerationPromptTemplate(editableTemplate) },
                enabled = editableTemplate.isNotBlank() && editableTemplate != promptTemplate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Prompt Template")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Default Configuration", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = if (isFetchingModels) "Fetching models..." else selectedModel,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Default Gemini Model") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Button(onClick = { expandedModelDropdown = true }) {
                                Text("Change")
                            }
                        }
                    }
                )
                DropdownMenu(
                    expanded = expandedModelDropdown,
                    onDismissRequest = { expandedModelDropdown = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                viewModel.updateSelectedModel(model)
                                expandedModelDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { viewModel.navigateTo(PwaDestinations.ModelSettings) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configure AI Models & RPD Limits")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEditorScreen(
    onProjectGenerated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = LocalPwaViewModel.current
    val projects by viewModel.projects.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val sharedImages by viewModel.sharedImagePaths.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    var prompt by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var includeInAiGen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.successEvents.collect {
            Toast.makeText(context, "PWA Generated Successfully!", Toast.LENGTH_SHORT).show()
            onProjectGenerated()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("PWA Builder AI") })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                if (apiKey.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = "Please set your Gemini API Key in Settings to enable AI generation.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (lastError != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Error Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearError() }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = lastError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("App Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.generateAndSavePwa(
                            prompt = prompt,
                            name = name,
                            imagePaths = if (includeInAiGen) sharedImages else emptyList()
                        )
                        if (includeInAiGen) {
                            viewModel.clearSharedImages()
                        }
                    },
                    enabled = !isGenerating && prompt.isNotBlank() && name.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Generate PWA")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectDashboardPreview() {
    PWABuilderTheme {
        ProjectDashboardScreen(onPreviewProject = {}, onProjectSettings = {}, onAddProject = {}, onOpenSettings = {}, onImportProject = {})
    }
}

@PreviewScreenSizes
@Composable
fun AiEditorPreview() {
    PWABuilderTheme {
        AiEditorScreen(onProjectGenerated = {})
    }
}

@Composable
fun ProjectItem(project: PwaProject, onPreviewClick: () -> Unit, onSettingsClick: () -> Unit) {
    val viewModel = LocalPwaViewModel.current
    val storageSize = remember(project.id) { viewModel.getProjectStorageSizeFormatted(project.id) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onSettingsClick() }
        ) {
            Text(text = project.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "${project.files.size} files • Storage: $storageSize", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Button(onClick = onPreviewClick) {
            Text("Preview")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalPwaViewModel.current
    val context = LocalContext.current
    val modelList by viewModel.allAvailableModels.collectAsState()
    val isFetchingModels by viewModel.isFetchingModels.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.fetchModels()
    }

    val rpdValues = remember(modelList) {
        mutableStateMapOf<String, String>().apply {
            modelList.forEach { modelName ->
                put(modelName, viewModel.getModelRpd(modelName).toString())
            }
        }
    }

    val filteredModels = remember(modelList, searchQuery) {
        if (searchQuery.isBlank()) {
            modelList
        } else {
            modelList.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI Model Settings (RPD)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            val map = rpdValues.mapValues { entry ->
                                entry.value.toIntOrNull() ?: 0
                            }
                            viewModel.saveAllModelRpds(map)
                            Toast.makeText(context, "All RPD settings saved successfully!", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save All RPD Settings")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "Set Requests Per Day (RPD) limit for each model. Models containing 'pro' default to 0. Models set to 0 or left blank will be hidden from selection lists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search models...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear search")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (isFetchingModels) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching available models from API...", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(filteredModels) { modelName ->
                if (!rpdValues.containsKey(modelName)) {
                    rpdValues[modelName] = viewModel.getModelRpd(modelName).toString()
                }
                val currentText = rpdValues[modelName] ?: viewModel.getModelRpd(modelName).toString()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = modelName, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentText,
                            onValueChange = { rpdValues[modelName] = it },
                            label = { Text("Requests Per Day (RPD)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
