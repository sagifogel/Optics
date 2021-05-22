import com.typesafe.tools.mima.plugin.MimaKeys._
import sbt.Keys.{moduleName, organization}
import sbt._

object MimaSettings {
  lazy val previousArtifactsToCompare = "0.1.0"
  def mimaSettings(failOnProblem: Boolean) = Seq(
    mimaFailOnProblem := failOnProblem,
    mimaPreviousArtifacts := Set(organization.value %% moduleName.value % previousArtifactsToCompare)
  )
}
