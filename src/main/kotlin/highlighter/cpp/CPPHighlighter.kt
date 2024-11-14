package highlighter.cpp

import CPP14Lexer
import common.ETA
import common.HCode.*
import common.HCode.Companion.hetaOf
import common.HETA

fun cppLexicalHighlighter(eta: ETA): HETA = when (eta.tokenRule) {
    in hashSetOf(
        CPP14Lexer.Alignas,
        CPP14Lexer.Alignof,
        CPP14Lexer.Asm,
        CPP14Lexer.Auto,
        CPP14Lexer.Bool,
        CPP14Lexer.Break,
        CPP14Lexer.Case,
        CPP14Lexer.Catch,
        CPP14Lexer.Char,
        CPP14Lexer.Char16,
        CPP14Lexer.Char32,
        CPP14Lexer.Class,
        CPP14Lexer.Const,
        CPP14Lexer.Constexpr,
        CPP14Lexer.Const_cast,
        CPP14Lexer.Continue,
        CPP14Lexer.Decltype,
        CPP14Lexer.Default,
        CPP14Lexer.Delete,
        CPP14Lexer.Do,
        CPP14Lexer.Double,
        CPP14Lexer.Dynamic_cast,
        CPP14Lexer.Else,
        CPP14Lexer.Enum,
        CPP14Lexer.Explicit,
        CPP14Lexer.Export,
        CPP14Lexer.Extern,
        CPP14Lexer.False_,
        CPP14Lexer.Final,
        CPP14Lexer.Float,
        CPP14Lexer.For,
        CPP14Lexer.Friend,
        CPP14Lexer.Goto,
        CPP14Lexer.If,
        CPP14Lexer.Inline,
        CPP14Lexer.Int,
        CPP14Lexer.Long,
        CPP14Lexer.Mutable,
        CPP14Lexer.Namespace,
        CPP14Lexer.New,
        CPP14Lexer.Noexcept,
        CPP14Lexer.Operator,
        CPP14Lexer.Override,
        CPP14Lexer.Private,
        CPP14Lexer.Protected,
        CPP14Lexer.Public,
        CPP14Lexer.Register,
        CPP14Lexer.Reinterpret_cast,
        CPP14Lexer.Return,
        CPP14Lexer.Short,
        CPP14Lexer.Signed,
        CPP14Lexer.Sizeof,
        CPP14Lexer.Static,
        CPP14Lexer.Static_assert,
        CPP14Lexer.Static_cast,
        CPP14Lexer.Struct,
        CPP14Lexer.Switch,
        CPP14Lexer.Template,
        CPP14Lexer.This,
        CPP14Lexer.Thread_local,
        CPP14Lexer.Throw,
        CPP14Lexer.True_,
        CPP14Lexer.Try,
        CPP14Lexer.Typedef,
        CPP14Lexer.Typeid_,
        CPP14Lexer.Typename_,
        CPP14Lexer.Union,
        CPP14Lexer.Unsigned,
        CPP14Lexer.Using,
        CPP14Lexer.Virtual,
        CPP14Lexer.Void,
        CPP14Lexer.Volatile,
        CPP14Lexer.Wchar,
        CPP14Lexer.While,
        CPP14Lexer.Question,
        CPP14Lexer.Ellipsis,
        CPP14Lexer.Integersuffix,
        CPP14Lexer.Whitespace,
        CPP14Lexer.Newline
    ) -> hetaOf(eta, KEYWORD)

    in hashSetOf(
        CPP14Lexer.IntegerLiteral,
        CPP14Lexer.BooleanLiteral,
        CPP14Lexer.PointerLiteral,
        CPP14Lexer.FloatingLiteral,
        CPP14Lexer.Nullptr,
        CPP14Lexer.UserDefinedLiteral,
        CPP14Lexer.UserDefinedFloatingLiteral,
        CPP14Lexer.UserDefinedIntegerLiteral,
        CPP14Lexer.DecimalLiteral,
        CPP14Lexer.OctalLiteral,
        CPP14Lexer.HexadecimalLiteral,
        CPP14Lexer.BinaryLiteral
    ) -> hetaOf(eta, LITERAL)

    in hashSetOf(
        CPP14Lexer.StringLiteral,
        CPP14Lexer.CharacterLiteral,
        CPP14Lexer.UserDefinedCharacterLiteral,
        CPP14Lexer.UserDefinedStringLiteral
    ) -> hetaOf(eta, CHAR_STRING_LITERAL)

    in hashSetOf(
        CPP14Lexer.LineComment, CPP14Lexer.BlockComment, CPP14Lexer.Directive, CPP14Lexer.MultiLineMacro,
    ) -> hetaOf(eta, COMMENT)

    else -> hetaOf(eta, ANY)
}
