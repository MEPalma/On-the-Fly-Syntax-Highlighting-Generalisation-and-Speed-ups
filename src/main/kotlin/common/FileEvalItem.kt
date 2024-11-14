package common

data class FileAccItem(
    val fileId: String,
    val isSnippet: Boolean,
    val acc: Double,
)

data class FileTimeItem(
    val fileId: String,
    val nss: List<Long>
)

data class FileSizeItem(
    val fileId: String,
    val ntoks: Int,
    val nchars: Int,
    val whitespace: Int,
    val lines: Int
)
