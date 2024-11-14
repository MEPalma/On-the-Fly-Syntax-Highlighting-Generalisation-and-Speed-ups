package highlighter.csharphighlighter

import CSharpLexer
import CSharpParser
import common.ETA
import common.HCode.*
import common.HCode.Companion.hetaOf
import common.HETA

fun csharpLexicalHighlighter(eta: ETA): HETA = when (eta.tokenRule) {
    in hashSetOf(
        CSharpLexer.ABSTRACT,
        CSharpLexer.AS,
        CSharpLexer.BASE,
        CSharpLexer.BOOL,
        CSharpLexer.BREAK,
        CSharpLexer.BYTE,
        CSharpLexer.CASE,
        CSharpLexer.CATCH,
        CSharpLexer.CHAR,
        CSharpLexer.CHECKED,
        CSharpLexer.CLASS,
        CSharpLexer.CONST,
        CSharpLexer.CONTINUE,
        CSharpLexer.DECIMAL,
        CSharpLexer.DEFAULT,
        CSharpLexer.DELEGATE,
        CSharpLexer.DO,
        CSharpLexer.DOUBLE,
        CSharpLexer.ELSE,
        CSharpLexer.ENUM,
        CSharpLexer.EVENT,
        CSharpLexer.EXPLICIT,
        CSharpLexer.EXTERN,
        CSharpLexer.FALSE,
        CSharpLexer.FINALLY,
        CSharpLexer.FIXED,
        CSharpLexer.FLOAT,
        CSharpLexer.FOR,
        CSharpLexer.FOREACH,
        CSharpLexer.GOTO,
        CSharpLexer.IF,
        CSharpLexer.IMPLICIT,
        CSharpLexer.IN,
        CSharpLexer.INT,
        CSharpLexer.INTERFACE,
        CSharpLexer.INTERNAL,
        CSharpLexer.IS,
        CSharpLexer.LOCK,
        CSharpLexer.LONG,
        CSharpLexer.NAMESPACE,
        CSharpLexer.NEW,
        CSharpLexer.OBJECT,
        CSharpLexer.OPERATOR,
        CSharpLexer.OUT,
        CSharpLexer.OVERRIDE,
        CSharpLexer.PARAMS,
        CSharpLexer.PRIVATE,
        CSharpLexer.PROTECTED,
        CSharpLexer.PUBLIC,
        CSharpLexer.READONLY,
        CSharpLexer.REF,
        CSharpLexer.RETURN,
        CSharpLexer.SBYTE,
        CSharpLexer.SEALED,
        CSharpLexer.SHORT,
        CSharpLexer.SIZEOF,
        CSharpLexer.STACKALLOC,
        CSharpLexer.STATIC,
        CSharpLexer.STRING,
        CSharpLexer.STRUCT,
        CSharpLexer.SWITCH,
        CSharpLexer.THIS,
        CSharpLexer.THROW,
        CSharpLexer.TRY,
        CSharpLexer.TYPEOF,
        CSharpLexer.UINT,
        CSharpLexer.ULONG,
        CSharpLexer.UNCHECKED,
        CSharpLexer.UNSAFE,
        CSharpLexer.USHORT,
        CSharpLexer.USING,
        CSharpLexer.VIRTUAL,
        CSharpLexer.VOID,
        CSharpLexer.VOLATILE,
        CSharpLexer.WHILE
    ) -> hetaOf(eta, KEYWORD)

    in hashSetOf(
        CSharpLexer.LITERAL_ACCESS,
        CSharpLexer.INTEGER_LITERAL,
        CSharpLexer.HEX_INTEGER_LITERAL,
        CSharpLexer.BIN_INTEGER_LITERAL,
        CSharpLexer.REAL_LITERAL,
        CSharpLexer.NULL_,
        CSharpLexer.TRUE,
        CSharpLexer.FALSE,
    ) -> hetaOf(eta, LITERAL)

    in hashSetOf(
        CSharpLexer.STRING,
        CSharpLexer.CHARACTER_LITERAL,
        CSharpLexer.REGULAR_STRING,
        CSharpLexer.REGULAR_STRING_INSIDE,
        CSharpLexer.VERBATIUM_STRING,
        CSharpLexer.VERBATIUM_INSIDE_STRING,
        CSharpLexer.INTERPOLATION_STRING,
        CSharpLexer.INTERPOLATED_REGULAR_STRING_START,
        CSharpLexer.INTERPOLATED_VERBATIUM_STRING_START,
        CSharpLexer.DOUBLE_QUOTE_INSIDE,
        CSharpLexer.REGULAR_CHAR_INSIDE,
        CSharpLexer.REGULAR_STRING_INSIDE,
    ) -> hetaOf(eta, CHAR_STRING_LITERAL)

    in hashSetOf(
        CSharpLexer.SINGLE_LINE_DOC_COMMENT,
        CSharpLexer.EMPTY_DELIMITED_DOC_COMMENT,
        CSharpLexer.DELIMITED_DOC_COMMENT,
        CSharpLexer.SINGLE_LINE_COMMENT,
        CSharpLexer.DELIMITED_COMMENT,
    ) -> hetaOf(eta, COMMENT)

    else -> hetaOf(eta, ANY)
}

fun csharpSemiLexicalHighlighter(eta: ETA): HETA = csharpLexicalHighlighter(eta).let { heta ->
    when (heta.eta.tokenRule) {
        in hashSetOf(
            CSharpLexer.ADD,
            CSharpLexer.ALIAS,
            CSharpLexer.ARGLIST,
            CSharpLexer.ASCENDING,
            CSharpLexer.ASYNC,
            CSharpLexer.AWAIT,
            CSharpLexer.BY,
            CSharpLexer.DESCENDING,
            CSharpLexer.DYNAMIC,
            CSharpLexer.EQUALS,
            CSharpLexer.FROM,
            CSharpLexer.GET,
            CSharpLexer.GROUP,
            CSharpLexer.INTO,
            CSharpLexer.JOIN,
            CSharpLexer.LET,
            CSharpLexer.NAMEOF,
            CSharpLexer.ON,
            CSharpLexer.ORDERBY,
            CSharpLexer.PARTIAL,
            CSharpLexer.REMOVE,
            CSharpLexer.SELECT,
            CSharpLexer.SET,
            CSharpLexer.UNMANAGED,
            CSharpLexer.VAR,
            CSharpLexer.WHEN,
            CSharpLexer.WHERE,
            CSharpLexer.YIELD
        ) -> when (heta.eta.parentRule) {
            CSharpParser.RULE_identifier -> heta
            else -> hetaOf(heta.eta, KEYWORD)
        }
        //
        else -> heta
    }
}

fun csharpPreprocessingLexicalHighlighter(eta: ETA): HETA = csharpSemiLexicalHighlighter(eta).let { heta ->
    when (heta.eta.tokenRule) {
        in hashSetOf(
            /* consider DIRECTIVES as comments */
            CSharpLexer.DIRECTIVE_SRC
        ) -> hetaOf(heta.eta, COMMENT)

        else -> heta
    }

}
