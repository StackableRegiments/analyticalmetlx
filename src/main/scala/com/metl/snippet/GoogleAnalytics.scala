package com.metl.snippet

import com.metl.model.Globals
import scala.xml.NodeSeq

class GoogleAnalytics {

  val gaFunction = "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');"

  def render: NodeSeq = {
    if (Globals.googleAnalytics.length() > 0) {
      <script>
        {gaFunction}
        ga('create', ' {Globals.googleAnalytics} ', 'auto');
        ga('send', 'pageview');
      </script>
    }
    else {
      <script/>
    }
  }
}
