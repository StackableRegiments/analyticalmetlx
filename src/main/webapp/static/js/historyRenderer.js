var intersectRect = function(r1, r2) {//Left,top,right,bottom
		if (typeof(r1) != "undefined" && typeof(r2) != "undefined"){
				return !(r2[0] > r1[2] ||
								 r2[2] < r1[0] ||
								 r2[1] > r1[3] ||
								 r2[3] < r1[1]);
		} else {
				return false;
		}
};
var overlapRect = function(r1,r2){
		if(!intersectRect(r1,r2)){
				return 0;
		}
		return (Math.max(r1[0], r2[0]) - Math.min(r1[2], r2[2])) * (Math.max(r1[1], r2[1]) - Math.min(r1[3], r2[3]));
};
var rectFromTwoPoints = function(pointA,pointB,minimumSideLength){
		minimumSideLength = minimumSideLength || 0;
		var topLeft = {x:0,y:0};
		var bottomRight = {x:0,y:0};
		if (pointA.x < pointB.x){
				topLeft.x = pointA.x;
				bottomRight.x = pointB.x;
		} else {
				topLeft.x = pointB.x;
				bottomRight.x = pointA.x;
		}
		if (pointA.y < pointB.y){
				topLeft.y = pointA.y;
				bottomRight.y = pointB.y;
		} else {
				topLeft.y = pointB.y;
				bottomRight.y = pointA.y;
		}
		var width = bottomRight.x - topLeft.x;
		var height = bottomRight.y - topLeft.y;
		if(width < minimumSideLength){
				bottomRight.x += minimumSideLength - width;
				width = bottomRight.x - topLeft.x;
		}
		if(height < minimumSideLength){
				bottomRight.y += minimumSideLength - height;
				height = bottomRight.y - topLeft.y;
		}
		return {
				left:topLeft.x,
				top:topLeft.y,
				right:bottomRight.x,
				bottom:bottomRight.y,
				width:width,
				height:height
		};
};

var pica = function(value){
    return value / 128;
};
var unpica = function(value){
    return Math.floor(value * 128);
}
var px = function(value){
    return sprintf("%spx",value);
}
var unpix = function(str){
    return str.slice(0,str.length-2);
}


