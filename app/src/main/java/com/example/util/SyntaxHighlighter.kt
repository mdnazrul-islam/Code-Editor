package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

object SyntaxHighlighter {

    // Theme Color Palette
    private val KeywordColor = Color(0xFFFF7B72)    // Bright Coral Pink
    private val TypeColor = Color(0xFF79C0FF)       // Light Ice Blue
    private val StringColor = Color(0xFF7EE787)     // Soft Mint Green
    private val CommentColor = Color(0xFF8B949E)    // Slate Steel Gray
    private val NumberColor = Color(0xFFD2A8FF)     // Rich Purple-Magenta
    private val AnnotationColor = Color(0xFFA5D6FF) // Sky Blue
    private val DefaultTextColor = Color(0xFFC9D1D9)// Off-white default text

    // Keyword Sets for different programming languages
    private val KotlinKeywords = listOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "return", "if", "else", "for", "while", "when", "is", "as", "in", "by",
        "private", "protected", "public", "internal", "companion", "init", "override",
        "this", "super", "break", "continue", "throw", "try", "catch", "finally",
        "typealias", "constructor", "get", "set"
    )

    private val PythonKeywords = listOf(
        "def", "class", "import", "from", "as", "return", "if", "el", "elif", "else",
        "for", "while", "in", "is", "not", "and", "or", "lambda", "pass", "break",
        "continue", "try", "except", "finally", "raise", "import", "global", "nonlocal",
        "assert", "yield", "with", "del"
    )

    private val JSKeywords = listOf(
        "function", "const", "let", "var", "class", "extends", "constructor", "return",
        "if", "else", "switch", "case", "for", "while", "do", "break", "continue",
        "import", "export", "default", "from", "new", "this", "super", "try", "catch",
        "finally", "throw", "typeof", "instanceof", "async", "await"
    )

    private val CSSKeywords = listOf(
        "display", "position", "margin", "padding", "border", "background", "color",
        "font-family", "font-size", "height", "width", "justify-content", "align-items",
        "flex", "grid", "box-shadow", "border-radius", "transition", "transform",
        "opacity", "cursor", "overflow", "z-index", "content"
    )

    // Highlight text based on extension
    fun highlight(code: String, extension: String): AnnotatedString {
        return buildAnnotatedString {
            // Append the entire content with default styling first
            append(code)
            addStyle(SpanStyle(color = DefaultTextColor), 0, code.length)

            val ext = extension.lowercase()

            // 1. Color Keywords based on language
            val keywords = when (ext) {
                "kt", "kts" -> KotlinKeywords
                "py" -> PythonKeywords
                "js", "ts" -> JSKeywords
                "css" -> CSSKeywords
                "html" -> JSKeywords // JS Keywords can serve HTML script tags
                "json" -> emptyList()
                else -> KotlinKeywords // Fallback
            }

            if (keywords.isNotEmpty()) {
                val keywordPattern = Pattern.compile("\\b(" + keywords.joinToString("|") + ")\\b")
                val matcher = keywordPattern.matcher(code)
                while (matcher.find()) {
                    addStyle(
                        SpanStyle(color = KeywordColor, fontWeight = FontWeight.SemiBold),
                        matcher.start(),
                        matcher.end()
                    )
                }
            }

            // 2. Language-specific matching details (e.g. annotations or CSS selectors, etc.)
            val typePattern = when (ext) {
                "kt", "kts" -> Pattern.compile("\\b(String|Int|Boolean|Float|Double|Long|List|Map|Set|State|MutableState|Activity|ComponentActivity|Bundle|Modifier|Scaffold|TopAppBar|Button|Text|Column|Row|Box|LazyColumn)\\b")
                "py" -> Pattern.compile("\\b(print|len|range|list|dict|int|str|float|bool|set|tuple|self)\\b")
                "js" -> Pattern.compile("\\b(console|log|document|window|setTimeout|setInterval|fetch|Promise|Array|Object|String|Number|getElementById|addEventListener)\\b")
                "css" -> Pattern.compile("(#\\w+|\\.\\w+|@media|:root|body|h1|button|div|span|p|a)\\b")
                "json" -> Pattern.compile("\"([^\"]+)\"\\s*:") // Matches keys in JSON
                "html" -> Pattern.compile("(<\\/?\\w+.*?>)")  // Matches HTML tags
                else -> Pattern.compile("\\b(String|Int|Boolean|Float|Double|Long)\\b")
            }

            val typeMatcher = typePattern.matcher(code)
            while (typeMatcher.find()) {
                addStyle(
                    SpanStyle(color = if (ext == "json") TypeColor else TypeColor, fontWeight = FontWeight.Normal),
                    typeMatcher.start(),
                    typeMatcher.end()
                )
            }

            // 3. Match Annotations (Kotlin decorators, etc.)
            if (ext == "kt" || ext == "kts") {
                val annoPattern = Pattern.compile("@[A-Za-z0-9_]+")
                val annoMatcher = annoPattern.matcher(code)
                while (annoMatcher.find()) {
                    addStyle(SpanStyle(color = AnnotationColor), annoMatcher.start(), annoMatcher.end())
                }
            }

            // 4. Color Numbers (Decimal values, Hex and floats)
            val numPattern = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)\\b")
            val numMatcher = numPattern.matcher(code)
            while (numMatcher.find()) {
                addStyle(SpanStyle(color = NumberColor), numMatcher.start(), numMatcher.end())
            }

            // 5. Color Strings (Double quotes and single quotes)
            val strPattern = Pattern.compile("(\"[^\"]*\"|'[^']*')")
            val strMatcher = strPattern.matcher(code)
            while (strMatcher.find()) {
                addStyle(SpanStyle(color = StringColor), strMatcher.start(), strMatcher.end())
            }

            // 6. Color Comments (Must come LAST to correctly override everything else inside them)
            val commentPattern = if (ext == "py" || ext == "properties" || ext == "env") {
                Pattern.compile("#.*")
            } else if (ext == "css") {
                Pattern.compile("/\\*[^*]*\\*+([^/*][^*]*\\*+)*/")
            } else {
                Pattern.compile("//.*|/\\*[^*]*\\*+([^/*][^*]*\\*+)*/")
            }

            val commentMatcher = commentPattern.matcher(code)
            while (commentMatcher.find()) {
                addStyle(SpanStyle(color = CommentColor), commentMatcher.start(), commentMatcher.end())
            }
        }
    }

    // Auto-complete suggestions for programming languages
    fun getAutocompleteSuggestions(extension: String, textSegment: String): List<String> {
        val lastWord = textSegment.split(Regex("[^a-zA-Z0-9_@]")).lastOrNull() ?: ""
        if (lastWord.length < 2) return emptyList()

        val list = when (extension.lowercase()) {
            "kt", "kts" -> listOf(
                "fun ", "val ", "var ", "class ", "interface ", "package ", "import ", "override ", "private ", "public ",
                "remember", "mutableStateOf(", "Scaffold", "Button", "Text(", "Column", "Row", "Box", "Modifier",
                "fillMaxSize()", "padding(", "clickable {", "lifecycle", "CoroutineScope", "viewModelScope", "Flow", "MutableStateFlow"
            )
            "py" -> listOf(
                "def ", "class ", "return ", "import ", "from ", "global ", "print(", "range(", "len(", "strip()", "append(",
                "self", "__init__", "isinstance(", "try:", "exceptException as e:"
            )
            "js" -> listOf(
                "console.log(", "function ", "const ", "let ", "document.getElementById(", "addEventListener(", "fetch(",
                "setTimeout(", "then(", "catch(", "async ", "await ", "export default ", "JSON.stringify("
            )
            "html" -> listOf(
                "&lt;div&gt;&lt;/div&gt;", "&lt;p&gt;&lt;/p&gt;", "&lt;span&gt;&lt;/span&gt;", "&lt;button id=\"&gt;&lt;/button&gt;",
                "class=\"\"", "id=\"\"", "style=\"\"", "&lt;script src=\"\"&gt;&lt;/script&gt;", "&lt;link rel=\"stylesheet\" href=\"\"&gt;"
            )
            "css" -> listOf(
                "color: ", "background-color: ", "border-radius: ", "font-family: ", "font-size: ", "padding: ", "margin: ",
                "display: flex;", "justify-content: center;", "align-items: center;", "position: relative;", "width: ", "height: "
            )
            "json" -> listOf(
                "\"version\": \"1.0.0\"", "\"author\": \"\"", "\"dependencies\": {", "\"project\": \"\"", "\"license\": \"MIT\""
            )
            else -> emptyList()
        }

        return list.filter { it.lowercase().startsWith(lastWord.lowercase()) && it.trim() != lastWord }
    }
}
