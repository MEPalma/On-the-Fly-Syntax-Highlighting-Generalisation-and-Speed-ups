package evaluator
import java.time.Duration

import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import common.*
import common.JSONSourceMarshaller.Companion.sourceToMD5FileId
import common.JSONSourceMarshaller.Companion.tryJSONHighlightedSourceFromJSON
import common.PygmentSol.Companion.toLookUpHCode
import common.PygmentSol.Companion.toPygmentSols
import highlighter.GrammaticalHighlighter
import highlighter.highlightedAs
import highlighter.toHighlightedHTML
import highlighter.tryToETAS
import org.antlr.v4.gui.TreeViewer
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTreeWalker
import utils.*
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.*
import java.lang.Exception
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import kotlin.collections.HashSet

abstract class Evaluator(
    val userArgs: Array<String>,
    val languageName: String,
    val oracleFileSourcesPath: String,
    val logOutputFilePath: String,
    val lexerOf: (CharStream) -> Lexer,
    val parserOf: (CommonTokenStream) -> Parser,
    val lexicalHighlighter: (ETA) -> HETA,
    val grammaticalHighlighter: GrammaticalHighlighter,
    val startRuleOf: (Parser) -> RuleContext,
    val relativePythonRunnerPath: String = "src/main/python/highlighter",
    val lexerChannels: Array<Int> = arrayOf(Token.HIDDEN_CHANNEL)
) : Runnable {
    private val REPEATS: Int = 30

    private fun postJson(client: HttpClient, uri: String, json: Any): HttpResponse<String> {
        val requestBody: String = jacksonObjectMapper().writeValueAsString(json)
        val request: HttpRequest =
            HttpRequest.newBuilder().uri(URI.create(uri)).setHeader("Content-Type", "application/json").POST(
                HttpRequest.BodyPublishers.ofString(requestBody)
            ).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response
    }

    private fun setupModelConnection(modelLogName: String, foldName: Int = 1): HttpClient {
        val client = HttpClient.newBuilder().build()
        this.postJson(
            client,
            "http://127.0.0.1:5000/load_model",
            LoadModelRequest(modelLogName, foldName)
        )
        return client
    }

    private fun evalWithModel(inputTokenIds: List<Int>, client: HttpClient): EvalWithModelResponse {
        val response = this.postJson(client, "http://127.0.0.1:5000/eval_model", EvalWithModelRequest(inputTokenIds))
        return jacksonObjectMapper().readValue<EvalWithModelResponse>(response.body())
    }

    private fun setupPygmentsConnection(lang: String): HttpClient {
        val client = HttpClient.newBuilder().build()
        this.postJson(
            client,
            "http://127.0.0.1:5000/load_pygments",
            LoadPygmentsRequest(lang)
        )
        return client
    }

    private fun setupTreeConnection(lang: String): HttpClient {
	val client = HttpClient.newBuilder().connectTimeout(Duration.ofDays(2)).build()
        this.postJson(
            client,
            "http://127.0.0.1:5000/setup",
            LoadPygmentsRequest(lang)
        )
        return client
    }

    private fun evalWithPygments(src: String, client: HttpClient): EvalWithPygmentsResponse {
        val response = this.postJson(client, "http://127.0.0.1:5000/eval_pygments", EvalWithPygmentsRequest(src))
        return jacksonObjectMapper().readValue<EvalWithPygmentsResponse>(response.body())
    }
    val OBJECT_MAPPER: ObjectMapper = ObjectMapper(
        JsonFactoryBuilder()
            .streamReadConstraints(
                StreamReadConstraints
                    .builder()
                    .maxStringLength(Int.MAX_VALUE) // your limit here
                    .build()
            ).build()
    ).also { it.apply { registerModule(KotlinModule()) } }
    private fun evalWithTree(src: String, client: HttpClient): EvalWithPygmentsResponse {
        val response = this.postJson(client, "http://127.0.0.1:5000/eval", EvalWithPygmentsRequest(src))
	val responseBody = response.body()
	return try {
	        OBJECT_MAPPER.readValue(responseBody, EvalWithPygmentsResponse::class.java)
	    } catch (e: Exception) {
	        println("Deserialization error: ${e.message}")
		println(responseBody)
	        throw e
	    }
    }

    private fun perFileAccModel(modelLogName: String) {
        val taskCode = modelLogName.split('_')[2].toInt()
        val taskAdapter = getTaskAdapter(taskCode) // This is how an oracle value is converted to a task value.

        for (foldName in 0..2) {
            val modClient = this.setupModelConnection(modelLogName, foldName)

            val telemetries_file = File("$logOutputFilePath/perFileAcc_${modelLogName}_${foldName}.json")
            if (!telemetries_file.isFile) {
                telemetries_file.writeText("[\n")
                //
                val jhetas_files = listOf(
                    Pair("$oracleFileSourcesPath/folds/fold${foldName}_testing.json", false),
                    Pair("$oracleFileSourcesPath/folds/fold${foldName}_snippets.json", true)
                )
                //
                var i = 0
                for (jheta_file in jhetas_files) {
                    println("Loading file ${jheta_file.first}")
                    val jhetas = jacksonObjectMapper().readValue(
                        File(jheta_file.first),
                        Array<JSONHighlightedSource>::class.java
                    )
                    //
                    var modelAccAcc = 0.0
                    //
                    for (jheta in jhetas) {
                        if (jheta.source.source.isNotEmpty() && jheta.hetas.isNotEmpty()) {
                            if (i % 100 == 0)
                                print("\rOn JHETA number $i, ${modelAccAcc / i}")

                            // Run on model.
                            val inputTokenIds = jheta.hetas.map { it.eta.tokenRule }.toList()
                            val modRes = this.evalWithModel(inputTokenIds, modClient)
                            val accModel =
                                modRes.ps.let { hCodes ->
                                    var nonWhitespaceChars = 0
                                    val numChars = jheta.source.source.length
                                    var accAccuracy = 0.toDouble()
                                    for (tokenIdx in hCodes.indices) {
                                        val predictHcode = hCodes[tokenIdx]
                                        val targetToken = jheta.hetas[tokenIdx]
                                        val targetHcode = taskAdapter[targetToken.highlightCode]
                                        val tokenSize = targetToken.eta.text.length
                                        nonWhitespaceChars += tokenSize
                                        val correctMul = if (targetHcode == predictHcode) 1 else 0
                                        accAccuracy += correctMul * tokenSize
                                    }
                                    (accAccuracy + numChars - nonWhitespaceChars) / numChars.toDouble()
                                }
                            modelAccAcc += accModel

                            // Create log.
                            val log = FileAccItem(
                                jheta.source.source.sourceToMD5FileId(),
                                jheta_file.second,
                                accModel,
                            )

                            // Write to file
                            if (i > 0)
                                telemetries_file.appendText(",\n")
                            telemetries_file.appendText(jacksonObjectMapper().writeValueAsString(log))
                            telemetries_file.appendText("\n")

                            ++i
                        }
                    }
                    println()
                }
                //
                telemetries_file.appendText("]\n")
                println("Done $foldName")
            } else println("Skipped $foldName")
        }
    }


    private fun perFileAccPygments() {
        val pygClient = this.setupPygmentsConnection(this.languageName)
        for (taskCode in arrayOf(28, 37, 55, 66)) {
            val taskAdapter = getTaskAdapter(taskCode)
            for (foldName in 0..2) {
                val telemetries_file = File("$logOutputFilePath/perFileAcc_pygments_${taskCode}_${foldName}.json")
                if (!telemetries_file.isFile) {
                    telemetries_file.writeText("[\n")
                    //
                    val jhetas_files = listOf(
                        Pair("$oracleFileSourcesPath/folds/fold${foldName}_testing.json", false),
                        Pair("$oracleFileSourcesPath/folds/fold${foldName}_snippets.json", true)
                    )
                    var i = 0
                    //
                    for (jheta_file in jhetas_files) {
                        println("Loading file ${jheta_file.first}")
                        val jhetas = jacksonObjectMapper().readValue(
                            File(jheta_file.first),
                            Array<JSONHighlightedSource>::class.java
                        )
                        //
                        var pygmAccAcc = 0.0
                        //
                        for (jheta in jhetas) {
                            if (jheta.source.source.isNotEmpty() && jheta.hetas.isNotEmpty()) {
                                // Run on pygments.
                                val pygRes = this.evalWithPygments(jheta.source.source, pygClient)
                                val accPygm =
                                    pygRes.res_json.let { strPygmentsTokenBindings ->
                                        jacksonObjectMapper().readValue<PygmentRawSolSeq?>(strPygmentsTokenBindings)
                                            ?.let { pygmentsTokenBindings ->
                                                val pygsHCodes = pygmentsTokenBindings.toLookUpHCode()
                                                val numChars = jheta.source.source.length
                                                var nonWhitespaceChars = 0
                                                var accAccuracy = 0.toDouble()
                                                for (heta in jheta.hetas) {
                                                    val startIdx = heta.eta.startIndex
                                                    val stopIdx = heta.eta.stopIndex
                                                    if (startIdx >= 0 && stopIdx >= startIdx) {
                                                        nonWhitespaceChars += heta.eta.text.length
                                                        val targetHcode = taskAdapter[heta.highlightCode]
                                                        for (charIdx in startIdx..stopIdx) {
                                                            if (charIdx <= pygsHCodes.lastIndex && taskAdapter[pygsHCodes[charIdx]] == targetHcode) {
                                                                accAccuracy += 1
                                                            }
                                                        }
                                                    }
                                                }
                                                (accAccuracy + numChars - nonWhitespaceChars) / numChars.toDouble()
                                            } ?: error("No valid acc Pygm 2 for $jheta.")
                                    } ?: error("No valid acc Pygm 1 for $jheta.")
                                pygmAccAcc += accPygm

                                // Create log.
                                val log = FileAccItem(
                                    jheta.source.source.sourceToMD5FileId(),
                                    jheta_file.second,
                                    accPygm
                                )

                                // Write to file
                                if (i > 0)
                                    telemetries_file.appendText(",\n")
                                telemetries_file.appendText(jacksonObjectMapper().writeValueAsString(log))
                                telemetries_file.appendText("\n")

                                ++i
                            }
                        }
                        println()
                    }
                    //
                    telemetries_file.appendText("]\n")
                    println("Done $foldName")
                } else println("Skipped $foldName")
            }
        }
    }

    private fun perFileAccTree() {
        val pygClient = this.setupTreeConnection(this.languageName)
        for (taskCode in arrayOf(28, 37, 55, 66)) {
            val taskAdapter = getTaskAdapter(taskCode)
            for (foldName in 0..2) {
                val telemetries_file = File("$logOutputFilePath/perFileAcc_tree_${taskCode}_${foldName}.json")
                if (!telemetries_file.isFile) {
                    telemetries_file.writeText("[\n")
                    //
                    val jhetas_files = listOf(
                        Pair("$oracleFileSourcesPath/folds/fold${foldName}_testing.json", false),
                        Pair("$oracleFileSourcesPath/folds/fold${foldName}_snippets.json", true)
                    )
                    var i = 0
                    //
                    for (jheta_file in jhetas_files) {
                        println("Loading file ${jheta_file.first}")
                        val jhetas = OBJECT_MAPPER.readValue(
                            File(jheta_file.first),
                            Array<JSONHighlightedSource>::class.java
                        )
                        //
                        var pygmAccAcc = 0.0
                        //
                        for (jheta in jhetas) {
                            if (jheta.source.source.isNotEmpty() && jheta.hetas.isNotEmpty()) {
                                // Run on pygments.
                                val pygRes = this.evalWithTree(jheta.source.source, pygClient)
                                val accPygm =
                                    pygRes.res_json.let { strPygmentsTokenBindings ->
                                        OBJECT_MAPPER.readValue<PygmentRawSolSeq?>(strPygmentsTokenBindings)
                                            ?.let { pygmentsTokenBindings ->
                                                val pygsHCodes = pygmentsTokenBindings.toLookUpHCode()
                                                val numChars = jheta.source.source.length
                                                var nonWhitespaceChars = 0
                                                var accAccuracy = 0.toDouble()
                                                for (heta in jheta.hetas) {
                                                    val startIdx = heta.eta.startIndex
                                                    val stopIdx = heta.eta.stopIndex
                                                    if (startIdx >= 0 && stopIdx >= startIdx) {
                                                        nonWhitespaceChars += heta.eta.text.length
                                                        val targetHcode = taskAdapter[heta.highlightCode]
                                                        for (charIdx in startIdx..stopIdx) {
                                                            if (charIdx <= pygsHCodes.lastIndex && taskAdapter[pygsHCodes[charIdx]] == targetHcode) {
                                                                accAccuracy += 1
                                                            }
                                                        }
                                                    }
                                                }
                                                (accAccuracy + numChars - nonWhitespaceChars) / numChars.toDouble()
                                            } ?: error("No valid acc Tree 2 for $jheta.")
                                    } ?: error("No valid acc Pygm 1 for $jheta.")
                                pygmAccAcc += accPygm

                                // Create log.
                                val log = FileAccItem(
                                    jheta.source.source.sourceToMD5FileId(),
                                    jheta_file.second,
                                    accPygm
                                )

                                // Write to file
                                if (i > 0)
                                    telemetries_file.appendText(",\n")
                                telemetries_file.appendText(jacksonObjectMapper().writeValueAsString(log))
                                telemetries_file.appendText("\n")

                                ++i
                            }
                        }
                        println()
                    }
                    //
                    telemetries_file.appendText("]\n")
                    println("Done $foldName")
                } else println("Skipped $foldName")
            }
        }
    }


    private fun perFileAccBrute() {
        for (taskCode in arrayOf(28, 37, 55, 66)) {
            val taskAdapter = getTaskAdapter(taskCode)
            for (foldName in 0..2) {
                val telemetries_file = File("$logOutputFilePath/perFileAcc_Brute_${taskCode}_${foldName}.json")
                if (!telemetries_file.isFile) {
                    telemetries_file.writeText("[\n")
                    //
                    val snippetsFile = "$oracleFileSourcesPath/folds/fold${foldName}_snippets.json"
                    //
                    var i = 0
                    println("Loading file ${snippetsFile}")
                    val jhetas = jacksonObjectMapper().readValue(
                        File(snippetsFile),
                        Array<JSONHighlightedSource>::class.java
                    )
                    //
                    var bruteAccAcc = 0.0
                    //
                    for (jheta in jhetas) {
                        if (jheta.source.source.isNotEmpty() && jheta.hetas.isNotEmpty()) {
                            if (i % 100 == 0)
                                print("\rOn JHETA number $i, ${bruteAccAcc / i}")

                            // Target task sequence.
                            val targetHCharSeq =
                                jheta.hetas.toHChars(jheta.source.source).also { it.adaptedToInplace(taskAdapter) }

                            // Run on brute.
                            val accBrute = let {
                                var startRule: RuleContext? = null
                                jheta.source.source.tryToETAS(
                                    lexerOf = lexerOf,
                                    parserOf = parserOf,
                                    startRuleOf = { startRuleOf(it).let { st -> startRule = st; st } },
                                    resolver = ETAMarshaller::tryFromContext,
                                    lexerChannels = lexerChannels,
                                    withErrorListeners = false
                                )?.let { etas ->
                                    val hetas = etas.highlightedAs { lexicalHighlighter(it) }
                                    startRule?.let {
                                        grammaticalHighlighter.reset() // Redundant.
                                        ParseTreeWalker.DEFAULT.walk(grammaticalHighlighter, it)
                                        OHighlight.applyOverrides(hetas, grammaticalHighlighter.getOverrides())
                                        grammaticalHighlighter.reset()
                                    }
                                    // Oracle is always task 4 (66), hence always needs converting.
                                    val brutePredHCharSeq =
                                        hetas.toHChars(jheta.source.source)
                                            .also { it.adaptedToInplace(taskAdapter) }
                                    charBaseAccOf(brutePredHCharSeq, targetHCharSeq)
                                } ?: 0.0
                            }
                            bruteAccAcc += accBrute

                            // Create log.
                            val log = FileAccItem(
                                jheta.source.source.sourceToMD5FileId(),
                                true,
                                accBrute,
                            )

                            // Write to file
                            if (i > 0)
                                telemetries_file.appendText(",\n")
                            telemetries_file.appendText(jacksonObjectMapper().writeValueAsString(log))
                            telemetries_file.appendText("\n")

                            ++i
                        }
                    }
                    //
                    telemetries_file.appendText("]\n")
                    println("Done $foldName")
                } else println("Skipped $foldName")
            }
        }
    }


    private fun perFileSize() {
        val jhetasFilepath = "$oracleFileSourcesPath/oracle/jhetas_clean.json"
        val sizes = mutableListOf<FileSizeItem>()
        File(jhetasFilepath).bufferedReader().forEachLine { line ->
            line.tryJSONHighlightedSourceFromJSON()?.let { jheta ->
                val source = jheta.source.source
                val sourceId = source.sourceToMD5FileId()
                val ntoks = jheta.hetas.size
                val nchars = source.length
                val whitespace = source.count { it.isWhitespace() }
                val lines = source.lines().size
                sizes.add(FileSizeItem(sourceId, ntoks, nchars, whitespace, lines))
            }
        }

        val logFile = File("$logOutputFilePath/perFileSize.json")
        sizes.sortByDescending { fileSizeItem -> fileSizeItem.ntoks }
        logFile.writeText(jacksonObjectMapper().writeValueAsString(sizes))
    }

    private fun oracleIndex() {
        val indexItems = mutableListOf<OracleIndexItem>()
        File("$oracleFileSourcesPath/oracle/jhetas_clean.json").bufferedReader().forEachLine { line ->
            line.tryJSONHighlightedSourceFromJSON()?.let { jheta ->
                val source = jheta.source.source
                val sourceId = source.sourceToMD5FileId()
                indexItems.add(
                    OracleIndexItem(
                        id = sourceId,
                        url = jheta.source.file.url ?: "Unavailable",
                        source = source
                    )
                )
            }
        }
        File("$logOutputFilePath/oracleIndex.json")
            .writeText(jacksonObjectMapper().writeValueAsString(indexItems))

        val snipIndexes = HashSet<String>()
        val snipIndexesList = mutableListOf<OracleIndexItem>()
        for (foldName in 0..2) {
            val jhetas = jacksonObjectMapper().readValue(
                File("$oracleFileSourcesPath/folds/fold${foldName}_snippets.json"),
                Array<JSONHighlightedSource>::class.java
            )
            jhetas.forEach { jheta ->
                val source = jheta.source.source
                val sourceId = source.sourceToMD5FileId()
                if (snipIndexes.add(sourceId)) {
                    snipIndexesList.add(
                        OracleIndexItem(
                            id = sourceId,
                            url = jheta.source.file.url ?: "Unavailable",
                            source = source
                        )
                    )
                }
            }
        }
        File("$logOutputFilePath/snipIndex.json")
            .writeText(jacksonObjectMapper().writeValueAsString(snipIndexesList))
    }

    private fun perFileTimeModel(modelLogName: String, repeats: Int) {
        val modelClient: HttpClient = this.setupModelConnection(modelLogName = modelLogName)

        File("$logOutputFilePath/perFileTimeModel_${modelLogName}.json").let { telemetries_file ->
            telemetries_file.writeText("[\n")
            //
            val jhetasFilepath = "$oracleFileSourcesPath/oracle/jhetas_clean.json"
            var i = 1
            File(jhetasFilepath).bufferedReader().forEachLine { line ->
                line.tryJSONHighlightedSourceFromJSON()?.let { jheta ->
                    if (i % 100 == 0)
                        print("\rOn JHETA number $i")
                    //
                    val source = jheta.source.source
                    if (source.isNotEmpty()) {
                        val nanoseconds = mutableListOf<Long>()
                        repeat(repeats) {
                            nanoseconds.add(runModelAndGetNanos(modelClient, source))
                        }
                        //
                        if (i > 1)
                            telemetries_file.appendText(",\n")
                        telemetries_file.appendText(
                            jacksonObjectMapper().writeValueAsString(
                                FileTimeItem(
                                    source.sourceToMD5FileId(),
                                    nanoseconds
                                )
                            )
                        )
                        telemetries_file.appendText("\n")
                    }
                    //
                    ++i
                }
                System.gc()
            }
            //
            telemetries_file.appendText("]")
        }
    }

    private fun runModelAndGetNanos(modelClient: HttpClient, source: String): Long {
        val t0 = System.nanoTime()
        val allTokens: List<Int> = lexerOf(CharStreams.fromString(source)).allTokens.map { it.type }.toList()
        val t1 = System.nanoTime()
        val evalRes: EvalWithModelResponse = this.evalWithModel(
            allTokens, modelClient
        )
        return evalRes.ns + (t1 - t0)
    }

    private fun perFileTimeBrute(repeats: Int) {
        val jhetasFilepath = "$oracleFileSourcesPath/oracle/jhetas_clean.json"
        var i = 1
        val jacksonObjectMapper = jacksonObjectMapper()
        val runSources = HashSet<String>()
        File("$logOutputFilePath/perFileTimeBrute.json").let { telemetriesFile ->

            telemetriesFile.forEachLine { line ->
                try {
                    jacksonObjectMapper.readValue<FileTimeItem>(line).let { runSources.add(it.fileId) }
                } catch (ex: Exception) {
                    // ignore.
                }
            }

            if (runSources.size == 0)
                telemetriesFile.writeText("[\n")

            File(jhetasFilepath).bufferedReader().forEachLine { line ->
                line.tryJSONHighlightedSourceFromJSON()?.let { jheta ->
                    if (i % 100 == 0)
                        print("\rOn JHETA number $i")

                    val source = jheta.source.source
                    val fileId = source.sourceToMD5FileId()

                    if (source.isNotEmpty() and !runSources.contains(fileId)) {
                        runSources.add(fileId)

                        val nanoseconds = mutableListOf<Long>()
                        repeat(repeats) {
                            nanoseconds.add(runBruteAndGetNanos(source))
                        }

                        if (i > 1)
                            telemetriesFile.appendText(",\n")
                        telemetriesFile.appendText(
                            jacksonObjectMapper.writeValueAsString(
                                FileTimeItem(
                                    source.sourceToMD5FileId(),
                                    nanoseconds
                                )
                            )
                        )
                        telemetriesFile.appendText("\n")
                    }
                    //
                    ++i
                }
                System.gc()
            }
            telemetriesFile.appendText("]")
        }
    }

    private fun runBruteAndGetNanos(source: String): Long {
        grammaticalHighlighter.reset()
        //
        val t0 = System.nanoTime()
        //
        val lexer = lexerOf(CharStreams.fromString(source))
        val parser = parserOf(CommonTokenStream(lexer)).also { it.removeErrorListeners() }
        val startRule = startRuleOf(parser)
        ParseTreeWalker.DEFAULT.walk(grammaticalHighlighter, startRule)
        //
        val t1 = System.nanoTime()
        //
        grammaticalHighlighter.reset()
        //
        return t1 - t0
    }

    private fun perFileTimePygments(repeats: Int) {
        val pygClient = this.setupPygmentsConnection(this.languageName)

        val jhetasFilepath = "$oracleFileSourcesPath/oracle/jhetas_clean.json"
        var i = 1
        File("$logOutputFilePath/perFileTimePygments.json").let { telemetries_file ->
            telemetries_file.writeText("[\n")
            File(jhetasFilepath).bufferedReader().forEachLine { line ->
                line.tryJSONHighlightedSourceFromJSON()?.let { jheta ->
                    if (i % 100 == 0)
                        print("\rOn JHETA number $i")
                    //
                    val source = jheta.source.source
                    if (source.isNotEmpty()) {
                        val nanoseconds = mutableListOf<Long>()
                        repeat(repeats) {
                            nanoseconds.add(runPygmentsAndGetNanos(pygClient, source))
                        }
                        //
                        if (i > 1)
                            telemetries_file.appendText(",\n")
                        telemetries_file.appendText(
                            jacksonObjectMapper().writeValueAsString(
                                FileTimeItem(
                                    source.sourceToMD5FileId(),
                                    nanoseconds
                                )
                            )
                        )
                        telemetries_file.appendText("\n")
                        //
                        ++i
                    }
                    System.gc()
                }
            }
            telemetries_file.appendText("]")
        }
    }

    private fun perFileTimeTree(repeats: Int) {
        val pygClient = this.setupTreeConnection(this.languageName)

        val jhetasFilepath = "$oracleFileSourcesPath/oracle/jhetas_clean.json"
        var i = 1
        File("$logOutputFilePath/perFileTimeTree.json").let { telemetries_file ->
            telemetries_file.writeText("[\n")
            File(jhetasFilepath).bufferedReader().forEachLine { line ->
                line.tryJSONHighlightedSourceFromJSON()?.let { jheta ->
                    if (i % 100 == 0)
                        print("\rOn JHETA number $i")
                    //
                    val source = jheta.source.source
                    if (source.isNotEmpty()) {
                        val nanoseconds = mutableListOf<Long>()
                        repeat(repeats) {
                            nanoseconds.add(runTreeAndGetNanos(pygClient, source))
                        }
                        //
                        if (i > 1)
                            telemetries_file.appendText(",\n")
                        telemetries_file.appendText(
                            jacksonObjectMapper().writeValueAsString(
                                FileTimeItem(
                                    source.sourceToMD5FileId(),
                                    nanoseconds
                                )
                            )
                        )
                        telemetries_file.appendText("\n")
                        //
                        ++i
                    }
                    System.gc()
                }
            }
            telemetries_file.appendText("]")
        }
    }


    private fun runPygmentsAndGetNanos(pygmentsClient: HttpClient, source: String): Long {
        val pygRes: EvalWithPygmentsResponse = this.evalWithPygments(source, pygmentsClient)
        return pygRes.ns
    }

    private fun runTreeAndGetNanos(pygmentsClient: HttpClient, source: String): Long {
        val pygRes: EvalWithPygmentsResponse = this.evalWithTree(source, pygmentsClient)
        return pygRes.ns
    }

    private fun renderTree() {
        val jframe = JFrame("TreeViewer")
        jframe.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        jframe.size = Dimension(800, 800)
        jframe.contentPane.layout = GridLayout(2, 1, 0, 0)

        val textPane = JTextPane()
        val treePane = JPanel(GridLayout(1, 1, 0, 2))
        textPane.font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        fun renderText(src: String) {
            val charStream = CharStreams.fromString(src)
            val lexer = this.lexerOf(charStream)
            val tokenStreams = CommonTokenStream(lexer)
            val parser = parserOf(tokenStreams)
            val parseTree = startRuleOf(parser)
            val treeView = TreeViewer(parser.ruleNames.toList(), parseTree)

            val scrollPane = JScrollPane(treeView)
            scrollPane.verticalScrollBar.unitIncrement = 16
            scrollPane.horizontalScrollBar.unitIncrement = 16

            treePane.removeAll()
            treePane.add(scrollPane)
            treePane.repaint()
            treePane.revalidate()
        }

        textPane.addKeyListener(object : KeyAdapter() {
            var tmpText: String? = null
            override fun keyReleased(e: KeyEvent?) {
                // TODO: use swing worker pattern.
                val txt = textPane.text
                if (txt != tmpText) {
                    renderText(textPane.text)
                    tmpText = txt
                }
            }
        })

        jframe.contentPane.add(treePane)
        jframe.contentPane.add(textPane)
        jframe.setLocationRelativeTo(null)
        jframe.isVisible = true
    }

    private fun fileToHTMLBrute(filepath: String) {
        File(filepath).readText().let { src ->
            var startRule: RuleContext? = null
            src.tryToETAS(
                lexerOf = lexerOf,
                parserOf = parserOf,
                startRuleOf = { startRuleOf(it).let { st -> startRule = st; st } },
                resolver = ETAMarshaller::tryFromContext,
                lexerChannels = lexerChannels,
                withErrorListeners = false
            )?.let { etas ->
                val hetas = etas.highlightedAs { lexicalHighlighter(it) }
                startRule?.let {
                    grammaticalHighlighter.reset() // Redundant.
                    ParseTreeWalker.DEFAULT.walk(grammaticalHighlighter, it)
                    OHighlight.applyOverrides(hetas, grammaticalHighlighter.getOverrides())
                    grammaticalHighlighter.reset()
                } ?: error("No start rule definition.")
                val tmp = toHighlightedHTML(hetas, src)
                println(tmp)
                File("out.html").writeText(tmp)
            } ?: error("Could not derive hetas.")
        }
    }

    private fun fileToHTMLModel(filepath: String, modelLogName: String) {
        val modClient: HttpClient = this.setupModelConnection(modelLogName)
        File(filepath).readText(charset = Charsets.UTF_8).let { src ->
            val allTokens = lexerOf(CharStreams.fromString(src)).allTokens
            val tokenIds = allTokens.map { it.type }.toList()
            val response: EvalWithModelResponse = this.evalWithModel(tokenIds, modClient)
            //
            response.ps.let { hIntCodes ->
                val etas = allTokens.map { tok ->
                    ETA(
                        startIndex = tok.startIndex,
                        stopIndex = tok.stopIndex,
                        text = tok.text,
                        symbolicName = tok.type.toString(), // Not needed.
                        tokenRule = tok.type
                    )
                }
                val hetas = etas.mapIndexed { i, eta ->
                    val hIntCode = if (i == etas.lastIndex) HCode.ANY.ordinal else hIntCodes[i]
                    val hcode = HCode.values()[hIntCode]
                    HETA(eta, hIntCode, hcode.colorCode)
                }
                val tmp = toHighlightedHTML(hetas.toTypedArray(), src)
                println(tmp)
                File("out.html").writeText(tmp)
                var startRule: RuleContext? = null
                src.tryToETAS(
                    lexerOf = lexerOf,
                    parserOf = parserOf,
                    startRuleOf = { startRuleOf(it).let { st -> startRule = st; st } },
                    resolver = ETAMarshaller::tryFromContext,
                    lexerChannels = lexerChannels,
                    withErrorListeners = false
                )?.let { oetas ->
                    val ohetas = oetas.highlightedAs { lexicalHighlighter(it) }
                    startRule?.let {
                        grammaticalHighlighter.reset() // Redundant.
                        ParseTreeWalker.DEFAULT.walk(grammaticalHighlighter, it)
                        OHighlight.applyOverrides(ohetas, grammaticalHighlighter.getOverrides())
                        grammaticalHighlighter.reset()
                    } ?: error("No start rule definition.")
                    val acc = charBaseAccOf(hetas.toTypedArray().toHChars(src), ohetas.toHChars(src))
                    println("Accuracy: $acc")
                } ?: println("Accuracy unavailable.")
            }
        }
    }

    private fun fileToHTMLPygments(filepath: String) {
        val pygClient: HttpClient = this.setupPygmentsConnection(this.languageName)
        //
        File(filepath).readText(charset = Charsets.UTF_8).let { src ->

            val pygRes: EvalWithPygmentsResponse = this.evalWithPygments(src, pygClient)
            pygRes.res_json.let { strPygmentsTokenBindings ->
                jacksonObjectMapper().readValue<PygmentRawSolSeq?>(strPygmentsTokenBindings)
                    ?.let { pygmentsTokenBindings ->
                        // Pygments is always task 4 (66), hence always needs converting.
                        val pygPredHCharSeq = pygmentsTokenBindings.toPygmentSols().toHChars(src)
                        val tmp = toHighlightedHTML(pygPredHCharSeq, src)
                        println(tmp)
                        File("out.html").writeText(tmp)
                        var startRule: RuleContext? = null
                        src.tryToETAS(
                            lexerOf = lexerOf,
                            parserOf = parserOf,
                            startRuleOf = { startRuleOf(it).let { st -> startRule = st; st } },
                            resolver = ETAMarshaller::tryFromContext,
                            lexerChannels = lexerChannels,
                            withErrorListeners = false
                        )?.let { oetas ->
                            val ohetas = oetas.highlightedAs { lexicalHighlighter(it) }
                            startRule?.let {
                                grammaticalHighlighter.reset() // Redundant.
                                ParseTreeWalker.DEFAULT.walk(grammaticalHighlighter, it)
                                OHighlight.applyOverrides(ohetas, grammaticalHighlighter.getOverrides())
                                grammaticalHighlighter.reset()
                            } ?: error("No start rule definition.")
                            val acc = charBaseAccOf(pygPredHCharSeq, ohetas.toHChars(src))
                            println("Accuracy: $acc")
                        } ?: println("Accuracy unavailable.")
                    } ?: error("No valid acc Pygm 2 for.")
            } ?: error("No valid acc Pygm 1 for.")
        }
    }

    private fun fileToHTMLTree(filepath: String) {
        val pygClient: HttpClient = this.setupTreeConnection(this.languageName)
        //
        File(filepath).readText(charset = Charsets.UTF_8).let { src ->

            val pygRes: EvalWithPygmentsResponse = this.evalWithTree(src, pygClient)
            pygRes.res_json.let { strPygmentsTokenBindings ->
                jacksonObjectMapper().readValue<PygmentRawSolSeq?>(strPygmentsTokenBindings)
                    ?.let { pygmentsTokenBindings ->
                        // Pygments is always task 4 (66), hence always needs converting.
                        val pygPredHCharSeq = pygmentsTokenBindings.toPygmentSols().toHChars(src)
                        val tmp = toHighlightedHTML(pygPredHCharSeq, src)
                        println(tmp)
                        File("out.html").writeText(tmp)
                        var startRule: RuleContext? = null
                        src.tryToETAS(
                            lexerOf = lexerOf,
                            parserOf = parserOf,
                            startRuleOf = { startRuleOf(it).let { st -> startRule = st; st } },
                            resolver = ETAMarshaller::tryFromContext,
                            lexerChannels = lexerChannels,
                            withErrorListeners = false
                        )?.let { oetas ->
                            val ohetas = oetas.highlightedAs { lexicalHighlighter(it) }
                            startRule?.let {
                                grammaticalHighlighter.reset() // Redundant.
                                ParseTreeWalker.DEFAULT.walk(grammaticalHighlighter, it)
                                OHighlight.applyOverrides(ohetas, grammaticalHighlighter.getOverrides())
                                grammaticalHighlighter.reset()
                            } ?: error("No start rule definition.")
                            val acc = charBaseAccOf(pygPredHCharSeq, ohetas.toHChars(src))
                            println("Accuracy: $acc")
                        } ?: println("Accuracy unavailable.")
                    } ?: error("No valid acc Pygm 2 for.")
            } ?: error("No valid acc Pygm 1 for.")
        }
    }


    override fun run() {
        when (userArgs[0]) {
            "perFileAccModel" ->
                perFileAccModel(userArgs[1].removeSuffix(".json"))

            "perFileAccPygments" ->
                perFileAccPygments()

            "perFileAccTree" ->
                perFileAccTree()

            "perFileAccBrute" ->
                perFileAccBrute()

            "perFileTimeBrute" ->
                perFileTimeBrute(REPEATS)

            "perFileTimeModel" ->
                perFileTimeModel(userArgs[1].removeSuffix(".json"), REPEATS)

            "perFileTimePygments" ->
                perFileTimePygments(REPEATS)

            "perFileTimeTree" ->
                perFileTimeTree(REPEATS)

            "fileToHTMLBrute" ->
                fileToHTMLBrute(userArgs[1])

            "fileToHTMLModel" ->
                fileToHTMLModel(userArgs[1], userArgs[2].removeSuffix(".json"))

            "fileToHTMLPygments" ->
                fileToHTMLPygments(userArgs[1])

            "fileToHTMLTree" ->
                fileToHTMLTree(userArgs[1])

            "renderTree" ->
                renderTree()

            "perFileSize" ->
                perFileSize()

            "oracleIndex" ->
                oracleIndex()

            else -> println("Unknown task arguments ${userArgs.toList()}")
        }
    }


}
