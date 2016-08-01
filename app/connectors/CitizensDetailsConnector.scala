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

package connectors

import config.{ApplicationConfig, WSHttp}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse, HttpGet}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait CitizensDetailsConnector extends ApplicationConfig with RawResponseReads{

  val http: HttpGet = WSHttp
  lazy val serviceURL = baseUrl("citizens-details")

  def getDesignatoryDetails(nino: String)(implicit headerCarrier: HeaderCarrier): Future[Int] = {
    val uri = s"$serviceURL/citizen-details/${nino}/designatory-details"
    http.GET[HttpResponse](uri)(httpReads, headerCarrier).map { response => response.status }
  }

}

object CitizensDetailsConnector extends CitizensDetailsConnector{

}
