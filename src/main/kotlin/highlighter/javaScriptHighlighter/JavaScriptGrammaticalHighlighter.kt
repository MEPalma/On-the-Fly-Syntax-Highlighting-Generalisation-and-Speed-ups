package highlighter.javaScriptHighlighter

import JavaScriptLexer
import JavaScriptParser
import JavaScriptParser.ArgumentsExpressionContext
import JavaScriptParser.NewExpressionContext
import JavaScriptParserBaseListener
import common.HCode
import common.OHighlight
import common.OHighlight.Companion.overrideOf
import highlighter.GrammaticalHighlighter
import isProduction
import isTerminal
import loopingOnChildren
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import java.util.*

class JavaScriptGrammaticalHighlighter : GrammaticalHighlighter, JavaScriptParserBaseListener() {
    private val oHighlights = hashMapOf<Int, OHighlight>()

    private fun OHighlight.addReplacing() {
        oHighlights[this.startIndex] = this
    }

    override fun getOverrides(): Collection<OHighlight> =
        this.oHighlights.values

    override fun reset() {
        this.oHighlights.clear()
    }

    private fun ParserRuleContext?.myLoopingOnChildren(
        onTerminal: (TerminalNode) -> HCode? = { _ -> null },
        targetTerminalIndex: Int? = null,
        onProduction: (ParserRuleContext) -> HCode? = { _ -> null },
        targetProductionIndex: Int? = null,
        onAddedExit: Boolean = false,
        reversed: Boolean = false,
    ) =
        this.loopingOnChildren(
            parserVocab = JavaScriptParser.ruleNames,
            addReplacingFunc = { it.addReplacing() },
            onTerminal = onTerminal,
            targetTerminalIndex = targetTerminalIndex,
            onProduction = onProduction,
            targetProductionIndex = targetProductionIndex,
            onAddedExit = onAddedExit,
            reversed = reversed
        )

    private fun assignOnFirstIdentifier(ctx: ParserRuleContext?, hcode: HCode) =
        ctx.myLoopingOnChildren(
            targetTerminalIndex = JavaScriptParser.Identifier,
            onTerminal = { hcode },
            onAddedExit = true
        )

    override fun exitClassDeclaration(ctx: JavaScriptParser.ClassDeclarationContext?) {
        var isFunDeclaration = false
        ctx.myLoopingOnChildren(
            onTerminal = { isFunDeclaration = true; null },
            targetTerminalIndex = JavaScriptLexer.Function_,
            onProduction = { if (isFunDeclaration) HCode.FUNCTION_DECLARATOR else HCode.CLASS_DECLARATOR },
            targetProductionIndex = JavaScriptParser.RULE_identifier,
            onAddedExit = true
        )
    }

    override fun exitVariableDeclaration(ctx: JavaScriptParser.VariableDeclarationContext?) =
        ctx.myLoopingOnChildren(
            targetProductionIndex = JavaScriptParser.RULE_assignable,
            onProduction = { HCode.VARIABLE_DECLARATOR },
            onAddedExit = true
        )

    override fun exitMethodDefinition(ctx: JavaScriptParser.MethodDefinitionContext?) =
        ctx.myLoopingOnChildren(
            targetProductionIndex = JavaScriptParser.RULE_propertyName,
            onProduction = { HCode.FUNCTION_DECLARATOR }
        )

    override fun exitFunctionDeclaration(ctx: JavaScriptParser.FunctionDeclarationContext?) {
        ctx.myLoopingOnChildren(
            targetProductionIndex = JavaScriptParser.RULE_identifier,
            onProduction = { HCode.FUNCTION_DECLARATOR }
        )
    }


    // Highlight super classes
    override fun exitClassTail(ctx: JavaScriptParser.ClassTailContext?) {
        ctx.myLoopingOnChildren(
            targetProductionIndex = JavaScriptParser.RULE_singleExpression,
            onProduction = { HCode.TYPE_IDENTIFIER }
        )
    }

    // Highlight property names
    override fun exitPropertyExpressionAssignment(ctx: JavaScriptParser.PropertyExpressionAssignmentContext?) {
        ctx.myLoopingOnChildren(
            targetProductionIndex = JavaScriptParser.RULE_propertyName,
            onProduction = { HCode.FIELD_IDENTIFIER } // ToDo: chage to Varable_DECLARATONsss
        )
    }

    override fun exitMemberDotExpression(ctx: JavaScriptParser.MemberDotExpressionContext?) {
        val methodInvocationParents = listOf(ArgumentsExpressionContext::class, NewExpressionContext::class)
        if (methodInvocationParents.any { it.isInstance(ctx?.parent?.ruleContext) }) {
            // method invocation after DOT call
            var isNewExpression = ctx?.parent?.ruleContext is NewExpressionContext
            ctx.myLoopingOnChildren(
                targetProductionIndex = JavaScriptParser.RULE_identifierName,
                onProduction = { if (isNewExpression) HCode.TYPE_IDENTIFIER else HCode.FUNCTION_IDENTIFIER },
                onTerminal = { HCode.TYPE_IDENTIFIER },
                targetTerminalIndex = JavaScriptParser.Identifier,
                onAddedExit = true
            )
        } else if (ctx?.parent?.ruleContext !is JavaScriptParser.ImportExpressionContext) {
            // Field accessor after DOT call
            ctx.myLoopingOnChildren(
                targetProductionIndex = JavaScriptParser.RULE_identifierName,
                onProduction = { HCode.FIELD_IDENTIFIER }
            )
        }
    }

    override fun exitArgumentsExpression(ctx: ArgumentsExpressionContext?) {
        ctx?.children?.forEach { pt ->
            if (pt is JavaScriptParser.IdentifierExpressionContext) {
                pt.children.getOrNull(0)?.isProduction(JavaScriptParser.RULE_identifier)?.let { typeTree ->
                    typeTree.allSubNAMEStoUnit {
                        overrideOf(
                            it,
                            HCode.FUNCTION_IDENTIFIER,
                            JavaScriptParser.RULE_identifier,
                            JavaScriptParser.ruleNames
                        ).addReplacing()
                    }
                }
            }
        }
    }

    private fun ParseTree.allSubNAMEStoUnit(action: (TerminalNode) -> Unit) {
        val fringe = Stack<ParseTree>()
        fringe.push(this)
        while (!fringe.isEmpty()) {
            val pt = fringe.pop()
            pt.isTerminal(JavaScriptLexer.Identifier)?.let { action(it) } ?: pt.isProduction()
                ?.let { p -> p.children.forEach { fringe.push(it) } }
        }
    }

    override fun exitNewExpression(ctx: NewExpressionContext?) {
        ctx?.children?.forEach { pt ->
            if (pt is JavaScriptParser.IdentifierExpressionContext) {
                pt.children.getOrNull(0)?.isProduction(JavaScriptParser.RULE_identifier)?.let { typeTree ->
                    typeTree.allSubNAMEStoUnit {
                        overrideOf(
                            it,
                            HCode.TYPE_IDENTIFIER,
                            JavaScriptParser.RULE_identifier,
                            JavaScriptParser.ruleNames
                        ).addReplacing()
                    }
                }
            }
        }
    }

}
