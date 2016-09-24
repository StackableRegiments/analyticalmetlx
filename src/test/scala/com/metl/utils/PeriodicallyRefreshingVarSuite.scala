package com.metl.utils

import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.util.Helpers.TimeSpan

import org.scalatest._
import org.scalatest.time.SpanSugar._
import matchers.MustMatchers
import concurrent.AsyncAssertions

import com.metl.utils._ 

class PeriodicallyRefreshingVarSuite extends FunSuite with AsyncAssertions with MustMatchers {

    test("refresh after time delay on creation") {

      val w = new Waiter
      val refresher = new PeriodicallyRefreshingVar(new TimeSpan(20), () => { w.dismiss })

      w.await
    }

    test("refresh after refresh message sent") {
        
        var counter = 0 // starts at 0
        val w = new Waiter
        val refresher = new PeriodicallyRefreshingVar(new TimeSpan(100000), () => { 
            counter += 1 // adds 1 to make initial state
            w.dismiss //first dismissal on the waiter
        })

        refresher ! Refresh // should increment counter to 2 and dismissals to 2
        w.await(timeout(300 millis), dismissals(2))
        assert(counter === 2)
    }

    test("get last result") {

        var counter = 0
        val w = new Waiter
        val refresher = new PeriodicallyRefreshingVar(new TimeSpan(10000), () => {
          counter += 1
          w.dismiss
          counter
        })
        
        assert(counter === refresher.get)
        assert(counter === 1)
        w.await
    }
}
