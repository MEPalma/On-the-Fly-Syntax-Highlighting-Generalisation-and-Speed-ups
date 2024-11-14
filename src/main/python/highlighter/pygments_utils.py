import sys
import json
from typing import Final
from pygments.formatter import Formatter
import utils as utils


class DropFormatter(Formatter):
    name = 'DropFormatter'
    aliases = ['DropFormatter']
    filenames = ['*.*']

    def __init__(self, **options):
        Formatter.__init__(self, **options)

    def formatter(self, tokensource, outfile):
        pass

    def format_unencoded(self, tokensource, outfile):
        self.formatter(tokensource, outfile)


class JSONFormatter(Formatter):
    name = 'JSONFormatter'
    aliases = ['JSONFormatter']
    filenames = ['*.*']

    def __init__(self, bindings, **options):
        Formatter.__init__(self, **options)
        self.bindings = bindings

    def formatter(self, tokensource, outfile):
        sols = []
        for ttype, value in tokensource:
            sttype = str(ttype)
            sols.append((str(value), sttype, to_oracle_bindings(sttype, bindings=self.bindings)))
        outfile.write(json.dumps(sols, sort_keys=False))
        outfile.flush()

    def format_unencoded(self, tokensource, outfile):
        self.formatter(tokensource, outfile)


