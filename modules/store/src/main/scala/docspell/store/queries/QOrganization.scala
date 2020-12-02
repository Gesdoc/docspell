package docspell.store.queries

import cats.implicits._
import fs2._

import docspell.common._
import docspell.store.impl.Column
import docspell.store.impl.Implicits._
import docspell.store.records.ROrganization.{Columns => OC}
import docspell.store.records.RPerson.{Columns => PC}
import docspell.store.records._
import docspell.store.{AddResult, Store}

import doobie._
import doobie.implicits._

object QOrganization {

  def findOrgAndContact(
      coll: Ident,
      query: Option[String],
      order: OC.type => Column
  ): Stream[ConnectionIO, (ROrganization, Vector[RContact])] = {
    val oColl  = ROrganization.Columns.cid.prefix("o")
    val oName  = ROrganization.Columns.name.prefix("o")
    val oNotes = ROrganization.Columns.notes.prefix("o")
    val oId    = ROrganization.Columns.oid.prefix("o")
    val cOrg   = RContact.Columns.orgId.prefix("c")
    val cVal   = RContact.Columns.value.prefix("c")

    val cols = ROrganization.Columns.all.map(_.prefix("o")) ++ RContact.Columns.all
      .map(_.prefix("c"))
    val from = ROrganization.table ++ fr"o LEFT JOIN" ++
      RContact.table ++ fr"c ON" ++ cOrg.is(oId)

    val q = Seq(oColl.is(coll)) ++ (query match {
      case Some(str) =>
        val v = s"%$str%"
        Seq(or(cVal.lowerLike(v), oName.lowerLike(v), oNotes.lowerLike(v)))
      case None =>
        Seq.empty
    })

    (selectSimple(cols, from, and(q)) ++ orderBy(order(OC).prefix("o").f))
      .query[(ROrganization, Option[RContact])]
      .stream
      .groupAdjacentBy(_._1)
      .map({ case (ro, chunk) =>
        val cs = chunk.toVector.flatMap(_._2)
        (ro, cs)
      })
  }

  def getOrgAndContact(
      coll: Ident,
      orgId: Ident
  ): ConnectionIO[Option[(ROrganization, Vector[RContact])]] = {
    val oColl = ROrganization.Columns.cid.prefix("o")
    val oId   = ROrganization.Columns.oid.prefix("o")
    val cOrg  = RContact.Columns.orgId.prefix("c")

    val cols = ROrganization.Columns.all.map(_.prefix("o")) ++ RContact.Columns.all
      .map(_.prefix("c"))
    val from = ROrganization.table ++ fr"o LEFT JOIN" ++
      RContact.table ++ fr"c ON" ++ cOrg.is(oId)

    val q = and(oColl.is(coll), oId.is(orgId))

    selectSimple(cols, from, q)
      .query[(ROrganization, Option[RContact])]
      .stream
      .groupAdjacentBy(_._1)
      .map({ case (ro, chunk) =>
        val cs = chunk.toVector.flatMap(_._2)
        (ro, cs)
      })
      .compile
      .last
  }

  def findPersonAndContact(
      coll: Ident,
      query: Option[String],
      order: PC.type => Column
  ): Stream[ConnectionIO, (RPerson, Option[ROrganization], Vector[RContact])] = {
    val pColl  = PC.cid.prefix("p")
    val pName  = RPerson.Columns.name.prefix("p")
    val pNotes = RPerson.Columns.notes.prefix("p")
    val pId    = RPerson.Columns.pid.prefix("p")
    val cPers  = RContact.Columns.personId.prefix("c")
    val cVal   = RContact.Columns.value.prefix("c")
    val oId    = ROrganization.Columns.oid.prefix("o")
    val pOid   = RPerson.Columns.oid.prefix("p")

    val cols = RPerson.Columns.all.map(_.prefix("p")) ++
      ROrganization.Columns.all.map(_.prefix("o")) ++
      RContact.Columns.all.map(_.prefix("c"))
    val from = RPerson.table ++ fr"p LEFT JOIN" ++
      ROrganization.table ++ fr"o ON" ++ pOid.is(oId) ++ fr"LEFT JOIN" ++
      RContact.table ++ fr"c ON" ++ cPers.is(pId)

    val q = Seq(pColl.is(coll)) ++ (query match {
      case Some(str) =>
        val v = s"%${str.toLowerCase}%"
        Seq(or(cVal.lowerLike(v), pName.lowerLike(v), pNotes.lowerLike(v)))
      case None =>
        Seq.empty
    })

    (selectSimple(cols, from, and(q)) ++ orderBy(order(PC).prefix("p").f))
      .query[(RPerson, Option[ROrganization], Option[RContact])]
      .stream
      .groupAdjacentBy(_._1)
      .map({ case (rp, chunk) =>
        val cs = chunk.toVector.flatMap(_._3)
        val ro = chunk.map(_._2).head.flatten
        (rp, ro, cs)
      })
  }

