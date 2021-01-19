package docspell.joex.learn

import java.nio.file.Path
import cats.implicits._
import bitpeace.RangeDef
import cats.data.OptionT
import cats.effect._
import docspell.store.Store
import docspell.analysis.classifier.{ClassifierModel, TextClassifier}
import docspell.common._
import docspell.store.records.RClassifierModel

object Classify {

  def apply[F[_]: Sync: ContextShift](
      blocker: Blocker,
      logger: Logger[F],
      workingDir: Path,
      store: Store[F],
      classifier: TextClassifier[F],
      coll: Ident,
      text: String
  )(cname: ClassifierName): F[Option[String]] =
    (for {
      _     <- OptionT.liftF(logger.info(s"Guessing label for ${cname.name} …"))
      model <- OptionT(store.transact(RClassifierModel.findByName(coll, cname.name)))
      modelData =
        store.bitpeace
          .get(model.fileId.id)
          .unNoneTerminate
          .through(store.bitpeace.fetchData2(RangeDef.all))
      cls <- OptionT(File.withTempDir(workingDir, "classify").use { dir =>
        val modelFile = dir.resolve("model.ser.gz")
        modelData
          .through(fs2.io.file.writeAll(modelFile, blocker))
          .compile
          .drain
          .flatMap(_ => classifier.classify(logger, ClassifierModel(modelFile), text))
      }).filter(_ != LearnClassifierTask.noClass)
      _ <- OptionT.liftF(logger.debug(s"Guessed: ${cls}"))
    } yield cls).value

}
