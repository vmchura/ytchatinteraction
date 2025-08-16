package models

import java.time.Instant


case class FrontalStreamerEvent(eventId: Int,
                                channelId: String,
                                eventName: String,
                                eventDescription: String,
                                currentConfidenceAmount: Int,
                                endTime: Option[Instant] = None,
                                frontalPoll: Seq[FrontalPoll])

object FrontalStreamerEvent {
  def apply(streamerEvent: StreamerEvent): Option[FrontalStreamerEvent] = {
    for {
      eventID <- streamerEvent.eventId
      eventDescription <- streamerEvent.eventDescription
      _ <- Option.when(streamerEvent.isActive || streamerEvent.endTime.isEmpty)(true)
    } yield {
      new FrontalStreamerEvent(eventID, streamerEvent.channelId, streamerEvent.eventName, eventDescription,
        streamerEvent.currentConfidenceAmount, streamerEvent.endTime, Nil)
    }

  }
}

case class FrontalPollOption(optionId: Int,
                       optionText: String,
                       confidenceRatio: BigDecimal
                     ) {
  def inverseConfidenceRatio: BigDecimal =  PollOption.fromProbabilityWinRate(confidenceRatio)
}
case class FrontalPoll(pollId: Int,
                       pollQuestion: String,
                       options: Seq[FrontalPollOption])