var createCanvasRenderer = function(canvasElem){
	var boardContext = canvasElem[0].getContext("2d");
	var boardContent = {
		inks:{},
		highlighters:{},
		texts:{},
		multiWordTexts:{},
		images:{},
		videos:{}
	};
	var pressureSimilarityThreshold = 32,
    viewboxX = 0,
    viewboxY = 0,
    viewboxWidth = 80,//why wouldnt this be device size
    viewboxHeight = 60,
    boardWidth = 0,
    boardHeight = 0;

	/* //not sure what these are yet!
	var visibleBounds = [];
	var renders = {};
	*/

	var boardLimit = 10000;

	var precision = Math.pow(10,3);
	var round = function(n){
			return Math.round(n * precision) / precision;
	};
	var calculateImageBounds = function(image){
			image.bounds = [image.x,image.y,image.x + image.width,image.y + image.height];
			return image;
	}
	var calculateVideoBounds = function(video){
			video.bounds = [video.x,video.y,video.x + video.width,video.y + video.height];
			return video;
	}

	var determineCanvasConstants = _.once(function(){
		var currentDevice = "browser";
		if ("DeviceConfiguration" in window && "getCurrentDevice" in DeviceConfiguration){
			currentDevice = DeviceConfiguration.getCurrentDevice();
		}
			var maxX = 32767;//2147483647;
			var maxY = 32767;//2147483647;
			if (currentDevice == "browser"){
					//      maxX = 500;
					//      maxY = 500;
			}
			else if (currentDevice == "iPad" ){
					maxX = 6144;
					maxY = 6144;
			} else if (currentDevice == "iPhone"){
					maxX = 2048;
					maxY = 2048;
			} else if (currentDevice == "IE9"){
					maxX = 8192;
					maxY = 8192;
			}
			return {x:maxX,y:maxY};
	});

	var determineScaling = function(inX,inY){
			var outputX = inX * highQualityMultiplier;
			var outputY = inY * highQualityMultiplier;
			var outputScaleX = 1.0;
			var outputScaleY = 1.0;
			var canvasConstants = determineCanvasConstants();
			var maxX = canvasConstants.x;
			var maxY = canvasConstants.y;
			if (outputX > maxX){
					outputScaleX = maxX / outputX;
					outputX = outputX * outputScaleX;
					outputScaleY = outputScaleX;
					outputY = outputY * outputScaleX;
			}
			if (outputY > maxY){
					outputScaleY = maxY / outputY;
					outputY = outputY * outputScaleY;
					outputScaleX = outputScaleY;
					outputX = outputX * outputScaleY;
			}
			var returnObj = {
					width:outputX,
					height:outputY,
					scaleX:outputScaleX * highQualityMultiplier,
					scaleY:outputScaleY * highQualityMultiplier
			};
			return returnObj;
	}

	var incorporateBoardBounds = function(bounds){
			if (!isNaN(bounds[0])){
					boardContent.minX = Math.min(boardContent.minX,bounds[0]);
			}
			if (!isNaN(bounds[1])){
					boardContent.minY = Math.min(boardContent.minY,bounds[1]);
			}
			if (!isNaN(bounds[2])){
					boardContent.maxX = Math.max(boardContent.maxX,bounds[2]);
			}
			if (!isNaN(bounds[3])){
					boardContent.maxY = Math.max(boardContent.maxY,bounds[3]);
			}
			boardContent.width = boardContent.maxX - boardContent.minX;
			boardContent.height = boardContent.maxY - boardContent.minY;
	}
	var mergeBounds = function(b1,b2){
			var b = {};
			b.minX = Math.min(b1[0],b2[0]);
			b.minY = Math.min(b1[1],b2[1]);
			b.maxX = Math.max(b1[2],b2[2]);
			b.maxY = Math.max(b1[3],b2[3]);
			b.width = b.maxX - b.minX;
			b.height = b.maxY - b.minY;
			b.centerX = b.minX + b.width / 2;
			b.centerY = b.minY + b.height / 2;
			b[0] = b.minX;
			b[1] = b.minY;
			b[2] = b.maxX;
			b[3] = b.maxY;
			return b;
	}
	var isUsable = function(element){
			var boundsOk = !(_.some(element.bounds,function(p){
					return isNaN(p);// || p > boardLimit || p < -boardLimit;
			}));
			var sizeOk = "size" in element? !isNaN(element.size) : true
			var textOk =  "text" in element? element.text.length > 0 : true;
			/*
			var myGroups = _.map(Conversations.getCurrentGroup(),"id");
			var forMyGroup = _.isEmpty(element.audiences) ||
					Conversations.isAuthor() ||
					_.some(element.audiences,function(audience){
							return audience.action == "whitelist" && _.includes(myGroups,audience.name);
					});

			var isMine = element.author == UserSettings.getUsername();
			var isDirectedToMe = _.some(element.audiences,function(audience){
					return audience.action == "direct" && audience.name == UserSettings.getUsername();
			});
			var availableToMe = isMine || isDirectedToMe;// || forMyGroup;
			return boundsOk && sizeOk && textOk && availableToMe;
			*/
			return boundsOk && sizeOk && textOk; //&& availableToMe;
	}

	var transformReceived = function(transform){
		if (preTransform(transform)){
			var transformBounds = (function(){
				var myBounds = [undefined,undefined,undefined,undefined]; //minX,minY,maxX,maxY
				var incBounds = function(bounds){
					var max = function(count){
						var reference = myBounds[count];
						if (reference != undefined && !isNaN(reference)){
							myBounds[count] = Math.max(reference,bounds[count]);
						} else {
							myBounds[count] = bounds[count];
						}
					};
					var min = function(count){
						var reference = myBounds[count];
						if (reference != undefined && !isNaN(reference)){
							myBounds[count] = Math.min(reference,bounds[count]);
						} else {
							myBounds[count] = bounds[count];
						}
					};
					min(0);
					min(1);
					max(2);
					max(3);
				};
				var getBounds = function(){
					return myBounds;
				};
				var incBoardBounds = function(){
					var thisBounds = getBounds();
					if (thisBounds[0] != undefined && thisBounds[1] != undefined && thisBounds[2] != undefined && thisBounds[3] != undefined){
						incorporateBoardBounds(thisBounds);
					}
				};
				var setMinX = function(input){
					safelySet(input,0);
				};
				var setMinY = function(input){
					safelySet(input,1);
				};
				var setMaxX = function(input){
					safelySet(input,2);
				};
				var setMaxY = function(input){
					safelySet(input,3);
				};
				var safelySet = function(input,reference){
					if (input != undefined && !isNaN(input)){
						myBounds[reference] = input;
					}
				};
				return {
						"minX":getBounds[0],
						"setMinX":setMinX,
						"minY":getBounds[1],
						"setMinY":setMinY,
						"maxX":getBounds[2],
						"setMaxX":setMaxX,
						"maxY":getBounds[3],
						"setMaxY":setMaxY,
						"incorporateBounds":incBounds,
						"getBounds":getBounds,
						"incorporateBoardBounds":incBoardBounds
				};
			})();
			
			var relevantInks = [];
			var relevantTexts = [];
			var relevantMultiWordTexts = [];
			var relevantImages = [];
			var relevantVideos = [];
			_.forEach(transform.inkIds,function(id,i){
				var cand1 = boardContent.inks[id];
				if (cand1 !== undefined){
					relevantInks.push(cand1);
				}
				var cand2 = boardContent.highlighters[id];
				if (cand2 !== undefined){
					relevantInks.push(cand2);
				}
			});
			_.forEach(transform.imageIds,function(id,i){
				var cand = boardContent.images[id];
				if (cand !== undefined){
					relevantImages.push(cand);
				}
			});
			_.forEach(transform.videoIds,function(id,i){
				var cand = boardContent.videos[id];
				if (cand !== undefined){
					relevantVideos.push(cand);
				}
			});
			_.forEach(transform.textIds,function(id,i){
				var cand = boardContent.texts[id];
				if (cand !== undefined){
					relevantTexts.push(cand);
				}
			});
			_.forEach(transform.multiWordTextIds,function(id,i){
//				if(id in Modes.text.echoesToDisregard) return;
				var cand = boardContent.multiWordTexts[id];
				if (cand !== undefined){
					relevantMultiWordTexts.push(cand);
				}
			});
			var point = function(x,y){return {"x":x,"y":y};};
			var totalBounds = point(0,0);
			
			var deltaX = transform.xTranslate || 0;
			var deltaY = transform.yTranslate || 0;

			if(transform.xScale != 1 || transform.yScale != 1){
				if ("xOrigin" in transform && "yOrigin" in transform){
					totalBounds.x = transform.xOrigin;
					totalBounds.y = transform.yOrigin;
				} else {
					var first = true;
					var updateRect = function(point){
						if (first){
							totalBounds.x = point.x;
							totalBounds.y = point.y;
							first = false;
						} else {
							if (point.x < totalBounds.x){
								totalBounds.x = point.x;
							}
							if (point.y < totalBounds.y){
								totalBounds.y = point.y;
							}
						}
					};
					$.each(relevantInks,function(i,ink){
						if (ink != undefined && "bounds" in ink && _.size(ink.bounds) > 1){
							updateRect(point(ink.bounds[0],ink.bounds[1]));
						}
					});
					$.each(relevantTexts,function(i,text){
						if (text != undefined && "x" in text && "y" in text){
							updateRect(point(text.x,text.y));
						}
					});
					$.each(relevantMultiWordTexts,function(i,text){
						if (text != undefined && "x" in text && "y" in text){
							updateRect(point(text.x,text.y));
						}
					});
					$.each(relevantImages,function(i,image){
						if (image != undefined && "x" in image && "y" in image){
							updateRect(point(image.x,image.y));
						}
					});
					$.each(relevantVideos,function(i,video){
						if (video != undefined && "x" in video && "y" in video){
							updateRect(point(video.x,video.y));
						}
					});
				}
				transformBounds.setMinX(totalBounds.x);
				transformBounds.setMinY(totalBounds.y);
			}
			_.forEach(relevantInks,function(ink,i){
				if (preTransformItem(ink,transform)){
					if (transform.isDeleted){
						if (preDeleteItem(ink)){
							if (boardContent.inks[ink.identity] == ink){
								delete boardContent.inks[ink.identity];
							}
							if (boardContent.highlighters[ink.identity] == ink){
								delete boardContent.highlighters[ink.identity];
							}
							postDeleteItem(ink);
						}
						postTransformItem(ink,transform);
					} else {
						if (transform.xScale != 1 || transform.yScale != 1){
							var ps = ink.points;
							var xPos = ink.bounds[0];
							var yPos = ink.bounds[1];
							var xp, yp;

							var internalX = xPos - totalBounds.x;
							var internalY = yPos - totalBounds.y;
							var offsetX = -(internalX - (internalX * transform.xScale));
							var offsetY = -(internalY - (internalY * transform.yScale));

							for(var p = 0; p < ps.length; p += 3){
								xp = ps[p] - xPos;
								yp = ps[p + 1] - yPos;
								ps[p] = (xPos + xp * transform.xScale) + offsetX;
								ps[p+1] = (yPos + yp * transform.yScale) + offsetY;
							}
							calculateInkBounds(ink);
							transformBounds.incorporateBounds(ink.bounds);

						}
						if (transform.xTranslate != 0 || transform.yTranslate != 0){
							var ps = ink.points;
							for(var p = 0; p < ps.length; p += 3){
								ps[p] += deltaX;
								ps[p+1] += deltaY;
							}
							calculateInkBounds(ink);
							transformBounds.incorporateBounds(ink.bounds);
						}
						postTransformItem(ink,transform);
					}
				}
			});
			_.forEach(relevantImages,function(image,i){
				if (preTransformItem(image,transform)){
					if (transform.isDeleted){
						if (preDeleteItem(image)){
							if (boardContent.images[image.identity] == image){
								delete boardContent.images[image.identity];
							}
							postDeleteItem(image);
						}
						postTransformItem(image,transform);
					} else {
						if (transform.xScale != 1 || transform.yScale != 1){
							image.width = image.width * transform.xScale;
							image.height = image.height * transform.yScale;

							var internalX = image.x - totalBounds.x;
							var internalY = image.y - totalBounds.y;
							var offsetX = -(internalX - (internalX * transform.xScale));
							var offsetY = -(internalY - (internalY * transform.yScale));
							image.x = image.x + offsetX;
							image.y = image.y + offsetY;

							calculateImageBounds(image);
							transformBounds.incorporateBounds(image.bounds);
						}
						if (transform.xTranslate != 0 || transform.yTranslate != 0){
							image.x += transform.xTranslate;
							image.y += transform.yTranslate;
							calculateImageBounds(image);
							transformBounds.incorporateBounds(image.bounds);
						}
						postTransformItem(image,transform);
					}
				}
			});
			_.forEach(relevantVideos,function(video,i){
				if (preTransformItem(video,transform)){
					if (transform.isDeleted){
						if (preDeleteItem(video)){
							if (boardContent.videos[video.identity] == video){
								delete boardContent.videos[video.identity];
							}
							postDeleteItem(video);
						}
						postTransformItem(video,transform);
					} else {
						if (transform.xScale != 1 || transform.yScale != 1){
							video.width = video.width * transform.xScale;
							video.height = video.height * transform.yScale;

							var internalX = video.x - totalBounds.x;
							var internalY = video.y - totalBounds.y;
							var offsetX = -(internalX - (internalX * transform.xScale));
							var offsetY = -(internalY - (internalY * transform.yScale));
							video.x = video.x + offsetX;
							video.y = video.y + offsetY;

							calculateVideoBounds(video);
							transformBounds.incorporateBounds(video.bounds);
						}
						if (transform.xTranslate != 0 || transform.yTranslate != 0){
							video.x += transform.xTranslate;
							video.y += transform.yTranslate;
							calculateVideoBounds(video);
							transformBounds.incorporateBounds(video.bounds);
						}
						postTransformItem(video,transform);
					}
				}
			});
			_.forEach(relevantTexts,function(text,i){
				if (preTransformItem(text,transform)){
					if (transform.isDeleted){
						if (preDeleteItem(text)){
							if (boardContent.texts[text.identity] == text){
								delete boardContent.texts[text.identity];
							}
							postDeleteItem(text);
						}
						postTransformItem(text,transform);
					} else {
						if (transform.xScale != 1 || transform.yScale != 1){
							text.width = text.width * transform.xScale;
							text.height = text.height * transform.yScale;

							var internalX = text.x - totalBounds.x;
							var internalY = text.y - totalBounds.y;
							var offsetX = -(internalX - (internalX * transform.xScale));
							var offsetY = -(internalY - (internalY * transform.yScale));
							text.x = text.x + offsetX;
							text.y = text.y + offsetY;

							text.size = text.size * transform.yScale;
							if(isUsable(text)){
								prerenderText(text);
								calculateTextBounds(text);
							}
							else{
								if(text.identity in boardContent.texts){
									delete boardContent.texts[text.identity];
								}
							}
							transformBounds.incorporateBounds(text.bounds);
						}
						if (transform.xTranslate != 0 || transform.yTranslate != 0){
							text.x += transform.xTranslate;
							text.y += transform.yTranslate;
							calculateTextBounds(text);
							transformBounds.incorporateBounds(text.bounds);
						}
						postTransformItem(text,transform);
					}
				}
			});
			_.forEach(relevantMultiWordTexts,function(multiWordText,i){
				if (preTransformItem(multiWordText,transform)){
					if (transform.isDeleted){
						if (preDeleteItem(multiWordText)){
							if (boardContent.multiWordTexts[multiWordText.identity] == multiWordText){
								delete boardContent.multiWordTexts[multiWordText.identity];
							}
							postDeleteItem(multiWordText);
						}
						postTransformItem(multiWordText,transform);
					} else {
						var text = multiWordText;
						if (transform.xScale != 1 || transform.yScale != 1){
							var newWidth = (text.width || text.requestedWidth) * transform.xScale;
							text.requestedWidth = newWidth;
							text.width = text.requestedWidth;
							text.doc.width(text.width);
							_.each(text.words,function(word){
									word.size = word.size * transform.xScale;
							});

							var internalX = text.x - totalBounds.x;
							var internalY = text.y - totalBounds.y;

							var offsetX = -(internalX - (internalX * transform.xScale));
							var offsetY = -(internalY - (internalY * transform.yScale));
							text.doc.position = {x:text.x + offsetX,y:text.y + offsetY};
							text.doc.load(text.words);
							transformBounds.incorporateBounds(text.bounds);
						}
						if (transform.xTranslate != 0 || transform.yTranslate != 0){
//							if(text.id in Modes.text.echoesToDisregard) return;
							var doc = text.doc;
							doc.position.x += transform.xTranslate;
							doc.position.y += transform.yTranslate;
							text.x = doc.position.x;
							text.y = doc.position.y;
							text.doc.invalidateBounds();
							transformBounds.incorporateBounds(text.bounds);
						}
						postTransformItem(multiWordText,transform);
					}
				}
			});
			transformBounds.incorporateBoardBounds();
			postTransform(transform);
			render();
		}
	}

	var addStanzaFunc = function(stanza){
		if (stanza !== undefined && "type" in stanza){
			switch (stanza.type) {
				case "moveDelta":
					transformReceived(stanza);
					break;
				case "ink":
					var ink = stanza;
					prerenderInk(ink,true);
					if (ink.isHighlighter){
						boardContent.highlighters[ink.identity] = stanza;
					} else {
						boardContent.inks[ink.identity] = stanza;
					}
					render();
					break;
				case "text":
					prerenderText(stanza,true);
					boardContent.texts[stanza.identity] = stanza;
					render();
					break;
				case "image":
					var image = stanza;
					image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
					incorporateBoardBounds(image.bounds);
					var dataImage = new Image();
					image.imageData = dataImage;
					var url = calculateImageSource(image,true);
					dataImage.onload = function(data){
						var shouldReCalcBounds = false;
						if (image.width == 0){
							image.width = dataImage.naturalWidth;
							shouldReCalcBounds = true;
						}
						if (image.height == 0){
							image.height = dataImage.naturalHeight;
							shouldReCalcBounds = true;
						}
						if (shouldReCalcBounds){
							image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
							incorporateBoardBounds(image.bounds);
						}
						prerenderImage(image);
						calculateImageBounds(image);
						boardContent.images[stanza.identity] = stanza;
						render();
					};
					dataImage.onError = function(error){
						passException(error,"addStanza:imageDataLoad",[dataImage,image]);
					};
					dataImage.src = url;
					break;
				case "multiWordText":
					console.log("add mwt",stanza);
					var newStanza = prerenderMultiwordText(stanza,true);
					console.log("add mwt postPreRender",stanza);
					boardContent.multiWordTexts[stanza.identity] = newStanza;
					render();
					break;
				case "video":
					prerenderVideo(stanza,true);
					boardContent.videos[stanza.identity] = stanza;
					render();
					break;
				default:
					break;	
			}
			historyUpdated(boardContent);
		}
	};
	var highQualityMultiplier = 4;
	var prerenderInk = function(ink,onBoard){
			if(!isUsable(ink)){
					if(ink.identity in boardContent.inks){
							delete boardContent.inks[ink.identity];
					}
					if(ink.identity in boardContent.highlighters){
							delete boardContent.highlighters[ink.identity];
					}
					return false;
			}
			calculateInkBounds(ink);
			if(onBoard){
					incorporateBoardBounds(ink.bounds);
			}
			//var isPrivate = ink.privacy.toUpperCase() == "PRIVATE";
			var rawWidth = (ink.bounds[2] - ink.bounds[0] + (ink.thickness));
			var rawHeight = (ink.bounds[3] - ink.bounds[1] + (ink.thickness));

			var scaleMeasurements = determineScaling(rawWidth,rawHeight);

			var scaleX = scaleMeasurements.scaleX;
			var scaleY = scaleMeasurements.scaleY;

			var canvas = $("<canvas />",{
					width:scaleMeasurements.width,
					height:scaleMeasurements.height
			})[0];
			ink.canvas = canvas;
			var context = canvas.getContext("2d");
			canvas.width = scaleMeasurements.width;
			canvas.height = scaleMeasurements.height;
			var rawPoints = _.clone(ink.points);
			var points = [];
			var x,y,pr,p;
			for (p = 0; p < rawPoints.length; p += 3){
					points.push(rawPoints[p] * scaleX);
					points.push(rawPoints[p + 1] * scaleY);
					points.push(rawPoints[p + 2] / 256);
			}
			var contentOffsetX = -1 * ((ink.minX - ink.thickness / 2)) * scaleX;
			var contentOffsetY = -1 * ((ink.minY - ink.thickness / 2)) * scaleY;
			var scaledThickness = ink.thickness * scaleX;
			/*
			if(isPrivate){
					x = points[0] + contentOffsetX;
					y = points[1] + contentOffsetY;
					context.lineWidth = scaledThickness;
					context.lineCap = "round";
					context.strokeStyle = "red";
					context.globalAlpha = 0.3;
					context.moveTo(x,y);
					for(p = 0; p < points.length; p += 3){
							context.beginPath();
							context.moveTo(x,y);
							x = points[p]+contentOffsetX;
							y = points[p+1]+contentOffsetY;
							pr = scaledThickness * points[p+2];
							context.lineWidth = pr + 2;
							context.lineTo(x,y);
							context.stroke();
					}
					context.globalAlpha = 1.0;
			}
			*/
			context.strokeStyle = ink.color[0];
			context.fillStyle = ink.color[0];
			x = points[0] + contentOffsetX;
			y = points[1] + contentOffsetY;

			context.beginPath();
			context.moveTo(x,y);
			pr = scaledThickness * points[2];
			context.arc(x,y,pr/2,0,2 * Math.PI);
			context.fill();
			context.lineCap = "round";
			for(p = 0; p < points.length; p += 3){
					context.beginPath();
					context.moveTo(x,y);
					x = points[p+0] + contentOffsetX;
					y = points[p+1] + contentOffsetY;
					pr = scaledThickness * points[p+2];
					context.lineWidth = pr;
					context.lineTo(x,y);
					context.stroke();
			}
			return true;
	};

	var renderViewbox = function(){
		boardContext.strokeStyle = "#FF0000";
		boardContext.beginPath();
		var tl = worldToScreen(viewboxX,viewboxY);
		var br = worldToScreen(viewboxX + viewboxWidth, viewboxY + viewboxHeight);
		boardContext.lineWidth = 2;
		boardContext.moveTo(tl.x,tl.y);
		boardContext.lineTo(br.x,tl.y);
		boardContext.lineTo(br.x,br.y);
		boardContext.lineTo(tl.x,br.y);
		boardContext.lineTo(tl.x,tl.y);
		boardContext.stroke();
	};
	var renderCanvasEdges = function(){
		boardContext.strokeStyle = "#0000FF";
		boardContext.beginPath();
		boardContext.lineWidth = 2;
		boardContext.moveTo(0,0);
		boardContext.lineTo(0,boardWidth);
		boardContext.lineTo(boardHeight,boardWidth);
		boardContext.lineTo(boardHeight,0);
		boardContext.lineTo(0,0);
		boardContext.stroke();
	};
	var calculateImageSource = function(image){
		return image.source;
	}
	var calculateVideoSource = function(video){
		return video.source;
	}
	var calculateTextBounds = function(text){
			text.bounds = [text.x,text.y,text.x + text.width, text.y + (text.runs.length * text.size * 1.25)];
			return text;
	}
	var calculateInkBounds = function(ink){
			var minX = Infinity;
			var minY = Infinity;
			var maxX = -Infinity;
			var maxY = -Infinity;
			var widths = [];
			var points = ink.points;
			var hw = ink.thickness / 2;
			var hh = ink.thickness / 2;
			if(points.length == 6){
					minX = points[0] - hw;
					maxX = points[0] + hw;
					minY = points[1] - hh;
					maxY = points[1] + hh;
					widths.push(points[2]);
			}
			else{
					for(var cindex = 0; cindex < points.length; cindex += 3){
							var x = round(points[cindex]);
							var y = round(points[cindex+1]);
							points[cindex] = x;
							points[cindex+1] = y;
							widths.push(points[cindex+2]);
							minX = Math.min(x - hw,minX);
							minY = Math.min(y - hh,minY);
							maxX = Math.max(x + hw ,maxX);
							maxY = Math.max(y + hh,maxY);
					}
			}
			ink.minX = minX;
			ink.minY = minY;
			ink.maxX = maxX;
			ink.maxY = maxY;
			ink.width = maxX - minX;
			ink.height = maxY - minY;
			ink.centerX = minX + hw;
			ink.centerY = minY + hh;
			ink.bounds=[minX,minY,maxX,maxY];
			ink.widths=widths;
			return ink;
	}
	var scale = function(){
			return Math.min(boardWidth / viewboxWidth, boardHeight / viewboxHeight);
	}

	var textEditorFor = function(t){
		var editor = boardContent.multiWordTexts[t.identity];
		if(!editor){
				editor = boardContent.multiWordTexts[t.identity] = t;
		}
		if(!editor.doc){
			var minimumWidth = 100;
			var minimumHeight = 30;
			editor.doc = carota.editor.create(
				$("<div />",{id:sprintf("t_%s",t.identity)}).appendTo($("#textInputInvisibleHost"))[0],
				canvasElem[0],
				t,
				rendererObj,
				function(eventName,eventParams){
					//do nothing with this yet.  This could receive:
					//"boundsChanged",[carotaEditorId,carotaEditor,t,newBounds = t.bounds] //already changed by the time the callback is triggered
					//"loaded",[carotaEditorId,carotaEditor,t,]
					//"textInserted",[carotaEditorId,carotaEditor,t,newText]
					//"selectionChanged",[carotaEditorId,carotaEditor,t,selectionStart,selectionEnd]
					//"undo",[carotaEditorId,carotaEditor,t]
				},
				minimumWidth,minimumHeight);
			editor.doc.position = {x:t.x,y:t.y};
			editor.doc.width(t.width);
		} 
		return editor;
	};

	var prerenderMultiwordText = function(text){
		var editor = textEditorFor(text).doc;
		editor.load(text.words);
		editor.invalidateBounds();
		editor.updateCanvas();
		incorporateBoardBounds(editor.stanza.bounds);
		return editor.stanza;
	}
	var prerenderImage = function(image) {
		var canvas = $("<canvas/>")[0];
		image.canvas = canvas;
		canvas.width = image.width;
		canvas.height = image.height;

		var borderW = canvas.width * 0.10;
		var borderH = canvas.height * 0.10;
		canvas.width = image.width + borderW;
		canvas.height = image.height + borderH;
		var context = canvas.getContext("2d");
		context.drawImage(image.imageData,borderW / 2,borderH / 2,image.width, image.height);
		/*
		if(image.privacy.toUpperCase() == "PRIVATE"){
				context.globalAlpha = 0.2;
				context.fillStyle = "red";
				context.fillRect(
						0,0,
						canvas.width,
						canvas.height);
				context.globalAlpha = 1.0;
		}
		*/
		delete image.imageData;
	}
	var prerenderVideo = function(video){
		video.bounds = [video.x,video.y,video.x + video.width,video.y + video.height];
		if (!("video" in video)){
			var vid = $("<video/>",{
				preload:"auto",
				src:calculateVideoSource(video)
			});
			video.video = vid[0];
			video.getState = function(){
				return {
					paused:vid[0].paused,
					ended:vid[0].ended,
					currentTime:vid[0].currentTime,
					duration:vid[0].duration,
					muted:vid[0].muted,
					volume:vid[0].volume,
					readyState:vid[0].readyState,
					played:vid[0].played,
					buffered:vid[0].buffered,
					playbackRate:vid[0].playbackRate,
					loop:vid[0].loop
				};
			};
			video.seek = function(newPosition){
				vid[0].currentTime = Math.min(vid[0].duration,Math.max(0,newPosition));
				if (vid[0].paused){
					video.play();
				}
			};
			video.muted = function(newState){
				if (newState != undefined){
					vid[0].muted = newState;
				}
				return vid[0].muted;
			};
			video.play = function(){
				var paintVideoFunc = function(){
					if (video.video.paused || video.video.ended){
						return false;
					} else {
						requestAnimationFrame(function(){
							blit();
							paintVideoFunc();
						});
						return true;
					}
				};
				video.video.addEventListener("play",function(){
					paintVideoFunc();
				},false);
				if (video.video.paused || video.video.ended){
					video.video.play();
				}
			};
			video.destroy = function(){
				video.video.removeAttribute("src");
				video.video.load();
			};
			video.pause = function(){
				if (!video.video.paused){
					video.video.pause();
				}
			};
		}
		if (!("bounds" in video)){
			calculateVideoBounds(video);
		}
		incorporateBoardBounds(video.bounds);
		return video;
	}
	var prerenderText = function(text){
			var canvas = $("<canvas />")[0];

			text.canvas = canvas;
			var context = canvas.getContext("2d");
			context.strokeStyle = text.color;
			context.font = text.font;
			var newline = /\n/;
			if(!text.width){
					text.width = Math.max.apply(Math,text.text.split(newline).map(
							function(subtext){
									return context.measureText(subtext).width;
							}));
			}
			var run = "";
			var yOffset = 0;
			var runs = [];
			var breaking = false;
			_.each(text.text.split(''),function(c,i){
					if(c.match(newline)){
							runs.push(""+run);
							run = "";
							return;
					}
					else if(breaking && c == " "){
							runs.push(run);
							run = "";
							return;
					}
					var w = context.measureText(run).width;
					breaking = w >= text.width - 80;
					run += c;
			});
			runs.push(run);
			runs = runs.map(function(r){
					return r.trim();
			});
			text.runs = runs;
			calculateTextBounds(text);
			var rawWidth = (text.bounds[2] - text.bounds[0]);
			var rawHeight = (text.bounds[3] - text.bounds[1]);
			var scaleMeasurements = determineScaling(rawWidth,rawHeight);
			
			var scaleX = scaleMeasurements.scaleX;
			var scaleY = scaleMeasurements.scaleY;
			
			var contentOffsetX = 0;
			var contentOffsetY = 0;

			canvas.width = scaleMeasurements.width + contentOffsetX;
			canvas.height = scaleMeasurements.height + contentOffsetY;

			text.height = rawHeight;

			/*
			if(text.privacy.toUpperCase() == "PRIVATE"){
					context.globalAlpha = 0.2;
					context.fillStyle = "red";
					context.fillRect(
							0,0,
							scaleMeasurements.width,
							scaleMeasurements.height);
					context.globalAlpha = 1.0;
			}
			*/
			context.fillStyle = text.color[0];
			context.textBaseline = "top";
			function generateTextFont(text) {
					var font = px(text.size * scaleY) + " " + text.font;
					if(text.weight == "bold")
							font = font + ' bold';
					if(text.style == "italic")
							font = font + ' italic';
					return font;
			}

			_.each(text.runs,function(run,ri){
					var underline = function(){
							var lines = text.height/(text.size * 1.25 * scaleY);
							var range = _.range(text.size, text.height, text.height/lines);
							_.each(range, function(y){
									context.beginPath();
									context.strokeStyle = text.color[0];
									var underlineY = contentOffsetY + y;
									context.moveTo(contentOffsetX, underlineY);
									var underlineEndX = contentOffsetX + scaleMeasurements.width;
									context.lineTo(underlineEndX, underlineY);
									context.stroke();
							});
					};
					var _yOffset = ri * text.size * 1.25 * scaleY;
					context.font = generateTextFont(text);
					context.fillText(run,
													 contentOffsetX * scaleX,
													 (contentOffsetY + _yOffset),
													 scaleMeasurements.width);
					if(text.decoration == "underline")
							underline();

			});
			incorporateBoardBounds(text.bounds);
	}

	var renderInks = function(inks,rendered,viewBounds){
			if (inks != undefined){
					_.each(inks,function(ink,i){
							try{
								if (preRenderItem(ink,boardContext)){
									if(intersectRect(ink.bounds,viewBounds)){
											drawInk(ink);
											postRenderItem(ink,boardContext);
											rendered.push(ink);
									}
								}
							}
							catch(e){
									passException(e,"renderInks",[i,ink]);
							}
					});
			}
	};
	var renderRichTexts = function(texts,rendered,viewBounds){
		if(texts){
			_.each(texts,function(text,i){
				if (preRenderItem(text,boardContext)){
					if(text.doc){
						if(!text.bounds){
							text.doc.invalidateBounds();
						}
						if(intersectRect(text.bounds,viewBounds)){
							drawMultiwordText(text);
							postRenderItem(text,boardContext);
							rendered.push(text);
						}
					}
				}
			});
		}
	};
	var renderVideos = function(videos,rendered,viewBounds){
		if (videos){
			//Modes.clearCanvasInteractables("videos");
			_.each(videos,function(video,i){
				if (preRenderItem(video,boardContext)){
					if (intersectRect(video.bounds,viewBounds)){
							drawVideo(video);
							//Modes.pushCanvasInteractable("videos",videoControlInteractable(video));
							postRenderItem(video,boardContext);
							rendered.push(video);
					}
				}
			});
		}
	};

	var renderTexts = function(texts,rendered,viewBounds){
			_.each(texts,function(text,i){
				if (preRenderItem(text,boardContext)){
					if(intersectRect(text.bounds,viewBounds)){
							drawText(text);
							postRenderItem(text,boardContext);
							rendered.push(text);
					}
				}
			});
	};
	var renderImmediateContent = function(content,rendered,viewBounds){
		renderVideos(content.videos,rendered,viewBounds);
		renderInks(content.highlighters,rendered,viewBounds);
		renderTexts(content.texts,rendered,viewBounds);
		renderRichTexts(content.multiWordTexts,rendered,viewBounds);
		renderInks(content.inks,rendered,viewBounds);
	};

	var proportion = function(width,height){
			var targetWidth = boardWidth;
			var targetHeight = boardHeight;
			return (width / height) / (targetWidth / targetHeight);
	}
	var scaleScreenToWorld = function(i){
			var p = proportion(viewboxWidth,viewboxHeight);//boardWidth,boardHeight);
			var scale;
			if(p > 1){//Viewbox wider than board
					scale = viewboxWidth / boardWidth;
			}
			else{//Viewbox narrower than board
					scale = viewboxHeight / boardHeight;
			}
			return i * scale;
	}
	var scaleWorldToScreen = function(i){
			var p = proportion(viewboxWidth,viewboxHeight);//boardWidth,boardHeight);
			var scale;
			if(p > 1){//Viewbox wider than board
					scale = viewboxWidth / boardWidth;
			}
			else{//Viewbox narrower than board
					scale = viewboxHeight / boardHeight;
			}
			return i / scale;
	}

	var screenToWorld = function(x,y){
			var worldX = scaleScreenToWorld(x) + viewboxX;
			var worldY = scaleScreenToWorld(y) + viewboxY;
			return {x:worldX,y:worldY};
	}
	var worldToScreen = function(x,y){
			var screenX = scaleWorldToScreen(x - viewboxX);
			var screenY = scaleWorldToScreen(y - viewboxY);
			return {x:screenX,y:screenY};
	}

	var renderImages = function(images,rendered,viewBounds){
			_.each(images,function(image,id){
					try{
						if (preRenderItem(image)){
							if(intersectRect(image.bounds,viewBounds)){
									drawImage(image);
									postRenderItem(image,boardContext);
									rendered.push(image);
							}
						}
					}
					catch(e){
						passException(e,"renderImages",[i,image]);
					}
			});
	};

	var clearBoard = function(rect){
			try {
					var r = rect == undefined ? {x:0,y:0,w:boardWidth,h:boardHeight} : rect;
					boardContext.clearRect(r.x,r.y,r.w,r.h);
			} catch(e){
				passException(e,"clearBoard",[rect]);
			}
	}
	var isInClearSpace = function(bounds){
			return !_.some(visibleBounds,function(onscreenElement){
					return intersectRect(onscreenElement,bounds);
			});
	};
	var screenBounds = function(worldBounds){
			var screenPos = worldToScreen(worldBounds[0],worldBounds[1]);
			var screenLimit = worldToScreen(worldBounds[2],worldBounds[3]);
			var screenWidth = screenLimit.x - screenPos.x;
			var screenHeight = screenLimit.y - screenPos.y;
			return {
					screenPos:screenPos,
					screenLimit:screenLimit,
					screenWidth:screenWidth,
					screenHeight:screenHeight
			};
	};
	var scaleCanvas = function(incCanvas,w,h){
			if (w >= 1 && h >= 1){
					var canvas = $("<canvas />");
					canvas.width = w;
					canvas.height = h;
					canvas.attr("width",w);
					canvas.attr("height",h);
					canvas.css({
							width:px(w),
							height:px(h)
					});
					var ctx = canvas[0].getContext("2d");
					ctx.drawImage(incCanvas,0,0,w,h);
					return canvas[0];
			} else {
					return incCanvas;
			}
	};

	var mipMappingEnabled = true;
	var multiStageRescale = function(incCanvas,w,h,stanza){
			if (mipMappingEnabled){
					stanza = stanza == undefined ? {} : stanza;
					if (!("mipMap" in stanza)){
							stanza.mipMap = {};
					}
					var mm = stanza.mipMap;
					var sf = 0.5;
					var iw = incCanvas.width;
					var ih = incCanvas.height;
					var save = true;

					var iwSize = Math.floor(iw);
					if (w >= 1 && iw >= 1 && w < iw){ //shrinking
							var sdw = iw * sf;
							var sdh = ih * sf;
							if (sdw < w){
									return incCanvas;
							} else {
									var key = Math.floor(sdw);
									if (!(key in mm)){
											var newCanvas = scaleCanvas(incCanvas,sdw,sdh);
											mm[key] = newCanvas;
									}
									return multiStageRescale(mm[key],w,h,stanza);
							}
					} else {
							return incCanvas;
					}
			} else {
					return incCanvas;
			}
	}

	var drawImage = function(image){
			try{
				if (image.canvas != undefined){
					var sBounds = screenBounds(image.bounds);
					visibleBounds.push(image.bounds);
					if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
						var borderW = sBounds.screenWidth * 0.10;
						var borderH = sBounds.screenHeight * 0.10;
						boardContext.drawImage(multiStageRescale(image.canvas,sBounds.screenWidth,sBounds.screenHeight,image), sBounds.screenPos.x - (borderW / 2), sBounds.screenPos.y - (borderH / 2), sBounds.screenWidth + borderW ,sBounds.screenHeight + borderH);
					}
				}
			}
			catch(e){
				passException(e,"drawImage",[image]);
			}
	}

	var drawMultiwordText = function(item){
			try {
				if(item.doc && item.doc.canvas){
					var sBounds = screenBounds(item.bounds);
					visibleBounds.push(item.bounds);
					if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
						boardContext.drawImage(multiStageRescale(item.doc.canvas,sBounds.screenWidth,sBounds.screenHeight,item), sBounds.screenPos.x, sBounds.screenPos.y, sBounds.screenWidth,sBounds.screenHeight);
					}
				}
			}
			catch(e){
				passException(e,"drawMutliwordText",[item]);
			}
	}
	var drawText = function(text){
			try{
					var sBounds = screenBounds(text.bounds);
					visibleBounds.push(text.bounds);
					if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
							boardContext.drawImage(multiStageRescale(text.canvas,sBounds.screenWidth,sBounds.screenHeight,text),
																			sBounds.screenPos.x,
																			sBounds.screenPos.y,
																			sBounds.screenWidth,
																			sBounds.screenHeight);
					}
			}
			catch(e){
				passException(e,"drawText",[text]);
			}
	}
	var drawInk = function(ink){
			var sBounds = screenBounds(ink.bounds);
			visibleBounds.push(ink.bounds);
			var c = ink.canvas;
			if(!c){
					c = ink.canvas = prerenderInk(ink);
			}
			var cWidth = c.width;
			var cHeight = c.height;
			if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
					var img = multiStageRescale(c,sBounds.screenWidth,sBounds.screenHeight,ink);
					if(img){
						try{
								var inset = ink.thickness / 2;
								var sW = img.width;
								var sH = img.height;
								var xFactor = img.width / cWidth;
								var yFactor = img.height / cHeight;
								var iX = inset * xFactor;
								var iY = inset * yFactor;

								var tX = sBounds.screenPos.x - iX;
								var tY = sBounds.screenPos.y - iY;
								var tW = sBounds.screenWidth + (2 * iX);
								var tH = sBounds.screenHeight + (2 * iY);
								boardContext.drawImage(img,
																				0, 0,
																				sW, sH,
																				tX,tY,
																				tW,tH);
						}
						catch(e){
							passException(e,"drawInk",[ink,img]);
						}
					} else {
						c = ink.canvas = prerenderInk(ink,incCanvasContext);
						img = multiStageRescale(c,sBounds.screenWidth,sBounds.screenHeight,ink);
					}
			}
	}
	var drawVideo = function(video){
			var sBounds = screenBounds(video.bounds);
			visibleBounds.push(video.bounds);
			if (sBounds.screenHeight >= 1 && sBounds.screenWidth >= 1){
					boardContext.drawImage(video.video,
																	sBounds.screenPos.x,sBounds.screenPos.y,
																	sBounds.screenWidth,sBounds.screenHeight);
			}
	}

	var measureBoardContent = function(includingText){
			if(includingText){
					_.each(boardContent.multiWordTexts,function(t){
							t.doc.invalidateBounds();
					});
			}
			var content = _.flatMap([boardContent.multiWordTexts,boardContent.inks,boardContent.images,boardContent.videos],_.values);
			if(content.length == 0){
					boardContent.height = boardHeight;
					boardContent.width = boardWidth;
			}
			else{
					var bs = _.map(content,"bounds")
					bs.push([0,0,0,0]);/*Ensure origin is included*/
					var bounds = _.reduce(bs,mergeBounds);
					boardContent.width = bounds.width;
					boardContent.height = bounds.height;
					boardContent.minX = bounds.minX;
					boardContent.minY = bounds.minY;
			}
	}

	var adaptViewboxToCanvas = function(){
		//this readjusts the viewbox to include the visible canvas
		var tl = screenToWorld(0,0);
		var br = screenToWorld(boardWidth,boardHeight);
/*
		viewboxX = tl.x;
		viewboxY = tl.y;
		viewboxWidth = br.x - tl.x;
		viewboxHeight = br.y - tl.y;
*/
	};

	var render = function(){
		var renderStart = new Date().getTime();
		try {
			if (boardContent !== undefined){
				var content = boardContent;
				clearBoard({x:0,y:0,w:boardWidth,h:boardHeight});
				canvasElem.width(boardWidth);
				canvasElem.height(boardHeight);	
				canvasElem.attr("width",boardWidth);
				canvasElem.attr("height",boardHeight);	
				canvasElem[0].width = boardWidth;
				canvasElem[0].height = boardHeight;	
				renderStarting(boardContext,canvasElem,boardContent);
				if(content){
					try{
						visibleBounds = [];
						var rendered = [];
						adaptViewboxToCanvas();
						var viewBounds = [viewboxX,viewboxY,viewboxX + viewboxWidth,viewboxY + viewboxHeight];
						renderImages(content.images,rendered,viewBounds);
						renderImmediateContent(content,rendered,viewBounds);

						//REMOVE ME FIX ME drawViewbox
						//renderCanvasEdges();
						renderViewbox();
						statistic("render",new Date().getTime() - renderStart,true);
					}
					catch(e){
						passException(e,"renderWithContent",[content]);
						statistic("render",new Date().getTime() - renderStart,false,e);
					}
				}
				renderComplete(boardContext,canvasElem,boardContent);
			}
		} catch(e){
			passException(e,"render",[]);
			statistic("render",new Date().getTime() - renderStart,false,e);
		}
	}
	var blit = function(content){
		render();
	};

	var preRenderHistory = function(history,afterFunc){
		var start = new Date().getTime();
		try {
			history.multiWordTexts = _.pickBy(history.multiWordTexts,isUsable);
			history.texts = _.pickBy(history.texts,isUsable);
			history.images = _.pickBy(history.images,isUsable);
			history.inks = _.pickBy(history.inks,isUsable);

			boardContent = history;
			boardContent.minX = 0;
			boardContent.minY = 0;
			boardContent.maxX = boardWidth;
			boardContent.maxY = boardHeight;

			_.forEach(boardContent.inks,function(ink,i){
				prerenderInk(ink,true);
			});
			_.forEach(boardContent.highlighters,function(ink,i){
				prerenderInk(ink,true);
			});
			_.forEach(boardContent.multiWordTexts,function(text,i){
				prerenderMultiwordText(text,true);
			});
			_.forEach(boardContent.texts,function(text,i){
				prerenderText(text,true);
			});
			/*
			_.forEach(boardContent.images,function(image,i){
				prerenderImage(image,true);
			});
			*/
			_.forEach(boardContent.videos,function(video,i){
				prerenderVideo(video,true);
			});
			boardContent.width = boardContent.maxX - boardContent.minX;
			boardContent.height = boardContent.maxY - boardContent.minY;
			var startRender = function(){
				if (boardContent.minX == Infinity){
					boardContent.minX = 0;
				}
				if (boardContent.minY == Infinity){
					boardContent.minY = 0;
				} 
				rendererObj.setViewbox(boardContent.minX,boardContent.minY,boardContent.maxX - boardContent.minX,boardContent.maxY - boardContent.minY);
				statistic("preRenderHistory",new Date().getTime() - start,true);
				if (afterFunc !== undefined){
					afterFunc();
				}
			}
			if (_.size(boardContent.images) == 0){
				startRender();
			} else {
				var loaded = 0;
				var limit = _.size(boardContent.images);
				_.forEach(boardContent.images,function(image){
					image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
					incorporateBoardBounds(image.bounds);
					var dataImage = new Image();
					image.imageData = dataImage;
					var url = calculateImageSource(image,true);
					dataImage.onload = function(data){
						var shouldReCalcBounds = false;
						if (image.width == 0){
							image.width = dataImage.naturalWidth;
							shouldReCalcBounds = true;
						}
						if (image.height == 0){
							image.height = dataImage.naturalHeight;
							shouldReCalcBounds = true;
						}
						calculateImageBounds(image);
						if (shouldReCalcBounds){
							image.bounds = [image.x,image.y,image.x+image.width,image.y+image.height];
							incorporateBoardBounds(image.bounds);
						}
						prerenderImage(image);
						limit -= 1;
						if (loaded >= limit){
							_.defer(startRender);
						}
					};
					dataImage.onError = function(error){
						passException(error,"preRenderHistory:imageDataLoad",[dataImage,image]);
						limit -= 1;
						if (loaded >= limit){
							_.defer(startRender);
						}
					};
					dataImage.src = url;
				});
			}
		} catch(e){
			passException(e,"preRenderHistory",[boardContent]);
			statistic("preRenderHistory",new Date().getTime() - start,false,e);
		}
	};

	var renderFunc = function(){
		blit();
	};
	var receiveHistoryFunc = function(history){
		preRenderHistory(history,function(){
			historyReceived(history);
			blit();
		});
	};
	var passException = function(e,loc,params){ };
	var renderStarting = function(ctx,elem,history){ };
	var renderComplete = function(ctx,elem,history){ };
	var viewboxChanged = function(vb,ctx,elem){ };
	var dimensionsChanged = function(dims,ctx,elem){ };
	var scaleChanged = function(scale,ctx,elem){ };
	var historyReceived = function(history){ };
	var historyUpdated = function(history){ };
	var statistic = function(category,time,success,exception){ };
	var preRenderItem = function(item,ctx){
		return true;
	};
	var postRenderItem = function(item,ctx){ };
	var preTransform = function(transform){
		return true;
	};
	var postTransform = function(transform){ }
	var preTransformItem = function(item,transform){
		return true;
	};
	var postTransformItem = function(item,transform){ };
	var preDeleteItem = function(item){
		return true;
	};
	var postDeleteItem = function(item){ };
	var setDimensionsFunc = function(dims){
		if (dims !== undefined && "width" in dims && "height" in dims){
			if (dims.width !== boardWidth || dims.height != boardHeight){
				boardWidth = dims.width;
				boardHeight = dims.height;
			};
			blit();
		}
	};
	var rendererObj = {
		setHistory:receiveHistoryFunc,
		addStanza:addStanzaFunc,	
		render:renderFunc,
		getBoardContent:function(){return boardContent;},
		getBoardContext:function(){return boardContext;},
		screenToWorld:screenToWorld,
		worldToScreen:worldToScreen,
		scaleWorldToScreen:scaleWorldToScreen,
		scaleScreenToWorld:scaleScreenToWorld,
		getViewbox:function(){
			return {
				width:viewboxWidth,
				height:viewboxHeight,
				x:viewboxX,
				y:viewboxY
			};
		},
		getDimensions:function(){
			return {
				width:boardWidth,
				height:boardHeight
			};
		},
		setDimensions:setDimensionsFunc,
		getScale:function(){
			return scale();
		},
		setViewbox:function(x,y,w,h){
			viewboxX = x;
			viewboxY = y;
			viewboxWidth = w;
			viewboxHeight = h;
			viewboxChanged({
				width:viewboxWidth,
				height:viewboxHeight,
				x:viewboxX,
				y:viewboxY
			});
		},
		onHistoryChanged:function(f){
			historyReceived = f;
		},
		onHistoryUpdated:function(f){
			historyUpdated = f;
		},
		onException:function(f){
			passException = f;
		},
		onRenderStarting:function(f){
			renderStarting = f;
		},
		onStatistic:function(f){
			statistic = f;
		},
		onRenderComplete:function(f){
			renderComplete = f;
		},
		onViewboxChanged:function(f){
			viewboxChanged = f;
		},
		onDimensionsChanged:function(f){
			dimensionsChanged = f;
		},
		onScaleChanged:function(f){
			scaleChanged = f;
		},
		onPreRenderItem:function(f){
			preRenderItem = f;
		},
		onPostRenderItem:function(f){
			postRenderItem = f;
		},
		onPreTransform:function(f){
			preTransform = f;
		},
		onPostTransform:function(f){
			postTransform = f;
		},
		onPreTransformItem:function(f){
			preTransformItem = f;
		},
		onPostTransformItem:function(f){
			postTransformItem = f;
		},
		onPreDeleteItem:function(f){
			preDeleteItem = f;
		},
		onPostDeleteItem:function(f){
			postDeleteItem = f;
		},	
		setImageSourceCalculationFunction:function(f){
			calculateImageSource = f;
		},
		setVideoSourceCalculationFunction:function(f){
			calculateVideoSource = f;
		},
		getDataURI:function(){
			return canvasElem[0].toDataURL();
		},
		determineCanvasConstants:determineCanvasConstants,
		determineScaling:determineScaling,
		drawImage:drawImage,
		drawInk:drawInk,
		drawText:drawText,
		drawMultiwordText:drawMultiwordText,
		drawVideo:drawVideo,
		prerenderInk:prerenderInk,
		prerenderText:prerenderText,
		prerenderImage:prerenderImage,
		prerenderVideo:prerenderVideo,
		prerenderMultiwordText:prerenderMultiwordText
	};
	return rendererObj;
};
