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

  val awaitTimeout = timeout(5 * 60 * 1000 millis) // 5 minutes
    val OneHour = new TimeSpan(60 * 60 * 1000)
    test("refresh after time delay on creation") {

      val w = new Waiter
      val refresher = new PeriodicallyRefreshingVar(OneHour, () => { 
        w.dismiss 
      })
      w.await
      refresher ! Stop
    }

    test("refresh after refresh message sent") {
        
        var counter = 0 // starts at 0
        val w = new Waiter
        val refresher = new PeriodicallyRefreshingVar(OneHour, () => { 
            counter += 1 // adds 1 to make initial state
            w.dismiss //first dismissal on the waiter
        })

        refresher ! Refresh // should increment counter to 2 and dismissals to 2
        w.await(awaitTimeout, dismissals(2))
        refresher ! Stop
        assert(counter >= 2)
    }

    test("get last result") {

        var counter = 0
        val w = new Waiter
        val refresher = new PeriodicallyRefreshingVar(OneHour, () => {
          counter += 1
          w.dismiss
          counter
        })
        w.await
        refresher ! Stop
        assert(refresher.get === 1)
    }
    test("get last result after 10 iterations") {

        var counter = 0
        val w = new Waiter
        val refresher = new PeriodicallyRefreshingVar(new TimeSpan(20), () => {
          counter += 1
          w.dismiss
          counter
        })
        w.await(awaitTimeout, dismissals(11)) // when run on a system with only 1 thread is going to have difficulty scheduling the awaiter and the dismisser such that the set of the value will definitely happen before the refresher.get occurs, so it's necessary to ask for one more dismissal than expected.
        refresher ! Stop
        assert(refresher.get >= 10) // not checking that it's exactly 10, because if the system is slow enough, it might re-fire.
    }

}
