package forms

import play.api.data.Form
import play.api.data.Forms.*

// Form for creating a new event
case class EventForm(
                      channelId: String,
                      eventName: String,
                      eventDescription: Option[String],
                      eventType: String,
                      startTime: String
                    )

// Form for creating a poll with dynamic options
case class PollForm(
                     pollQuestion: String,
                     options: List[String]
                   )

// Combined form data for event creation with poll
case class EventWithPollForm(
                              event: EventForm,
                              poll: PollForm
                            )
object Forms:
  val eventForm = Form(
    mapping(
      "channelId" -> nonEmptyText,
      "eventName" -> nonEmptyText,
      "eventDescription" -> optional(text),
      "eventType" -> nonEmptyText,
      "startTime" -> nonEmptyText
    )(EventForm.apply)(nn => Some(nn.channelId, nn.eventName, nn.eventDescription, nn.eventType, nn.startTime))
  )

  val pollForm = Form(
    mapping(
      "pollQuestion" -> nonEmptyText,
      "options" -> list(nonEmptyText).verifying("At least 2 options required", _.size >= 2)
    )(PollForm.apply)(nn => Some(nn.pollQuestion, nn.options))
  )

  val eventWithPollForm = Form(
    mapping(
      "event" -> mapping(
        "channelId" -> nonEmptyText,
        "eventName" -> nonEmptyText,
        "eventDescription" -> optional(text),
        "eventType" -> nonEmptyText,
        "startTime" -> nonEmptyText
      )(EventForm.apply)(nn => Some(nn.channelId, nn.eventName, nn.eventDescription, nn.eventType, nn.startTime)),
      "poll" -> mapping(
        "pollQuestion" -> nonEmptyText,
        "options" -> list(nonEmptyText).verifying("At least 2 options required", _.size >= 2)
      )(PollForm.apply)(nn => Some(nn.pollQuestion, nn.options))
    )(EventWithPollForm.apply)(nn => Some(nn.event, nn.poll))
  )
