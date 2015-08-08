
/*Analyzers
	Are now explicitly side-effecty.  This ensures sequencing, which means that later effects can scale previous effects, or remove them from the passing set.  It also saves on lines and makes the logic more explicit.  Parallelism is a casualty of this decision, but was killed by causality in the chain anyway.
*/
var analyzers = {
	"Naive coreference":function(post,passedResults,termIndex){
		var points = 1;
		eachTerm(post.discussion.content,function(term){
			$.map(_.keys(termIndex[term]),function(otherPost){
				setIfMissing(passedResults,otherPost,{
					id:otherPost,
					scores:[]
				})
				passedResults[otherPost].scores.push({
					func:function(i){
						return i + points;
					},
					term:term,
					narration:sprintf("(%s) Shared the word '%s'\n",pluralizePoints(points),term)
				})
			})
		})
	},
	"Stopword compensator":function(post,passedResults,termIndex){
		var points = -1;
		eachTerm(post.discussion.content,function(term){
			var stopped = $.inArray(term,Stopwords) >= 0;
			$.map(_.keys(termIndex[term]),function(otherPost){
				setIfMissing(passedResults,otherPost,{
					id:otherPost,
					scores:[]
				})
				if(stopped){
					passedResults[otherPost].scores.push({
						func:function(i){
							return i + points;
						},
						term:term,
						narration:sprintf("(%s) The word '%s' is generally too common to count\n",pluralizePoints(points),term)
					})
				}
			})
		})
	},
	"Term weighting by frequency in corpus": function(post,passedResults){
	},
	"Length comparator across corpus": function(post,passedResults){
		var sum = 0
		var cnt = 0
		var storeLength = function(item){
			if(item.discussion){
				sum += item.discussion.content.length;
				cnt++;
			}
			if(item.answers){
				item.answers.map(storeLength);
			}
			if(item.comments){
				item.comments.map(storeLength);
			}
		}
		_.filter(_.keys(items),function(id){
			return types[id] == "question";
		}).map(function(id){
			storeLength(items[id])
		})
		var avg = sum / cnt;
		setIfMissing(passedResults,post.id,{
			id:post.id,
			scores:[]
		})
		var l = post.discussion.content.length;
		var proportion = l / avg;
		if(proportion < 0.25 ){
			passedResults[post.id].scores.push({
				func:function(i){
					return i * proportion;
				},
				narration:sprintf("(Scaled %s) %s characters is very short\n",proportion,l)
			})
		}
		else if(proportion > 1.5){
			passedResults[post.id].scores.push({
				func:function(i){
					return i / proportion;
				},
				narration:sprintf("(Scaled %s) %s characters is very long\n",1 / proportion,l)
			})
		}
	}
}
function similarPosts(id){
	return $("<a />",{
		href:"#",
		class:"interactionLauncher",
		text:"(Similar)",
		click:function(){
			var allResults = {}
			var post = items[id]
			var termIndex = {}
			_.values(items).forEach(function(item){
				if(item.discussion){
					eachTerm(item.discussion.content,function(term){
						setIfMissing(termIndex,term,{})
						termIndex[term][item.id] = 1
					})
				}
			})
			$.each(analyzers,function(label,analyzer){
				analyzer(post,allResults,termIndex)
			})
			$.map(allResults,function(result){
				result.score = 0
				$.map(result.scores,function(score){
					result.score = score.func(result.score)
				})
			})
			var sortedCoReferences = _.sortBy(
					_.filter(allResults,function(result){
						return result.score > 0;
					}),function(v){
						return v.score;
					}).reverse()
			var cont = $("<span />");
			$.map(sortedCoReferences,function(v){
				var coReferent = items[v.id]
				var content = coReferent.discussion.content;
				var trimWidth = 40
				var summary = sprintf("(%s points) %s",
					v.score,
					content);
					/*
					content.slice(0,trimWidth),
					(content.length >= (trimWidth - 3) ? "..." : ""));
					*/
				cont.append($("<pre />",{
					text:summary,
					title:content
				}))
				var why = $("<a />",{
					href:"#",
					text:"(Explain)",
					class:"interactionLauncher",
					click:function(){
						var cont = $("<pre />")
						$.map(v.scores,function(scorer){
							cont.append(scorer.narration)
						})
						var label = $("<div />",{
							text:sprintf("(%s) Distance measure",v.score)
							})
						label.append(linkId(id))
						label.append(linkId(v.id))
						vjq(cont,label)
					}
				})
				cont.append($("<div />")
						.append(linkId(v.id))
						.append(why))
			})
			vjq(cont,$("<span />",{
				text:sprintf("%s Similar posts to",sortedCoReferences.length)
			}).append(linkId(id)))
		}
	})
}
console.log("Built analyzers")
