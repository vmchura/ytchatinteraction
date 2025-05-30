package models.repository

import models.PollOption
import models.component.{EventPollComponent, PollOptionComponent, StreamerEventComponent, UserComponent, YtStreamerComponent}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PollOptionRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  eventPollRepository: EventPollRepository
)(implicit ec: ExecutionContext) extends PollOptionComponent with EventPollComponent with StreamerEventComponent with YtStreamerComponent with UserComponent{
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  /**
   * Create a new option for a poll
   */
  def create(pollOption: PollOption): Future[PollOption] = db.run {
    (pollOptionsTable returning pollOptionsTable.map(_.optionId)
      into ((option, id) => option.copy(optionId = Some(id)))
    ) += pollOption
  }
  
  /**
   * Create multiple options for a poll in a single transaction
   */
  def createMultiple(pollId: Int, optionTexts: Seq[String], confidenceRatios: Seq[BigDecimal]): Future[Seq[PollOption]] = {
    require(optionTexts.length == confidenceRatios.length, "Number of option texts must match number of confidence ratios")
    db.run(createMultipleAction(pollId, optionTexts, confidenceRatios))
  }

  def createMultipleAction(pollId: Int, optionTexts: Seq[String], confidenceRatios: Seq[BigDecimal]): DBIO[Seq[PollOption]] = {
    require(optionTexts.length == confidenceRatios.length, "Number of option texts must match number of confidence ratios")

    val options = optionTexts.zip(confidenceRatios).map {
      case (text, ratio) => PollOption(None, pollId, text, ratio)
    }

      DBIO.sequence(
        options.map(option =>
          (pollOptionsTable returning pollOptionsTable.map(_.optionId)
            into ((opt, id) => opt.copy(optionId = Some(id)))
            ) += option
        )
      ).transactionally
  }
  
  /**
   * Get an option by ID
   */
  def getById(optionId: Int): Future[Option[PollOption]] = db.run {
    getByIdAction(optionId)
  }
  
  def getByIdAction(optionId: Int): DBIO[Option[PollOption]] =
    pollOptionsTable.filter(_.optionId === optionId).result.headOption
  
  
  /**
   * Get all options for a poll
   */
  def getByPollId(pollId: Int): Future[Seq[PollOption]] = db.run {
    getByPollIdAction(pollId)
  }

  /**
   * Action Get all options for a poll
   */
  def getByPollIdAction(pollId: Int): DBIO[Seq[PollOption]] =
    pollOptionsTable.filter(_.pollId === pollId).result

  /**
   * Update an option
   */
  def update(pollOption: PollOption): Future[Int] = {
    require(pollOption.optionId.isDefined, "Cannot update option without ID")
    
    db.run {
      pollOptionsTable
        .filter(_.optionId === pollOption.optionId.get)
        .update(pollOption)
    }
  }
  
  /**
   * Delete an option
   */
  def delete(optionId: Int): Future[Int] = db.run {
    pollOptionsTable.filter(_.optionId === optionId).delete
  }
  
  /**
   * Delete all options for a poll
   */
  def deleteByPollId(pollId: Int): Future[Int] = db.run {
    pollOptionsTable.filter(_.pollId === pollId).delete
  }
  
  // Get table query for use by other repositories
  def getTableQuery = pollOptionsTable
}
