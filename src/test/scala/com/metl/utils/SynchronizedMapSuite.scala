 package com.metl.utils

import net.liftweb.util._
import net.liftweb.common._

import org.scalatest._
import matchers.ShouldMatchers
import concurrent.AsyncAssertions
import concurrent._
import OptionValues._

import com.metl.utils._ 

class SynchronizedMapSuite extends FunSuite with AsyncAssertions with ShouldMatchers with BeforeAndAfter {

  var syncMap: SynchronizedWriteMap[Int, String] = _

    before {
        syncMap = new SynchronizedWriteMap[Int, String]
    }

    test("added value to map exists") {

        syncMap.put(1, "welcome to the jungle")
        val defined = syncMap.isDefinedAt(1)
        val value = syncMap.getOrElseUpdate(1, "")

        assert(defined === true)
        assert(value === "welcome to the jungle")
    }

    test("non-existant value does not throw exception") {
        
        val defined = syncMap.isDefinedAt(1)
        val value = syncMap.getOrElseUpdate(1, "")

        assert(defined === false)
        assert(value === "")
    }

    test("add and remove via += and -=") {
        
        syncMap += (1, "metl is awesome")
        syncMap -= 1

        val defined = syncMap.isDefinedAt(1)

        assert(defined === false)
    }

    test("update an existing value") {
        
        val syncMap = new SynchronizedWriteMap[Int, String]

        syncMap += (1, "metl is awesome")
        syncMap.update(1, "metl kicks ass")

        val value = syncMap.getOrElseUpdate(1, "")
        assert(value === "metl kicks ass")
    }

    test("clear all added values") {

        syncMap += (1, "go to the store")
        syncMap += (2, "buy some milk")
        syncMap.clear

        val defined1 = syncMap.isDefinedAt(1)
        val defined2 = syncMap.isDefinedAt(2)

        assert(defined1 === false)
        assert(defined2 === false)
    }
    
    test("add via += updates value of preexisting key") {

        val syncMap = new SynchronizedWriteMap[Int, String]

        syncMap += (1, "go to the store")
        syncMap += (1, "go to school")

        val value = syncMap.getOrElseUpdate(1, "")
        assert(value === "go to school")
    }

    test("update non-existing key") {
        
        syncMap.update(1, "metl is awesome")

        val value = syncMap.getOrElseUpdate(1, "")
        assert(value === "metl is awesome")
    }

    test("updated does not modify original") {

        syncMap += (1, "the original")
        val newSyncMap = syncMap.updated(1, "the clone")

        val origValue = syncMap.getOrElseUpdate(1, "")
        val cloneValue = newSyncMap.getOrElseUpdate(1, "")

        assert(origValue === "the original")
        assert(cloneValue === "the clone")
    }

    test("contrived test to evaluate the conductor") {

        val conductor = new Conductor
        import conductor._
        
        thread("producer") {

            syncMap.put(1, "cats")
            syncMap.put(2, "dogs")
            waitForBeat(1)
            syncMap.put(3, "cows")
        }

        thread("consumer") {

            waitForBeat(1)
            syncMap.remove(1).value should be ("cats")
            syncMap.remove(2).value should be ("dogs")
            assert(syncMap.isEmpty === false)
            waitForBeat(2)
            syncMap.remove(3).value should be ("cows")
        }

        whenFinished {

            assert(syncMap.isEmpty === true)
        }
    }
}
