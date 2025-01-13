package io.cockroachdb.jdbc.parser;

import org.antlr.v4.runtime.atn.DecisionInfo;
import org.antlr.v4.runtime.atn.DecisionState;

import java.time.Duration;
import java.util.stream.IntStream;

/**
 * SQL parser/lexer debugging helper.
 *
 * @author Kai Niemi
 */
public abstract class DebugUtils {
    private DebugUtils() {
    }

    public static void printParserProfile(CockroachSQLParser parser) {
        System.out.printf("%-" + 35 + "s", "rule");
        System.out.printf("%-" + 15 + "s", "time");
        System.out.printf("%-" + 15 + "s", "invocations");
        System.out.printf("%-" + 15 + "s", "lookahead");
        System.out.printf("%-" + 15 + "s", "lookahead(max)");
        System.out.printf("%-" + 15 + "s", "ambiguities");
        System.out.printf("%-" + 15 + "s", "errors");

        System.out.println();
        IntStream.rangeClosed(1, 15 * 6 + 35).forEach(value -> {
            System.out.printf("-");
        });
        System.out.println();

        for (DecisionInfo decisionInfo : parser.getParseInfo().getDecisionInfo()) {
            DecisionState ds = parser.getATN().getDecisionState(decisionInfo.decision);
            if (decisionInfo.timeInPrediction > 0) {
                System.out.printf("%-" + 35 + "s", parser.getRuleNames()[ds.ruleIndex]);
                System.out.printf("%-" + 15 + "s", Duration.ofNanos(decisionInfo.timeInPrediction));
                System.out.printf("%-" + 15 + "s", decisionInfo.invocations);
                System.out.printf("%-" + 15 + "s", decisionInfo.SLL_TotalLook);
                System.out.printf("%-" + 15 + "s", decisionInfo.SLL_MaxLook);
                System.out.printf("%-" + 15 + "s", decisionInfo.ambiguities);
                System.out.printf("%-" + 15 + "s%n", decisionInfo.errors);
            }
        }
    }

}
