package preprocessor.javascriptpreprocessor

import JavaScriptLexer
import JavaScriptParser
import highlighter.javaScriptHighlighter.JavaScriptGrammaticalHighlighter
import highlighter.javaScriptHighlighter.javaScriptSemiLexicalHighlighter
import preprocessor.Preprocessor
import utils.toResourcePath

class JavaScriptPreprocessor(userArgs: Array<String>) : Preprocessor(
    userArgs = userArgs,
    //
    oracleFileSourcesPath = "/javascript".toResourcePath(),
    //
    lexerOf = { JavaScriptLexer(it) },
    parserOf = { JavaScriptParser(it) },
    startRuleOf = { (it as JavaScriptParser).program() },
    //
    lexicalHighlighter = { javaScriptSemiLexicalHighlighter(it) },
    grammaticalHighlighter = JavaScriptGrammaticalHighlighter(),
)

fun main(args: Array<String>) =
    JavaScriptPreprocessor(args).run()
