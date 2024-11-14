package common

typealias PygmentRawSol = Array<String>
typealias PygmentRawSolSeq = Array<PygmentRawSol>

data class PygmentSol(
    val txt: String,
    val hCode: Int
) {
    companion object {
        fun PygmentRawSol.toPygmentSol(): PygmentSol =
            PygmentSol(this[0], this[2].toInt())

        fun PygmentRawSolSeq.toPygmentSols(): Array<PygmentSol> =
            this.map { it.toPygmentSol() }.toTypedArray()

        fun PygmentRawSolSeq.toLookUpHCode(): List<Int> {
            val hCodes = mutableListOf<Int>()
            for (rawSol in this) {
                val tokValue = rawSol[2].toInt()
                for (c in 0 until rawSol[0].length) {
                    hCodes.add(tokValue)
                }
            }
            return hCodes
        }

    }
}
