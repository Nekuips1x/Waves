package com.wavesplatform.transaction

import com.wavesplatform.account.{AddressScheme, KeyPair, PrivateKey, PublicKey}
import com.wavesplatform.crypto
import com.wavesplatform.lang.ValidationError
import com.wavesplatform.protobuf.transaction.PBTransactions
import com.wavesplatform.state._
import com.wavesplatform.transaction.serialization.impl.DataTxSerializer
import com.wavesplatform.transaction.validation.TxValidator
import com.wavesplatform.transaction.validation.impl.DataTxValidator
import monix.eval.Coeval
import play.api.libs.json._

import scala.util.Try

case class DataTransaction(
    version: TxVersion,
    sender: PublicKey,
    data: Seq[DataEntry[_]],
    fee: TxPositiveAmount,
    timestamp: TxTimestamp,
    proofs: Proofs,
    chainId: Byte
) extends ProvenTransaction
    with VersionedTransaction
    with TxWithFee.InWaves
    with FastHashId
    with LegacyPBSwitch.V2 {

  //noinspection TypeAnnotation
  override val builder = DataTransaction

  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(builder.serializer.bodyBytes(this))
  override val bytes: Coeval[Array[Byte]]     = Coeval.evalOnce(builder.serializer.toBytes(this))
  override val json: Coeval[JsObject]         = Coeval.eval(builder.serializer.toJson(this))

  private[wavesplatform] lazy val protoDataPayload = PBTransactions.protobuf(this).getTransaction.getDataTransaction.toByteArray
}

object DataTransaction extends TransactionParser {
  type TransactionT = DataTransaction

  val MaxBytes: Int      = 150 * 1024 // uses for RIDE CONST_STRING and CONST_BYTESTR
  val MaxProtoBytes: Int = 165890 // uses for RIDE CONST_BYTESTR
  val MaxEntryCount: Int = 100

  override val typeId: TxType                    = 12: Byte
  override val supportedVersions: Set[TxVersion] = Set(1, 2)

  implicit val validator: TxValidator[DataTransaction] = DataTxValidator

  implicit def sign(tx: DataTransaction, privateKey: PrivateKey): DataTransaction =
    tx.copy(proofs = Proofs(crypto.sign(privateKey, tx.bodyBytes())))

  val serializer = DataTxSerializer

  override def parseBytes(bytes: Array[TxVersion]): Try[DataTransaction] =
    serializer.parseBytes(bytes)

  def create(
      version: TxVersion,
      sender: PublicKey,
      data: Seq[DataEntry[_]],
      fee: Long,
      timestamp: TxTimestamp,
      proofs: Proofs,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, DataTransaction] =
    for {
      fee <- TxPositiveAmount(fee)(TxValidationError.InsufficientFee)
      tx  <- DataTransaction(version, sender, data, fee, timestamp, proofs, chainId).validatedEither
    } yield tx

  def signed(
      version: TxVersion,
      sender: PublicKey,
      data: Seq[DataEntry[_]],
      fee: Long,
      timestamp: TxTimestamp,
      signer: PrivateKey,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, DataTransaction] =
    create(version, sender, data, fee, timestamp, Proofs.empty, chainId).map(_.signWith(signer))

  def selfSigned(
      version: TxVersion,
      sender: KeyPair,
      data: Seq[DataEntry[_]],
      fee: Long,
      timestamp: TxTimestamp,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, DataTransaction] =
    signed(version, sender.publicKey, data, fee, timestamp, sender.privateKey, chainId)
}
