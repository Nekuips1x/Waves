package com.wavesplatform.lang.v1

import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import cats.Id
import cats.kernel.Monoid
import com.google.common.primitives.Longs
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.common.utils._
import com.wavesplatform.lang.directives.values._
import com.wavesplatform.lang.v1.FunctionHeader.Native
import com.wavesplatform.lang.v1.PureFunctionsRebenchmark._
import com.wavesplatform.lang.v1.compiler.Terms
import com.wavesplatform.lang.v1.compiler.Terms._
import com.wavesplatform.lang.v1.evaluator.ctx.EvaluationContext
import com.wavesplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.wavesplatform.lang.v1.evaluator.{EvaluatorV2, FunctionIds, Log}
import com.wavesplatform.lang.v1.traits.Environment
import com.wavesplatform.lang.{ExecutionError, Global}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import scala.util.Random

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 30)
@Measurement(iterations = 20)
class PureFunctionsRebenchmark {
  @Benchmark
  def parseIntValue(st: ParseIntVal, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def splitString(st: SplitString, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def toBase58(st: ToBase58, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def fromBase58(st: FromBase58, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def toBase64(st: ToBase64, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def fromBase64(st: FromBase64, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def sumString(st: SumString, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def sumByteString(st: SumByteString, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def longToBytes(st: LongToBytes, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def stringToBytes(st: StringToBytes, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def takeBytes(st: TakeBytes, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def dropBytes(st: DropBytes, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def takeString(st: TakeString, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def dropString(st: DropString, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def listAppend(st: ListAppend, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def listConstructor(st: ListConstructor, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def listConcat1(st: ListConcat1, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def listConcat2(st: ListConcat2, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def listConcat3(st: ListConcat3, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def toUtf8String(st: ToUtf8String, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def bytesToLong(st: BytesToLong, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def stringIndexOf(st: StringIndexOf, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def listGetElement1(st: ListGetElement1, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def listGetElement2(st: ListGetElement2, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  @Benchmark
  def listGetElement3(st: ListGetElement3, bh: Blackhole): Unit =
    bh.consume(eval(st.expr))

  // V5
  @Benchmark
  def parseIntValueV5(st: ParseIntVal, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def splitStringV5(st: SplitString, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def toBase58V5(st: ToBase58, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def fromBase58V5(st: FromBase58, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def toBase64V5(st: ToBase64, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def fromBase64V5(st: FromBase64, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def sumStringV5(st: SumString, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def sumByteStringV5(st: SumByteString, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def longToBytesV5(st: LongToBytes, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def stringToBytesV5(st: StringToBytes, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def takeBytesV5(st: TakeBytes, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def dropBytesV5(st: DropBytes, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def takeStringV5(st: TakeString, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def dropStringV5(st: DropString, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def listAppendV5(st: ListAppend, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def listConstructorV5(st: ListConstructor, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def listConcat1V5(st: ListConcat1, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def listConcat2V5(st: ListConcat2, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def listConcat3V5(st: ListConcat3, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def toUtf8StringV5(st: ToUtf8String, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def bytesToLongV5(st: BytesToLong, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def stringIndexOfV5(st: StringIndexOf, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def listGetElement1V5(st: ListGetElement1, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def listGetElement2V5(st: ListGetElement2, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))

  @Benchmark
  def listGetElement3V5(st: ListGetElement3, bh: Blackhole): Unit =
    bh.consume(evalV5(st.expr))
}

object PureFunctionsRebenchmark {
  val context: EvaluationContext[Environment, Id] =
    Monoid
      .combine(
        PureContext.build(V4, fixUnicodeFunctions = true, useNewPowPrecision = true).evaluationContext[Id],
        CryptoContext.build(Global, V4).evaluationContext[Id]
      )
      .asInstanceOf[EvaluationContext[Environment, Id]]

  val eval: EXPR => (Log[Id], Int, Either[ExecutionError, EVALUATED]) =
    EvaluatorV2.applyCompleted(context, _, V4, true)

  val evalV5: EXPR => (Log[Id], Int, Either[ExecutionError, EVALUATED]) =
    EvaluatorV2.applyCompleted(context, _, V5, true)

  def randomBytes(length: Int): Array[Byte] = {
    val bytes = new Array[Byte](length)
    ThreadLocalRandom.current().nextBytes(bytes)
    bytes
  }

  @State(Scope.Benchmark)
  class SplitString {
    val separator       = ","
    val separatedString = List.fill(1000)(Random.nextPrintableChar().toString * 31).mkString(separator)
    val expr: EXPR =
      FUNCTION_CALL(
        PureContext.splitStr,
        List(
          CONST_STRING(separatedString).explicitGet(),
          CONST_STRING(separator).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class ParseIntVal {
    val numStr = Long.MinValue.toString
    val expr: EXPR =
      FUNCTION_CALL(
        PureContext.parseIntVal,
        List(
          CONST_STRING(numStr).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class ToBase58 {
    val bytes = randomBytes(64)
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.TOBASE58),
        List(
          CONST_BYTESTR(ByteStr(bytes)).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class FromBase58 {
    val string = Base58.encode(randomBytes(75)) // approximately MaxBase58String
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.FROMBASE58),
        List(
          CONST_STRING(string).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class ToBase64 {
    val bytes = randomBytes(32 * 1024 - 1)
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.TOBASE64),
        List(
          CONST_BYTESTR(ByteStr(bytes)).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class FromBase64 {
    val string = Base58.encode(randomBytes(32 * 1024)) // approximately MaxBase64String
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.FROMBASE64),
        List(
          CONST_STRING(string, reduceLimit = false).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class SumString {
    val string1 = "a"
    val string2 = Random.nextPrintableChar().toString * 32766
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.SUM_STRING),
        List(
          CONST_STRING(string1).explicitGet(),
          CONST_STRING(string2).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class SumByteString {
    val byteString1 = ByteStr.fromBytes(1)
    val byteString2 = ByteStr(Array.fill[Byte](32766)(-127))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.SUM_BYTES),
        List(
          CONST_BYTESTR(byteString1).explicitGet(),
          CONST_BYTESTR(byteString2).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class LongToBytes {
    val long = Long.MinValue
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.LONG_TO_BYTES),
        List(
          CONST_LONG(long)
        )
      )
  }

  @State(Scope.Benchmark)
  class StringToBytes {
    val string = Random.nextPrintableChar().toString * 32767
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.STRING_TO_BYTES),
        List(
          CONST_STRING(string).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class TakeBytes {
    val bytes = ByteStr(Array.fill[Byte](32766)(-127))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.TAKE_BYTES),
        List(
          CONST_BYTESTR(bytes).explicitGet(),
          CONST_LONG(32765)
        )
      )
  }

  @State(Scope.Benchmark)
  class DropBytes {
    val bytes = ByteStr(Array.fill[Byte](Terms.DataTxMaxProtoBytes)(-127))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.DROP_BYTES),
        List(
          CONST_BYTESTR(bytes, CONST_BYTESTR.NoLimit).explicitGet(),
          CONST_LONG(1)
        )
      )
  }

  @State(Scope.Benchmark)
  class TakeString {
    val string = Random.nextPrintableChar().toString * 32766
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.TAKE_STRING),
        List(
          CONST_STRING(string).explicitGet(),
          CONST_LONG(32765)
        )
      )
  }

  @State(Scope.Benchmark)
  class DropString {
    val string = Random.nextPrintableChar().toString * 32766
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.DROP_STRING),
        List(
          CONST_STRING(string).explicitGet(),
          CONST_LONG(1)
        )
      )
  }

  @State(Scope.Benchmark)
  class ListConstructor {
    val list = Vector.fill(999)(CONST_LONG(1))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.CREATE_LIST),
        List(
          CONST_LONG(1),
          ARR(list, limited = true).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class ListAppend {
    val list = Vector.fill(999)(CONST_LONG(1))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.APPEND_LIST),
        List(
          ARR(list, limited = true).explicitGet(),
          CONST_LONG(1)
        )
      )
  }

  @State(Scope.Benchmark)
  class ListConcat1 {
    val list1 = Vector.fill(999)(CONST_LONG(1))
    val list2 = Vector.fill(1)(CONST_LONG(1))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.CONCAT_LIST),
        List(
          ARR(list1, limited = true).explicitGet(),
          ARR(list2, limited = true).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class ListConcat2 {
    val list1 = Vector.fill(1)(CONST_LONG(1))
    val list2 = Vector.fill(999)(CONST_LONG(1))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.CONCAT_LIST),
        List(
          ARR(list1, limited = true).explicitGet(),
          ARR(list2, limited = true).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class ListConcat3 {
    val list1 = Vector.fill(500)(CONST_LONG(1))
    val list2 = Vector.fill(500)(CONST_LONG(1))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.CONCAT_LIST),
        List(
          ARR(list1, limited = true).explicitGet(),
          ARR(list2, limited = true).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class ToUtf8String {
    val bytes = new Array[Byte](Terms.DataTxMaxProtoBytes)
    Random.nextBytes(bytes)
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.UTF8STRING),
        List(
          CONST_BYTESTR(ByteStr(bytes), CONST_BYTESTR.NoLimit).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class BytesToLong {
    val longBytes = Longs.toByteArray(Long.MinValue)
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.BININT),
        List(
          CONST_BYTESTR(ByteStr(longBytes)).explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class StringIndexOf {
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.INDEXOF),
        List(
          CONST_STRING("b" * 32766 + "a").explicitGet(),
          CONST_STRING("a").explicitGet()
        )
      )
  }

  @State(Scope.Benchmark)
  class ListGetElement1 {
    val list = Vector.fill(1000)(CONST_LONG(1))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.GET_LIST),
        List(
          ARR(list, limited = true).explicitGet(),
          CONST_LONG(1)
        )
      )
  }

  @State(Scope.Benchmark)
  class ListGetElement2 {
    val list = Vector.fill(1000)(CONST_LONG(1))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.GET_LIST),
        List(
          ARR(list, limited = true).explicitGet(),
          CONST_LONG(500)
        )
      )
  }

  @State(Scope.Benchmark)
  class ListGetElement3 {
    val list = Vector.fill(1000)(CONST_LONG(1))
    val expr: EXPR =
      FUNCTION_CALL(
        Native(FunctionIds.GET_LIST),
        List(
          ARR(list, limited = true).explicitGet(),
          CONST_LONG(1000)
        )
      )
  }
}
