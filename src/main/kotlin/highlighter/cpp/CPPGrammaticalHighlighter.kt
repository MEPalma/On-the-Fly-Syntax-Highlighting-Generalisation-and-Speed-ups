package highlighter.cpp

import CPP14Lexer
import CPP14Parser
import CPP14ParserBaseListener
import common.HCode
import common.OHighlight
import highlighter.GrammaticalHighlighter
import loopingOnChildren
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

class CPPGrammaticalHighlighter : GrammaticalHighlighter, CPP14ParserBaseListener() {
    private val oHighlights = hashMapOf<Int, OHighlight>()

    private fun OHighlight.addReplacing() {
        oHighlights[this.startIndex] = this
    }

    override fun getOverrides(): Collection<OHighlight> = this.oHighlights.values

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
    ) = this.loopingOnChildren(
        parserVocab = CPP14Parser.ruleNames,
        addReplacingFunc = { it.addReplacing() },
        onTerminal = onTerminal,
        targetTerminalIndex = targetTerminalIndex,
        onProduction = onProduction,
        targetProductionIndex = targetProductionIndex,
        onAddedExit = onAddedExit,
        reversed = reversed
    )

    private fun assignOnFirstIdentifier(ctx: ParserRuleContext?, hcode: HCode) = ctx.myLoopingOnChildren(
        targetTerminalIndex = CPP14Lexer.Identifier, onTerminal = { hcode }, onAddedExit = true
    )

    // +-----------------+
    // |  DECLARATIONS  |
    //+-----------------+

    // Class definitions and class as type
    override fun exitClassName(ctx: CPP14Parser.ClassNameContext?) {
        if (ctx?.parent?.ruleContext is CPP14Parser.ClassHeadNameContext) {
            assignOnFirstIdentifier(ctx, HCode.CLASS_DECLARATOR)
        } else if (ctx?.parent?.ruleContext is CPP14Parser.TheTypeNameContext) {
            if (ctx?.parent?.parent?.ruleContext !is CPP14Parser.NestedNameSpecifierContext) {
                assignOnFirstIdentifier(ctx, HCode.TYPE_IDENTIFIER)
            }
        }
    }

    // Class member variable declarations
    override fun exitMemberdeclaration(ctx: CPP14Parser.MemberdeclarationContext?) {
        var secondChild = ctx?.children?.getOrNull(1)
        if (secondChild is CPP14Parser.MemberDeclaratorListContext) {
            secondChild?.children?.filter { it is CPP14Parser.MemberDeclaratorContext }?.forEach { declarator ->
                (declarator as CPP14Parser.MemberDeclaratorContext).myLoopingOnChildren(
                    onProduction = { HCode.VARIABLE_DECLARATOR }, targetProductionIndex = CPP14Parser.RULE_declarator
                )
            }
        }

    }

    override fun exitSimpleDeclaration(ctx: CPP14Parser.SimpleDeclarationContext?) {
        if (ctx?.parent?.parent is CPP14Parser.DeclarationContext) {
            return
        }

        var contextList =
            arrayListOf(CPP14Parser.InitDeclaratorListContext::class, CPP14Parser.InitDeclaratorContext::class)
        if (ctx?.children?.any { it is CPP14Parser.DeclSpecifierSeqContext } == true) {
            var childCtx = ctx?.children?.getOrNull(1)
            if (childCtx is CPP14Parser.InitDeclaratorListContext) {

                childCtx?.children?.filter { it is CPP14Parser.InitDeclaratorContext }?.forEach { declarator ->
                    (declarator as CPP14Parser.InitDeclaratorContext).myLoopingOnChildren(
                        onProduction = { HCode.VARIABLE_DECLARATOR },
                        targetProductionIndex = CPP14Parser.RULE_declarator
                    )
                }
            }
        }
    }


    // Function definitions
    override fun exitNoPointerDeclarator(ctx: CPP14Parser.NoPointerDeclaratorContext?) {
        if (ctx?.children?.any { it is CPP14Parser.ParametersAndQualifiersContext } == true) {
            ctx.myLoopingOnChildren(targetProductionIndex = CPP14Parser.RULE_noPointerDeclarator,
                onProduction = { HCode.FUNCTION_DECLARATOR })
        }
    }


    // Field accessors and function calls
    override fun exitPostfixExpression(ctx: CPP14Parser.PostfixExpressionContext?) {
        var accessors = arrayListOf(".", "->")
        // check if current context is SimpleFieldAccessos
        if (ctx?.parent?.ruleContext is CPP14Parser.UnaryExpressionContext) {
            if (ctx?.children?.getOrNull(1) is TerminalNode && accessors.any { it == (ctx?.children?.getOrNull(1) as TerminalNode).symbol.text }) {
                ctx.myLoopingOnChildren(
                    onProduction = { HCode.FIELD_IDENTIFIER }, targetProductionIndex = CPP14Parser.RULE_idExpression
                )
            }
        }

        // check if current context is postfix FieldAccessor
        if (ctx?.parent?.ruleContext is CPP14Parser.PostfixExpressionContext && ctx?.parent?.childCount!! >= 3) {
            var secondParentChild =
                (ctx?.parent?.ruleContext as CPP14Parser.PostfixExpressionContext)?.children?.getOrNull(1)
            if (secondParentChild is TerminalNode) {
                if (secondParentChild.symbol.text == "(") {

                    // check if current context is using templates
                    val maxIterations = 10
                    var curIt = 0
                    var currentChild = ctx?.children?.getOrNull(0)
                    var isTemplate = false
                    while (true) {
                        if (currentChild == null || currentChild is TerminalNode || curIt >= maxIterations) {
                            break
                        }

                        if (currentChild is CPP14Parser.SimpleTemplateIdContext) {
                            isTemplate = true
                            currentChild.myLoopingOnChildren(
                                onProduction = { HCode.FUNCTION_IDENTIFIER },
                                targetProductionIndex = CPP14Parser.RULE_templateName

                            )
                            break
                        }
                        currentChild = (currentChild as ParserRuleContext)?.children?.getOrNull(0)
                        curIt += 1
                    }

                    if (!isTemplate) {
                        ctx.myLoopingOnChildren(
                            onProduction = { HCode.FUNCTION_IDENTIFIER },
                            targetProductionIndex = CPP14Parser.RULE_primaryExpression,
                        )
                        ctx.myLoopingOnChildren(
                            onProduction = { HCode.FUNCTION_IDENTIFIER },
                            targetProductionIndex = CPP14Parser.RULE_idExpression,
                        )
                    }
                } else if (accessors.any { it == secondParentChild.symbol.text }) {
                    ctx.myLoopingOnChildren(
                        onProduction = { HCode.FIELD_IDENTIFIER }, targetProductionIndex = CPP14Parser.RULE_idExpression
                    )
                }
            }
        }
    }

    // inheritance types
    override fun exitBaseTypeSpecifier(ctx: CPP14Parser.BaseTypeSpecifierContext?) {
        ctx.myLoopingOnChildren(
            onProduction = { HCode.TYPE_IDENTIFIER }, targetProductionIndex = CPP14Parser.RULE_classOrDeclType
        )
    }

    // Templates as types
    override fun exitTypeParameter(ctx: CPP14Parser.TypeParameterContext?) {
        if (ctx?.parent?.ruleContext is CPP14Parser.TemplateParameterContext) {
            ctx.myLoopingOnChildren(
                onTerminal = { HCode.TYPE_IDENTIFIER },
                targetProductionIndex = CPP14Lexer.Identifier,
                reversed = true,
                onAddedExit = true
            )
        }
    }

    override fun exitTemplateName(ctx: CPP14Parser.TemplateNameContext?) {
        ctx.myLoopingOnChildren(
            onTerminal = { HCode.TYPE_IDENTIFIER }, targetTerminalIndex = CPP14Lexer.Identifier
        )
    }

    override fun exitEnumHead(ctx: CPP14Parser.EnumHeadContext?) {
        ctx.myLoopingOnChildren(
            onTerminal = { HCode.CLASS_DECLARATOR },
            targetTerminalIndex = CPP14Lexer.Identifier,
            reversed = true,
            onAddedExit = true
        )
    }

    override fun exitQualifiedId(ctx: CPP14Parser.QualifiedIdContext?) {
        if (ctx?.children?.get(0) is CPP14Parser.NestedNameSpecifierContext) {
            var nestedNameCtx = ctx?.children?.getOrNull(0)
            if ((nestedNameCtx as CPP14Parser.NestedNameSpecifierContext).children.getOrNull(1)?.text.equals("::")) {
                ctx.myLoopingOnChildren(
                    onProduction = { HCode.FIELD_IDENTIFIER }, targetProductionIndex = CPP14Parser.RULE_unqualifiedId
                )
            }
        }
    }

}
