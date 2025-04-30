package forms

import play.api.data.Form
import play.api.data.Forms.*

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

object Forms:
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
