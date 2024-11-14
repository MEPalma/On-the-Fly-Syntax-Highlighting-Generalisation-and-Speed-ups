package highlighter.csharphighlighter

import CSharpParser
import CSharpParserBaseListener
import allSubsTo
import common.HCode
import common.OHighlight
import highlighter.GrammaticalHighlighter
import isProduction
import loopingOnChildren
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import java.util.*

class CSharpGrammaticalHighlighter : GrammaticalHighlighter, CSharpParserBaseListener() {
    private val oHighlights = hashMapOf<Int, OHighlight>()

    private fun OHighlight.addReplacing() {
        oHighlights[this.startIndex - 2] = this
    }

    override fun getOverrides(): Collection<OHighlight> =
        this.oHighlights.values

    override fun reset() {
        this.oHighlights.clear()
    }

    private fun ParserRuleContext?.thisLoopingOnChildren(
        onTerminal: (TerminalNode) -> HCode? = { _ -> null },
        targetTerminalIndex: Int? = null,
        onProduction: (ParserRuleContext) -> HCode? = { _ -> null },
        targetProductionIndex: Int? = null,
        onAddedExit: Boolean = false,
        reversed: Boolean = false,
    ) =
        this.loopingOnChildren(
            parserVocab = CSharpParser.ruleNames,
            addReplacingFunc = { it.addReplacing() },
            onTerminal = onTerminal,
            targetTerminalIndex = targetTerminalIndex,
            onProduction = onProduction,
            targetProductionIndex = targetProductionIndex,
            onAddedExit = onAddedExit,
            reversed = reversed
        )

    // +------------------+
    // |   DECLARATIONS   |
    // +------------------+

    private fun overrideOf(prc: ParserRuleContext, hCode: HCode, overridingRuleIndex: Int): OHighlight =
        OHighlight(
            startIndex = prc.start.startIndex,
            stopIndex = prc.stop.stopIndex,
            highlightCode = hCode.ordinal,
            highlightColor = hCode.colorCode,
            overridingRule = CSharpParser.ruleNames[overridingRuleIndex]
        )

