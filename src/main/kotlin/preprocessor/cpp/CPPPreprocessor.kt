package preprocessor.cpp

import CPP14Lexer
import CPP14Parser
import highlighter.cpp.CPPGrammaticalHighlighter
import highlighter.cpp.cppLexicalHighlighter
import preprocessor.Preprocessor
import utils.toResourcePath

class CPPPreprocessor(userArgs: Array<String>) : Preprocessor(
    userArgs = userArgs,
    //
    oracleFileSourcesPath = "/cpp".toResourcePath(),
    //
    lexerOf = { CPP14Lexer(it) },
    parserOf = { CPP14Parser(it) },
    startRuleOf = { (it as CPP14Parser).translationUnit() },
    //
    lexicalHighlighter = { cppLexicalHighlighter(it) },
    grammaticalHighlighter = CPPGrammaticalHighlighter(),
)

//fun main(args: Array<String>) =
//    CPPPreprocessor(args).run()

fun main(args: Array<String>) {
    print(CPP14Lexer.VOCABULARY.maxTokenType)
}

