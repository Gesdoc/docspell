package docspell.store.records

import cats.Eq
import fs2.Stream

import docspell.common.{IdRef, _}
import docspell.store.impl.Implicits._
import docspell.store.impl._

import doobie._
import doobie.implicits._

case class ROrganization(
    oid: Ident,
    cid: Ident,
    name: String,
    street: String,
    zip: String,
    city: String,
    country: String,
    notes: Option[String],
    created: Timestamp,
    updated: Timestamp
) {}

object ROrganization {
  implicit val orgEq: Eq[ROrganization] =
    Eq.by[ROrganization, Ident](_.oid)

  val table = fr"organization"

  object Columns {
    val oid     = Column("oid")
    val cid     = Column("cid")
    val name    = Column("name")
    val street  = Column("street")
    val zip     = Column("zip")
    val city    = Column("city")
    val country = Column("country")
    val notes   = Column("notes")
    val created = Column("created")
    val updated = Column("updated")
    val all     = List(oid, cid, name, street, zip, city, country, notes, created, updated)
  }

  import Columns._

  def insert(v: ROrganization): ConnectionIO[Int] = {
    val sql = insertRow(
      table,
      all,
      fr"${v.oid},${v.cid},${v.name},${v.street},${v.zip},${v.city},${v.country},${v.notes},${v.created},${v.updated}"
    )
    sql.update.run
  }

  def update(v: ROrganization): ConnectionIO[Int] = {
    def sql(now: Timestamp) =
      updateRow(
        table,
        and(oid.is(v.oid), cid.is(v.cid)),
        commas(
          cid.setTo(v.cid),
          name.setTo(v.name),
          street.setTo(v.street),
          zip.setTo(v.zip),
          city.setTo(v.city),
          country.setTo(v.country),
          notes.setTo(v.notes),
          updated.setTo(now)
        )
      )
    for {
      now <- Timestamp.current[ConnectionIO]
      n   <- sql(now).update.run
    } yield n
  }

  def existsByName(coll: Ident, oname: String): ConnectionIO[Boolean] =
    selectCount(oid, table, and(cid.is(coll), name.is(oname)))
      .query[Int]
      .unique
      .map(_ > 0)

  def findById(id: Ident): ConnectionIO[Option[ROrganization]] = {
    val sql = selectSimple(all, table, cid.is(id))
    sql.query[ROrganization].option
  }

  def find(coll: Ident, orgName: String): ConnectionIO[Option[ROrganization]] = {
    val sql = selectSimple(all, table, and(cid.is(coll), name.is(orgName)))
    sql.query[ROrganization].option
  }

  def findLike(coll: Ident, orgName: String): ConnectionIO[Vector[IdRef]] =
    selectSimple(List(oid, name), table, and(cid.is(coll), name.lowerLike(orgName)))
      .query[IdRef]
      .to[Vector]

  def findLike(
      coll: Ident,
      contactKind: ContactKind,
      value: String
  ): ConnectionIO[Vector[IdRef]] = {
    val CC = RContact.Columns
    val q = fr"SELECT DISTINCT" ++ commas(oid.prefix("o").f, name.prefix("o").f) ++
      fr"FROM" ++ table ++ fr"o" ++
      fr"INNER JOIN" ++ RContact.table ++ fr"c ON" ++ CC.orgId
        .prefix("c")
        .is(oid.prefix("o")) ++
      fr"WHERE" ++ and(
        cid.prefix("o").is(coll),
        CC.kind.prefix("c").is(contactKind),
        CC.value.prefix("c").lowerLike(value)
      )

    q.query[IdRef].to[Vector]
  }

  def findAll(
      coll: Ident,
      order: Columns.type => Column
  ): Stream[ConnectionIO, ROrganization] = {
    val sql = selectSimple(all, table, cid.is(coll)) ++ orderBy(order(Columns).f)
    sql.query[ROrganization].stream
  }

  def findAllRef(
      coll: Ident,
      nameQ: Option[String],
      order: Columns.type => Column
  ): ConnectionIO[Vector[IdRef]] = {
    val q = Seq(cid.is(coll)) ++ (nameQ match {
      case Some(str) => Seq(name.lowerLike(s"%${str.toLowerCase}%"))
      case None      => Seq.empty
    })
    val sql = selectSimple(List(oid, name), table, and(q)) ++ orderBy(order(Columns).f)
    sql.query[IdRef].to[Vector]
  }

  def delete(id: Ident, coll: Ident): ConnectionIO[Int] =
    deleteFrom(table, and(oid.is(id), cid.is(coll))).update.run
}
