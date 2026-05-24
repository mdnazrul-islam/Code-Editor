package com.example.util

import android.content.Context
import com.example.model.FileItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object WorkspaceManager {

    private const val WORKSPACE_DIR_NAME = "code_editor_workspace"

    fun getWorkspaceRootDir(context: Context): File {
        val root = File(context.filesDir, WORKSPACE_DIR_NAME)
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    // Prepare demo files inside workspace so users can immediately edit multiple languages offline
    fun initializeDemoWorkspace(context: Context) {
        val workspace = getWorkspaceRootDir(context)
        
        // Let's create demo folders
        val srcDir = File(workspace, "src")
        val webDir = File(workspace, "web")
        srcDir.mkdirs()
        webDir.mkdirs()

        // 1. Kotlin File
        val ktFile = File(srcDir, "MainActivity.kt")
        if (!ktFile.exists()) {
            ktFile.writeText("""package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodeCraftExample()
        }
    }
}

@Composable
fun CodeCraftExample() {
    var count by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Offline Editor Preview") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = { count++ }) {
                Text("Tap Count: ${'$'}count")
            }
        }
    }
}
""".trimIndent())
        }

        // 2. Python File
        val pyFile = File(workspace, "app.py")
        if (!pyFile.exists()) {
            pyFile.writeText("""# Quick Sort & Binary Search Algorithm demo
# Offline Python environment simulator

def qsort(arr):
    if len(arr) <= 1:
        return arr
    pivot = arr[len(arr) // 2]
    left = [x for x in arr if x < pivot]
    middle = [x for x in arr if x == pivot]
    right = [x for x in arr if x > pivot]
    return qsort(left) + middle + qsort(right)

def binary_search(arr, target):
    low = 0
    high = len(arr) - 1
    
    while low <= high:
        mid = (low + high) // 2
        if arr[mid] == target:
            return f"Found at index: {mid}"
        elif arr[mid] < target:
            low = mid + 1
        else:
            high = mid - 1
    return "Element not found"

if __name__ == "__main__":
    elements = [42, 10, 77, 101, 3, 5, 25]
    sorted_list = qsort(elements)
    print("Sorted Output:", sorted_list)
    result = binary_search(sorted_list, 42)
    print("Search Result:", result)
""".trimIndent())
        }

        // 3. HTML File
        val htmlFile = File(webDir, "index.html")
        if (!htmlFile.exists()) {
            htmlFile.writeText("""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Responsive Web IDE</title>
    <link rel="stylesheet" href="styles.css">
</head>
<body>
    <div class="card">
        <h1>Welcome to Web Workspace</h1>
        <p>This HTML/CSS/JS stack works fully offline inside Code Editor apk.</p>
        <button id="action-btn">Trigger Script</button>
        <div id="status">Ready</div>
    </div>
    <script src="script.js"></script>
</body>
</html>
""".trimIndent())
        }

        // 4. CSS File
        val cssFile = File(webDir, "styles.css")
        if (!cssFile.exists()) {
            cssFile.writeText(""":root {
    --bg-color: #0d1117;
    --text-color: #c9d1d9;
    --accent-color: #238636;
}

body {
    background-color: var(--bg-color);
    color: var(--text-color);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    margin: 0;
}

.card {
    border: 1px solid #30363d;
    background-color: #161b22;
    padding: 24px;
    border-radius: 12px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.5);
    text-align: center;
}

button {
    background-color: var(--accent-color);
    color: white;
    border: none;
    padding: 10px 20px;
    font-size: 16px;
    border-radius: 6px;
    cursor: pointer;
    transition: background 0.2s;
}

button:hover {
    background-color: #2ea043;
}
""".trimIndent())
        }

        // 5. JS File
        val jsFile = File(webDir, "script.js")
        if (!jsFile.exists()) {
            jsFile.writeText("""// Dynamic interactive JavaScript
document.getElementById('action-btn').addEventListener('click', () => {
    const statusDiv = document.getElementById('status');
    statusDiv.textContent = "Clicked! Running background process...";
    statusDiv.style.color = "#ff7b72";
    
    setTimeout(() => {
        statusDiv.textContent = "Task successfully complete in Web Console!";
        statusDiv.style.color = "#58a6ff";
    }, 1500);
});
""".trimIndent())
        }

        // 6. JSON Configuration File
        val jsonFile = File(workspace, "config.json")
        if (!jsonFile.exists()) {
            jsonFile.writeText("""{
  "project": "Dev Workspace",
  "version": "1.0.0",
  "author": "Nazrul Islam",
  "offlineSync": true,
  "theme": "Neon Slate",
  "activeBranches": [
    "main",
    "dev",
    "feature/editor-ui"
  ],
  "dependencies": {
    "syntax-highlighting": "enabled",
    "auto-completion": "enabled"
  }
}
""".trimIndent())
        }
    }

    // Returns structural mapping of existing workspace files
    fun listWorkspaceFiles(context: Context, directory: File? = null): List<FileItem> {
        val targetDir = directory ?: getWorkspaceRootDir(context)
        val files = targetDir.listFiles() ?: return emptyList()
        val rootPath = getWorkspaceRootDir(context).absolutePath

        return files.map { file ->
            val relativePath = file.absolutePath.removePrefix(rootPath).trim('/')
            if (file.isDirectory) {
                // Recursive listing
                val children = listWorkspaceFiles(context, file)
                FileItem(
                    name = file.name,
                    path = relativePath,
                    isDirectory = true,
                    size = 0,
                    lastModified = file.lastModified(),
                    children = children
                )
            } else {
                FileItem(
                    name = file.name,
                    path = relativePath,
                    isDirectory = false,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            }
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun readFileContent(context: Context, relativePath: String): String {
        val file = File(getWorkspaceRootDir(context), relativePath)
        return if (file.exists() && file.isFile) {
            file.readText()
        } else {
            ""
        }
    }

    fun writeFileContent(context: Context, relativePath: String, content: String): Boolean {
        return try {
            val file = File(getWorkspaceRootDir(context), relativePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createFile(context: Context, relativePath: String, isFolder: Boolean): Boolean {
        return try {
            val file = File(getWorkspaceRootDir(context), relativePath)
            if (isFolder) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                file.createNewFile()
                // initialize template if any matching extension is found
                when (file.extension) {
                    "kt" -> file.writeText("fun main() {\n    println(\"Hello World\")\n}")
                    "py" -> file.writeText("def main():\n    print(\"Hello World\")\n\nif __name__ == '__main__':\n    main()")
                    "html" -> file.writeText("<!DOCTYPE html>\n<html>\n<head><title>Page</title></head>\n<body>\n    <h1>Hello</h1>\n</body>\n</html>")
                    "css" -> file.writeText("body {\n    background-color: black;\n    color: white;\n}")
                    "js" -> file.writeText("console.log(\"Execution triggered\");")
                    "json" -> file.writeText("{\n  \"status\": \"success\"\n}")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteFile(context: Context, relativePath: String): Boolean {
        return try {
            val file = File(getWorkspaceRootDir(context), relativePath)
            if (file.exists()) {
                file.deleteRecursively()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun renameFile(context: Context, oldRelativePath: String, newName: String): Boolean {
        return try {
            val root = getWorkspaceRootDir(context)
            val oldFile = File(root, oldRelativePath)
            val parent = oldFile.parentFile ?: root
            val newFile = File(parent, newName)
            if (oldFile.exists() && !newFile.exists()) {
                oldFile.renameTo(newFile)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Zip and export workspace to the Cache directory for sharing
    fun zipWorkspace(context: Context): File? {
        val rootDir = getWorkspaceRootDir(context)
        val zipFile = File(context.cacheDir, "workspace_dev_export.zip")
        if (zipFile.exists()) {
            zipFile.delete()
        }
        return try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zipDirectory(rootDir, rootDir, zos)
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun zipDirectory(rootDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(4096)
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(rootDir, file, zos)
            } else {
                val relativePath = file.absolutePath.substring(rootDir.absolutePath.length + 1)
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    var count: Int
                    while (fis.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeEntry()
            }
        }
    }
}
