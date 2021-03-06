/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers


import config.GmpGlobal
import connectors.{DesGetHiddenRecordResponse, DesConnector}
import events.ResultsEvent
import models.{CalculationRequest, GmpCalculationResponse}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Action
import repositories.CalculationRepository
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CalculationController extends BaseController {

  val desConnector: DesConnector
  val repository: CalculationRepository
  val auditConnector: AuditConnector = GmpGlobal.auditConnector


  def requestCalculation(userId: String) = Action.async(parse.json) {

    implicit request => {

      withJsonBody[CalculationRequest] { calculationRequest =>

        repository.findByRequest(calculationRequest).flatMap {
          case Some(cr) => {
            sendResultsEvent(cr, true, userId)
            Future.successful(Ok(Json.toJson(cr)))
          }
          case None => {
            desConnector.getPersonDetails(calculationRequest.nino).flatMap {
              case DesGetHiddenRecordResponse => {
                val response = GmpCalculationResponse(calculationRequest.firstForename + " " + calculationRequest.surname, calculationRequest.nino,calculationRequest.scon,None,None,List(),LOCKED,None,None,None,false,calculationRequest.calctype.getOrElse(-1))
                Future.successful(Ok(Json.toJson(response)))
              }
              case _ => {
                val result = desConnector.calculate(userId, calculationRequest)
                result.map {
                  calculation => {
                    Logger.debug(s"[CalculationController][requestCalculation] : $calculation")

                    val transformedResult = GmpCalculationResponse.createFromCalculationResponse(calculation)(calculationRequest.nino, calculationRequest.scon, calculationRequest.firstForename + " " + calculationRequest.surname, calculationRequest.revaluationRate, calculationRequest.revaluationDate,
                      calculationRequest.dualCalc.fold(false)(_ == 1), calculationRequest.calctype.get)

                    Logger.debug(s"[CalculationController][transformedResult] : $transformedResult")
                    repository.insertByRequest(calculationRequest, transformedResult)
                    sendResultsEvent(transformedResult, false, userId)

                    Ok(Json.toJson(transformedResult))
                  }
                }.recover {
                  case e: Upstream5xxResponse if e.upstreamResponseCode == 500 => {
                    Logger.debug(s"[CalculateController][requestCalculation][transformedResult][ERROR:500] : ${e.getMessage}")
                    InternalServerError(e.getMessage)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def sendResultsEvent(response: GmpCalculationResponse, cached: Boolean, userId: String)(implicit hc:HeaderCarrier) {
    val idType = userId.take(1) match {
      case x if x.matches("[A-Z]") => "psa"
      case x if x.matches("[0-9]") => "psp"
    }
    val resultsEventResult = auditConnector.sendEvent(new ResultsEvent(!response.hasErrors, response.errorCodes, response.calcType, response.dualCalc, response.scon, cached, idType))
    resultsEventResult.onFailure {
      case e: Throwable => Logger.warn("[CalculationController][sendResultsEvent] : resultsEventResult: " + e.getMessage, e)
    }
  }
}

object CalculationController extends CalculationController {
  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation
  override val desConnector = DesConnector
  override val repository = CalculationRepository()
  // $COVERAGE-ON$
}
