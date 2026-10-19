package com.flowmind.api.execution;

import com.flowmind.api.workflow.WorkflowGraph;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class BranchEvaluatorTest {
    private final BranchEvaluator evaluator=new BranchEvaluator();

    @Test void selectsNamedTrueBranchFromNestedInput(){
        var node=node(Map.of("field","ticket.priority","operator","equals","value","critical","trueBranch","urgent","falseBranch","standard"));
        var result=evaluator.evaluate(node,Map.of("ticket",Map.of("priority","critical")),Map.of());
        assertThat(result.matched()).isTrue();
        assertThat(result.selectedBranch()).isEqualTo("urgent");
    }

    @Test void supportsNumericComparisonAndFalseBranch(){
        var node=node(Map.of("field","score","operator","gte","value",90,"trueBranch","pass","falseBranch","review"));
        assertThat(evaluator.evaluate(node,Map.of("score",72),Map.of()).selectedBranch()).isEqualTo("review");
        assertThat(evaluator.evaluate(node,Map.of("score",95),Map.of()).selectedBranch()).isEqualTo("pass");
    }

    @Test void canReadAnUpstreamNodeOutput(){
        var node=node(Map.of("field","outputs.classifier.label","operator","equals","value","billing"));
        var result=evaluator.evaluate(node,Map.of(),Map.of("classifier",Map.of("label","billing")));
        assertThat(result.matched()).isTrue();
    }

    private WorkflowGraph.Node node(Map<String,Object> configuration){return new WorkflowGraph.Node("condition","logic.condition",1,configuration,false);}
}
