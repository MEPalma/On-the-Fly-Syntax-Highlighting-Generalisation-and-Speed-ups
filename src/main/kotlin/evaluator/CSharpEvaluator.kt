package evaluator

import CSharpLexer
import CSharpParser
import highlighter.csharphighlighter.CSharpGrammaticalHighlighter
import highlighter.csharphighlighter.csharpPreprocessingLexicalHighlighter
import org.antlr.v4.runtime.Token
import utils.toResourcePath

class CSharpEvaluator(
    userArgs: Array<String>,
) : Evaluator(
    userArgs = userArgs,
    languageName = "csharp",
    oracleFileSourcesPath = "csharp".toResourcePath(),
    logOutputFilePath = "csharp".toResourcePath(),
    lexerOf = { CSharpLexer(it).also { lexer -> lexer.removeErrorListeners() }  },
    parserOf = { CSharpParser(it) },
    lexicalHighlighter = { csharpPreprocessingLexicalHighlighter(it) },
    grammaticalHighlighter = CSharpGrammaticalHighlighter(),
    startRuleOf = { (it as CSharpParser).compilation_unit() },
    lexerChannels =  arrayOf(Token.HIDDEN_CHANNEL, CSharpLexer.DIRECTIVE)

)

fun main(args: Array<String>) =
    CSharpEvaluator(args).run()