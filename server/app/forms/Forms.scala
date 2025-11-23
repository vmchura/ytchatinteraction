package forms

import evolutioncomplete.WinnerShared
import play.api.data.{Form, FormError}
import play.api.data.Forms.*
import play.api.data.format.Formatter

given winnerFormatter: Formatter[WinnerShared] with
  def bind(key: String, data: Map[String, String]): Either[Seq[FormError], WinnerShared] =
    data.get(key) match {
      case Some(value) =>
        WinnerShared.values.find(_.toString == value) match {
          case Some(WinnerShared.Undefined) =>
            Left(Seq(FormError(key, s"$value is not an allowed winner")))
          case Some(valid) =>
            Right(valid)
          case None =>
            Left(Seq(FormError(key, s"$value is not a valid winner option")))
        }
      case None => Left(Seq(FormError(key, "Missing winner value")))
    }

  def unbind(key: String, value: WinnerShared): Map[String, String] =
    Map(key -> value.toString)

// Form for creating a new event
case class EventForm(
                      channelId: String,
                      eventName: String,
                      eventDescription: Option[String],
                      eventType: String,
                      startTime: String,
                      currentConfidenceAmount: Int
                    )

// Form for creating a poll with dynamic options
case class PollForm(
                     pollQuestion: String,
                     options: List[String],
                     ratios: List[BigDecimal]
                   )

// Combined form data for event creation with poll
case class EventWithPollForm(
                              event: EventForm,
                              poll: PollForm
                            )

// Form for setting winner option
case class SetWinnerForm(
                          optionId: Int
                        )

// Form for adding currency to a YouTube streamer
case class CurrencyTransferForm(
                                 channelId: String,
                                 amount: Int
                               )

// Form for transferring currency from a streamer to a user
case class StreamerToUserCurrencyForm(
                                       userId: Long,
                                       amount: Int
                                     )

// Form for creating a new tournament
case class TournamentCreateForm(
                                 name: String,
                                 contentCreatorChannelId: Option[Long] = None
                               )

// Form for voting on polls
case class VoteFormData(
                         optionId: Int,
                         confidence: Int,
                         eventId: Int,
                         pollId: Int
                       )

// Form for changing user alias
case class AliasChangeForm(
                            newAlias: String
                          )

case class CloseMatchData(winner: WinnerShared, smurfsFirstParticipant: List[String], smurfsSecondParticipant: List[String])

case class AnalyticalFileData(playerID: Int, fileHash: String)

object Forms:
  val closeMatchForm = Form(mapping(
    "winner" -> of[WinnerShared],
    "smurfsFirstParticipant" -> list(nonEmptyText),
    "smurfsSecondParticipant" -> list(nonEmptyText),
  )(CloseMatchData.apply)(nn => Some(nn.winner, nn.smurfsFirstParticipant, nn.smurfsSecondParticipant)))

  val eventForm = Form(
    mapping(
      "channelId" -> nonEmptyText,
      "eventName" -> nonEmptyText,
      "eventDescription" -> optional(text),
      "eventType" -> nonEmptyText,
      "startTime" -> nonEmptyText,
      "currentConfidenceAmount" -> number
    )(EventForm.apply)(nn => Some(nn.channelId, nn.eventName, nn.eventDescription, nn.eventType, nn.startTime, nn.currentConfidenceAmount))
  )

  val pollForm = Form(
    mapping(
      "pollQuestion" -> nonEmptyText,
      "options" -> list(nonEmptyText).verifying("At least 2 options required", _.size >= 2),
      "ratios" -> list(bigDecimal).verifying("At least 2 options required", _.size >= 2)
    )(PollForm.apply)(nn => Some(nn.pollQuestion, nn.options, nn.ratios))
  )

  val eventWithPollForm = Form(
    mapping(
      "event" -> mapping(
        "channelId" -> nonEmptyText,
        "eventName" -> nonEmptyText,
        "eventDescription" -> optional(text),
        "eventType" -> nonEmptyText,
        "startTime" -> nonEmptyText,
        "currentConfidenceAmount" -> number
      )(EventForm.apply)(nn => Some(nn.channelId, nn.eventName, nn.eventDescription, nn.eventType, nn.startTime, nn.currentConfidenceAmount)),
      "poll" -> mapping(
        "pollQuestion" -> nonEmptyText,
        "options" -> list(nonEmptyText).verifying("At least 2 options required", _.size >= 2),
        "ratios" -> list(bigDecimal).verifying("At least 2 options required", _.size >= 2)
      )(PollForm.apply)(nn => Some(nn.pollQuestion, nn.options, nn.ratios))
    )(EventWithPollForm.apply)(nn => Some(nn.event, nn.poll))
  )

  val setWinnerForm = Form(
    mapping(
      "optionId" -> number
    )(SetWinnerForm.apply)(nn => Some(nn.optionId))
  )

  val currencyTransferForm = Form(
    mapping(
      "channelId" -> nonEmptyText,
      "amount" -> number.verifying("Amount must be greater than 0", _ > 0)
    )(CurrencyTransferForm.apply)(nn => Some(nn.channelId, nn.amount))
  )

  val streamerToUserCurrencyForm = Form(
    mapping(
      "userId" -> longNumber,
      "amount" -> number.verifying("Amount must be greater than 0", _ > 0)
    )(StreamerToUserCurrencyForm.apply)(nn => Some(nn.userId, nn.amount))
  )

  val tournamentCreateForm = Form(
    mapping(
      "name" -> nonEmptyText.verifying("Name cannot be empty", _.trim.nonEmpty),
      "contentCreatorChannelId" -> optional(longNumber)
    )(TournamentCreateForm.apply)(nn => Some(nn.name, nn.contentCreatorChannelId))
  )

  val voteForm = Form(
    mapping(
      "optionId" -> number,
      "confidence" -> number(min = 1),
      "eventId" -> number,
      "pollId" -> number
    )(VoteFormData.apply)(nn => Some(nn.optionId, nn.confidence, nn.eventId, nn.pollId))
  )

  val aliasChangeForm = Form(
    mapping(
      "newAlias" -> nonEmptyText
        .verifying("Alias cannot be empty", _.trim.nonEmpty)
        .verifying("Alias must be between 1 and 50 characters", alias => alias.trim.length >= 1 && alias.trim.length <= 50)
        .verifying("Alias can only contain letters, numbers, spaces, and basic punctuation", alias =>
          alias.matches("^[a-zA-Z0-9\\s\\-_\\.]+$"))
    )(AliasChangeForm.apply)(nn => Some(nn.newAlias))
  )

  val analyticalFileDataForm = Form(
    mapping(
      "playerID" -> number(0, 16),
      "fileHash" -> nonEmptyText
    )(AnalyticalFileData.apply)(nn => Some(nn.playerID, nn.fileHash))
  )
