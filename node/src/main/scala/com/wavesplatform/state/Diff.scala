package com.wavesplatform.state

import cats.data.Ior
import cats.implicits.{catsSyntaxSemigroup, toFlatMapOps, toFunctorOps}
import cats.kernel.{Monoid, Semigroup}
import cats.{Id, Monad}
import com.google.protobuf.ByteString
import com.wavesplatform.account.{Address, AddressOrAlias, Alias, PublicKey}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.features.BlockchainFeatures
import com.wavesplatform.lang.script.Script
import com.wavesplatform.state.diffs.FeeValidation
import com.wavesplatform.state.reader.LeaseDetails
import com.wavesplatform.transaction.Asset.IssuedAsset
import com.wavesplatform.transaction.smart.InvokeScriptTransaction
import com.wavesplatform.transaction.{Asset, Transaction}

import scala.collection.immutable.VectorMap

case class LeaseBalance(in: Long, out: Long) {
  def combineF[F[_]: Monad](that: LeaseBalance)(implicit s: Summarizer[F]): F[LeaseBalance] =
    for {
      in  <- s.sum(in, that.in, "Lease in")
      out <- s.sum(out, that.out, "Lease out")
    } yield LeaseBalance(in, out)
}

object LeaseBalance {
  val empty: LeaseBalance = LeaseBalance(0, 0)
}

case class VolumeAndFee(volume: Long, fee: Long)

object VolumeAndFee {
  val empty: VolumeAndFee = VolumeAndFee(0, 0)

  implicit val m: Monoid[VolumeAndFee] = new Monoid[VolumeAndFee] {
    override def empty: VolumeAndFee = VolumeAndFee.empty

    override def combine(x: VolumeAndFee, y: VolumeAndFee): VolumeAndFee =
      VolumeAndFee(x.volume + y.volume, x.fee + y.fee)
  }
}

case class AssetInfo(name: ByteString, description: ByteString, lastUpdatedAt: Height)

object AssetInfo {
  implicit val semigroup: Semigroup[AssetInfo] = (_, y) => y

  def apply(name: String, description: String, lastUpdatedAt: Height): AssetInfo =
    AssetInfo(ByteString.copyFromUtf8(name), ByteString.copyFromUtf8(description), lastUpdatedAt)
}

case class AssetStaticInfo(source: TransactionId, issuer: PublicKey, decimals: Int, nft: Boolean)

case class AssetVolumeInfo(isReissuable: Boolean, volume: BigInt)
object AssetVolumeInfo {
  implicit val assetInfoMonoid: Monoid[AssetVolumeInfo] = new Monoid[AssetVolumeInfo] {
    override def empty: AssetVolumeInfo = AssetVolumeInfo(isReissuable = true, 0)
    override def combine(x: AssetVolumeInfo, y: AssetVolumeInfo): AssetVolumeInfo =
      AssetVolumeInfo(x.isReissuable && y.isReissuable, x.volume + y.volume)
  }
}

case class AssetScriptInfo(script: Script, complexity: Long)

case class AssetDescription(
    originTransactionId: ByteStr,
    issuer: PublicKey,
    name: ByteString,
    description: ByteString,
    decimals: Int,
    reissuable: Boolean,
    totalVolume: BigInt,
    lastUpdatedAt: Height,
    script: Option[AssetScriptInfo],
    sponsorship: Long,
    nft: Boolean
)

case class AccountDataInfo(data: Map[String, DataEntry[_]])

object AccountDataInfo {
  implicit val accountDataInfoMonoid: Monoid[AccountDataInfo] = new Monoid[AccountDataInfo] {
    override def empty: AccountDataInfo = AccountDataInfo(Map.empty)

    override def combine(x: AccountDataInfo, y: AccountDataInfo): AccountDataInfo = AccountDataInfo(x.data ++ y.data)
  }

  implicit class AccountDataInfoExt(private val ad: AccountDataInfo) extends AnyVal {
    def filterEmpty: AccountDataInfo =
      ad.copy(ad.data.filterNot(_._2.isEmpty))
  }
}

sealed abstract class Sponsorship
case class SponsorshipValue(minFee: Long) extends Sponsorship
case object SponsorshipNoInfo             extends Sponsorship

object Sponsorship {
  implicit val sponsorshipMonoid: Monoid[Sponsorship] = new Monoid[Sponsorship] {
    override def empty: Sponsorship = SponsorshipNoInfo

    override def combine(x: Sponsorship, y: Sponsorship): Sponsorship = y match {
      case SponsorshipNoInfo => x
      case _                 => y
    }
  }

  def calcWavesFeeAmount(tx: Transaction, getSponsorship: IssuedAsset => Option[Long]): Long = tx.assetFee match {
    case (asset @ IssuedAsset(_), amountInAsset) =>
      val sponsorship = getSponsorship(asset).getOrElse(0L)
      Sponsorship.toWaves(amountInAsset, sponsorship)

    case (Asset.Waves, amountInWaves) =>
      amountInWaves
  }

  def sponsoredFeesSwitchHeight(blockchain: Blockchain): Int =
    blockchain
      .featureActivationHeight(BlockchainFeatures.FeeSponsorship.id)
      .map(h => h + blockchain.settings.functionalitySettings.activationWindowSize(h))
      .getOrElse(Int.MaxValue)

