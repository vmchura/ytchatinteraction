package controllers

import models.repository.UserAvailabilityRepository
import models.{User, UserTimezone}
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.*
import play.api.test.*
import play.api.test.Helpers.*
import play.silhouette.api.Silhouette
import modules.DefaultEnv

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class UserAvailabilityControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "UserAvailabilityController#userTimezoneForm" should {
    "accept valid timezone" in {
      val controller = new UserAvailabilityController(
        stubControllerComponents(),
        mock[Silhouette[DefaultEnv]],
        mock[UserAvailabilityRepository]
      )

      val form = controller.userTimezoneForm.bind(Map("timezone" -> "America/New_York"))
      form.hasErrors mustBe false
      form.get.timezone mustBe "America/New_York"
    }

    "accept valid UTC timezone" in {
      val controller = new UserAvailabilityController(
        stubControllerComponents(),
        mock[Silhouette[DefaultEnv]],
        mock[UserAvailabilityRepository]
      )

      val form = controller.userTimezoneForm.bind(Map("timezone" -> "UTC"))
      form.hasErrors mustBe false
      form.get.timezone mustBe "UTC"
    }

    "accept valid GMT+0 timezone" in {
      val controller = new UserAvailabilityController(
        stubControllerComponents(),
        mock[Silhouette[DefaultEnv]],
        mock[UserAvailabilityRepository]
      )

      val form = controller.userTimezoneForm.bind(Map("timezone" -> "GMT+0"))
      form.hasErrors mustBe false
      form.get.timezone mustBe "GMT+0"
    }

    "reject invalid timezone" in {
      val controller = new UserAvailabilityController(
        stubControllerComponents(),
        mock[Silhouette[DefaultEnv]],
        mock[UserAvailabilityRepository]
      )

      val form = controller.userTimezoneForm.bind(Map("timezone" -> "Invalid/Timezone"))
      form.hasErrors mustBe true
      form.errors.exists(_.message == "Invalid timezone. Please select a valid timezone from the list.") mustBe true
    }

    "reject empty timezone" in {
      val controller = new UserAvailabilityController(
        stubControllerComponents(),
        mock[Silhouette[DefaultEnv]],
        mock[UserAvailabilityRepository]
      )

      val form = controller.userTimezoneForm.bind(Map("timezone" -> ""))
      form.hasErrors mustBe true
    }

    "reject completely invalid timezone string" in {
      val controller = new UserAvailabilityController(
        stubControllerComponents(),
        mock[Silhouette[DefaultEnv]],
        mock[UserAvailabilityRepository]
      )

      val form = controller.userTimezoneForm.bind(Map("timezone" -> "NotATimezone"))
      form.hasErrors mustBe true
      form.errors.exists(_.message == "Invalid timezone. Please select a valid timezone from the list.") mustBe true
    }

    "accept Europe/London timezone" in {
      val controller = new UserAvailabilityController(
        stubControllerComponents(),
        mock[Silhouette[DefaultEnv]],
        mock[UserAvailabilityRepository]
      )

      val form = controller.userTimezoneForm.bind(Map("timezone" -> "Europe/London"))
      form.hasErrors mustBe false
      form.get.timezone mustBe "Europe/London"
    }

    "accept Asia/Tokyo timezone" in {
      val controller = new UserAvailabilityController(
        stubControllerComponents(),
        mock[Silhouette[DefaultEnv]],
        mock[UserAvailabilityRepository]
      )

      val form = controller.userTimezoneForm.bind(Map("timezone" -> "Asia/Tokyo"))
      form.hasErrors mustBe false
      form.get.timezone mustBe "Asia/Tokyo"
    }
  }
}