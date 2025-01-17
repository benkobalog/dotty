package dotty.tools.languageserver.util

import dotty.tools.languageserver.util.Code._
import dotty.tools.languageserver.util.actions._
import dotty.tools.languageserver.util.embedded.CodeMarker
import dotty.tools.languageserver.util.server.{TestFile, TestServer}
import org.eclipse.lsp4j.{CompletionItemKind, DocumentHighlightKind}

/**
 * Simulates an LSP client for test in a workspace defined by `sources`.
 *
 * @param sources The list of sources in the workspace
 * @param actions Unused
 */
class CodeTester(sources: List[SourceWithPositions], actions: List[Action]) {

  private val testServer = new TestServer(TestFile.testDir)

  private val files = sources.zipWithIndex.map {
    case (ScalaSourceWithPositions(text, _), i) => testServer.openCode(text, s"Source$i.scala")
    case (WorksheetWithPositions(text, _), i) => testServer.openCode(text, s"Worksheet$i.sc")
  }
  private val positions: PositionContext = getPositions(files)

  /**
   * Perform a hover over `range`, verifies that result matches `expected`.
   *
   * @param range    The range over which to hover.
   * @param expected The expected result.
   *
   * @see dotty.tools.languageserver.util.actions.CodeHover
   */
  def hover(range: CodeRange, expected: Option[String]): this.type =
    doAction(new CodeHover(range, expected))

  /**
   * Perform a jump to definition over `range`, verifies that the results are `expected`.
   *
   * @param range    The range of positions from which run `jump to definition`.
   * @param expected The expected positions to jump to.
   *
   * @see dotty.tools.languageserver.util.actions.CodeDefinition
   */
  def definition(range: CodeRange, expected: Seq[CodeRange]): this.type =
    doAction(new CodeDefinition(range, expected))

  /**
   * Perform a highlight over `range`, verifies that the ranges and kinds of symbols match
   * `expected`.
   *
   * @param range    The range of positions to highlight.
   * @param expected The expected ranges and the kind of symbols that should be highlighted.
   *
   * @see dotty.tools.languageserver.util.actions.CodeDefinition
   */
  def highlight(range: CodeRange, expected: (CodeRange, DocumentHighlightKind)*): this.type =
    doAction(new CodeDocumentHighlight(range, expected))

  /**
   * Finds all the references to the symbol in `range`, verifies that the results match `expected`.
   *
   * @param range    The range of positions from which search for references.
   * @param expected The expected positions of the references
   * @param withDecl When set, include the declaration of the symbol under `range` in the results.
   *
   * @see dotty.tools.languageserver.util.actions.CodeReferences
   */
  def references(range: CodeRange, expected: List[CodeRange], withDecl: Boolean = false): this.type =
    doAction(new CodeReferences(range, expected, withDecl))

  /**
   * Requests completion at the position defined by `marker`, verifies that the results match
   * `expected`.
   *
   * @param marker   The position from which to ask for completions.
   * @param expected The expected completion results.
   *
   * @see dotty.tools.languageserver.util.actions.CodeCompletion
   */
  def completion(marker: CodeMarker, expected: Set[(String, CompletionItemKind, String)]): this.type =
    doAction(new CodeCompletion(marker, expected))

  /**
   * Performs a workspace-wide renaming of the symbol under `marker`, verifies that the positions to
   * update match `expected`.
   *
   * @param marker   The position from which to ask for renaming.
   * @param newName  The new name to give to the symbol.
   * @param expected The expected positions to change.
   *
   * @see dotty.tools.languageserver.util.actions.CodeRename
   */
  def rename(marker: CodeMarker, newName: String, expected: Set[CodeRange]): this.type =
    doAction(new CodeRename(marker, newName, expected)) // TODO apply changes to the sources and positions

  /**
   * Queries for all the symbols referenced in the source file in `marker`, verifies that they match
   * `expected`.
   *
   * @param marker   The marker defining the source file from which to query.
   * @param expected The expected symbols to be found.
   *
   * @see dotty.tools.languageserver.util.actions.CodeDocumentSymbol
   */
  def documentSymbol(marker: CodeMarker, expected: SymInfo*): this.type =
    doAction(new CodeDocumentSymbol(marker, expected))

  /**
   * Queries the whole workspace for symbols matching `query`, verifies that the results match
   * `expected`.
   *
   * @param query    The query used to find symbols.
   * @param expected The expected symbols to be found.
   *
   * @see dotty.tools.languageserver.util.actions.CodeSymbol
   */
  def symbol(query: String, symbols: SymInfo*): this.type =
    doAction(new CodeSymbol(query, symbols))

  /**
   * Triggers evaluation of the worksheet specified by `marker`, verifies that the results of
   * evaluation match `expected.
   *
   * @param marker   A marker a identifies the worksheet to evaluate.
   * @param expected The expected output.
   *
   * @see dotty.tools.languageserver.util.actions.WorksheetEvaluate
   */
  def evaluate(marker: CodeMarker, expected: String*): this.type =
    doAction(new WorksheetEvaluate(marker, expected, strict = true))

  /**
   * Triggers evaluation of the worksheet specified by `marker`, verifies that each line of output
   * starts with `expected`.
   *
   * @param marker   A marker a identifies the worksheet to evaluate.
   * @param expected The expected starts of output.
   *
   * @see dotty.tools.languageserver.util.actions.WorksheetEvaluate
   */
  def evaluateNonStrict(marker: CodeMarker, expected: String*): this.type =
    doAction(new WorksheetEvaluate(marker, expected, strict = false))

  /**
   * Triggers evaluation of the worksheet specified by `marker`, then verifies that execution can be
   * cancelled after `afterMs` milliseconds.
   *
   * @param marker   A marker that identifier the worksheet to evaluate.
   * @param afterMs  The delay in milliseconds before cancelling execution.
   *
   * @see dotty.tools.languageserver.util.actions.WorksheetCancel
   */
  def cancelEvaluation(marker: CodeMarker, afterMs: Long): this.type =
    doAction(new WorksheetCancel(marker, afterMs))

  private def doAction(action: Action): this.type = {
    try {
      action.execute()(testServer, testServer.client, positions)
    } catch {
      case ex: AssertionError =>
        val sourcesStr = sources.zip(files).map{ case (source, file) => "// " + file.file + "\n" + source.text}.mkString("\n")
        val msg =
          s"""
            |
            |$sourcesStr
            |
            |while executing action: ${action.show(positions)}
            |
          """.stripMargin
        val assertionError = new AssertionError(msg + ex.getMessage)
        assertionError.setStackTrace(ex.getStackTrace)
        throw assertionError
    }
    this
  }

  private def getPositions(files: List[TestFile]): PositionContext = {
    val posSeq = {
      for {
        (code, file) <- sources.zip(files)
        (position, line, char) <- code.positions
      } yield position -> (file, line, char)
    }
    val posMap = posSeq.toMap
    assert(posSeq.size == posMap.size,
      "Each CodeMarker instance can only appear once in the code: " + posSeq.map(x => (x._1, x._2._2, x._2._3)))
    new PositionContext(posMap)
  }
}
