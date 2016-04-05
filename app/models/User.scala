package models

import java.util.UUID

import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import javax.inject.Inject
import persistence.UsersPersistence
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

/**
  * The user object.
  *
  * @param userID The unique ID of the user.
  * @param loginInfo The linked login info.
  * @param firstName Maybe the first name of the authenticated user.
  * @param lastName Maybe the last name of the authenticated user.
  * @param fullName Maybe the full name of the authenticated user.
  * @param email Maybe the email of the authenticated provider.
  */
case class User(
                 userID: UUID,
                 loginInfo: LoginInfo,
                 firstName: Option[String],
                 lastName: Option[String],
                 fullName: Option[String],
                 email: Option[String]) extends Identity

/**
  * Handles actions to users.
  */
trait UserService extends IdentityService[User] {

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: User): Future[User]

  /**
    * Saves the social profile for a user.
    *
    * If a user exists for this profile then update the user, otherwise create a new user with the given profile.
    *
    * @param profile The social profile to save.
    * @return The user for whom the profile was saved.
    */
  def save(profile: CommonSocialProfile): Future[User]
}

/**
  * Handles actions to users.
  *
  * @param usersPersistence The user DAO implementation.
  */
class UserServiceImpl @Inject() (usersPersistence: UsersPersistence) extends UserService {

  /**
    * Retrieves a user that matches the specified login info.
    *
    * @param loginInfo The login info to retrieve a user.
    * @return The retrieved user or None if no user could be retrieved for the given login info.
    */
  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = usersPersistence.find(loginInfo)

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: User) = usersPersistence.save(user)

  /**
    * Saves the social profile for a user.
    *
    * If a user exists for this profile then update the user, otherwise create a new user with the given profile.
    *
    * @param profile The social profile to save.
    * @return The user for whom the profile was saved.
    */
  def save(profile: CommonSocialProfile) = {
    usersPersistence.find(profile.loginInfo).flatMap {
      case Some(user) => // Update user with profile
        usersPersistence.save(user.copy(
          firstName = profile.firstName,
          lastName = profile.lastName,
          fullName = profile.fullName,
          email = profile.email
        ))
      case None => // Insert a new user
        usersPersistence.save(User(
          userID = UUID.randomUUID(),
          loginInfo = profile.loginInfo,
          firstName = profile.firstName,
          lastName = profile.lastName,
          fullName = profile.fullName,
          email = profile.email
        ))
    }
  }
}
