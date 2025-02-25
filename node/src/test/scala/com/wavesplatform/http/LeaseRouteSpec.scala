package com.wavesplatform.http

import akka.http.scaladsl.model.{ContentTypes, FormData, HttpEntity}
import akka.http.scaladsl.server.Route
import com.wavesplatform.account.{Address, AddressOrAlias, KeyPair}
import com.wavesplatform.api.common.{CommonAccountsApi, LeaseInfo}
import com.wavesplatform.api.http.ApiMarshallers._
import com.wavesplatform.api.http.leasing.LeaseApiRoute
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.common.utils.EitherExt2
import com.wavesplatform.db.WithDomain
import com.wavesplatform.db.WithState.AddrWithBalance
import com.wavesplatform.history.Domain
import com.wavesplatform.lang.directives.values.V5
import com.wavesplatform.lang.v1.FunctionHeader
import com.wavesplatform.lang.v1.compiler.Terms.{CONST_BYTESTR, CONST_LONG, FUNCTION_CALL}
import com.wavesplatform.lang.v1.compiler.TestCompiler
import com.wavesplatform.network.TransactionPublisher
import com.wavesplatform.state.diffs.ENOUGH_AMT
import com.wavesplatform.state.reader.LeaseDetails
import com.wavesplatform.state.{BinaryDataEntry, Blockchain, Diff, Height, TxMeta}
import com.wavesplatform.test.DomainPresets._
import com.wavesplatform.test._
import com.wavesplatform.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com.wavesplatform.transaction.smart.script.trace.TracedResult
import com.wavesplatform.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.wavesplatform.transaction.{Asset, TxHelpers, TxVersion}
import com.wavesplatform.utils.SystemTime
import com.wavesplatform.wallet.Wallet
import com.wavesplatform.{NTPTime, TestWallet, TransactionGen}
import org.scalacheck.Gen
import org.scalamock.scalatest.PathMockFactory
import play.api.libs.json.{JsArray, JsObject, Json}

import scala.concurrent.Future