  def toWaves(assetFee: Long, sponsorship: Long): Long =
    if (sponsorship == 0) Long.MaxValue
    else {
      val waves = BigInt(assetFee) * FeeValidation.FeeUnit / sponsorship
      waves.bigInteger.longValueExact()
    }

  def fromWaves(wavesFee: Long, sponsorship: Long): Long =
    if (wavesFee == 0 || sponsorship == 0) 0
    else {
      val assetFee = BigInt(wavesFee) * sponsorship / FeeValidation.FeeUnit
      assetFee.bigInteger.longValueExact()
    }
}

case class NewTransactionInfo(transaction: Transaction, affected: Set[Address], applied: Boolean, spentComplexity: Long)

case class NewAssetInfo(static: AssetStaticInfo, dynamic: AssetInfo, volume: AssetVolumeInfo)

case class LeaseActionInfo(invokeId: ByteStr, dAppPublicKey: PublicKey, recipient: AddressOrAlias, amount: Long)

case class Diff(
    transactions: collection.Map[ByteStr, NewTransactionInfo] = VectorMap.empty,
    portfolios: Map[Address, Portfolio] = Map.empty,
    issuedAssets: Map[IssuedAsset, NewAssetInfo] = Map.empty,
    updatedAssets: Map[IssuedAsset, Ior[AssetInfo, AssetVolumeInfo]] = Map.empty,
    aliases: Map[Alias, Address] = Map.empty,
    orderFills: Map[ByteStr, VolumeAndFee] = Map.empty,
    leaseState: Map[ByteStr, LeaseDetails] = Map.empty,
    scripts: Map[Address, Option[AccountScriptInfo]] = Map.empty,
    assetScripts: Map[IssuedAsset, Option[AssetScriptInfo]] = Map.empty,
    accountData: Map[Address, AccountDataInfo] = Map.empty,
    sponsorship: Map[IssuedAsset, Sponsorship] = Map.empty,
    scriptsRun: Int = 0,
    scriptsComplexity: Long = 0,
    scriptResults: Map[ByteStr, InvokeScriptResult] = Map.empty
) {
  def combine(newer: Diff): Either[String, Diff] =
    combineF[Either[String, *]](newer)

  def unsafeCombine(newer: Diff): Diff =
    combineF[Id](newer)

  private def combineF[F[_]: Monad: Summarizer](newer: Diff): F[Diff] =
    Diff
      .combineF[F](portfolios, newer.portfolios)
      .map(
        portfolios =>
          Diff(
            transactions = transactions ++ newer.transactions,
            portfolios = portfolios,
            issuedAssets = issuedAssets ++ newer.issuedAssets,
            updatedAssets = updatedAssets |+| newer.updatedAssets,
            aliases = aliases ++ newer.aliases,
            orderFills = orderFills.combine(newer.orderFills),
            leaseState = leaseState ++ newer.leaseState,
            scripts = scripts ++ newer.scripts,
            assetScripts = assetScripts ++ newer.assetScripts,
            accountData = accountData.combine(newer.accountData),
            sponsorship = sponsorship.combine(newer.sponsorship),
            scriptsRun = scriptsRun + newer.scriptsRun,
            scriptResults = scriptResults.combine(newer.scriptResults),
            scriptsComplexity = scriptsComplexity + newer.scriptsComplexity
        )
      )
}

object Diff {
  val empty: Diff = Diff()

  def combine(portfolios1: Map[Address, Portfolio], portfolios2: Map[Address, Portfolio]): Either[String, Map[Address, Portfolio]] =
    combineF[Either[String, *]](portfolios1, portfolios2)

  def unsafeCombine(portfolios1: Map[Address, Portfolio], portfolios2: Map[Address, Portfolio]): Map[Address, Portfolio] =
    combineF[Id](portfolios1, portfolios2)

  private def combineF[F[_]: Monad: Summarizer](portfolios1: Map[Address, Portfolio],
                                                portfolios2: Map[Address, Portfolio]): F[Map[Address, Portfolio]] =
    sumMapF[F, Address, Portfolio](portfolios1, portfolios2, _.combineF[F](_))

  implicit class DiffExt(private val d: Diff) extends AnyVal {
    def errorMessage(txId: ByteStr): Option[InvokeScriptResult.ErrorMessage] =
      d.scriptResults.get(txId).flatMap(_.error)

    def hashString: String =
      Integer.toHexString(d.hashCode())

    def bindTransaction(blockchain: Blockchain, tx: Transaction, applied: Boolean): Diff = {
      val calledScripts = d.scriptResults.values.flatMap(inv => InvokeScriptResult.Invocation.calledAddresses(inv.invokes))
      val maybeDApp = tx match {
        case i: InvokeScriptTransaction =>
          (i.dAppAddressOrAlias: @unchecked) match {
            case alias: Alias     => d.aliases.get(alias).orElse(blockchain.resolveAlias(alias).toOption)
            case address: Address => Some(address)
          }
        case _ =>
          None
      }
      val affectedAddresses = d.portfolios.keySet ++ d.accountData.keySet ++ calledScripts ++ maybeDApp
      d.copy(transactions = VectorMap(tx.id() -> NewTransactionInfo(tx, affectedAddresses, applied, d.scriptsComplexity)))
    }
  }
}
