package providers

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.silhouette.impl.providers.OAuth2Info

import scala.concurrent.Await
import scala.concurrent.duration.*

class DiscordProviderSpec extends PlaySpec {

  private val parser = new DiscordProfileParser

  "DiscordProfileParser" should {
    "parse a standard Discord user profile" in {
      val json = Json.parse("""
        {
          "id": "80351110224678912",
          "username": "nelly",
          "discriminator": "1337",
          "avatar": "8342729096ea3675442027381ff50dfe",
          "global_name": "Nelly",
          "email": "nelly@example.com",
          "verified": true
        }
      """)

      val profile = Await.result(parser.parse(json, OAuth2Info("access-token")), 5.seconds)

      profile.loginInfo.providerID mustBe DiscordProvider.ID
      profile.loginInfo.providerKey mustBe "80351110224678912"
      profile.fullName mustBe Some("Nelly")
      profile.email mustBe Some("nelly@example.com")
      profile.avatarURL mustBe Some(
        "https://cdn.discordapp.com/avatars/80351110224678912/8342729096ea3675442027381ff50dfe.png")
    }

    "fall back to username when global_name is missing" in {
      val json = Json.parse("""
        {
          "id": "123456789012345678",
          "username": "old-school-user",
          "avatar": null,
          "global_name": null
        }
      """)

      val profile = Await.result(parser.parse(json, OAuth2Info("access-token")), 5.seconds)

      profile.fullName mustBe Some("old-school-user")
      profile.avatarURL mustBe None
    }

    "use gif extension for animated avatars" in {
      val json = Json.parse("""
        {
          "id": "987654321098765432",
          "username": "animated",
          "avatar": "a_8342729096ea3675442027381ff50dfe",
          "global_name": "Animated"
        }
      """)

      val profile = Await.result(parser.parse(json, OAuth2Info("access-token")), 5.seconds)

      profile.avatarURL mustBe Some(
        "https://cdn.discordapp.com/avatars/987654321098765432/a_8342729096ea3675442027381ff50dfe.gif")
    }
  }
}
