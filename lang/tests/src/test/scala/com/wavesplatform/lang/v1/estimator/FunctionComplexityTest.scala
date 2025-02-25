package com.wavesplatform.lang.v1.estimator
import com.wavesplatform.DocSource
import com.wavesplatform.common.utils.EitherExt2
import com.wavesplatform.lang.directives.values._
import com.wavesplatform.lang.utils._
import com.wavesplatform.lang.v1.compiler.Terms
import com.wavesplatform.lang.v1.compiler.Terms.FUNCTION_CALL
import com.wavesplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.wavesplatform.lang.v1.evaluator.ctx.BaseFunction
import com.wavesplatform.lang.v1.traits.Environment
import com.wavesplatform.test.PropSpec
import org.scalatest.exceptions.TestFailedException

class FunctionComplexityTest extends PropSpec {
  def docCost(function: BaseFunction[Environment], version: StdLibVersion): Int =
    DocSource.funcData
      .getOrElse(
        (
          function.name,
          function.signature.args.map(_._2.toString).toList,
          version.id
        ),
        throw new Exception(s"Function ${function.name}(${function.signature.args.map(_._2.toString).toList.mkString(", ")}) not found in $version")
      )
      ._3

  property("all functions complexities") {
    lazyContexts.foreach { case ((ds, _), ctx) =>
      ctx().functions
        .filterNot(_.name.startsWith("$"))
        .foreach { function =>
          val expr = FUNCTION_CALL(function.header, List.fill(function.args.size)(Terms.TRUE))
          val estimatedCost = ScriptEstimatorV3(fixOverflow = true)(
            varNames(ds.stdLibVersion, ds.contentType),
            functionCosts(ds.stdLibVersion, ds.contentType),
            expr
          ).explicitGet() - function.args.size

          val expectedCost = docCost(function, ds.stdLibVersion)

          if (estimatedCost != expectedCost)
            throw new TestFailedException(
              s"Estimated complexity = $estimatedCost is not equal to doc complexity = $expectedCost for ${ds.stdLibVersion} $function",
              0
            )
        }
    }
  }
}
