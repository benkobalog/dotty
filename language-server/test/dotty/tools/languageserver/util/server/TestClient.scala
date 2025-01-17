package dotty.tools.languageserver.util.server

import dotty.tools.languageserver.worksheet.{WorksheetExecOutput, WorksheetClient}

import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services._

import scala.collection.mutable.Buffer

class TestClient extends WorksheetClient {

  class Log[T] {
    private[this] val log = Buffer.empty[T]

    def +=(elem: T): this.type = { log += elem; this }
    def get: List[T] = log.toList
    def clear(): Unit = log.clear()
  }

  val log = new Log[MessageParams]
  val diagnostics = new Log[PublishDiagnosticsParams]
  val telemetry = new Log[Any]
  val worksheetOutput = new Log[WorksheetExecOutput]

  override def logMessage(message: MessageParams) = {
    log += message
  }

  override def showMessage(messageParams: MessageParams) = {
    log += messageParams
  }

  override def telemetryEvent(obj: scala.Any) = {
    telemetry += obj
  }

  override def showMessageRequest(requestParams: ShowMessageRequestParams) = {
    log += requestParams
    new CompletableFuture[MessageActionItem]
  }

  override def publishDiagnostics(diagnosticsParams: PublishDiagnosticsParams) = {
    diagnostics += diagnosticsParams
  }

  override def publishOutput(output: WorksheetExecOutput) = {
    worksheetOutput += output
  }

}
