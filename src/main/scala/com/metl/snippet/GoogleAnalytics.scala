package com.metl.snippet

import com.metl.model.Globals
import scala.xml.NodeSeq

class GoogleAnalytics {

  val gaFunction = "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');"

  def render: NodeSeq = {
    <script>{gaFunction}</script> ++ List(Globals.googleAnalytics, Globals.clientGoogleAnalytics)
      .filter(key => key._2.exists(s => s.length > 0))
      .map(key =>
      <script>
        ga('create', '{key._2.getOrElse("")}', 'auto', '{key._1}');
        ga('{key._1}.send', 'pageview');
      </script>).foldLeft(NodeSeq.Empty)(_ ++ _)
  }
}