    override fun exitLocal_variable_declarator(ctx: CSharpParser.Local_variable_declaratorContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.VARIABLE_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitVariable_declarator(ctx: CSharpParser.Variable_declaratorContext?) {
        ctx.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.VARIABLE_DECLARATOR },
            onAddedExit = true
        )
    }

    override fun exitConstant_declarator(ctx: CSharpParser.Constant_declaratorContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.VARIABLE_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitFixed_pointer_declarator(ctx: CSharpParser.Fixed_pointer_declaratorContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.VARIABLE_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitLet_clause(ctx: CSharpParser.Let_clauseContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.VARIABLE_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitClass_definition(ctx: CSharpParser.Class_definitionContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.CLASS_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitStruct_definition(ctx: CSharpParser.Struct_definitionContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.CLASS_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitInterface_definition(ctx: CSharpParser.Interface_definitionContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.CLASS_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitEnum_definition(ctx: CSharpParser.Enum_definitionContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.CLASS_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitNamespace_declaration(ctx: CSharpParser.Namespace_declarationContext?) {
        ctx?.qualified_identifier().thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.CLASS_DECLARATOR },
            onAddedExit = false,
        )
    }

    override fun exitDelegate_definition(ctx: CSharpParser.Delegate_definitionContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.FUNCTION_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitLocal_function_header(ctx: CSharpParser.Local_function_headerContext?) {
        ctx?.identifier()?.let { identifier ->
            overrideOf(
                prc = identifier,
                hCode = HCode.FUNCTION_DECLARATOR,
                overridingRuleIndex = CSharpParser.RULE_local_function_header
            ).addReplacing()
        }
    }

    override fun exitEvent_declaration(ctx: CSharpParser.Event_declarationContext?) {
        ctx?.member_name()?.allSubsTo(CSharpParser.RULE_identifier) {
            overrideOf(
                prc = it,
                hCode = HCode.CLASS_DECLARATOR,
                overridingRuleIndex = CSharpParser.RULE_event_declaration,
            ).addReplacing()
        }
    }

    override fun exitConstructor_declaration(ctx: CSharpParser.Constructor_declarationContext?) {
        ctx?.identifier()?.let { identifier ->
            overrideOf(
                prc = identifier,
                hCode = HCode.FUNCTION_DECLARATOR,
                overridingRuleIndex = CSharpParser.RULE_local_function_header
            ).addReplacing()
        }
    }

    override fun exitDestructor_definition(ctx: CSharpParser.Destructor_definitionContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.FUNCTION_DECLARATOR },
            onAddedExit = true,
        )
    }

    override fun exitMethod_member_name(ctx: CSharpParser.Method_member_nameContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.FUNCTION_DECLARATOR },
            onAddedExit = true,
        )
    }

    // +-----------+
    // |   TYPES   |
    // +-----------+

    override fun exitNamespace_or_type_name(ctx: CSharpParser.Namespace_or_type_nameContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.TYPE_IDENTIFIER },
            onAddedExit = false,
        )
        ctx?.qualified_alias_member()?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.TYPE_IDENTIFIER },
            onAddedExit = false,
        )
    }

    override fun exitVariant_type_parameter(ctx: CSharpParser.Variant_type_parameterContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.TYPE_IDENTIFIER },
            onAddedExit = true,
        )
    }

    override fun exitType_parameter(ctx: CSharpParser.Type_parameterContext?) {
        ctx?.thisLoopingOnChildren(
            targetProductionIndex = CSharpParser.RULE_identifier,
            onProduction = { HCode.TYPE_IDENTIFIER },
            onAddedExit = true,
        )
    }

    override fun exitType_parameter_constraints_clause(ctx: CSharpParser.Type_parameter_constraints_clauseContext?) {
        ctx?.identifier()?.let { identifier ->
            overrideOf(
                prc = identifier,
                hCode = HCode.TYPE_IDENTIFIER,
                overridingRuleIndex = CSharpParser.RULE_type_parameter_constraints_clause
            ).addReplacing()
        }
    }

    override fun exitUsingAliasDirective(ctx: CSharpParser.UsingAliasDirectiveContext?) {
        ctx?.identifier()?.let { identifier ->
            overrideOf(
                prc = identifier,
                hCode = HCode.TYPE_IDENTIFIER,
                overridingRuleIndex = CSharpParser.RULE_using_directive
            ).addReplacing()
        }
    }

    override fun exitLabeled_Statement(ctx: CSharpParser.Labeled_StatementContext?) {
        ctx?.identifier()?.let { identifier ->
            overrideOf(
                prc = identifier,
                hCode = HCode.FUNCTION_DECLARATOR,
                overridingRuleIndex = CSharpParser.RULE_labeled_Statement
            ).addReplacing()
        }
    }

    // Creation calls (Constructor calls).

    // +---------------+
    // |   FUNCTIONS   |
    // +---------------+
    override fun exitPrimary_expression(ctx: CSharpParser.Primary_expressionContext?) {
        val fringe = Stack<ParseTree>()
        ctx?.children?.forEach { c ->
            c.isProduction(CSharpParser.RULE_method_invocation)?.let { _ ->
                if (fringe.isNotEmpty()) {
                    val last = fringe.peek()
                    last.isProduction(
                        setOf(
                            CSharpParser.RULE_member_access,
                            CSharpParser.RULE_qualified_alias_member,
                            CSharpParser.RULE_primary_expression_start
                        )
                    )
                        ?.let { methodCallSubj ->
                            methodCallSubj.thisLoopingOnChildren(
                                targetProductionIndex = CSharpParser.RULE_identifier,
                                onProduction = { HCode.FUNCTION_IDENTIFIER },
                                onAddedExit = false,
                            )
                        } ?: last?.isProduction(CSharpParser.RULE_identifier)?.let { idenCallSubj ->
                        overrideOf(
                            prc = idenCallSubj,
                            hCode = HCode.FUNCTION_IDENTIFIER,
                            overridingRuleIndex = CSharpParser.RULE_method_invocation
                        ).addReplacing()
                    }
                }
            }
            c.isProduction(CSharpParser.RULE_primary_expression_start)?.let { exprStart ->
                exprStart.children?.first()?.isProduction(CSharpParser.RULE_qualified_alias_member)?.children?.get(2)
                    ?.isProduction(CSharpParser.RULE_identifier)?.let { iden ->
                        fringe.add(iden)
                    }
            } ?: fringe.add(c)
        }
    }

    // +------------+
    // |   FIELDS   |
    // +------------+
    override fun exitMember_access(ctx: CSharpParser.Member_accessContext?) {
        ctx?.identifier()?.let { memberId ->
            overrideOf(
                prc = memberId,
                hCode = HCode.FIELD_IDENTIFIER,
                overridingRuleIndex = ctx.ruleIndex
            ).addReplacing()
        }
    }

    override fun exitQualified_alias_member(ctx: CSharpParser.Qualified_alias_memberContext?) {
        ctx?.identifier(1)?.let { identifier ->
            overrideOf(
                prc = identifier,
                hCode = HCode.FIELD_IDENTIFIER,
                overridingRuleIndex = CSharpParser.RULE_qualified_alias_member
            ).addReplacing()
        }
    }

//    override fun exitMemberAccessExpression(ctx: CSharpParser.MemberAccessExpressionContext?) {
//
//    }

}
