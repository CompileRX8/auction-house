package controllers

import models.{OrganizationData, Event, Contact, Organization}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.util.{Failure, Success}

object OrganizationController extends Controller with Secured {
  val logger = Logger(OrganizationController.getClass)

  implicit val organizationFormat = Json.format[Organization]
  implicit val contactFormat = Json.format[Contact]
  implicit val eventFormat = Json.format[Event]
  implicit val organizationDataFormat = Json.format[OrganizationData]

  def organizations = Action.async { implicit request =>
    Organization.currentOrganizations() map { orgs =>
      Ok(Json.toJson(orgs))
    } recover {
      case e @ Throwable =>
        logger.error("Unable to send organizations", e)
        BadRequest(e.getMessage)
    }
  }

  def newOrganization = Action.async(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]

    Organization.create(name) map { org =>
      AppController.pushOrganizations()
      Ok(s"Created organization $name")
    } recover {
      case e @ Throwable =>
        logger.error("Unable to create organization", e)
        BadRequest(e.getMessage)
    }
  }

  def editOrganization(organizationId: Long) = Action.async(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]

    Organization.edit(organizationId, name) map {
      case Some(org) =>
        AppController.pushOrganizations()
        Ok(s"Edited organization $name")
      case None =>
        val msg = s"Unable to find organization ID $organizationId to edit"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e @ Throwable =>
        logger.error("Unable to edit organization", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteOrganization(organizationId: Long) = Action.async { implicit request =>
    Organization.delete(organizationId) map {
      case Some(org) =>
        AppController.pushOrganizations()
        Ok(s"Deleted organization ${org.name}")
      case None =>
        val msg = s"Unable to find organization ID $organizationId to delete"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e @ Throwable =>
        logger.error("Unable to delete organization", e)
        BadRequest(e.getMessage)
    }
  }
}