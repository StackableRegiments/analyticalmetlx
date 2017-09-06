var mockInk = {
	type:"ink",
	timestamp:2,
	identity:"2",
	thickness:5,
	points:[50,50,50,100,100,50,100,50,50,50,50,50],
	color:["000000",255],
	isHighlighter:false,
	target:"presentationSpace",
	author:"test",
	privacy:"PUBLIC"
};
var mockText = {
	type:"text",
	timestamp:1,
	identity:"1",
	x:20,
	y:20,
	width:100,
	height:30,
	color:["000000",255],
	text:"what about this",
	font:"arial",
	size:12,
	weight:"regular",
	style:"normal",
	target:"presentationSpace",
	author:"test",
	privacy:"PUBLIC"
};
var mockImage = {
	type:"image",
	timestamp:3,
	identity:"3",
	x:10,
	y:10,
	width:160,
	height:120,
	target:"presentationSpace",
	author:"test",
	privacy:"PUBLIC",
	source:"/static/images/insert.png"
};
var mockVideo = {
	type:"video",
	timestamp:5,
	identity:"5",
	x:20,
	y:200,
	width:160,
	height:120,
	target:"presentationSpace",
	author:"test",
	privacy:"PUBLIC",
	source:"https://www.w3schools.com/html/mov_bbb.mp4"
};
var mockHistory = {
	type:"history",
	inks:{},
	highlighters:{},
	texts:{},
	multiWordTexts:{},
	images:{},
	videos:{}
};
var mockMultiWordText = {
	type:"multiWordText",
	identity:"4",		
	x:320,
	y:20,
	width:300,
	height:100,
	tag:"testTag",
	requestedWidth:300,
	timestamp:4,
	author:"test",
	privacy:"PUBLIC",
	target:"presentationSpace",
	words:[
		{
			text:"red ",
			bold:false,
			underline:false,
			italic:true,
			justify:"left",
			color:["#FF0000",255],
			font:"arial",
			size:12	
		},
		{
			text:"green",
			bold:false,
			underline:true,
			italic:false,
			justify:"left",
			color:["#00FF00",255],
			font:"arial",
			size:10	
		},
		{
			text:" blue",
			bold:true,
			underline:false,
			italic:false,
			justify:"left",
			color:["#0000FF",255],
			font:"arial",
			size:14	
		}
	]
};
mockHistory.inks[mockInk.identity] = mockInk;
mockHistory.texts[mockText.identity] = mockText;
mockHistory.images[mockImage.identity] = mockImage;
mockHistory.videos[mockVideo.identity] = mockVideo;
mockHistory.multiWordTexts[mockMultiWordText.identity] = mockMultiWordText;
var stanzas = [mockText,mockInk,mockImage,mockVideo,mockMultiWordText];
