package highlighter.javaScriptHighlighter

import JavaScriptLexer
import JavaScriptParser
import common.ETA
import common.HCode.*
import common.HCode.Companion.hetaOf
import common.HETA

fun javaScriptLexicalHighlighter(eta: ETA): HETA =
    when (eta.tokenRule) {
        in hashSetOf(
            JavaScriptLexer.Assign,
            JavaScriptLexer.Break,
            JavaScriptLexer.Do,
            JavaScriptLexer.Instanceof,
            JavaScriptLexer.Typeof,
            JavaScriptLexer.Case,
            JavaScriptLexer.Else,
            JavaScriptLexer.New,
            JavaScriptLexer.Var,
            JavaScriptLexer.Catch,
            JavaScriptLexer.Finally,
            JavaScriptLexer.Return,
            JavaScriptLexer.Void,
            JavaScriptLexer.Continue,
            JavaScriptLexer.For,
            JavaScriptLexer.Switch,
            JavaScriptLexer.While,
            JavaScriptLexer.Debugger,
            JavaScriptLexer.Function_,
            JavaScriptLexer.This,
            JavaScriptLexer.With,
            JavaScriptLexer.Default,
            JavaScriptLexer.If,
            JavaScriptLexer.Throw,
            JavaScriptLexer.Delete,
            JavaScriptLexer.In,
            JavaScriptLexer.Try,
            JavaScriptLexer.Class,
            JavaScriptLexer.Enum,
            JavaScriptLexer.Extends,
            JavaScriptLexer.Super,
            JavaScriptLexer.Const,
            JavaScriptLexer.Export,
            JavaScriptLexer.Import,
            JavaScriptLexer.Async,
            JavaScriptLexer.Await,
            JavaScriptLexer.Yield,
            JavaScriptLexer.Implements,
            JavaScriptLexer.StrictLet,
            JavaScriptLexer.NonStrictLet,
            JavaScriptLexer.Private,
            JavaScriptLexer.Public,
            JavaScriptLexer.Interface,
            JavaScriptLexer.Package,
            JavaScriptLexer.Protected,
            JavaScriptLexer.Static
        ) -> hetaOf(eta, KEYWORD)

        in hashSetOf(
            JavaScriptLexer.NullLiteral,
            JavaScriptLexer.BooleanLiteral,
            JavaScriptLexer.DecimalLiteral,
            JavaScriptLexer.HexIntegerLiteral,
            JavaScriptLexer.OctalIntegerLiteral,
            JavaScriptLexer.OctalIntegerLiteral2,
            JavaScriptLexer.BinaryIntegerLiteral,
            JavaScriptLexer.BigHexIntegerLiteral,
            JavaScriptLexer.BigOctalIntegerLiteral,
            JavaScriptLexer.BigBinaryIntegerLiteral,
            JavaScriptLexer.BigDecimalIntegerLiteral
        ) -> hetaOf(eta, LITERAL)

        in hashSetOf(
            JavaScriptLexer.StringLiteral, JavaScriptLexer.TemplateStringAtom
        ) -> hetaOf(eta, CHAR_STRING_LITERAL)

        in hashSetOf(
            JavaScriptLexer.CDataComment,
            JavaScriptLexer.HtmlComment,
            JavaScriptLexer.MultiLineComment,
            JavaScriptLexer.SingleLineComment
        ) -> hetaOf(eta, COMMENT)

        else -> hetaOf(eta, ANY)
    }

fun javaScriptSemiLexicalHighlighter(eta: ETA): HETA =
    javaScriptLexicalHighlighter(eta).let { heta ->
        when (heta.eta.tokenRule) {
            // SoftKeyword.
            in hashSetOf(
                JavaScriptLexer.Async,
                JavaScriptLexer.Yield,
                JavaScriptLexer.NonStrictLet
            ) -> when (heta.eta.parentRule) {
                JavaScriptParser.RULE_identifier -> heta
                else -> hetaOf(heta.eta, KEYWORD)
            }
            //
            else -> heta
        }
    }
