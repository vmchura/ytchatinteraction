package models
import upickle.default.ReadWriter as RW
import upickle.default._
object StarCraftModels:

  /** StarCraft races
    */
  sealed trait SCRace {
    def shortLabel: Char
  }

  case object Zerg extends SCRace {
    val shortLabel = 'Z'
  }

  case object Terran extends SCRace {
    val shortLabel = 'T'
  }

  case object Protoss extends SCRace {
    val shortLabel = 'P'
  }
  object SCRace {
    given ReadWriter[SCRace] = readwriter[String].bimap[SCRace](
      x => x.shortLabel.toString(),
      str =>
        str.headOption match {
          case Some('P') => Protoss
          case Some('T') => Terran
          case Some('Z') => Zerg
          case _         => Zerg
        }
    )
  }

  sealed trait SCMatchMode

  case object TopVsBottom extends SCMatchMode

  case object Melee extends SCMatchMode

  case object OneVsOneMode extends SCMatchMode

  case object DangerMode extends SCMatchMode

  case object UnknownMode extends SCMatchMode

  case class SCPlayer(name: String, race: SCRace, id: Int) derives ReadWriter

  /** Team in a match
    */
  case class Team(index: Int, participants: List[SCPlayer])
