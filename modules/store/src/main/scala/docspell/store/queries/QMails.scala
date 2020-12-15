package docspell.store.queries

import cats.data.OptionT

import docspell.common._
import docspell.store.impl.Column
import docspell.store.impl.Implicits._
import docspell.store.records._

import doobie._
import doobie.implicits._

object QMails {

  def delete(coll: Ident, mailId: Ident): ConnectionIO[Int] =
    (for {
      m <- OptionT(findMail(coll, mailId))
      k <- OptionT.liftF(RSentMailItem.deleteMail(mailId))
      n <- OptionT.liftF(RSentMail.delete(m._1.id))
    } yield k + n).getOrElse(0)

  def findMail(coll: Ident, mailId: Ident): ConnectionIO[Option[(RSentMail, Ident)]] = {
    val iColl = RItem.Columns.cid.prefix("i")
    val mId   = RSentMail.Columns.id.prefix("m")

    val (cols, from) = partialFind

    val cond = Seq(mId.is(mailId), iColl.is(coll))

    selectSimple(cols, from, and(cond)).query[(RSentMail, Ident)].option
  }

  def findMails(coll: Ident, itemId: Ident): ConnectionIO[Vector[(RSentMail, Ident)]] = {
    val iColl    = RItem.Columns.cid.prefix("i")
    val tItem    = RSentMailItem.Columns.itemId.prefix("t")
    val mCreated = RSentMail.Columns.created.prefix("m")

    val (cols, from) = partialFind

    val cond = Seq(tItem.is(itemId), iColl.is(coll))

    (selectSimple(cols, from, and(cond)) ++ orderBy(mCreated.f) ++ fr"DESC")
      .query[(RSentMail, Ident)]
      .to[Vector]
  }

  private def partialFind: (Seq[Column], Fragment) = {
    val user  = RUser.as("u")
    val iId   = RItem.Columns.id.prefix("i")
    val tItem = RSentMailItem.Columns.itemId.prefix("t")
    val tMail = RSentMailItem.Columns.sentMailId.prefix("t")
    val mId   = RSentMail.Columns.id.prefix("m")
    val mUser = RSentMail.Columns.uid.prefix("m")

    val cols = RSentMail.Columns.all.map(_.prefix("m")) :+ user.login.column
    val from = RSentMail.table ++ fr"m INNER JOIN" ++
      RSentMailItem.table ++ fr"t ON" ++ tMail.is(mId) ++
      fr"INNER JOIN" ++ RItem.table ++ fr"i ON" ++ tItem.is(iId) ++
      fr"INNER JOIN" ++ Fragment.const(user.tableName) ++ fr"u ON" ++ user.uid.column.is(
        mUser
      )

    (cols, from)
  }

}