class LeaseRouteSpec
    extends RouteSpec("/leasing")
    with TransactionGen
    with RestAPISettingsHelper
    with NTPTime
    with WithDomain
    with TestWallet
    with PathMockFactory {
  private def route(domain: Domain) =
    LeaseApiRoute(
      restAPISettings,
      testWallet,
      domain.blockchain,
      (_, _) => Future.successful(TracedResult(Right(true))),
      ntpTime,
      CommonAccountsApi(() => domain.blockchainUpdater.bestLiquidDiff.getOrElse(Diff.empty), domain.db, domain.blockchain)
    )

  private def withRoute(balances: Seq[AddrWithBalance])(f: (Domain, Route) => Unit): Unit =
    withDomain(settings = mostRecent, balances = balances) { d =>
      f(d, route(d).route)
    }

  private def setScriptTransaction(sender: KeyPair) =
    SetScriptTransaction
      .selfSigned(
        TxVersion.V2,
        sender,
        Some(TestCompiler(V5).compileContract("""
          |{-# STDLIB_VERSION 4 #-}
          |{-# CONTENT_TYPE DAPP #-}
          |{-# SCRIPT_TYPE ACCOUNT #-}
          |
          |@Callable(inv)
          |func leaseTo(recipient: ByteVector, amount: Int) = {
          |  let lease = Lease(Address(recipient), amount)
          |  [
          |    lease,
          |    BinaryEntry("leaseId", lease.calculateLeaseId())
          |  ]
          |}
          |
          |@Callable(inv)
          |func cancelLease(id: ByteVector) = {
          |  [
          |    LeaseCancel(id)
          |  ]
          |}
          |""".stripMargin)),
        0.01.waves,
        ntpTime.getTimestamp()
      )
      .explicitGet()

  private def invokeLeaseCancel(sender: KeyPair, leaseId: ByteStr) =
    InvokeScriptTransaction
      .selfSigned(
        TxVersion.V2,
        sender,
        sender.toAddress,
        Some(
          FUNCTION_CALL(
            FunctionHeader.User("cancelLease"),
            List(CONST_BYTESTR(leaseId).explicitGet())
          )
        ),
        Seq.empty,
        0.005.waves,
        Asset.Waves,
        ntpTime.getTimestamp()
      )
      .explicitGet()

  private def leaseCancelTransaction(sender: KeyPair, leaseId: ByteStr) =
    LeaseCancelTransaction.selfSigned(TxVersion.V3, sender, leaseId, 0.001.waves, ntpTime.getTimestamp()).explicitGet()

  private def checkDetails(id: ByteStr, details: LeaseDetails, json: JsObject): Unit = {
    (json \ "id").as[ByteStr] shouldEqual id
    (json \ "originTransactionId").as[ByteStr] shouldEqual details.sourceId
    (json \ "sender").as[String] shouldEqual details.sender.toAddress.toString
    (json \ "amount").as[Long] shouldEqual details.amount
  }

  private def checkActiveLeasesFor(address: AddressOrAlias, route: Route, expectedDetails: Seq[(ByteStr, LeaseDetails)]): Unit =
    Get(routePath(s"/active/$address")) ~> route ~> check {
      val resp = responseAs[Seq[JsObject]]
      resp.size shouldEqual expectedDetails.size
      resp.zip(expectedDetails).foreach {
        case (json, (id, details)) => checkDetails(id, details, json)
      }
    }

  private def toDetails(lt: LeaseTransaction) = LeaseDetails(lt.sender, lt.recipient, lt.amount.value, LeaseDetails.Status.Active, lt.id(), 1)

  private def leaseGen(sender: KeyPair, maxAmount: Long, timestamp: Long): Gen[LeaseTransaction] =
    for {
      fee       <- smallFeeGen
      recipient <- accountGen
      amount    <- Gen.chooseNum(1, (maxAmount - fee).max(1))
      version   <- Gen.oneOf(1.toByte, 2.toByte, 3.toByte)
    } yield LeaseTransaction.selfSigned(version, sender, recipient.toAddress, amount, fee, timestamp).explicitGet()

  "returns active leases which were" - {
    val sender  = TxHelpers.signer(1)
    val leaseTx = leaseGen(sender, ENOUGH_AMT, ntpTime.correctedTime())

    "created and cancelled by Lease/LeaseCancel transactions" in forAll(leaseTx) { leaseTransaction =>
      withRoute(Seq(AddrWithBalance(sender.toAddress))) { (d, r) =>
        d.appendBlock(leaseTransaction)
        val expectedDetails = Seq(leaseTransaction.id() -> toDetails(leaseTransaction))
        // check liquid block
        checkActiveLeasesFor(leaseTransaction.sender.toAddress, r, expectedDetails)
        checkActiveLeasesFor(leaseTransaction.recipient, r, expectedDetails)
        // check hardened block
        d.appendKeyBlock()
        checkActiveLeasesFor(leaseTransaction.sender.toAddress, r, expectedDetails)
        checkActiveLeasesFor(leaseTransaction.recipient, r, expectedDetails)

        d.appendMicroBlock(leaseCancelTransaction(sender, leaseTransaction.id()))
        // check liquid block
        checkActiveLeasesFor(leaseTransaction.sender.toAddress, r, Seq.empty)
        checkActiveLeasesFor(leaseTransaction.recipient, r, Seq.empty)
        // check hardened block
        d.appendKeyBlock()
        checkActiveLeasesFor(leaseTransaction.sender.toAddress, r, Seq.empty)
        checkActiveLeasesFor(leaseTransaction.recipient, r, Seq.empty)
      }
    }

    "created by LeaseTransaction and canceled by InvokeScriptTransaction" in forAll(leaseTx) { leaseTransaction =>
      withRoute(Seq(AddrWithBalance(sender.toAddress))) { (d, r) =>
        d.appendBlock(leaseTransaction)
        val expectedDetails = Seq(leaseTransaction.id() -> toDetails(leaseTransaction))
        // check liquid block
        checkActiveLeasesFor(leaseTransaction.sender.toAddress, r, expectedDetails)
        checkActiveLeasesFor(leaseTransaction.recipient, r, expectedDetails)
        // check hardened block
        d.appendKeyBlock()
        checkActiveLeasesFor(leaseTransaction.sender.toAddress, r, expectedDetails)
        checkActiveLeasesFor(leaseTransaction.recipient, r, expectedDetails)

        d.appendMicroBlock(
          setScriptTransaction(sender),
          invokeLeaseCancel(sender, leaseTransaction.id())
        )
        // check liquid block
        checkActiveLeasesFor(leaseTransaction.sender.toAddress, r, Seq.empty)
        checkActiveLeasesFor(leaseTransaction.recipient, r, Seq.empty)
        // check hardened block
        d.appendKeyBlock()
        checkActiveLeasesFor(leaseTransaction.sender.toAddress, r, Seq.empty)
        checkActiveLeasesFor(leaseTransaction.recipient, r, Seq.empty)
      }
    }

    val setScriptAndInvoke = {
      val sender    = TxHelpers.signer(1)
      val recipient = TxHelpers.signer(2)

      (
        sender,
        setScriptTransaction(sender),
        InvokeScriptTransaction
          .selfSigned(
            TxVersion.V2,
            sender,
            sender.toAddress,
            Some(
              FUNCTION_CALL(
                FunctionHeader.User("leaseTo"),
                List(CONST_BYTESTR(ByteStr(recipient.toAddress.bytes)).explicitGet(), CONST_LONG(10_000.waves))
              )
            ),
            Seq.empty,
            0.005.waves,
            Asset.Waves,
            ntpTime.getTimestamp()
          )
          .explicitGet(),
        recipient.toAddress
      )
    }

    "created by InvokeScriptTransaction and canceled by CancelLeaseTransaction" in forAll(setScriptAndInvoke) {
      case (sender, setScript, invoke, recipient) =>
        withRoute(Seq(AddrWithBalance(sender.toAddress))) { (d, r) =>
          d.appendBlock(setScript)
          d.appendBlock(invoke)
          val leaseId = d.blockchain
            .accountData(sender.toAddress, "leaseId")
            .collect {
              case i: BinaryDataEntry => i.value
            }
            .get
          val expectedDetails = Seq(leaseId -> LeaseDetails(setScript.sender, recipient, 10_000.waves, LeaseDetails.Status.Active, invoke.id(), 1))
          // check liquid block
          checkActiveLeasesFor(sender.toAddress, r, expectedDetails)
          checkActiveLeasesFor(recipient, r, expectedDetails)
          // check hardened block
          d.appendKeyBlock()
          checkActiveLeasesFor(sender.toAddress, r, expectedDetails)
          checkActiveLeasesFor(recipient, r, expectedDetails)

          d.appendMicroBlock(leaseCancelTransaction(sender, leaseId))
          // check liquid block
          checkActiveLeasesFor(sender.toAddress, r, Seq.empty)
          checkActiveLeasesFor(recipient, r, Seq.empty)
          // check hardened block
          d.appendKeyBlock()
          checkActiveLeasesFor(sender.toAddress, r, Seq.empty)
          checkActiveLeasesFor(recipient, r, Seq.empty)
        }
    }

    "created and canceled by InvokeScriptTransaction" in forAll(setScriptAndInvoke) {
      case (sender, setScript, invoke, recipient) =>
        withRoute(Seq(AddrWithBalance(sender.toAddress))) { (d, r) =>
          d.appendBlock(setScript)
          d.appendBlock(invoke)
          val invokeStatus = d.blockchain.transactionMeta(invoke.id()).get.succeeded
          assert(invokeStatus, "Invoke has failed")

          val leaseId = d.blockchain
            .accountData(sender.toAddress, "leaseId")
            .collect {
              case i: BinaryDataEntry => i.value
            }
            .get
          val expectedDetails = Seq(leaseId -> LeaseDetails(setScript.sender, recipient, 10_000.waves, LeaseDetails.Status.Active, invoke.id(), 1))
          // check liquid block
          checkActiveLeasesFor(sender.toAddress, r, expectedDetails)
          checkActiveLeasesFor(recipient, r, expectedDetails)
          // check hardened block
          d.appendKeyBlock()
          checkActiveLeasesFor(sender.toAddress, r, expectedDetails)
          checkActiveLeasesFor(recipient, r, expectedDetails)

          d.appendMicroBlock(invokeLeaseCancel(sender, leaseId))
          // check liquid block
          checkActiveLeasesFor(sender.toAddress, r, Seq.empty)
          checkActiveLeasesFor(recipient, r, Seq.empty)
          // check hardened block
          d.appendKeyBlock()
          checkActiveLeasesFor(sender.toAddress, r, Seq.empty)
          checkActiveLeasesFor(recipient, r, Seq.empty)
        }
    }

    val nestedInvocation = {
      val proxy     = TxHelpers.signer(1)
      val target    = TxHelpers.signer(2)
      val recipient = TxHelpers.signer(3)

      (
        (proxy, target, recipient.toAddress),
        Seq(
          setScriptTransaction(target),
          SetScriptTransaction
            .selfSigned(
              TxVersion.V2,
              proxy,
              Some(TestCompiler(V5).compileContract("""
                                                      |{-# STDLIB_VERSION 4 #-}
                                                      |{-# CONTENT_TYPE DAPP #-}
                                                      |{-# SCRIPT_TYPE ACCOUNT #-}
                                                      |
                                                      |@Callable(inv)
                                                      |func callProxy(targetDapp: ByteVector, recipient: ByteVector, amount: Int) = {
                                                      |  strict result = invoke(Address(targetDapp), "leaseTo", [recipient, amount], [])
                                                      |  []
                                                      |}
                                                      |""".stripMargin)),
              0.01.waves,
              ntpTime.getTimestamp()
            )
            .explicitGet()
        )
      )
    }

    "created by nested invocations" in {
      val ((proxy, target, recipient), transactions) = nestedInvocation
      withRoute(Seq(AddrWithBalance(proxy.toAddress), AddrWithBalance(target.toAddress))) { (d, r) =>
        d.appendBlock(transactions: _*)
        val ist = InvokeScriptTransaction
          .selfSigned(
            TxVersion.V2,
            proxy,
            proxy.toAddress,
            Some(
              FUNCTION_CALL(
                FunctionHeader.User("callProxy"),
                List(
                  CONST_BYTESTR(ByteStr(target.toAddress.bytes)).explicitGet(),
                  CONST_BYTESTR(ByteStr(recipient.bytes)).explicitGet(),
                  CONST_LONG(10_000.waves)
                )
              )
            ),
            Seq.empty,
            0.005.waves,
            Asset.Waves,
            ntpTime.getTimestamp()
          )
          .explicitGet()

        d.appendBlock(ist)
        val leaseId = d.blockchain
          .accountData(target.toAddress, "leaseId")
          .collect {
            case i: BinaryDataEntry => i.value
          }
          .get

        val expectedDetails = Seq(leaseId -> LeaseDetails(target.publicKey, recipient, 10_000.waves, LeaseDetails.Status.Active, ist.id(), 1))
        // check liquid block
        checkActiveLeasesFor(target.toAddress, r, expectedDetails)
        checkActiveLeasesFor(recipient, r, expectedDetails)
        // check hardened block
        d.appendKeyBlock()
        checkActiveLeasesFor(target.toAddress, r, expectedDetails)
        checkActiveLeasesFor(recipient, r, expectedDetails)
      }
    }
  }

  routePath("/info") in {
    val blockchain = stub[Blockchain]
    val commonApi  = stub[CommonAccountsApi]

    val route = LeaseApiRoute(restAPISettings, stub[Wallet], blockchain, stub[TransactionPublisher], SystemTime, commonApi).route

    val lease       = TxHelpers.lease()
    val leaseCancel = TxHelpers.leaseCancel(lease.id())
    (blockchain.transactionInfo _).when(lease.id()).returning(Some(TxMeta(Height(1), true, 0L) -> lease))
    (commonApi.leaseInfo _)
      .when(lease.id())
      .returning(
        Some(
          LeaseInfo(
            lease.id(),
            lease.id(),
            lease.sender.toAddress,
            lease.recipient.asInstanceOf[Address],
            lease.amount.value,
            1,
            LeaseInfo.Status.Canceled,
            Some(2),
            Some(leaseCancel.id())
          )
        )
      )
    (commonApi.leaseInfo _).when(*).returning(None)

    Get(routePath(s"/info/${lease.id()}")) ~> route ~> check {
      val response = responseAs[JsObject]
      response should matchJson(s"""{
                               |  "id" : "${lease.id()}",
                               |  "originTransactionId" : "${lease.id()}",
                               |  "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                               |  "recipient" : "3MuVqVJGmFsHeuFni5RbjRmALuGCkEwzZtC",
                               |  "amount" : 1000000000,
                               |  "height" : 1,
                               |  "status" : "canceled",
                               |  "cancelHeight" : 2,
                               |  "cancelTransactionId" : "${leaseCancel.id()}"
                               |}""".stripMargin)
    }

    val leasesListJson = Json.parse(s"""[{
                                       |  "id" : "${lease.id()}",
                                       |  "originTransactionId" : "${lease.id()}",
                                       |  "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                       |  "recipient" : "3MuVqVJGmFsHeuFni5RbjRmALuGCkEwzZtC",
                                       |  "amount" : 1000000000,
                                       |  "height" : 1,
                                       |  "status" : "canceled",
                                       |  "cancelHeight" : 2,
                                       |  "cancelTransactionId" : "${leaseCancel.id()}"
                                       |},
                                       {
                                       |  "id" : "${lease.id()}",
                                       |  "originTransactionId" : "${lease.id()}",
                                       |  "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                       |  "recipient" : "3MuVqVJGmFsHeuFni5RbjRmALuGCkEwzZtC",
                                       |  "amount" : 1000000000,
                                       |  "height" : 1,
                                       |  "status" : "canceled",
                                       |  "cancelHeight" : 2,
                                       |  "cancelTransactionId" : "${leaseCancel.id()}"
                                       |}]""".stripMargin)

    Get(routePath(s"/info?id=${lease.id()}&id=${lease.id()}")) ~> route ~> check {
      val response = responseAs[JsArray]
      response should matchJson(leasesListJson)
    }

    Post(
      routePath(s"/info"),
      HttpEntity(ContentTypes.`application/json`, Json.obj("ids" -> Seq(lease.id().toString, lease.id().toString)).toString())
    ) ~> route ~> check {
      val response = responseAs[JsArray]
      response should matchJson(leasesListJson)
    }

    Post(
      routePath(s"/info"),
      HttpEntity(
        ContentTypes.`application/json`,
        Json.obj("ids" -> (0 to restAPISettings.transactionsByAddressLimit).map(_ => lease.id().toString)).toString()
      )
    ) ~> route ~> check {
      val response = responseAs[JsObject]
      response should matchJson("""{
                                  |  "error" : 10,
                                  |  "message" : "Too big sequence requested: max limit is 10000 entries"
                                  |}""".stripMargin)
    }

    Post(
      routePath(s"/info"),
      FormData("id" -> lease.id().toString, "id" -> lease.id().toString)
    ) ~> route ~> check {
      val response = responseAs[JsArray]
      response should matchJson(leasesListJson)
    }

    Get(routePath(s"/info?id=nonvalid&id=${leaseCancel.id()}")) ~> route ~> check {
      val response = responseAs[JsObject]
      response should matchJson(s"""
                               |{
                               |  "error" : 116,
                               |  "message" : "Request contains invalid IDs. nonvalid, ${leaseCancel.id()}",
                               |  "ids" : [ "nonvalid", "${leaseCancel.id()}" ]
                               |}""".stripMargin)
    }
  }
}
