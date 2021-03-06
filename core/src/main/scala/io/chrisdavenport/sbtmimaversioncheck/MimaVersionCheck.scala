package io.chrisdavenport.sbtmimaversioncheck

import sbt.Def
import sbt.Keys._
import sbt._
import com.typesafe.tools.mima.plugin._
import MimaKeys._

object MimaVersionCheck extends AutoPlugin {

  object autoImport extends MimaVersionCheckKeys
  import autoImport._

  override def trigger =
    allRequirements

  override def requires =
    MimaPlugin

  def semverBinCompatVersions(
      major: Int,
      minor: Int,
      patch: Int,
      isSnapshotOfLatestReleasedVersion: Boolean
  ): Set[(Int, Int, Int)] = {
    val majorVersions: List[Int] =
      if (major == 0 && minor == 0) List.empty[Int] // If 0.0.x do not check MiMa
      else List(major)
    val minorVersions: List[Int] =
      if (major >= 1) Range(0, minor).inclusive.toList
      else List(minor)
    def patchVersions(currentMinVersion: Int): List[Int] =
      if (minor == 0 && patch == 0) List.empty[Int]
      else if (currentMinVersion != minor) List(0)
      else {
        val maxPatchValue = if (isSnapshotOfLatestReleasedVersion) patch else patch - 1
        Range(0, maxPatchValue).inclusive.toList
      }

    val versions = for {
      maj <- majorVersions
      min <- minorVersions
      pat <- patchVersions(min)
    } yield (maj, min, pat)
    versions.toSet
  }

  def mimaVersions(version: String, isSnapshotOfLatestReleasedVersion: Boolean): Set[String] =
    VersionNumber(version) match {
      case VersionNumber(Seq(major, minor, patch, _*), _, _) =>
        semverBinCompatVersions(
          major.toInt,
          minor.toInt,
          patch.toInt,
          isSnapshotOfLatestReleasedVersion
        ).map { case (maj, min, pat) => maj.toString + "." + min.toString + "." + pat.toString }
      case _ =>
        Set.empty[String]
    }

  override def globalSettings: Seq[Def.Setting[_]] = List(
    mimaVersionCheckExtraVersions := Set(),
    mimaVersionCheckExcludedVersions := Set(),
    mimaVersionCheckSnapshotUsesLatestReleasedVersion := false,
  )

  override def projectSettings: Seq[Def.Setting[_]] = {
    List(
      mimaFailOnNoPrevious := false,
      mimaFailOnProblem := mimaVersions(
        version.value,
        isSnapshot.value && mimaVersionCheckSnapshotUsesLatestReleasedVersion.value
      ).toList.nonEmpty,
      mimaPreviousArtifacts := {
        val fullVersionSet =
          (mimaVersions(
            version.value,
            isSnapshot.value && mimaVersionCheckSnapshotUsesLatestReleasedVersion.value
          ) ++ mimaVersionCheckExtraVersions.value)
            .diff(mimaVersionCheckExcludedVersions.value)
        val msg =
          if (fullVersionSet.nonEmpty)
            s"Checking against versions ${fullVersionSet.toList.sorted.mkString(", ")}"
          else "No versions to check"
        sLog.value.info(s"MiMa: ${moduleName.value} - $msg")
        fullVersionSet
          .map { v =>
            val moduleN = if (sbtPlugin.value) {
              moduleName.value + "_" + scalaBinaryVersion.value + "_" + sbtBinaryVersion.value
            } else {
              moduleName.value + "_" + scalaBinaryVersion.value
            }
            organization.value % moduleN % v
          }
      }
    )
  }

}
