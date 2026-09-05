package com.bellogate_caliphate.chatgpthelper.data

data class Batch(
    val index: Int,
    val urls: List<String>
) {
    val formattedPrompt: String
        get() = urls.joinToString(separator = "\n")
}

data class UrlBatchInfo(
    val totalUrls: Int,
    val totalBatches: Int,
    val batches: List<Batch>
)

object UrlBatchParser {

    fun parseBatches(rawContent: String): UrlBatchInfo {
        if (rawContent.isBlank()) {
            return UrlBatchInfo(totalUrls = 0, totalBatches = 0, batches = emptyList())
        }

        val normalized = rawContent.replace("\r\n", "\n")
        val rawBlocks = normalized.split(Regex("\n\\s*\n+"))

        val batches = mutableListOf<Batch>()
        var globalIndex = 1

        for (block in rawBlocks) {
            val lines = block.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }

            if (lines.isNotEmpty()) {
                batches.add(
                    Batch(
                        index = globalIndex++,
                        urls = lines
                    )
                )
            }
        }

        val totalUrls = batches.sumOf { it.urls.size }
        return UrlBatchInfo(
            totalUrls = totalUrls,
            totalBatches = batches.size,
            batches = batches
        )
    }
}
