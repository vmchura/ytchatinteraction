package models

import models.StarCraftModels.SCRace

import java.time.Instant

// Case class
case class AnalyticalResult(
                             id: Long = 0L,
                             userId: Long,
                             matchId: Option[Long],
                             userRace: SCRace,
                             rivalRace: SCRace,
                             originalFileName: String,
                             analysisStartedAt: Instant,
                             analysisFinishedAt: Option[Instant],
                             algorithmVersion: Option[String],
                             result: Option[Boolean],
                             casualMatchId: Option[Long]
                           )

trait GenericAnalyticalResult:
  def id: Long
  def userId: Long
  def userRace: SCRace
  def rivalRace: SCRace
  def originalFileName: String
  def analysisStartedAt: Instant
  def analysisFinishedAt: Option[Instant]
  def algorithmVersion: Option[String]
  def result: Option[Boolean]
  def toAnalyticalResult: AnalyticalResult
  def withResults(result: Boolean, algorithmVersion: String, analysisFinishedAt: Instant):  GenericAnalyticalResult
  def withAnalysisFinishedAt(analysisFinishedAt: Instant):  GenericAnalyticalResult


case class TournamentAnalyticalResult(id: Long = 0L,
                                      userId: Long,
                                      matchId: Long,
                                      userRace: SCRace,
                                      rivalRace: SCRace,
                                      originalFileName: String,
                                      analysisStartedAt: Instant,
                                      analysisFinishedAt: Option[Instant],
                                      algorithmVersion: Option[String],
                                      result: Option[Boolean]) extends GenericAnalyticalResult:
  override def toAnalyticalResult: AnalyticalResult = AnalyticalResult(id, userId, Some(matchId), userRace, rivalRace, originalFileName, analysisStartedAt, analysisFinishedAt, algorithmVersion, result, casualMatchId = None)

  override def withResults(result: Boolean, algorithmVersion: String, analysisFinishedAt: Instant): GenericAnalyticalResult = copy(result=Some(result), algorithmVersion=Some(algorithmVersion), analysisFinishedAt=Some(analysisFinishedAt))

  override def withAnalysisFinishedAt(analysisFinishedAt: Instant): GenericAnalyticalResult = copy(analysisFinishedAt=Some(analysisFinishedAt))


case class CasualMatchAnalyticalResult(id: Long = 0L,
                                       userId: Long,
                                       userRace: SCRace,
                                       rivalRace: SCRace,
                                       originalFileName: String,
                                       analysisStartedAt: Instant,
                                       analysisFinishedAt: Option[Instant],
                                       algorithmVersion: Option[String],
                                       result: Option[Boolean],
                                       casualMatchId: Long) extends GenericAnalyticalResult:
  override def toAnalyticalResult: AnalyticalResult = AnalyticalResult(id, userId, None, userRace, rivalRace, originalFileName, analysisStartedAt, analysisFinishedAt, algorithmVersion, result, Some(casualMatchId))

  override def withAnalysisFinishedAt(analysisFinishedAt: Instant): GenericAnalyticalResult = copy(analysisFinishedAt=Some(analysisFinishedAt))

  override def withResults(result: Boolean, algorithmVersion: String, analysisFinishedAt: Instant): GenericAnalyticalResult = copy(
    result=Some(result),
    algorithmVersion=Some(algorithmVersion),
    analysisFinishedAt=Some(analysisFinishedAt))


object AnalyticalResult:
  given Conversion[AnalyticalResult, TournamentAnalyticalResult] = {
    case AnalyticalResult(id, userId, Some(matchId), userRace, rivalRace, originalFileName, analysisStartedAt, analysisFinishedAt, algorithmVersion, result, None) =>
      TournamentAnalyticalResult(id, userId, matchId, userRace, rivalRace, originalFileName, analysisStartedAt, analysisFinishedAt, algorithmVersion, result)
  }

  given Conversion[AnalyticalResult, CasualMatchAnalyticalResult] = {
    case AnalyticalResult(id, userId, None, userRace, rivalRace, originalFileName, analysisStartedAt, analysisFinishedAt, algorithmVersion, result, Some(casualMatchId)) =>
      CasualMatchAnalyticalResult(id, userId, userRace, rivalRace, originalFileName, analysisStartedAt, analysisFinishedAt, algorithmVersion, result, casualMatchId)
  }

case class AnalyticalResultView(userAlias: String,
                                userRace: SCRace,
                                rivalRace: SCRace,
                                originalFileName: String,
                                analysisStartedAt: Instant,
                                analysisFinishedAt: Option[Instant],
                                algorithmVersion: Option[String],
                                result: Option[Boolean])