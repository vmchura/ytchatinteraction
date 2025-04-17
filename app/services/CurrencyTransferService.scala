package services

import javax.inject.{Inject, Singleton}
import models.*
import models.repository.{UserRepository, UserStreamerStateRepository, YtStreamerRepository}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

/**
 * Service responsible for handling currency transfers between users and streamers.
 * Ensures all balance changes are performed within transactions.
 */
@Singleton
class CurrencyTransferService @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  userRepository: UserRepository,
  ytStreamerRepository: YtStreamerRepository,
  userStreamerStateRepository: UserStreamerStateRepository
)(implicit ec: ExecutionContext) {
  
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import dbConfig.profile.api._

  def transferCurrencyUserStream(userID: Long, channelID: String, amount: Int): DBIO[Boolean] = {
    (for{
      relationExists <- userStreamerStateRepository.existsAction(userID, channelID)
      _ <- if (!relationExists) userStreamerStateRepository.createAction(userID, channelID, 0) else DBIO.successful(())
      userBalanceOption <- getUserBalance(userID, channelID)
      userBalance <- userBalanceOption.fold(DBIO.failed(new IllegalStateException("No balance of the user found")))(DBIO.successful)
      rows_update_event <- if(userBalance + amount < 0) DBIO.failed(new IllegalStateException("Negative balance for user"))
      else userStreamerStateRepository.updateBalanceAction(userID, channelID, userBalance + amount)
      streamerBalanceOption <- getStreamerBalance(channelID)
      streamerBalance <- streamerBalanceOption.fold(DBIO.failed(new IllegalStateException("No balance of the streamer found")))(DBIO.successful)
      rows_update_streamer <- if(streamerBalance - amount < 0) DBIO.failed(new IllegalStateException("Negative balance for streamer"))
      else ytStreamerRepository.updateBalanceAction(channelID, streamerBalance - amount)
      operation_complete <- if(rows_update_event == 1 && rows_update_streamer == 1) DBIO.successful(true) else DBIO.failed(new IllegalStateException("Not updated done"))
    }yield {
      operation_complete
    }).transactionally
  }
  /**
   * Transfers currency from a user to a streamer channel.
   *
   * @param fromUserId The ID of the user sending currency
   * @param toChannelId The channel ID of the streamer receiving currency
   * @param amount The amount of currency to transfer
   * @return Future containing a tuple of (userNewBalance, streamerNewBalance) if successful, 
   *         or a failed future with an appropriate error message
   */
  def sendCurrencyFromUserToStreamer(
    fromUserId: Long, 
    toChannelId: String, 
    amount: Int
  ): Future[Boolean] = {
    
    db.run(transferCurrencyUserStream(fromUserId, toChannelId, -amount))
  }
  
  /**
   * Transfers currency from a streamer channel to a user.
   *
   * @param fromChannelId The channel ID of the streamer sending currency
   * @param toUserId The ID of the user receiving currency
   * @param amount The amount of currency to transfer
   * @return Future containing a tuple of (streamerNewBalance, userNewBalance) if successful, 
   *         or a failed future with an appropriate error message
   */
  def sendCurrencyFromStreamerToUser(
    fromChannelId: String, 
    toUserId: Long, 
    amount: Int
  ): Future[Boolean] = {

    db.run(transferCurrencyUserStream(toUserId, fromChannelId, amount))
  }

  // Helper methods to get balances as DBIO actions (for use within transactions)
  
  private def getUserBalance(userId: Long, streamerChannelId: String): DBIO[Option[Int]] = {
    userStreamerStateRepository.getUserStreamerBalanceAction(userId, streamerChannelId)
  }
  
  private def getStreamerBalance(channelId: String): DBIO[Option[Int]] = {
    ytStreamerRepository.getStreamerBalanceAction(channelId)
  }
}