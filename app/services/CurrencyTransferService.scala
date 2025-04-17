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
    
    // First check all preconditions
    val action = for {
      // Verify the user exists
      userExists <- userRepository.existsAction(fromUserId)
      _ <- if (!userExists) DBIO.failed(new Exception(s"User with ID $fromUserId not found"))
           else DBIO.successful(())
           
      // Verify the streamer exists
      streamerOpt <- ytStreamerRepository.getTableQuery.filter(_.channelId === toChannelId).result.headOption
      _ <- if (streamerOpt.isEmpty) DBIO.failed(new Exception(s"Streamer with channel ID $toChannelId not found"))
           else DBIO.successful(())
      
      // Get the current relationship or create it if it doesn't exist
      relationExists <- userStreamerStateRepository.existsAction(fromUserId, toChannelId)
      _ <- if (!relationExists) {
             userStreamerStateRepository.createAction(fromUserId, toChannelId, 0)
           } else DBIO.successful(())
      
      // Get current balances
      userBalance <- getUserBalance(fromUserId, toChannelId)
      
      // Ensure user has enough balance
      _ <- if (userBalance.getOrElse(0) < amount)
             DBIO.failed(new Exception(s"Insufficient balance: User has $userBalance, trying to send $amount"))
           else 
             DBIO.successful(())
      
      // Update balances atomically within the transaction
      _ <- userStreamerStateRepository.incrementBalanceAction(fromUserId, toChannelId, -amount)
      _ <- ytStreamerRepository.incrementBalanceAction(toChannelId, amount)
      
      // Get the new balances to return
    } yield true
    
    // Run the entire action as a transaction
    db.run(action.transactionally)
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
    
    // First check all preconditions
    val action = for {
      // Verify the streamer exists
      streamerOpt <- ytStreamerRepository.getTableQuery.filter(_.channelId === fromChannelId).result.headOption
      _ <- if (streamerOpt.isEmpty) DBIO.failed(new Exception(s"Streamer with channel ID $fromChannelId not found"))
           else DBIO.successful(())
      
      // Verify the user exists
      userExists <- userRepository.existsAction(toUserId)
      _ <- if (!userExists) DBIO.failed(new Exception(s"User with ID $toUserId not found"))
           else DBIO.successful(())
           
      // Get the current relationship or create it if it doesn't exist
      relationExists <- userStreamerStateRepository.existsAction(toUserId, fromChannelId)
      _ <- if (!relationExists) {
             userStreamerStateRepository.createAction(toUserId, fromChannelId, 0)
           } else DBIO.successful(())
      
      // Get current streamer balance
      streamerBalance <- getStreamerBalance(fromChannelId)
      
      // Ensure streamer has enough balance
      _ <- streamerBalance.fold(DBIO.failed(new IllegalStateException("Streamer with Null balance")))(streamerBalanceInt => if(streamerBalanceInt < amount) {
             DBIO.failed(new Exception(s"Insufficient balance: Streamer has $streamerBalance, trying to send $amount"))}
                else
             DBIO.successful(()))
      
      // Update balances atomically within the transaction
      _ <- ytStreamerRepository.incrementBalanceAction(fromChannelId, -amount)
      _ <- userStreamerStateRepository.incrementBalanceAction(toUserId, fromChannelId, amount)

    } yield true
    
    // Run the entire action as a transaction
    db.run(action.transactionally)
  }

  // Helper methods to get balances as DBIO actions (for use within transactions)
  
  private def getUserBalance(userId: Long, streamerChannelId: String): DBIO[Option[Int]] = {
    userStreamerStateRepository.getUserStreamerBalanceAction(userId, streamerChannelId)
  }
  
  private def getStreamerBalance(channelId: String): DBIO[Option[Int]] = {
    ytStreamerRepository.getStreamerBalanceAction(channelId)
  }
}