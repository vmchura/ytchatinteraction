package replayuploader

import evolutioncomplete._
import sttp.model.Uri
import sttp.client4._
import upickle.default.{read, write}

import java.util.UUID

trait MetaSessionShared[SS <: TUploadStateShared[SS]] {
  def fetchStateUri: Uri
  def updateStateUri: Uri
  def removeFileUri(fileUUID: UUID): Uri
  def default(): SS
  def error(): SS
  def readJson(jsonResponse: String): SS
  def writeJson(state: SS): String
}
case class TournamentMetaSession(matchId: Long, tournamentId: Long)
    extends MetaSessionShared[TournamentUploadStateShared]:
  def fetchStateUri: Uri = uri"/fetchstate/$matchId/$tournamentId"
  def updateStateUri: Uri = uri"/updatestate"
  def removeFileUri(fileUUID: UUID): Uri =
    uri"/remove/${tournamentId}/${matchId}/${fileUUID.toString}"
  def default(): TournamentUploadStateShared =
    TournamentUploadStateShared.default()
  def error(): TournamentUploadStateShared =
    TournamentUploadStateShared.errorOne()
  def readJson(jsonResponse: String): TournamentUploadStateShared =
    read[TournamentUploadStateShared](jsonResponse)
  def writeJson(state: TournamentUploadStateShared): String = write(state)

case class CasualMatchMetaSession(casualMatchId: Long)
    extends MetaSessionShared[CasualMatchStateShared]:
  def fetchStateUri: Uri = uri"/casual/fetchstate/$casualMatchId"
  def updateStateUri: Uri = uri"/casual/updatestate"
  def removeFileUri(fileUUID: UUID): Uri =
    uri"/casual/remove/${casualMatchId}/${fileUUID.toString}"
  def default(): CasualMatchStateShared =
    CasualMatchStateShared.default()
  def error(): CasualMatchStateShared =
    CasualMatchStateShared.errorOne()
  def readJson(jsonResponse: String): CasualMatchStateShared =
    read[CasualMatchStateShared](jsonResponse)
  def writeJson(state: CasualMatchStateShared): String = write(state)