  def getPersonAndContact(
      coll: Ident,
      persId: Ident
  ): ConnectionIO[Option[(RPerson, Option[ROrganization], Vector[RContact])]] = {
    val pColl = PC.cid.prefix("p")
    val pId   = RPerson.Columns.pid.prefix("p")
    val cPers = RContact.Columns.personId.prefix("c")
    val oId   = ROrganization.Columns.oid.prefix("o")
    val pOid  = RPerson.Columns.oid.prefix("p")

    val cols = RPerson.Columns.all.map(_.prefix("p")) ++
      ROrganization.Columns.all.map(_.prefix("o")) ++
      RContact.Columns.all.map(_.prefix("c"))
    val from = RPerson.table ++ fr"p LEFT JOIN" ++
      ROrganization.table ++ fr"o ON" ++ pOid.is(oId) ++ fr"LEFT JOIN" ++
      RContact.table ++ fr"c ON" ++ cPers.is(pId)

    val q = and(pColl.is(coll), pId.is(persId))

    selectSimple(cols, from, q)
      .query[(RPerson, Option[ROrganization], Option[RContact])]
      .stream
      .groupAdjacentBy(_._1)
      .map({ case (rp, chunk) =>
        val cs = chunk.toVector.flatMap(_._3)
        val ro = chunk.map(_._2).head.flatten
        (rp, ro, cs)
      })
      .compile
      .last
  }

  def findPersonByContact(
      coll: Ident,
      value: String,
      ck: Option[ContactKind],
      concerning: Option[Boolean]
  ): Stream[ConnectionIO, RPerson] = {
    val pColl = PC.cid.prefix("p")
    val pConc = PC.concerning.prefix("p")
    val pId   = PC.pid.prefix("p")
    val cPers = RContact.Columns.personId.prefix("c")
    val cVal  = RContact.Columns.value.prefix("c")
    val cKind = RContact.Columns.kind.prefix("c")

    val from = RPerson.table ++ fr"p INNER JOIN" ++
      RContact.table ++ fr"c ON" ++ cPers.is(pId)
    val q = Seq(
      cVal.lowerLike(s"%${value.toLowerCase}%"),
      pColl.is(coll)
    ) ++ concerning.map(pConc.is(_)).toSeq ++ ck.map(cKind.is(_)).toSeq

    selectDistinct(PC.all.map(_.prefix("p")), from, and(q))
      .query[RPerson]
      .stream
  }

  def addOrg[F[_]](
      org: ROrganization,
      contacts: Seq[RContact],
      cid: Ident
  ): Store[F] => F[AddResult] = {
    val insert = for {
      n  <- ROrganization.insert(org)
      cs <- contacts.toList.traverse(RContact.insert)
    } yield n + cs.sum

    val exists = ROrganization.existsByName(cid, org.name)

    store => store.add(insert, exists)
  }

  def addPerson[F[_]](
      person: RPerson,
      contacts: Seq[RContact],
      cid: Ident
  ): Store[F] => F[AddResult] = {
    val insert = for {
      n  <- RPerson.insert(person)
      cs <- contacts.toList.traverse(RContact.insert)
    } yield n + cs.sum

    val exists = RPerson.existsByName(cid, person.name)

    store => store.add(insert, exists)
  }

  def updateOrg[F[_]](
      org: ROrganization,
      contacts: Seq[RContact],
      cid: Ident
  ): Store[F] => F[AddResult] = {
    val insert = for {
      n  <- ROrganization.update(org)
      d  <- RContact.deleteOrg(org.oid)
      cs <- contacts.toList.traverse(RContact.insert)
    } yield n + cs.sum + d

    val exists = ROrganization.existsByName(cid, org.name)

    store => store.add(insert, exists)
  }

  def updatePerson[F[_]](
      person: RPerson,
      contacts: Seq[RContact],
      cid: Ident
  ): Store[F] => F[AddResult] = {
    val insert = for {
      n  <- RPerson.update(person)
      d  <- RContact.deletePerson(person.pid)
      cs <- contacts.toList.traverse(RContact.insert)
    } yield n + cs.sum + d

    val exists = RPerson.existsByName(cid, person.name)

    store => store.add(insert, exists)
  }

  def deleteOrg(orgId: Ident, collective: Ident): ConnectionIO[Int] =
    for {
      n0 <- RItem.removeCorrOrg(collective, orgId)
      n1 <- RContact.deleteOrg(orgId)
      n2 <- ROrganization.delete(orgId, collective)
    } yield n0 + n1 + n2

  def deletePerson(personId: Ident, collective: Ident): ConnectionIO[Int] =
    for {
      n0 <- RItem.removeCorrPerson(collective, personId)
      n1 <- RItem.removeConcPerson(collective, personId)
      n2 <- RContact.deletePerson(personId)
      n3 <- RPerson.delete(personId, collective)
    } yield n0 + n1 + n2 + n3
}
