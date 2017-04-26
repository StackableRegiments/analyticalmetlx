package com.metl.utils

import com.metl.liftAuthenticator.LiftAuthStateData

object CasUtils {

  def getFirstName(liftAuthStateData: Option[LiftAuthStateData]): String = {
    getInfoGroupDetails(liftAuthStateData, "firstname").head
  }

  def getSurname(liftAuthStateData: Option[LiftAuthStateData]): String = {
    getInfoGroupDetails(liftAuthStateData, "surname").head
  }

  def getEmailAddress(liftAuthStateData: Option[LiftAuthStateData]): String = {
    getInfoGroupDetails(liftAuthStateData, "email").head
  }

  protected def getInfoGroupDetails(liftAuthStateData: Option[LiftAuthStateData], key: String): Seq[String] = {
    liftAuthStateData.map(d => d.informationGroups.filter(d => d.key == key).map(d => d.value)).getOrElse(List.empty)
  }

  def getOrgUnits(liftAuthStateData: Option[LiftAuthStateData]): Seq[String] = {
    liftAuthStateData.map(d => d.eligibleGroups.map(o => o.name + " (" + o.ouType + ")")).getOrElse(List.empty)
  }
}
