package common

data class OracleIndexItem(
    val id: String,
    val url: String,
    val source: String,
){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OracleIndexItem

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
