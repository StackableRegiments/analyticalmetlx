package com.metl.snippet

import com.metl.model.Globals
import scala.xml.NodeSeq

class GoogleAnalytics {

  val gaFunction = "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');"

  def render: NodeSeq = {
    val hasStackable = Globals.googleAnalytics.length() > 0
    val hasClient = Globals.clientGoogleAnalytics.length() > 0
    if( hasStackable || hasClient ) {
      <script>
        {gaFunction}
      </script>
    }

    if (hasStackable) {
      <script>
        ga('create', ' {Globals.googleAnalytics} ', 'auto');
        ga('send', 'pageview');
      </script>
    }

    if (hasClient) {
      <script>
        ga('create', ' {Globals.clientGoogleAnalytics} ', 'auto');
        ga('send', 'pageview');
      </script>
    }
  }
}
