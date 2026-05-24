package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.model.*
import com.example.ui.CodeEditorViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.SyntaxHighlighter
import com.example.util.WorkspaceManager
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: CodeEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Bottom Navigation IDs
enum class Tab(val id: String, val title: String) {
    FILES("files", "ফাইল"),
    EDITOR("editor", "এডিটর"),
    GIT("git", "গিট"),
    ACTIONS("actions", "অ্যাকশন"),
    SYNC("sync", "সিঙ্ক")
}

@Composable
fun MainScreen(viewModel: CodeEditorViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(Tab.FILES) }

    val filesState by viewModel.filesList.collectAsState()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val editorContent by viewModel.editorText.collectAsState()
    val isModified by viewModel.isModified.collectAsState()
    val modifiedFiles by viewModel.modifiedFiles.collectAsState()
    val currentBranch by viewModel.currentBranch.collectAsState()
    val gitCommits by viewModel.gitCommits.collectAsState()
    val stagedFiles by viewModel.stagedFiles.collectAsState()
    val workflowRuns by viewModel.workflowRuns.collectAsState()
    val activeRun by viewModel.activeRunningRun.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    // Dialog state
    var showCreateDialog by remember { mutableStateOf(false) }
    var createIsDirectory by remember { mutableStateOf(false) }
    var inputFileName by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<String?>(null) }
    var inputRenameName by remember { mutableStateOf("") }

    var showDiffDialog by remember { mutableStateOf(false) }
    var diffFilePath by remember { mutableStateOf<String?>(null) }

    var showCommitDialog by remember { mutableStateOf(false) }
    var commitMessageInput by remember { mutableStateOf("") }

    var showAiAssistantDialog by remember { mutableStateOf(false) }
    var aiInstructionInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)) // Slate Dark Background
    ) {
        // App top header
        TopHeaderBar(
            selectedFile = selectedFile,
            isModified = isModified,
            currentBranch = currentBranch,
            onSaveClick = { viewModel.saveCurrentFile() },
            onZipExportClick = {
                viewModel.getExportZipFile { file ->
                    if (file != null) {
                        shareZipFile(context, file)
                    } else {
                        Toast.makeText(context, "Zip ফাইল তৈরী করা যায়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        // Main view content box with slide transition
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                Tab.FILES -> FilesTabView(
                    files = filesState,
                    selectedFile = selectedFile,
                    onFileClick = { file ->
                        viewModel.selectFile(file)
                        activeTab = Tab.EDITOR // auto focus editor on select
                    },
                    onCreateFile = { isDir ->
                        createIsDirectory = isDir
                        inputFileName = ""
                        showCreateDialog = true
                    },
                    onRenameFile = { path, name ->
                        fileToRename = path
                        inputRenameName = name
                        showRenameDialog = true
                    },
                    onDeleteFile = { path ->
                        viewModel.deleteWorkspaceFile(path)
                    }
                )

                Tab.EDITOR -> EditorTabView(
                    selectedFile = selectedFile,
                    editorText = editorContent,
                    onTextChange = { viewModel.updateEditorText(it) },
                    onUndoClick = { viewModel.undo() },
                    onRedoClick = { viewModel.redo() },
                    onFormatClick = {
                        val extension = selectedFile?.name?.substringAfterLast(".", "kt") ?: "kt"
                        if (extension == "json") {
                            try {
                                val clean = editorContent.trim()
                                if (clean.startsWith("{") || clean.startsWith("[")) {
                                    // Simulated pretty format for JSON
                                    val formatted = formatJsonSimulation(clean)
                                    viewModel.updateEditorText(formatted)
                                    Toast.makeText(context, "JSON ফরম্যাট সম্পন্ন!", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "ত্রুটিপূর্ণ JSON!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // Trim double newline spikes
                            val formatted = editorContent.replace(Regex("\n{3,}"), "\n\n")
                            viewModel.updateEditorText(formatted)
                            Toast.makeText(context, "কোড ফরম্যাটিং সম্পন্ন!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAiAssistantClick = {
                        showAiAssistantDialog = true
                    }
                )

                Tab.GIT -> GitTabView(
                    currentBranch = currentBranch,
                    commits = gitCommits,
                    modifiedFiles = modifiedFiles,
                    stagedFiles = stagedFiles,
                    onStageToggle = { viewModel.toggleStageFile(it) },
                    onBranchChange = { viewModel.switchBranch(it) },
                    onViewDiffClick = { path ->
                        diffFilePath = path
                        showDiffDialog = true
                    },
                    onTriggerCommitClick = {
                        commitMessageInput = ""
                        showCommitDialog = true
                    }
                )

                Tab.ACTIONS -> ActionsTabView(
                    workflowRuns = workflowRuns,
                    activeRun = activeRun,
                    onTriggerActionRun = {
                        viewModel.triggerWorkflowRun()
                    }
                )

                Tab.SYNC -> SyncTabView(
                    syncStatus = syncStatus,
                    onSyncClick = { viewModel.performCloudSync() },
                    onRepoLinkChange = { viewModel.updateSyncRepo(it) }
                )
            }
        }

        // Standard bottom navigation bar with material design ripples
        BottomNavigationBar(
            activeTab = activeTab,
            onTabSelected = { activeTab = it }
        )
    }

    // CREATE FILE/FOLDER DIALOG
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(text = if (createIsDirectory) "নতুন ফোল্ডার" else "নতুন ফাইল") },
            text = {
                Column {
                    Text("নাম লিখুন (যেমন: scripts.py, style.css):", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputFileName,
                        onValueChange = { inputFileName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_item_input"),
                        singleLine = true,
                        placeholder = { Text("App.kt") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputFileName.isNotBlank()) {
                            viewModel.createWorkspaceFile(inputFileName, createIsDirectory)
                            showCreateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_button")
                ) {
                    Text("তৈরি করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // RENAME DIALOG
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("রিনেম করুন") },
            text = {
                Column {
                    Text("নতুন নাম লিখুন:", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputRenameName,
                        onValueChange = { inputRenameName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = fileToRename
                        if (path != null && inputRenameName.isNotBlank()) {
                            viewModel.renameWorkspaceFile(path, inputRenameName)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("সংরক্ষণ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // GIT DIFF DIALOG
    if (showDiffDialog) {
        val path = diffFilePath
        if (path != null) {
            val diffLines = viewModel.getDiffForFile(path)
            Dialog(onDismissRequest = { showDiffDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF161B22),
                    border = BorderStroke(1.dp, Color(0xFF30363D))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Diff: $path",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            IconButton(onClick = { showDiffDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                            }
                        }
                        Divider(color = Color(0xFF30363D), modifier = Modifier.padding(vertical = 8.dp))
                        
                        if (diffLines.isEmpty()) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text("কোনো পরিবর্তন পাওয়া যায়নি বা ফাইলটি নতুন!", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF0D1117))
                                    .padding(8.dp)
                            ) {
                                items(diffLines) { (line, color) ->
                                    val bg = when {
                                        line.startsWith("+") -> Color(0xFF1F2F20)
                                        line.startsWith("-") -> Color(0xFF371E1E)
                                        else -> Color.Transparent
                                    }
                                    Text(
                                        text = line,
                                        color = color,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(bg)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // WORKSPACE GIT COMMIT DIALOG
    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("গিট কমিট করুন") },
            text = {
                Column {
                    Text("কমিট মেসেজ লিখুন:", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commitMessageInput,
                        onValueChange = { commitMessageInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("commit_msg_input"),
                        placeholder = { Text("যেমন: Fix logic bugs or style tweaks") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (commitMessageInput.isNotBlank()) {
                            viewModel.commitStagedFiles(commitMessageInput)
                            showCommitDialog = false
                        } else {
                            Toast.makeText(context, "দয়া করে কমিট মেসেজ দিন", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("submit_commit_button")
                ) {
                    Text("কমিট")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // AI CODE ASSISTANT DIALOG
    if (showAiAssistantDialog) {
        Dialog(onDismissRequest = { showAiAssistantDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF161B22),
                border = BorderStroke(1.dp, Color(0xFF30363D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = Color(0xFFD2A8FF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini AI কোড অ্যাসিস্ট্যান্ট", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        IconButton(onClick = { 
                            viewModel.clearAiOutput()
                            showAiAssistantDialog = false 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                        }
                    }
                    Divider(color = Color(0xFF30363D), modifier = Modifier.padding(vertical = 8.dp))

                    if (aiResult == null) {
                        Column {
                            Text(
                                "পছন্দসই প্রোগ্রামিং টাস্ক বা নির্দেশাবলি লিখুন। AI স্বয়ংক্রিয়ভাবে কোড জেনারেট বা আপনার কোডটি অপ্টিমাইজ করবে।",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = aiInstructionInput,
                                onValueChange = { aiInstructionInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("ai_instruction_input"),
                                label = { Text("নির্দেশনা (যেমন: Write binary search in Python)", color = Color.Gray) },
                                textStyle = TextStyle(color = Color.White)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val promptSuggestions = listOf(
                                "কোডে কোনো সিকিউরিটি বাগ আছে কিনা চেক করো",
                                "Write factorial function recursively",
                                "Create a professional modern navbar inside CSS",
                                "এই ফাইলটি অপ্টিমাইজ এবং রিফ্যাক্টর করো"
                            )
                            Text("কুইক সাজেশন:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Column {
                                promptSuggestions.forEach { suggestion ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
                                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { aiInstructionInput = suggestion }
                                    ) {
                                        Text(
                                            text = suggestion,
                                            color = Color(0xFF58A6FF),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (aiInstructionInput.isNotBlank()) {
                                        viewModel.queryGeminiAssistant(aiInstructionInput)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ask_ai_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                            ) {
                                if (isAiLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                } else {
                                    Text("Gemini AI কে জিজ্ঞেস করুন")
                                }
                            }
                        }
                    } else {
                        // Show AI Generated Code response with Insert button
                        Column(modifier = Modifier.weight(1f)) {
                            Text("প্রতিক্রিয়া:", color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = aiResult ?: "",
                                    color = Color(0xFFC9D1D9),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { viewModel.clearAiOutput() }) {
                                    Text("আবার চেষ্টা করুন", color = Color.LightGray)
                                }
                                Button(
                                    onClick = {
                                        val generated = aiResult
                                        if (generated != null) {
                                            viewModel.applyAiCodeToEditor(generated)
                                            showAiAssistantDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                                ) {
                                    Text("কোড এডিটরে ইমপোর্ট করুন")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// TOP HEADER COMPONENT
@Composable
fun TopHeaderBar(
    selectedFile: FileItem?,
    isModified: Boolean,
    currentBranch: String,
    onSaveClick: () -> Unit,
    onZipExportClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = Color(0xFF161B22),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = "Logo",
                    tint = Color(0xFF3FB950),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = selectedFile?.name ?: "কোনো ফাইল সিলেক্টেড নেই",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ForkRight,
                            contentDescription = "Branch",
                            tint = Color(0xFF58A6FF),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = currentBranch,
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp
                        )
                        if (isModified && selectedFile != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFE5C07B), RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Unsaved", color = Color(0xFFE5C07B), fontSize = 10.sp)
                        }
                    }
                }
            }
            // Control Actions
            Row {
                if (selectedFile != null) {
                    IconButton(
                        onClick = onSaveClick,
                        modifier = Modifier.testTag("save_editor_file_button")
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save file",
                            tint = if (isModified) Color(0xFFE5C07B) else Color.LightGray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onZipExportClick,
                    modifier = Modifier.testTag("zip_project_export")
                ) {
                    Icon(
                        Icons.Default.FolderZip,
                        contentDescription = "Export ZIP",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// TAB 1: FILE EXPLORER BROWSER
@Composable
fun FilesTabView(
    files: List<FileItem>,
    selectedFile: FileItem?,
    onFileClick: (FileItem) -> Unit,
    onCreateFile: (Boolean) -> Unit,
    onRenameFile: (String, String) -> Unit,
    onDeleteFile: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📂 অফলাইন ফোল্ডার ওয়ার্কস্পেস",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    TextButton(
                        onClick = { onCreateFile(true) },
                        modifier = Modifier.testTag("create_dir_trigger")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Folder", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ফোল্ডার", fontSize = 12.sp, color = Color(0xFF58A6FF))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = { onCreateFile(false) },
                        modifier = Modifier.testTag("create_file_trigger")
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "File", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ফাইল", fontSize = 12.sp, color = Color(0xFF3FB950))
                    }
                }
            }
        }

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(50.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("ওয়ার্কস্পেস ফাঁকা! একটি নতুন ফাইল তৈরি করুন।", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                items(files) { file ->
                    FileTreeItemRow(
                        file = file,
                        isSelected = file.path == selectedFile?.path,
                        onFileClick = onFileClick,
                        onRename = { onRenameFile(file.path, file.name) },
                        onDelete = { onDeleteFile(file.path) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileTreeItemRow(
    file: FileItem,
    isSelected: Boolean,
    onFileClick: (FileItem) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    depth: Int = 0
) {
    val indicatorColor = if (isSelected) Color(0xFF238636) else Color.Transparent

    Column {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) Color(0xFF21262D) else Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable { onFileClick(file) }
                .testTag("file_item_${file.name}"),
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (depth * 14).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(indicatorColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else getFileIcon(file.name),
                        contentDescription = "File Type",
                        tint = if (file.isDirectory) Color(0xFFE5C07B) else getFileIconColor(file.name),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file.name,
                        color = if (isSelected) Color.White else Color(0xFFC9D1D9),
                        fontSize = 14.sp
                    )
                }
                
                // Rename & delete operations
                Row {
                    IconButton(onClick = onRename, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF85149), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        
        // Render children if directory structure
        if (file.isDirectory && file.children.isNotEmpty()) {
            file.children.forEach { child ->
                FileTreeItemRow(
                    file = child,
                    isSelected = isSelected,
                    onFileClick = onFileClick,
                    onRename = onRename,
                    onDelete = onDelete,
                    depth = depth + 1
                )
            }
        }
    }
}

// TAB 2: ACTUAL MONOSPACE EDITOR FIELD
@Composable
fun EditorTabView(
    selectedFile: FileItem?,
    editorText: String,
    onTextChange: (String) -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onFormatClick: () -> Unit,
    onAiAssistantClick: () -> Unit
) {
    if (selectedFile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CodeOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(60.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("কোড এডিট করতে ফাইল সেকশন থেকে যেকোনো ফাইল সিলেক্ট করুন।", color = Color.Gray, fontSize = 13.sp)
            }
        }
        return
    }

    val extension = selectedFile.name.substringAfterLast(".", "kt")
    val highlightedText = remember(editorText, extension) {
        SyntaxHighlighter.highlight(editorText, extension)
    }

    // Auto-complete suggestion items based on last segment typed
    val activeSuggestions = remember(editorText, extension) {
        SyntaxHighlighter.getAutocompleteSuggestions(extension, editorText)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Core control bar for saving formatting undo/redos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                IconButton(onClick = onUndoClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(onClick = onRedoClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(onClick = onFormatClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Format", tint = Color(0xFF58A6FF), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Format",
                    color = Color(0xFF58A6FF),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .clickable { onFormatClick() }
                )
            }

            // AI trigger button
            Button(
                onClick = onAiAssistantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2DE2)),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("ai_assistant_trigger")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("AI Assistant", fontSize = 11.sp, color = Color.White)
            }
        }

        // Live Dynamic Suggestion Chip Bar (Over keyboard layout)
        if (activeSuggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF21262D))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("পরামর্শ:", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                activeSuggestions.forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                            .clickable {
                                // insert autocompletion word
                                val words = editorText.split(Regex("[^a-zA-Z0-9_@]"))
                                val lastWord = words.lastOrNull() ?: ""
                                val updated = editorText.removeSuffix(lastWord) + suggestion
                                onTextChange(updated)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text = suggestion, color = Color(0xFF79C0FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Editor Scroll Workspace containing synchronized line-number column and code field
        val scrollState = rememberScrollState()
        val totalLines = editorText.lines().size

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0D1117))
                .verticalScroll(scrollState)
        ) {
            // Line numbers column
            Column(
                modifier = Modifier
                    .background(Color(0xFF161B22))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..totalLines) {
                    Text(
                        text = "$i",
                        color = Color(0xFF5C6370),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.height(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                // Background Highlight annotation display
                Text(
                    text = highlightedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                // Invisible Overlay writing text field capturing cursor selection and keyboards inputs
                BasicTextField(
                    value = editorText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("code_editor_field"),
                    textStyle = TextStyle(
                        color = Color.Transparent, // Color set to transparent to allow Highlight block painting
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(Color.White)
                )
            }
        }
    }
}

// TAB 3: ROBUST SIMULATED VERSION CONTROL VIEW (GIT)
@Composable
fun GitTabView(
    currentBranch: String,
    commits: List<Commit>,
    modifiedFiles: List<String>,
    stagedFiles: Set<String>,
    onStageToggle: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onViewDiffClick: (String) -> Unit,
    onTriggerCommitClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Active status headers
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌿 গিট রিপোজিটরি সিমুলেটর", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    // Branch Picker representation
                    var showBranchMenu by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { showBranchMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("git_branch_switcher")
                        ) {
                            Icon(Icons.Default.ForkRight, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF58A6FF))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(currentBranch, fontSize = 11.sp, color = Color.White)
                        }
                        DropdownMenu(
                            expanded = showBranchMenu,
                            onDismissRequest = { showBranchMenu = false },
                            modifier = Modifier.background(Color(0xFF161B22))
                        ) {
                            listOf("main", "dev", "feature/editor-ui").forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b, color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        onBranchChange(b)
                                        showBranchMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("পরিবর্তন হয়েছে: ${modifiedFiles.size} ফাইল", color = Color.LightGray, fontSize = 12.sp)
                    Text("স্টেজড: ${stagedFiles.size} ফাইল", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }

        // Modified file listings and visual commit triggers
        Card(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ফাইল স্থিতি (Changes state)", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (modifiedFiles.isNotEmpty()) {
                        Button(
                            onClick = onTriggerCommitClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("commit_assets_button")
                        ) {
                            Text("কমিট করুন", fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (modifiedFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF3FB950), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("সব ফাইল আপ-টু-ডেট আছে!", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(modifiedFiles) { path ->
                            val isStaged = stagedFiles.contains(path)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(Color(0xFF161B22), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isStaged,
                                        onCheckedChange = { onStageToggle(path) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF238636),
                                            uncheckedColor = Color.Gray
                                        ),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .testTag("stage_check_$path")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(path, color = Color(0xFFE5C07B), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                                Row {
                                    TextButton(onClick = { onViewDiffClick(path) }) {
                                        Text("Diff দেখুন", color = Color(0xFF58A6FF), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Historic timeline of commit revisions
        Card(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("📜 কমিট হিস্ট্রি (Revision log)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(commits) { commit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF58A6FF))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(commit.message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(commit.id, color = Color(0xFF58A6FF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(commit.author.substringBefore("<").trim(), color = Color.Gray, fontSize = 11.sp)
                                    Text("সম্পন্ন", color = Color(0xFF3FB950), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Divider(color = Color(0xFF21262D))
                    }
                }
            }
        }
    }
}

// TAB 4: CI/CD WORKFLOWS ACTIONS MONITOR (GITHUB ACTIONS)
@Composable
fun ActionsTabView(
    workflowRuns: List<GithubWorkflowRun>,
    activeRun: GithubWorkflowRun?,
    onTriggerActionRun: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("🤖 Github Actions পাইপলাইন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("বিল্ড, লিন্ট এবং আর্টফ্যাক্ট জেনারেশন", color = Color.Gray, fontSize = 11.sp)
                }
                Button(
                    onClick = onTriggerActionRun,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3FB950)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = activeRun == null,
                    modifier = Modifier.testTag("run_action_trigger")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("রান করুন", fontSize = 12.sp)
                }
            }
        }

        // Active terminal simulator showing log output scrolling down in real time
        if (activeRun != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                border = BorderStroke(1.dp, Color(0xFF3FB950)) // Glowing Green border to indicate active run
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "📺 রিয়েল-টাইম বিল্ড কনসোল: #${activeRun.runNumber}",
                        color = Color(0xFF3FB950),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        activeRun.steps.forEach { step ->
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (step.status) {
                                        "COMPLETED" -> Icons.Default.CheckCircle to Color(0xFF3FB950)
                                        "RUNNING" -> Icons.Default.Sync to Color(0xFFFF9F1C)
                                        else -> Icons.Default.Schedule to Color.Gray
                                    }
                                    Icon(icon.first, contentDescription = null, modifier = Modifier.size(12.dp), tint = icon.second)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(step.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                step.logs.forEach { log ->
                                    Text(
                                        text = "   $ $log",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Closed historic lists of pipeline runs
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("📋 পাইপলাইন রান ইতিহাস (CI/CD History)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(workflowRuns) { run ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(Color(0xFF21262D), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val stateIcon = if (run.status == "SUCCESS") {
                                    Icons.Default.CheckCircle to Color(0xFF3FB950)
                                } else {
                                    Icons.Default.Error to Color(0xFFF85149)
                                }
                                Icon(stateIcon.first, contentDescription = null, modifier = Modifier.size(20.dp), tint = stateIcon.second)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(run.workflowName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("কমিট: ${run.commitMessage.take(30)}...", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("#${run.runNumber}", color = Color(0xFF58A6FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${run.durationSeconds}s | ${run.completedTime}", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// TAB 5: OPTIONAL PREPARATORY CLOUD INTEGRATION & CREDENTIALS
@Composable
fun SyncTabView(
    syncStatus: CloudSyncStatus,
    onSyncClick: () -> Unit,
    onRepoLinkChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("☁️ ক্লাউড অটোসিঙ্ক সেটিংস", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(14.dp))
                
                Text("সংযুক্ত গিটহাব রিপোজিটরি:", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = syncStatus.connectedRepo,
                    onValueChange = onRepoLinkChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("গিটহাব পার্সোনাল অ্যাক্সেস টোকেন:", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                var githubToken by remember { mutableStateOf("ghp_************************") }
                OutlinedTextField(
                    value = githubToken,
                    onValueChange = { githubToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("অফলাইন ফোল্ডার অটো-সিঙ্ক", color = Color.White, fontSize = 14.sp)
                        Text("ফাইল সেভ করার সাথে সাথে অটো সিঙ্ক হবে", color = Color.Gray, fontSize = 11.sp)
                    }
                    var autoSyncChecked by remember { mutableStateOf(true) }
                    Switch(
                        checked = autoSyncChecked,
                        onCheckedChange = { autoSyncChecked = it },
                        modifier = Modifier.testTag("auto_backup_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSyncClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cloud_backup_sync_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
            enabled = !syncStatus.isSyncing
        ) {
            if (syncStatus.isSyncing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ক্লাউড সিঙ্কিং হচ্ছে...")
            } else {
                Icon(Icons.Default.CloudSync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ম্যানুয়ালি এখনই ক্লাউড সিঙ্ক করুন (Push & Pull)")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = syncStatus.lastSyncTime,
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// BOTTOM SYSTEM NAVIGATION BAR
@Composable
fun BottomNavigationBar(activeTab: Tab, onTabSelected: (Tab) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF161B22),
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val tabList = listOf(
            Triple(Tab.FILES, Icons.Default.FolderOpen, Icons.Outlined.FolderOpen),
            Triple(Tab.EDITOR, Icons.Default.Code, Icons.Outlined.Code),
            Triple(Tab.GIT, Icons.Default.ForkLeft, Icons.Outlined.ForkLeft),
            Triple(Tab.ACTIONS, Icons.Default.SmartToy, Icons.Outlined.SmartToy),
            Triple(Tab.SYNC, Icons.Default.CloudSync, Icons.Outlined.CloudSync)
        )

        tabList.forEach { item ->
            val (tab, selectedIcon, unselectedIcon) = item
            val isSelected = activeTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) selectedIcon else unselectedIcon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text(tab.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF3FB950), // Standard Neon GitHub style
                    unselectedIconColor = Color.LightGray,
                    selectedTextColor = Color(0xFF3FB950),
                    unselectedTextColor = Color.LightGray,
                    indicatorColor = Color(0xFF21262D)
                ),
                modifier = Modifier.testTag("nav_item_${tab.id}")
            )
        }
    }
}

// STYLIZED EXTENSIONS RENDERING UTIL
fun getFileIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = name.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "kt" -> Icons.Default.Code
        "py" -> Icons.Default.Code
        "js" -> Icons.Default.Code
        "html" -> Icons.Default.Html
        "css" -> Icons.Default.Css
        "json" -> Icons.Default.Settings
        else -> Icons.Default.Article
    }
}

fun getFileIconColor(name: String): Color {
    val ext = name.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "kt" -> Color(0xFFA5D6FF) // blue
        "py" -> Color(0xFFE5C07B) // yellow
        "js" -> Color(0xFFF9E076) // bright yellow
        "html" -> Color(0xFFFF9F1C) // orange
        "css" -> Color(0xFF58A6FF) // cyan blue
        "json" -> Color(0xFFD2A8FF) // purple
        else -> Color.LightGray
    }
}

// Simulated JSON deep reformatter block
fun formatJsonSimulation(input: String): String {
    // Basic formatting lines to clean curly indents
    var indent = 0
    val sb = java.lang.StringBuilder()
    val clean = input.replace("\n", "").replace("\\s+".toRegex(), " ")
    for (i in clean.indices) {
        val char = clean[i]
        if (char == '{' || char == '[') {
            sb.append(char).append("\n")
            indent++
            sb.append("  ".repeat(indent))
        } else if (char == '}' || char == ']') {
            sb.append("\n")
            indent = maxOf(0, indent - 1)
            sb.append("  ".repeat(indent)).append(char)
        } else if (char == ',') {
            sb.append(char).append("\n").append("  ".repeat(indent))
        } else if (char == ':') {
            sb.append(" : ")
        } else {
            sb.append(char)
        }
    }
    return sb.toString().replace("  :  ", ": ").replace(" ,", ",").trim()
}

// Send local file content via FileProvider Sharing System Intent
fun shareZipFile(context: Context, file: File) {
    try {
        val authority = "com.aistudio.codeeditor.qgzwpl.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Zip ফাইলটি শেয়ার করুন")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "শেয়ারিং সম্পন্ন করা সম্ভব হয়নি: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
