package com.metl.utils

import com.metl.external.LiftAuthStateData

object CasUtils {

  def getFirstName(liftAuthStateData: Option[LiftAuthStateData]): String = {
    getInfoGroupDetails(liftAuthStateData, "firstname").headOption.getOrElse("unknown")
  }

  def getSurname(liftAuthStateData: Option[LiftAuthStateData]): String = {
    getInfoGroupDetails(liftAuthStateData, "surname").headOption.getOrElse("unknown")
  }

  def getEmailAddress(liftAuthStateData: Option[LiftAuthStateData]): String = {
    getInfoGroupDetails(liftAuthStateData, "email").headOption.getOrElse("unknown")
  }

  protected def getInfoGroupDetails(liftAuthStateData: Option[LiftAuthStateData], key: String): Seq[String] = {
    liftAuthStateData.map(d => d.informationGroups.filter(d => d.key == key).map(d => d.value)).getOrElse(List.empty)
  }

  def getOrgUnits(liftAuthStateData: Option[LiftAuthStateData]): Seq[String] = {
    liftAuthStateData.map(d => d.eligibleGroups.map(o => o.name + " (" + o.ouType + ")")).getOrElse(List.empty)
  }
}
