package docspell.store.records

import docspell.common._
import docspell.store.impl.Implicits._
import docspell.store.impl._

import bitpeace.FileMeta
import doobie._
import doobie.implicits._

/** A preview image of an attachment. The `id` is shared with the
  * attachment, to create a 1-1 (or 0..1-1) relationship.
  */
case class RAttachmentPreview(
    id: Ident, //same as RAttachment.id
    fileId: Ident,
    name: Option[String],
    created: Timestamp
)

object RAttachmentPreview {

  val table = fr"attachment_preview"

  object Columns {
    val id      = Column("id")
    val fileId  = Column("file_id")
    val name    = Column("filename")
    val created = Column("created")

    val all = List(id, fileId, name, created)
  }

  import Columns._

  def insert(v: RAttachmentPreview): ConnectionIO[Int] =
    insertRow(table, all, fr"${v.id},${v.fileId},${v.name},${v.created}").update.run

  def findById(attachId: Ident): ConnectionIO[Option[RAttachmentPreview]] =
    selectSimple(all, table, id.is(attachId)).query[RAttachmentPreview].option

  def delete(attachId: Ident): ConnectionIO[Int] =
    deleteFrom(table, id.is(attachId)).update.run

  def findByIdAndCollective(
      attachId: Ident,
      collective: Ident
  ): ConnectionIO[Option[RAttachmentPreview]] = {
    val bId   = RAttachment.Columns.id.prefix("b")
    val aId   = Columns.id.prefix("a")
    val bItem = RAttachment.Columns.itemId.prefix("b")
    val iId   = RItem.Columns.id.prefix("i")
    val iColl = RItem.Columns.cid.prefix("i")

    val from = table ++ fr"a INNER JOIN" ++
      RAttachment.table ++ fr"b ON" ++ aId.is(bId) ++
      fr"INNER JOIN" ++ RItem.table ++ fr"i ON" ++ bItem.is(iId)

    val where = and(aId.is(attachId), bId.is(attachId), iColl.is(collective))

    selectSimple(all.map(_.prefix("a")), from, where).query[RAttachmentPreview].option
  }

  def findByItem(itemId: Ident): ConnectionIO[Vector[RAttachmentPreview]] = {
    val sId   = Columns.id.prefix("s")
    val aId   = RAttachment.Columns.id.prefix("a")
    val aItem = RAttachment.Columns.itemId.prefix("a")

    val from = table ++ fr"s INNER JOIN" ++ RAttachment.table ++ fr"a ON" ++ sId.is(aId)
    selectSimple(all.map(_.prefix("s")), from, aItem.is(itemId))
      .query[RAttachmentPreview]
      .to[Vector]
  }

  def findByItemWithMeta(
      id: Ident
  ): ConnectionIO[Vector[(RAttachmentPreview, FileMeta)]] = {
    import bitpeace.sql._

    val aId       = Columns.id.prefix("a")
    val afileMeta = fileId.prefix("a")
    val bPos      = RAttachment.Columns.position.prefix("b")
    val bId       = RAttachment.Columns.id.prefix("b")
    val bItem     = RAttachment.Columns.itemId.prefix("b")
    val mId       = RFileMeta.Columns.id.prefix("m")

    val cols = all.map(_.prefix("a")) ++ RFileMeta.Columns.all.map(_.prefix("m"))
    val from = table ++ fr"a INNER JOIN" ++
      RFileMeta.table ++ fr"m ON" ++ afileMeta.is(mId) ++ fr"INNER JOIN" ++
      RAttachment.table ++ fr"b ON" ++ aId.is(bId)
    val where = bItem.is(id)

    (selectSimple(cols, from, where) ++ orderBy(bPos.asc))
      .query[(RAttachmentPreview, FileMeta)]
      .to[Vector]
  }

}
