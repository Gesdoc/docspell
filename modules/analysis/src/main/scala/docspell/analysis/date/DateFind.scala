package docspell.analysis.date

import java.time.LocalDate

import scala.util.Try

import fs2.{Pure, Stream}

import docspell.analysis.split._
import docspell.common._

object DateFind {

  def findDates(text: String, lang: Language): Stream[Pure, NerDateLabel] =
    TextSplitter
      .splitToken(text, " \t.,\n\r/".toSet)
      .sliding(3)
      .filter(_.length == 3)
      .map(q =>
        SimpleDate
          .fromParts(q.toList, lang)
          .map(sd =>
            NerDateLabel(
              sd.toLocalDate,
              NerLabel(
                text.substring(q.head.begin, q(2).end),
                NerTag.Date,
                q.head.begin,
                q(1).end
              )
            )
          )
      )
      .collect({ case Some(d) => d })

  private case class SimpleDate(year: Int, month: Int, day: Int) {
    def toLocalDate: LocalDate =
      LocalDate.of(if (year < 100) 2000 + year else year, month, day)
  }

  private object SimpleDate {
    val p0 = (readYear >> readMonth >> readDay).map {
      case ((y, m), d) => SimpleDate(y, m, d)
    }
    val p1 = (readDay >> readMonth >> readYear).map {
      case ((d, m), y) => SimpleDate(y, m, d)
    }
    val p2 = (readMonth >> readDay >> readYear).map {
      case ((m, d), y) => SimpleDate(y, m, d)
    }

    // ymd ✔, ydm, dmy ✔, dym, myd, mdy ✔
    def fromParts(parts: List[Word], lang: Language): Option[SimpleDate] = {
      val p = lang match {
        case Language.English => p2.or(p0).or(p1)
        case Language.German  => p1.or(p0).or(p2)
        case Language.French  => p1.or(p0).or(p2)
      }
      p.read(parts).toOption
    }

    def readYear: Reader[Int] =
      Reader.readFirst(w =>
        w.value.length match {
          case 2 => Try(w.value.toInt).filter(n => n >= 0).toOption
          case 4 => Try(w.value.toInt).filter(n => n > 1000).toOption
          case _ => None
        }
      )

    def readMonth: Reader[Int] =
      Reader.readFirst(w =>
        Some(months.indexWhere(_.contains(w.value))).filter(_ > 0).map(_ + 1)
      )

    def readDay: Reader[Int] =
      Reader.readFirst(w => Try(w.value.toInt).filter(n => n > 0 && n <= 31).toOption)

    case class Reader[A](read: List[Word] => Result[A]) {
      def >>[B](next: Reader[B]): Reader[(A, B)] =
        Reader(read.andThen(_.next(next)))

      def map[B](f: A => B): Reader[B] =
        Reader(read.andThen(_.map(f)))

      def or(other: Reader[A]): Reader[A] =
        Reader(words =>
          read(words) match {
            case Result.Failure           => other.read(words)
            case s @ Result.Success(_, _) => s
          }
        )
    }

    object Reader {
      def fail[A]: Reader[A] =
        Reader(_ => Result.Failure)

      def readFirst[A](f: Word => Option[A]): Reader[A] =
        Reader({
          case Nil => Result.Failure
          case a :: as =>
            f(a).map(value => Result.Success(value, as)).getOrElse(Result.Failure)
        })
    }

    sealed trait Result[+A] {
      def toOption: Option[A]
      def map[B](f: A => B): Result[B]
      def next[B](r: Reader[B]): Result[(A, B)]
    }

    object Result {
      final case class Success[A](value: A, rest: List[Word]) extends Result[A] {
        val toOption                     = Some(value)
        def map[B](f: A => B): Result[B] = Success(f(value), rest)
        def next[B](r: Reader[B]): Result[(A, B)] =
          r.read(rest).map(b => (value, b))
      }
      final case object Failure extends Result[Nothing] {
        val toOption                                    = None
        def map[B](f: Nothing => B): Result[B]          = this
        def next[B](r: Reader[B]): Result[(Nothing, B)] = this
      }
    }

    private val months = List(
      List("jan", "january", "januar", "01"),
      List("feb", "february", "februar", "02"),
      List("mar", "march", "märz", "marz", "03"),
      List("apr", "april", "04"),
      List("may", "mai", "05"),
      List("jun", "june", "juni", "06"),
      List("jul", "july", "juli", "07"),
      List("aug", "august", "08"),
      List("sep", "september", "09"),
      List("oct", "october", "oktober", "10"),
      List("nov", "november", "11"),
      List("dec", "december", "dezember", "12")
    )
  }
}
