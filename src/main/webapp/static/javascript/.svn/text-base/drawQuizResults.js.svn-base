function drawQuizResults(data) {
	console.log(data.length,data)
	switch(data.length){
		case 0:
			break;
		default:
			var plotWidth = 250
			var plotHeight = 250
			var padding = 30
			var barWidth = (plotWidth - padding) / data.length
			var y = pv.Scale.linear(data, function (d) { return d.value.y }).range(0, plotHeight)
			var plot = new pv.Panel()
				.canvas("quizResults")
				.width(plotWidth)
				.strokeStyle("black")
				.lineWidth(2)
				.height(plotHeight)
						plot.add(pv.Bar)
				.data(data)
				.width(barWidth)
				.bottom(0)
				.height(plotHeight)
				.left(function () { return padding + this.index * barWidth })
				.fillStyle("white")
				.title(function (d) { return d.label })
							plot.add(pv.Bar)
				.data(data)
				.width(barWidth)
				.bottom(0)
				.height(function (d) { return y(d.value.y) })
				.left(function () { return padding + this.index * barWidth })
				.fillStyle(function (d) { return d.color })
				.title(function (d) { return d.label })
				.anchor("bottom").add(pv.Label)
					.data(data)
					.text(function (d) { return d.label.slice(0, 5) })
					.textStyle("black")
						plot.add(pv.Rule)
				.data(y.ticks(5))
				.bottom(y)
				.strokeStyle("lightgrey")
				.anchor("left")
				.add(pv.Label)
					.left(30)
					.text(y.tickFormat)
					.textStyle("grey")
						plot.render()
	}
}
