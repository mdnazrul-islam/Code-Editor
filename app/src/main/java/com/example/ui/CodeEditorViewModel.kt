package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.model.*
import com.example.service.GeminiRequest
import com.example.service.RetrofitClient
import com.example.service.Content as GeminiContent
import com.example.service.Part as GeminiPart
import com.example.util.WorkspaceManager
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CodeEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()

    // Workspace & Editor states
    private val _filesList = MutableStateFlow<List<FileItem>>(emptyList())
    val filesList: StateFlow<List<FileItem>> = _filesList.asStateFlow()

    private val _selectedFile = MutableStateFlow<FileItem?>(null)
    val selectedFile: StateFlow<FileItem?> = _selectedFile.asStateFlow()

    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    // Undo / Redo Stacks for active editor
    private var undoStack = mutableListOf<String>()
    private var redoStack = mutableListOf<String>()

    // Git simulator states
    private val _currentBranch = MutableStateFlow("main")
    val currentBranch: StateFlow<String> = _currentBranch.asStateFlow()

    private val _gitCommits = MutableStateFlow<List<Commit>>(emptyList())
    val gitCommits: StateFlow<List<Commit>> = _gitCommits.asStateFlow()

    // Compare original file states to detect modifications
    private val baseFilesMap = mutableMapOf<String, String>()

    // Staged files for git check-ins
    private val _stagedFiles = MutableStateFlow<Set<String>>(emptySet())
    val stagedFiles: StateFlow<Set<String>> = _stagedFiles.asStateFlow()

    // Modified files state tracking
    private val _modifiedFiles = MutableStateFlow<List<String>>(emptyList())
    val modifiedFiles: StateFlow<List<String>> = _modifiedFiles.asStateFlow()

    // GitHub action runs mockup
    private val _workflowRuns = MutableStateFlow<List<GithubWorkflowRun>>(emptyList())
    val workflowRuns: StateFlow<List<GithubWorkflowRun>> = _workflowRuns.asStateFlow()

    private val _activeRunningRun = MutableStateFlow<GithubWorkflowRun?>(null)
    val activeRunningRun: StateFlow<GithubWorkflowRun?> = _activeRunningRun.asStateFlow()

    // Cloud connection state
    private val _syncStatus = MutableStateFlow(CloudSyncStatus())
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    // AI Helper state
    private val _aiResult = MutableStateFlow<String?>(null)
    val aiResult: StateFlow<String?> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    init {
        // Prepare demo workflow, demo repo commits and sample workspace
        setupInitialFiles()
    }

    private fun setupInitialFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            // Setup demo folders & files on physical local context
            WorkspaceManager.initializeDemoWorkspace(context)
            loadWorkspaceFiles()

            // Save baseline of baseline files for Git mock comparison
            val root = WorkspaceManager.getWorkspaceRootDir(context)
            snapshotBaseline(root)

            // Setup mock commits
            _gitCommits.value = listOf(
                Commit(
                    id = "db7f82b",
                    message = "🎯 Initial commit with code configurations and demo sources",
                    author = "Nazrul Islam <nazrul.islam.uli019@gmail.com>",
                    timestamp = System.currentTimeMillis() - 7200000,
                    changedFiles = listOf("src/MainActivity.kt", "config.json")
                ),
                Commit(
                    id = "e19c9ba",
                    message = "🎨 Stylized responsive web workspace in HTML/CSS/JS",
                    author = "Nazrul Islam <nazrul.islam.uli019@gmail.com>",
                    timestamp = System.currentTimeMillis() - 3600000,
                    changedFiles = listOf("web/index.html", "web/styles.css", "web/script.js")
                )
            )

            // Build starter GitHub Action run history
            _workflowRuns.value = listOf(
                GithubWorkflowRun(
                    runNumber = 12,
                    workflowName = "CI/CD Build & Syntax Verify",
                    status = "SUCCESS",
                    triggerSource = "push",
                    commitMessage = "🎨 Stylized responsive web workspace in HTML/CSS/JS",
                    durationSeconds = 48,
                    completedTime = "1 hour ago",
                    steps = listOf(
                        WorkflowStep("Checkout repository", "COMPLETED", listOf("Checkout branch 'main'", "Success: Checkout completed in 2s")),
                        WorkflowStep("Set up JDK 17", "COMPLETED", listOf("Installing Amazon Corretto 17 JDK", "JDK added to paths")),
                        WorkflowStep("Validate syntax & code formats", "COMPLETED", listOf("Scanning files: Kotlin, HTML, CSS", "Format checks: Passed")),
                        WorkflowStep("Build and compile application assets", "COMPLETED", listOf("Triggering gradle assembly", "Packaged successfully: dev_build_v12.zip"))
                    )
                ),
                GithubWorkflowRun(
                    runNumber = 11,
                    workflowName = "Automated Unit Tests Pipeline",
                    status = "FAILURE",
                    triggerSource = "pull_request",
                    commitMessage = "⚠️ Fix syntax error in sample main program",
                    durationSeconds = 25,
                    completedTime = "3 hours ago",
                    steps = listOf(
                        WorkflowStep("Checkout repository", "COMPLETED", listOf("Checkout branch 'dev'")),
                        WorkflowStep("Set up JDK 17", "COMPLETED", listOf("Caches loaded correctly")),
                        WorkflowStep("Run unit tests & UI scenarios", "FAILED", listOf("Initializing Espresso Unit Runner...", "Error count: 1 failed in UnitTest", "Error: Missing import reference inside script file")),
                        WorkflowStep("Upload build logs artifact", "COMPLETED", listOf("Created error crash dump: log_dump_v11.txt"))
                    )
                )
            )
        }
    }

    private fun snapshotBaseline(directory: File) {
        val rootPath = WorkspaceManager.getWorkspaceRootDir(context).absolutePath
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                snapshotBaseline(file)
            } else {
                val relativePath = file.absolutePath.removePrefix(rootPath).trim('/')
                baseFilesMap[relativePath] = file.readText()
            }
        }
    }

    // Load file list from background IO thread
    fun loadWorkspaceFiles() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                WorkspaceManager.listWorkspaceFiles(context)
            }
            _filesList.value = list
            refreshModifiedFiles()
        }
    }

    // Select file with Undo stack resets
    fun selectFile(fileItem: FileItem) {
        if (fileItem.isDirectory) return
        _selectedFile.value = fileItem
        viewModelScope.launch(Dispatchers.IO) {
            val content = WorkspaceManager.readFileContent(context, fileItem.path)
            _editorText.value = content
            _isModified.value = isFileContentModified(fileItem.path, content)
            
            // Clear current file action stacks
            undoStack.clear()
            redoStack.clear()
        }
    }

    // Handles user typing
    fun updateEditorText(newText: String) {
        val current = _editorText.value
        if (current != newText) {
            // Simple logic to debounce adding to undo stack to avoid stack overflows
            if (undoStack.isEmpty() || current != undoStack.lastOrNull()) {
                if (undoStack.size > 50) undoStack.removeAt(0)
                undoStack.add(current)
            }
            redoStack.clear()
            _editorText.value = newText

            val file = _selectedFile.value
            if (file != null) {
                val modified = isFileContentModified(file.path, newText)
                _isModified.value = modified
                
                val currentList = _modifiedFiles.value.toMutableSet()
                if (modified) {
                    currentList.add(file.path)
                } else {
                    currentList.remove(file.path)
                }
                _modifiedFiles.value = currentList.toList()
            }
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(_editorText.value)
            _editorText.value = previous
            
            val file = _selectedFile.value
            if (file != null) {
                val modified = isFileContentModified(file.path, previous)
                _isModified.value = modified
                
                val currentList = _modifiedFiles.value.toMutableSet()
                if (modified) {
                    currentList.add(file.path)
                } else {
                    currentList.remove(file.path)
                }
                _modifiedFiles.value = currentList.toList()
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(_editorText.value)
            _editorText.value = next

            val file = _selectedFile.value
            if (file != null) {
                val modified = isFileContentModified(file.path, next)
                _isModified.value = modified
                
                val currentList = _modifiedFiles.value.toMutableSet()
                if (modified) {
                    currentList.add(file.path)
                } else {
                    currentList.remove(file.path)
                }
                _modifiedFiles.value = currentList.toList()
            }
        }
    }

    private fun isFileContentModified(path: String, text: String): Boolean {
        val original = baseFilesMap[path] ?: ""
        return original != text
    }

    // Save edited file
    fun saveCurrentFile() {
        val file = _selectedFile.value ?: return
        val text = _editorText.value
        viewModelScope.launch(Dispatchers.IO) {
            val success = WorkspaceManager.writeFileContent(context, file.path, text)
            withContext(Dispatchers.Main) {
                if (success) {
                    _isModified.value = isFileContentModified(file.path, text)
                    Toast.makeText(context, "ফাইল সফলভাবে সংরক্ষণ করা হয়েছে", Toast.LENGTH_SHORT).show()
                    loadWorkspaceFiles()
                } else {
                    Toast.makeText(context, "সংরক্ষণ করতে ব্যর্থ!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun createWorkspaceFile(name: String, isFolder: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileCreated = WorkspaceManager.createFile(context, name, isFolder)
            withContext(Dispatchers.Main) {
                if (fileCreated) {
                    Toast.makeText(context, "তৈরি করা সম্পূর্ণ: $name", Toast.LENGTH_SHORT).show()
                    if (!isFolder) {
                        // Put new file empty baseline
                        baseFilesMap[name] = ""
                    }
                    loadWorkspaceFiles()
                } else {
                    Toast.makeText(context, "তৈরি করতে ব্যর্থ!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteWorkspaceFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = WorkspaceManager.deleteFile(context, path)
            withContext(Dispatchers.Main) {
                if (deleted) {
                    Toast.makeText(context, "সম্পূর্ণ মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    if (_selectedFile.value?.path == path) {
                        _selectedFile.value = null
                        _editorText.value = ""
                        _isModified.value = false
                    }
                    loadWorkspaceFiles()
                } else {
                    Toast.makeText(context, "মুছে ফেলতে ত্রুটি!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun renameWorkspaceFile(oldPath: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val renamed = WorkspaceManager.renameFile(context, oldPath, newName)
            withContext(Dispatchers.Main) {
                if (renamed) {
                    Toast.makeText(context, "সফলভাবে রিনেম করা হয়েছে", Toast.LENGTH_SHORT).show()
                    if (_selectedFile.value?.path == oldPath) {
                        // Re-select renamed file
                        val parentPath = oldPath.substringBeforeLast("/", "")
                        val finalPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
                        val updatedFile = FileItem(name = newName, path = finalPath, isDirectory = false)
                        _selectedFile.value = updatedFile
                    }
                    loadWorkspaceFiles()
                } else {
                    Toast.makeText(context, "রিনেম ব্যর্থ!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Export entire Workspace as a ZIP and get sharing Intent file
    fun getExportZipFile(onFinish: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = WorkspaceManager.zipWorkspace(context)
            withContext(Dispatchers.Main) {
                onFinish(file)
            }
        }
    }

    // Git Operations
    fun getModifiedFiles(): List<String> {
        return _modifiedFiles.value
    }

    fun refreshModifiedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<String>()
            val root = WorkspaceManager.getWorkspaceRootDir(context)
            for ((path, originalText) in baseFilesMap) {
                val file = File(root, path)
                if (file.exists() && file.isFile) {
                    try {
                        val currentText = file.readText()
                        if (currentText != originalText) {
                            list.add(path)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _modifiedFiles.value = list
        }
    }

    fun toggleStageFile(path: String) {
        val currentSet = _stagedFiles.value.toMutableSet()
        if (currentSet.contains(path)) {
            currentSet.remove(path)
        } else {
            currentSet.add(path)
        }
        _stagedFiles.value = currentSet
    }

    fun switchBranch(branch: String) {
        _currentBranch.value = branch
        Toast.makeText(context, "ব্রাঞ্চ পরিবর্তন: '$branch'", Toast.LENGTH_SHORT).show()
    }

    // Computes green (+) added and red (-) deleted lists for visual diff
    fun getDiffForFile(path: String): List<Pair<String, Color>> {
        val original = baseFilesMap[path] ?: ""
        val current = try {
            val root = WorkspaceManager.getWorkspaceRootDir(context)
            File(root, path).readText()
        } catch (e: Exception) { "" }

        val result = mutableListOf<Pair<String, Color>>()
        val origLines = original.lines()
        val currLines = current.lines()

        // Quick LCS/Diff approximation for crisp UI listing
        val maxLines = maxOf(origLines.size, currLines.size)
        for (i in 0 until maxLines) {
            val orig = origLines.getOrNull(i)
            val curr = currLines.getOrNull(i)

            if (orig != curr) {
                if (orig != null && orig.trim().isNotEmpty()) {
                    result.add("-" to Color(0xFFF85149)) // Red for delete representation
                    result.add(" $orig" to Color(0xFFF85149))
                }
                if (curr != null && curr.trim().isNotEmpty()) {
                    result.add("+" to Color(0xFF58A6FF)) // Green for added representation
                    result.add(" $curr" to Color(0xFF3FB950))
                }
            } else if (orig != null) {
                result.add("  $orig" to Color.LightGray)
            }
        }
        return result
    }

    fun commitStagedFiles(message: String) {
        if (message.isBlank()) {
            Toast.makeText(context, "দয়া করে একটি কমিট মেসেজ লিখুন", Toast.LENGTH_SHORT).show()
            return
        }

        val changed = getModifiedFiles()
        if (changed.isEmpty()) {
            Toast.makeText(context, "কোনো পরিবর্তন করা ফাইল পাওয়া যায়নি!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Commit logic - save current snapshot into baseFiles
            for (path in changed) {
                val file = File(WorkspaceManager.getWorkspaceRootDir(context), path)
                if (file.exists()) {
                    baseFilesMap[path] = file.readText()
                }
            }

            val newCommit = Commit(
                id = UUID.randomUUID().toString().take(7),
                message = "📦 $message",
                author = "Nazrul Islam <nazrul.islam.uli019@gmail.com>",
                timestamp = System.currentTimeMillis(),
                changedFiles = changed.toList()
            )

            // Insert at the top of commit list
            withContext(Dispatchers.Main) {
                val currentCommits = _gitCommits.value.toMutableList()
                currentCommits.add(0, newCommit)
                _gitCommits.value = currentCommits
                _stagedFiles.value = emptySet()
                _isModified.value = false
                Toast.makeText(context, "সফলভাবে কমিট সম্পন্ন হয়েছে", Toast.LENGTH_LONG).show()
            }
        }
    }

    // GitHub action runner progress simulations
    fun triggerWorkflowRun() {
        if (_activeRunningRun.value != null) return

        val message = _gitCommits.value.firstOrNull()?.message ?: "📦 Force build simulation triggers"
        val nextRunNumber = (_workflowRuns.value.maxOfOrNull { it.runNumber } ?: 0) + 1

        val run = GithubWorkflowRun(
            runNumber = nextRunNumber,
            workflowName = "Main Code Verification & Artifact Package",
            status = "IN_PROGRESS",
            triggerSource = "manual",
            commitMessage = message,
            durationSeconds = 0,
            completedTime = "Running...",
            steps = listOf(
                WorkflowStep("Checkout repository codes", "RUNNING", listOf("Scanning git tree..."), 0),
                WorkflowStep("Setup compile container (Corretto JDK 17)", "IDLE", emptyList(), 0),
                WorkflowStep("Run standard unit testing coverage", "IDLE", emptyList(), 0),
                WorkflowStep("Perform zip compile build & export APK target", "IDLE", emptyList(), 0)
            )
        )

        _activeRunningRun.value = run

        viewModelScope.launch(Dispatchers.Default) {
            // Step 1 simulation
            delay(1500)
            updateRunStep(0, "COMPLETED", listOf(
                "Initializing local filesystem mounting",
                "Checking out repository commit HEAD...",
                "Commit metadata: Nazrul Islam - $message",
                "SUCCESS: Checkout verified successfully"
            ), 2)

            // Step 2 simulation
            updateRunStep(1, "RUNNING", listOf("Installing Java binaries", "Mapping compiler files tools..."), 2)
            delay(2000)
            updateRunStep(1, "COMPLETED", listOf("Using Amazon Corretto 17.0.8 JDK compiler", "Compiled targets successfully configured on sandbox PATH"), 4)

            // Step 3 simulation
            updateRunStep(2, "RUNNING", listOf("Injecting Mock JUnit framework engine...", "Running automated scripts..."), 4)
            delay(2500)
            updateRunStep(2, "COMPLETED", listOf("UnitTest: test FACTORIAL evaluation passed (0ms)", "UnitTest: test SORT recursion valid (1ms)", "UnitTest: test OFFLINE EDITOR loading checked (0ms)", "Success: 100% test coverage passed successfully!"), 6)

            // Step 4 simulation
            updateRunStep(3, "RUNNING", listOf("Compressing files into release APK target container...", "Evaluating compression keys..."), 6)
            delay(2000)
            updateRunStep(3, "COMPLETED", listOf("ZIP Archiver complete.", "Exported artifact: target_build_v${nextRunNumber}.zip in cacheDir"), 8)

            // Finish
            val finishedRun = _activeRunningRun.value?.copy(
                status = "SUCCESS",
                durationSeconds = 12,
                completedTime = "Just now"
            )

            if (finishedRun != null) {
                withContext(Dispatchers.Main) {
                    val currentRuns = _workflowRuns.value.toMutableList()
                    currentRuns.add(0, finishedRun)
                    _workflowRuns.value = currentRuns
                    _activeRunningRun.value = null
                    Toast.makeText(context, "পাইপলাইন সফলভাবে সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateRunStep(index: Int, status: String, logs: List<String>, durationSeconds: Int) {
        val currentRun = _activeRunningRun.value ?: return
        val currentSteps = currentRun.steps.toMutableList()
        val existingStep = currentSteps[index]
        currentSteps[index] = existingStep.copy(status = status, logs = logs, durationSeconds = durationSeconds)

        val nextStatus = if (currentSteps.all { it.status == "COMPLETED" }) "SUCCESS" else "IN_PROGRESS"
        _activeRunningRun.value = currentRun.copy(
            status = nextStatus,
            durationSeconds = currentSteps.sumOf { it.durationSeconds },
            steps = currentSteps
        )
    }

    // Simulated Cloud sync triggers
    fun performCloudSync() {
        val current = _syncStatus.value
        if (current.isSyncing) return

        _syncStatus.value = current.copy(isSyncing = true)
        viewModelScope.launch(Dispatchers.IO) {
            delay(3000) // simulated delay
            withContext(Dispatchers.Main) {
                _syncStatus.value = CloudSyncStatus(
                    connectedRepo = current.connectedRepo,
                    lastSyncTime = "সরাসরি সিঙ্ক করা হয়েছে: Just now",
                    syncPendingCount = 0,
                    isSyncing = false
                )
                Toast.makeText(context, "ক্লাউড সিঙ্ক্রোনাইজেশন সম্পন্ন হয়েছে!", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateSyncRepo(repo: String) {
        _syncStatus.value = _syncStatus.value.copy(connectedRepo = repo)
    }

    // Direct Gemini AI code helper integration
    fun queryGeminiAssistant(instruction: String) {
        val code = _editorText.value
        val file = _selectedFile.value
        val ext = file?.name?.substringAfterLast(".", "kt") ?: "kt"

        val prompt = if (code.isBlank()) {
            "You are a coding assistant. Write a simple mock $ext code for: $instruction"
        } else {
            "Here is my code (extension: $ext):\n```\n$code\n```\nInstruction: $instruction\nOnly output the replacement code or your helpful developer advice clearly."
        }

        _isAiLoading.value = true
        _aiResult.value = null

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _aiResult.value = "দুঃখিত! Gemini API key পাওয়া যায়নি। দয়া করে AI Studio Secrets panel-এ আপনার GEMINI_API_KEY প্রদান করুন।"
                    _isAiLoading.value = false
                    return@launch
                }

                val req = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = prompt)
                            )
                        )
                    )
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, req)
                }

                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _aiResult.value = text ?: "Gemini থেকে কোনো রেসপন্স পাওয়া যায়নি।"
            } catch (e: Exception) {
                _aiResult.value = "ত্রুটি ঘটেছে: ${e.localizedMessage ?: e.message}"
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun applyAiCodeToEditor(codeSegment: String) {
        // Strip markdown code wrap patterns ``` if AI generated code blocks
        var cleaned = codeSegment
        if (cleaned.contains("```")) {
            val lines = cleaned.lines()
            val filteredLines = lines.filter { !it.trim().startsWith("```") }
            cleaned = filteredLines.joinToString("\n")
        }
        updateEditorText(cleaned)
        _aiResult.value = null
        Toast.makeText(context, "কোড এডিটরে সফলভাবে যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
    }

    fun clearAiOutput() {
        _aiResult.value = null
    }
}
