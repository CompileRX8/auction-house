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

  def organizations = Action { implicit request =>
    Organization.currentOrganizations() match {
      case Success(orgs) =>
        Ok(Json.toJson(orgs))
      case Failure(e) =>
        logger.error("Unable to send organizations", e)
        BadRequest(e.getMessage)
    }
  }

  def newOrganization = Action(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]

    Organization.create(name) match {
      case Success(org) =>
        AppController.pushOrganizations()
        Ok(s"Created organization $name")
      case Failure(e) =>
        logger.error("Unable to create organization", e)
        BadRequest(e.getMessage)
    }
  }

  def editOrganization(organizationId: Long) = Action(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]

    Organization.edit(organizationId, name) match {
      case Success(org) =>
        AppController.pushOrganizations()
        Ok(s"Edited organization $name")
      case Failure(e) =>
        logger.error("Unable to edit organization", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteOrganization(organizationId: Long) = Action { implicit request =>
    Organization.delete(organizationId) match {
      case Success(org) =>
        AppController.pushOrganizations()
        Ok(s"Deleted organization ${org.name}")
      case Failure(e) =>
        logger.error("Unable to delete organization", e)
        BadRequest(e.getMessage)
    }
  }
}