# Pygments to HCode class bindings.
#
# HCodes class IDs.
_ANY = utils.ANY[0]
_KEYWORD = utils.KEYWORD[0]
_LITERAL = utils.LITERAL[0]
_STRING_LIT = utils.CHAR_STRING_LITERAL[0]
_COMMENT = utils.COMMENT[0]
_CLASS_DECLARATOR = utils.CLASS_DECLARATOR[0]
_FUNCTION_DECLARATOR = utils.FUNCTION_DECLARATOR[0]
_VARIABLE_DECLARATOR = utils.VARIABLE_DECLARATOR[0]
_TYPE_IDENTIFIER = utils.TYPE_IDENTIFIER[0]
_FUNCTION_IDENTIFIER = utils.FUNCTION_IDENTIFIER[0]
_FIELD_IDENTIFIER = utils.FIELD_IDENTIFIER[0]
_ANNOTATION_DECLARATOR = utils.ANNOTATION_DECLARATOR[0]
#
_UNKNOWN_PYGMENT_TOKEN_TYPE = -1
#
# As reported from:
#   https://pygments.org/docs/tokens/#module-pygments.token
#   last reviewed on the 21/08/2021.
_TO_ORACLE_BASE_BINDINGS: Final[dict[str, int]] = {
    # Keyword Tokens.
    # For any kind of keyword (especially if it doesn’t match any of the subtypes of course).
    "Token.Keyword":                            _KEYWORD,
    # For keywords that are constants (e.g. None in future Python versions).
    "Token.Keyword.Constant":                   _KEYWORD,
    # For keywords used for variable declaration (e.g. var in some programming languages like JavaScript).
    "Token.Keyword.Declaration":                _KEYWORD,
    # For keywords used for namespace declarations (e.g. import in Python and Java and package in Java).
    "Token.Keyword.Namespace":                  _KEYWORD,
    # For keywords that aren’t really keywords (e.g. None in old Python versions).
    "Token.Keyword.Pseudo":                     _KEYWORD,
    # For reserved keywords.
    "Token.Keyword.Reserved":                   _KEYWORD,
    # For builtin types that can’t be used as identifiers (e.g. int, char etc. in C).
    "Token.Keyword.Type":                       _KEYWORD,
    # Name Tokens.
    # For any name (variable names, function names, classes).
    "Token.Name":                               _ANY,
    # For all attributes (e.g. in HTML tags).
    "Token.Name.Attribute":                     _FIELD_IDENTIFIER,
    # Builtin names; names that are available in the global namespace.
    "Token.Name.Builtin":                       _ANY,
    # Builtin names that are implicit (e.g. self in Ruby, this in Java).
    "Token.Name.Builtin.Pseudo":                _KEYWORD,
    # Class names. Because no lexer can know if a name is a class or a function or something else this
    # token is meant for class declarations.
    "Token.Name.Class":                         _CLASS_DECLARATOR,
    # Token type for constants. In some languages you can recognise a token by the way it’s defined
    # (the value after a const keyword for example). In other languages constants are uppercase by definition (Ruby).
    "Token.Name.Constant":                      _VARIABLE_DECLARATOR,
    # Token type for decorators. Decorators are syntactic elements in the Python language. Similar syntax
    # elements exist in C# and Java.
    "Token.Name.Decorator":                     _ANNOTATION_DECLARATOR,
    # Token type for special entities. (e.g. &nbsp; in HTML).
    "Token.Name.Entity":                        _ANY,
    # Token type for exception names (e.g. RuntimeError in Python). Some languages define exceptions in the
    # function signature (Java). You can highlight the name of that exception using this token then.
    "Token.Name.Exception":                     _TYPE_IDENTIFIER,
    # Token type for function names.
    "Token.Name.Function":                      _FUNCTION_IDENTIFIER,
    # same as Name.Function but for special function names that have an implicit use in a language
    # (e.g. __init__ method in Python).
    "Token.Name.Function.Magic":                _FUNCTION_IDENTIFIER,
    # Token type for label names (e.g. in languages that support goto).
    "Token.Name.Label":                         _KEYWORD,
    # Token type for namespaces. (e.g. import paths in Java/Python), names following the module/namespace
    # keyword in other languages.
    "Token.Name.Namespace":                     _KEYWORD,
    # Other names. Normally unused.
    "Token.Name.Other":                         _ANY,
    # Property.
    "Token.Name.Property":                      _ANY,
    # Tag names (in HTML/XML markup or configuration files).
    "Token.Name.Tag":                           _ANY,
    # Token type for variables. Some languages have prefixes for variable names (PHP, Ruby, Perl).
    "Token.Name.Variable":                      _ANY,
    # Same as Name.Variable but for class variables (also static variables).
    "Token.Name.Variable.Class":                _ANY,
    # Same as Name.Variable but for global variables (used in Ruby, for example).
    "Token.Name.Variable.Global":               _ANY,
    # Same as Name.Variable but for instance variables.
    "Token.Name.Variable.Instance":             _ANY,
    # Same as Name.Variable but for special variable names that have an implicit use in a
    # language (e.g. __doc__ in Python).
    "Token.Name.Variable.Magic":                _ANY,
    # Literals.
    # For any literal (if not further defined).
    "Token.Literal":                            _LITERAL,
    # For date literals (e.g. 42d in Boo).
    "Token.Literal.Date":                       _ANY,
    # For any string literal.
    "Token.Literal.String":                     _STRING_LIT,
    # Token type for affixes that further specify the type of the string they’re attached to
    # (e.g. the prefixes r and u8 in r"foo" and u8"foo").
    "Token.Literal.String.Affix":                _STRING_LIT,
    # Token type for strings enclosed in backticks.
    "Token.Literal.String.Backtick":            _STRING_LIT,
    # Token type for single characters (e.g. Java, C).
    "Token.Literal.String.Char":                _STRING_LIT,
    # Token type for delimiting identifiers in “heredoc”, raw and other similar strings
    # (e.g. the word END in Perl code print <<'END';).
    "Token.Literal.String.Delimiter":           _STRING_LIT,
    # Token type for documentation strings (for example Python).
    "Token.Literal.String.Doc":                 _STRING_LIT,
    # Double-quoted strings.
    "Token.Literal.String.Double":              _STRING_LIT,
    # Token type for escape sequences in strings.
    "Token.Literal.String.Escape":              _STRING_LIT,
    # Token type for “heredoc” strings (e.g. in Ruby or Perl).
    "Token.Literal.String.Heredoc":             _STRING_LIT,
    # Token type for interpolated parts in strings (e.g. #{foo} in Ruby).
    "Token.Literal.String.Interpol":            _STRING_LIT,
    # Token type for any other strings (for example %q{foo} string constructs in Ruby).
    "Token.Literal.String.Other":               _STRING_LIT,
    # Token type for regular expression literals (e.g. /foo/ in JavaScript).
    "Token.Literal.String.Regex":               _STRING_LIT,
    # Token type for single quoted strings.
    "Token.Literal.String.Single":              _STRING_LIT,
    # Token type for symbols (e.g. :foo in LISP or Ruby).
    "Token.Literal.String.Symbol":              _STRING_LIT,
    # Token type for any number literal.
    "Token.Literal.Number":                     _LITERAL,
    # Token type for binary literals (e.g. 0b101010).
    "Token.Literal.Number.Bin":                 _LITERAL,
    # Token type for float literals (e.g. 42.0).
    "Token.Literal.Number.Float":               _LITERAL,
    # Token type for hexadecimal number literals (e.g. 0xdeadbeef).
    "Token.Literal.Number.Hex":                 _LITERAL,
    # Token type for integer literals (e.g. 42).
    "Token.Literal.Number.Integer":             _LITERAL,
    # Token type for long integer literals (e.g. 42L in Python).
    "Token.Literal.Number.Integer.Long":        _LITERAL,
    # Token type for octal literals.
    "Token.Literal.Number.Oct":                 _LITERAL,
    # Operators.
    # For any punctuation operator (e.g. +, -).
    "Token.Operator":                           _ANY,
    # For any operator that is a word (e.g. not).
    "Token.Operator.Word":                      _KEYWORD,
    # Punctuation.
    # For any punctuation which is not an operator (e.g. [, (…)
    "Token.Punctuation":                        _ANY,
    # For markers that point to a location (e.g., carets in Python tracebacks for syntax errors).
    "Token.Punctuation.Marker":                 _ANY,
    # Comments.
    # Token type for any comment.
    "Token.Comment":                            _COMMENT,
    # Token type for hashbang comments (i.e. first lines of files that start with #!).
    "Token.Comment.Hashbang":                   _ANY,
    # Token type for multiline comments.
    "Token.Comment.Multiline":                  _COMMENT,
    # Token type for preprocessor comments (also <?php/<% constructs).
    "Token.Comment.Preproc":                    _COMMENT,
    # Token type for preprocessor comments (also <?php/<% constructs).
    "Token.Comment.PreprocFile":                _COMMENT,
    # Token type for comments that end at the end of a line (e.g. # foo).
    "Token.Comment.Single":                     _COMMENT,
    # Special data in comments. For example code tags, author and license information, etc.
    "Token.Comment.Special":                    _COMMENT,
    # Generic Tokens.
    # A generic, un-styled token. Normally you don’t use this token type.
    "Token.Generic":                            _ANY,
    # Marks the token value as deleted.
    "Token.Generic.Deleted":                    _ANY,
    # Marks the token value as emphasized.
    "Token.Generic.Emph":                       _ANY,
    # Marks the token value as an error message.
    "Token.Generic.Error":                      _ANY,
    # Marks the token value as headline.
    "Token.Generic.Heading":                    _ANY,
    # Marks the token value as inserted.
    "Token.Generic.Inserted":                   _ANY,
    # Marks the token value as program output (e.g. for python cli lexer).
    "Token.Generic.Output":                     _ANY,
    # Marks the token value as command prompt (e.g. bash lexer).
    "Token.Generic.Prompt":                     _ANY,
    # Marks the token value as bold (e.g. for rst lexer).
    "Token.Generic.Strong":                     _ANY,
    # Marks the token value as sub-headline.
    "Token.Generic.Subheading":                 _ANY,
    # Marks the token value as a part of an error traceback.
    "Token.Generic.Traceback":                  _ANY,
    # Others.
    "Token":                                    _ANY,
    # For any type of text data.
    "Token.Text":                               _ANY,
    # For specially highlighted whitespace.
    "Token.Text.Whitespace":                    _ANY,
    # Represents lexer errors
    "Token.Error":                              _ANY,
    # Special token for data not matched by a parser (e.g. HTML markup in PHP code)
    "Token.Other":                              _ANY,
}


# Custom bindings per language.
JAVA_ORACLE_BINDINGS = _TO_ORACLE_BASE_BINDINGS
KOTLIN_ORACLE_BINDINGS = _TO_ORACLE_BASE_BINDINGS
PYTHON3_ORACLE_BINDINGS = _TO_ORACLE_BASE_BINDINGS
JS_ORACLE_BINDINGS = _TO_ORACLE_BASE_BINDINGS
CPP_ORACLE_BINDINGS = _TO_ORACLE_BASE_BINDINGS
CS_ORACLE_BINDINGS = _TO_ORACLE_BASE_BINDINGS


def to_oracle_bindings(pygment_token_type: str, bindings=_TO_ORACLE_BASE_BINDINGS) -> int:
    ob = bindings.get(pygment_token_type, _UNKNOWN_PYGMENT_TOKEN_TYPE)
    if ob == _UNKNOWN_PYGMENT_TOKEN_TYPE:
        print(f"Unrecognised Pygment token type {pygment_token_type}", file=sys.stderr)
        ob = _ANY
    return ob
