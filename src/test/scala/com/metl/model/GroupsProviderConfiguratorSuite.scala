package com.metl.model

import com.metl.external.{GroupStoreData, GroupStoreProvider, GroupsProvider, LiftAuthStateData, OrgUnit}
import org.scalatest._
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher, ShouldMatchers}
import org.scalatest.OptionValues._
import net.liftweb.util.Helpers._
import net.liftweb.common._
import com.metl.model._

import scala.xml._

class MockGroupStoreProvider(gsd:GroupStoreData) extends GroupStoreProvider {
  override val getData = gsd
}

class GroupsProviderConfiguratorSuite extends FunSuite with ShouldMatchers {
  def fixture(groupsByMember:Map[String,List[OrgUnit]]):GroupsProvider = {
    new StoreBackedGroupsProvider("mockId","mock",new MockGroupStoreProvider(GroupStoreData(
      groupsForMembers = groupsByMember
    )))
  }
  test("not filter anything out when the filterBehaviour configuration isn't valid") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = NodeSeq.Empty
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    ))
  }
  test("not filter anything out when the filterBehaviour doesn't declare a filter") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    ))
  }
  test("not filter anything out when it doesn't match") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group key="elephant"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    ))
  }
  test("filter members out by name") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group key="testKind"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter members out by namePrefix") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group key="testKind"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter members out by nameSuffix") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group key="testKind"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter groups out by key") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group key="testKind"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter groups out by key suffix") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group keySuffix="Kind"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter groups out by key prefix") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("testKind","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group keyPrefix="test"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter groups out by value") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("mockType","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group value="testCourse3"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter groups out by value suffix") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("mockType","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group valueSuffix="3"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter groups out by value prefix") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("mockType","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group valuePrefix="test"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None)
    ))
  }
  test("filter groups out with multiple predicates on a single term") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("mockType","mockCourse2",Nil,Nil,None),
      OrgUnit("mockType","mockCourse3",Nil,Nil,None),
      OrgUnit("mockType","testCourse1",Nil,Nil,None),
      OrgUnit("mockType","testCourse2",Nil,Nil,None),
      OrgUnit("mockType","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group valuePrefix="mock" valueSuffix="3"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("mockType","mockCourse2",Nil,Nil,None),
      OrgUnit("mockType","testCourse1",Nil,Nil,None),
      OrgUnit("mockType","testCourse2",Nil,Nil,None),
      OrgUnit("mockType","testCourse3",Nil,Nil,None)
    ))
  }
  test("filter groups out additively with multiple clauses") {
    val gp = fixture(Map("testUser" -> List(
      OrgUnit("mockType","mockCourse1",Nil,Nil,None),
      OrgUnit("mockType","mockCourse2",Nil,Nil,None),
      OrgUnit("mockType","mockCourse3",Nil,Nil,None),
      OrgUnit("mockType","testCourse1",Nil,Nil,None),
      OrgUnit("mockType","testCourse2",Nil,Nil,None),
      OrgUnit("mockType","testCourse3",Nil,Nil,None)
    )))
    val filterXml = 
      <filterNot>
        <group valuePrefix="mock"/>
        <group valueSuffix="3"/>
      </filterNot>
    val fgp = GroupsProviderConfigurator.possiblyFilter(filterXml,gp)
    val actual = fgp.getGroupsFor(LiftAuthStateData(false,"testUser",Nil,Nil))
    actual should equal(List(
      OrgUnit("mockType","testCourse1",Nil,Nil,None),
      OrgUnit("mockType","testCourse2",Nil,Nil,None)
    ))
  }
}
