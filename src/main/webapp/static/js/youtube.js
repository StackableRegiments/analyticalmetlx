// sourced from: https://github.com/endlesshack/youtube-video/blob/master/youtube-video.js

(function() {
  window.YoutubeVideo = function(id, callback) {
    return $.ajax({
      url: "http://www.youtube.com/get_video_info?video_id=" + id,
      dataType: "text"
    }).done(function(video_info) {
      var video;
      video = YoutubeVideo.decodeQueryString(video_info);
      if (video.status === "fail") {
        return callback(video);
      }
      video.sources = YoutubeVideo.decodeStreamMap(video.url_encoded_fmt_stream_map);
      video.getSource = function(type, quality) {
        var exact, key, lowest, source, _ref;
        lowest = null;
        exact = null;
        _ref = this.sources;
        for (key in _ref) {
          source = _ref[key];
          if (source.type.match(type)) {
            if (source.quality.match(quality)) {
              exact = source;
            } else {
              lowest = source;
            }
          }
        }
        return exact || lowest;
      };
      return callback(video);
    });
  };
  window.YoutubeVideo.decodeQueryString = function(queryString) {
    var key, keyValPair, keyValPairs, r, val, _i, _len;
    r = {};
    keyValPairs = queryString.split("&");
    for (_i = 0, _len = keyValPairs.length; _i < _len; _i++) {
      keyValPair = keyValPairs[_i];
      key = decodeURIComponent(keyValPair.split("=")[0]);
      val = decodeURIComponent(keyValPair.split("=")[1] || "");
      r[key] = val;
    }
    return r;
  };
  window.YoutubeVideo.decodeStreamMap = function(url_encoded_fmt_stream_map) {
    var quality, sources, stream, type, urlEncodedStream, _i, _len, _ref;
    sources = {};
    _ref = url_encoded_fmt_stream_map.split(",");
    for (_i = 0, _len = _ref.length; _i < _len; _i++) {
      urlEncodedStream = _ref[_i];
      stream = YoutubeVideo.decodeQueryString(urlEncodedStream);
      type = stream.type.split(";")[0];
      quality = stream.quality.split(",")[0];
      stream.original_url = stream.url;
      stream.url = "" + stream.url + "&signature=" + stream.sig;
      sources["" + type + " " + quality] = stream;
    }
    return sources;
  };
}).call(this);

// this bit's mine

var renderVideoByUrlToCanvas = function(videoSrc,canvas,x,y,w,h){
	renderVideoToCanvas(function(v){
		v.attr("src",videoSrc);
	},canvas,x,y,w,h);
};

var renderVideoFromWebcamToCanvas = function(canvas,x,y,w,h){
	renderVideoToCanvas(function(v){
		navigator.mediaDevices.getUserMedia({
			audio:true,
			video:true
		}).then(function(stream){
			v.srcObject = stream;
		}).catch(function(error){
			console.log("couldn't get stream",error);
		});
	},canvas,x,y,w,h);
};

var renderVideoToCanvas = function(videoSrcFunc,canvas,x,y,w,h){
	var refreshRate = 40 // 25 frames per second
	var context = canvas.getContext("2d");
	var v = $("<video/>");
	var renderVideoFrame = function(v,x,y,w,h){
		if (v.paused || v.ended){
			return false;
		} else {
			context.drawImage(v,x,y,w,h);
			requestAnimationFrame(function(){
				renderVideoFrame(v,x,y,w,h);
			});
		}
	};
	v[0].addEventListener("play",function(){
		renderVideoFrame(this,x,y,w,h);
	},false);
	videoSrcFunc(v);
	v[0].play();
};
