var carotaTest = (function(){
    //Pre-optimization
    //Average 88.45 milis over 20 runs of Textbox sample render width 20000 words in 1769 milis
    var time = function(label,f){
        var samples = [];
        var testCount = 1;
        for(var i = 0; i < testCount; i++){
            var start = new Date();
            f();
            var end = new Date();
            samples.push(end - start);
        }
        var elapsed = _.sum(samples);
        var mean = _.mean(samples);
        console.log(sprintf("//Average %s millis over %s runs of %s in %s milis",mean,testCount,label,elapsed));
    }
    var wordCount = 5000;
    var prime = function(){
        var width = 2000;
        var height = 500;
        var x = 100;
        var y = 100;
        var stanza = {
            bounds:[x,y,x+width,y+height],
            identity:"sim1",
            privacy:"PUBLIC",
            slide:1001,
            target:"presentationSpace",
            requestedWidth:width,
            width:width,
            height:height,
            x:x,
            y:y,
            type:"multiWordText",
            author:UserSettings.getUsername(),
            words:_.map(_.range(0,wordCount),function(i){
                return {
                    text: sprintf("primo %s secundo %s tertius %s quaternius %s quintum %s ",i,i,i,i,i),
                    italic: i % 2 == 0,
                    bold: i % 5 == 0,
                    underline: i % 20 == 0,
                    color: ['#00ff00',255],
                    size:25
                };
            })
        };
        boardContent.multiWordTexts[stanza.identity] = stanza;
        prerenderMultiwordText(stanza);
        boardContent.multiWordTexts[stanza.identity].doc.invalidateBounds();
        var bounds = boardContent.multiWordTexts[stanza.identity].bounds;
    };
    var paintCount = 0;
    return {
        paint:function(){
            paintCount++;
        },
        getPaintCount:function(){
            return paintCount;
        },
        prime:prime,
        run:function(){
            prime();
            carotaTest.sample = function(){
                time(sprintf("Textbox sample render width %s words",wordCount * 5 * 2),blit);
                console.log(sprintf("Paint called %s times",carotaTest.getPaintCount()));
            }
            carotaTest.sample();
        }
    };
})();
(function(){
    (function (modules) {
        'use strict';

        var resolve, getRequire, wmRequire, notFoundError, findFile
        , extensions = {".js":[],".json":[],".css":[],".html":[]}
        , envRequire = typeof require === 'function' ? require : null;

        notFoundError = function (path) {
            var error = new Error("Could not find module '" + path + "'");
            error.code = 'MODULE_NOT_FOUND';
            return error;
        };
        findFile = function (scope, name, extName) {
            var i, ext;
            if (typeof scope[name + extName] === 'function') return name + extName;
            for (i = 0; (ext = extensions[extName][i]); ++i) {
                if (typeof scope[name + ext] === 'function') return name + ext;
            }
            return null;
        };
        resolve = function (scope, tree, path, fullPath, state, id) {
            var name, dir, exports, module, fn, found, ext;
            path = path.split(/[\\/]/);
            name = path.pop();
            if ((name === '.') || (name === '..')) {
                path.push(name);
                name = '';
            }
            while ((dir = path.shift()) != null) {
                if (!dir || (dir === '.')) continue;
                if (dir === '..') {
                    scope = tree.pop();
                    id = id.slice(0, id.lastIndexOf('/'));
                } else {
                    tree.push(scope);
                    scope = scope[dir];
                    id += '/' + dir;
                }
                if (!scope) throw notFoundError(fullPath);
            }
            if (name && (typeof scope[name] !== 'function')) {
                found = findFile(scope, name, '.js');
                if (!found) found = findFile(scope, name, '.json');
                if (!found) found = findFile(scope, name, '.css');
                if (!found) found = findFile(scope, name, '.html');
                if (found) {
                    name = found;
                } else if ((state !== 2) && (typeof scope[name] === 'object')) {
                    tree.push(scope);
                    scope = scope[name];
                    id += '/' + name;
                    name = '';
                }
            }
            if (!name) {
                if ((state !== 1) && scope[':mainpath:']) {
                    return resolve(scope, tree, scope[':mainpath:'], fullPath, 1, id);
                }
                return resolve(scope, tree, 'index', fullPath, 2, id);
            }
            fn = scope[name];
            if (!fn) throw notFoundError(fullPath);
            if (fn.hasOwnProperty('module')) return fn.module.exports;
            exports = {};
            fn.module = module = { exports: exports, id: id + '/' + name };
            fn.call(exports, exports, module, getRequire(scope, tree, id));
            return module.exports;
        };
        wmRequire = function (scope, tree, fullPath, id) {
            var name, path = fullPath, t = fullPath.charAt(0), state = 0;
            if (t === '/') {
                path = path.slice(1);
                scope = modules['/'];
                if (!scope) {
                    if (envRequire) return envRequire(fullPath);
                    throw notFoundError(fullPath);
                }
                id = '/';
                tree = [];
            } else if (t !== '.') {
                name = path.split('/', 1)[0];
                scope = modules[name];
                if (!scope) {
                    if (envRequire) return envRequire(fullPath);
                    throw notFoundError(fullPath);
                }
                id = name;
                tree = [];
                path = path.slice(name.length + 1);
                if (!path) {
                    path = scope[':mainpath:'];
                    if (path) {
                        state = 1;
                    } else {
                        path = 'index';
                        state = 2;
                    }
                }
            }
            return resolve(scope, tree, path, fullPath, state, id);
        };
        getRequire = function (scope, tree, id) {
            return function (path) {
                return wmRequire(scope, [].concat(tree), path, id);
            };
        };
        return getRequire(modules, [], '');
    })({
        "carota": {
            "src": {
                "carota.js": function (exports, module, require) {
                    var node = require('./node');
                    var editor = require('./editor');
                    var doc = require('./doc');
                    var dom = require('./dom');
                    var runs = require('./runs');
                    var html = require('./html');
                    var frame = require('./frame');
                    var text = require('./text');
                    var rect = require('./rect');

                    var bundle = {
                        node: node,
                        editor: editor,
                        document: doc,
                        dom: dom,
                        runs: runs,
                        html: html,
                        frame: frame,
                        text: text,
                        rect: rect
                    };

                    module.exports = bundle;

                    if (typeof window !== 'undefined' && window.document) {
                        if (window.carota) {
                            throw new Error('Something else is called carota!');
                        }
                        window.carota = bundle;
                    }
                },
                "characters.js": function (exports, module, require) {
                    var runs = require('./runs');

                    var compatible = function(a, b) {
                        if (a._runs !== b._runs) {
                            throw new Error('Characters for different documents');
                        }
                    };

                    var prototype = {
                        equals: function(other) {
                            compatible(this, other);
                            return this._run === other._run && this._offset === other._offset;
                        },
                        cut: function(upTo) {
                            compatible(this, upTo);
                            var self = this;
                            return function(eachRun) {
                                for (var runIndex = self._run; runIndex <= upTo._run; runIndex++) {
                                    var run = self._runs[runIndex];
                                    if (run) {
                                        var start = (runIndex === self._run) ? self._offset : 0;
                                        var stop = (runIndex === upTo._run) ? upTo._offset : runs.getTextLength(run.text);
                                        if (start < stop) {
                                            runs.getSubText(function(piece) {
                                                var pieceRun = Object.create(run);
                                                pieceRun.text = piece;
                                                eachRun(pieceRun);
                                            }, run.text, start, stop - start);
                                        }
                                    }
                                }
                            };
                        }
                    };

                    function character(runArray, run, offset) {
                        return Object.create(prototype, {
                            _runs: { value: runArray },
                            _run: { value: run },
                            _offset: { value: offset },
                            char: {
                                value: run >= runArray.length ? null :
                                    runs.getTextChar(runArray[run].text, offset)
                            }
                        });
                    }

                    function firstNonEmpty(runArray, n) {
                        for (; n < runArray.length; n++) {
                            if (runs.getTextLength(runArray[n].text) != 0) {
                                return character(runArray, n, 0);
                            }
                        }
                        return character(runArray, runArray.length, 0);
                    }

                    module.exports = function(runArray) {
                        return function(emit) {
                            var c = firstNonEmpty(runArray, 0);
                            while (!emit(c) && (c.char !== null)) {
                                c = (c._offset + 1 < runs.getTextLength(runArray[c._run].text))
                                    ? character(runArray, c._run, c._offset + 1)
                                    : firstNonEmpty(runArray, c._run + 1);
                            }
                        };
                    };
                },
                "codes.js": function (exports, module, require) {
                    var text = require('./text');
                    var frame = require('./frame');
                    var node = require('./node');
                    var rect = require('./rect');
                    var util = require('./util');

                    var inlineNodePrototype = node.derive({
                        parent: function() {
                            return this._parent;
                        },
                        draw: function(ctx) {
                            this.inline.draw(ctx,
                                             this.left,
                                             this.baseline,
                                             this.measured.width,
                                             this.measured.ascent,
                                             this.measured.descent,
                                             this.formatting);
                        },
                        position: function(left, baseline, bounds) {
                            this.left = left;
                            this.baseline = baseline;
                            if (bounds) {
                                this._bounds = bounds;
                            }
                        },
                        bounds: function() {
                            return this._bounds || rect(this.left, this.baseline - this.measured.ascent,
                                                        this.measured.width, this.measured.ascent + this.measured.descent);
                        },
                        byCoordinate: function(x, y) {
                            if (x <= this.bounds().center().x) {
                                return this;
                            }
                            return this.next();
                        }
                    });

                    var inlineNode = function(inline, parent, ordinal, length, formatting) {
                        if (!inline.draw || !inline.measure) {
                            throw new Error();
                        }
                        return Object.create(inlineNodePrototype, {
                            inline: { value: inline },
                            _parent: { value: parent },
                            ordinal: { value: ordinal },
                            length: { value: length },
                            formatting: { value: formatting },
                            measured: {
                                value: inline.measure(formatting)
                            }
                        });
                    };

                    var codes = {};

                    codes.number = function(obj, number) {
                        var formattedNumber = (number + 1) + '.';
                        return {
                            measure: function(formatting) {
                                return text.measure(formattedNumber, formatting);
                            },
                            draw: function(ctx, x, y, width, ascent, descent, formatting) {
                                text.draw(ctx, formattedNumber, formatting, x, y, width, ascent, descent);
                            }
                        };
                    };

                    var listTerminator = function(obj) {
                        return util.derive(obj, {
                            eof: true,
                            measure: function(formatting) {
                                return { width: 18, ascent: 0, descent: 0 }; // text.measure(text.enter, formatting);
                            },
                            draw: function(ctx, x, y) {
                                // ctx.fillText(text.enter, x, y);
                            }
                        });
                    };

                    codes.listNext = codes.listEnd = listTerminator;

                    codes.listStart = function(obj, data, allCodes) {
                        return util.derive(obj, {
                            block: function(left, top, width, ordinal, parent, formatting) {
                                var list = node.generic('list', parent, left, top),
                                    itemNode,
                                    itemFrame,
                                    itemMarker;

                                var indent = 50, spacing = 10;

                                var startItem = function(code, formatting) {
                                    itemNode = node.generic('item', list);
                                    var marker = allCodes(code.marker || { $: 'number' }, list.children().length);
                                    itemMarker = inlineNode(marker, itemNode, ordinal, 1, formatting);
                                    itemMarker.block = true;
                                    itemFrame = frame(
                                        left + indent, top, width - indent, ordinal + 1, itemNode,
                                        function(terminatorCode) {
                                            return terminatorCode.$ === 'listEnd';
                                        },
                                        itemMarker.measured.ascent
                                    );
                                };

                                startItem(obj, formatting);

                                return function(inputWord) {
                                    if (itemFrame) {
                                        itemFrame(function(finishedFrame) {
                                            ordinal = finishedFrame.ordinal + finishedFrame.length;
                                            var frameBounds = finishedFrame.bounds();

                                            // get first line and position marker
                                            var firstLine = finishedFrame.first();
                                            var markerLeft = left + indent - spacing - itemMarker.measured.width;
                                            var markerBounds = rect(left, top, indent, frameBounds.h);
                                            if ('baseline' in firstLine) {
                                                itemMarker.position(markerLeft, firstLine.baseline, markerBounds);
                                            } else {
                                                itemMarker.position(markerLeft, top + itemMarker.measured.ascent, markerBounds);
                                            }

                                            top = frameBounds.t + frameBounds.h;

                                            itemNode.children().push(itemMarker);
                                            itemNode.children().push(finishedFrame);
                                            itemNode.finalize();

                                            list.children().push(itemNode);
                                            itemNode = itemFrame = itemMarker = null;
                                        }, inputWord);
                                    } else {
                                        ordinal++;
                                    }

                                    if (!itemFrame) {
                                        var i = inputWord.code();
                                        if (i) {
                                            if (i.$ == 'listEnd') {
                                                list.finalize();
                                                return list;
                                            }
                                            if (i.$ == 'listNext') {
                                                startItem(i, inputWord.codeFormatting());
                                            }
                                        }
                                    }
                                };
                            }
                        });
                    };

                    module.exports = exports = function(obj, number, allCodes) {
                        var impl = codes[obj.$];
                        return impl && impl(obj, number, allCodes);
                    };

                    exports.editFilter = function(doc) {
                        var balance = 0;

                        if (!doc.words.some(function(word, i) {
                            var code = word.code();
                            if (code) {
                                switch (code.$) {
                                case 'listStart':
                                    balance++;
                                    break;
                                case 'listNext':
                                    if (balance === 0) {
                                        doc.spliceWordsWithRuns(i, 1, [util.derive(word.codeFormatting(), {
                                            text: {
                                                $: 'listStart',
                                                marker: code.marker
                                            }
                                        })]);
                                        return true;
                                    }
                                    break;
                                case 'listEnd':
                                    if (balance === 0) {
                                        doc.spliceWordsWithRuns(i, 1, []);
                                    }
                                    balance--;
                                    break;
                                }
                            }
                        })) {
                            if (balance > 0) {
                                var ending = [];
                                while (balance > 0) {
                                    balance--;
                                    ending.push({
                                        text: { $: 'listEnd' }
                                    });
                                }
                                doc.spliceWordsWithRuns(doc.words.length - 1, 0, ending);
                            }
                        }
                    };
                },
                "doc.js": function (exports, module, require) {
                    var per = require('per');
                    var characters = require('./characters');
                    var split = require('./split');
                    var word = require('./word');
                    var node = require('./node');
                    var runs = require('./runs');
                    var range = require('./range');
                    var util = require('./util');
                    var frame = require('./frame');
                    var codes = require('./codes');
                    var rect = require('./rect');

                    var makeEditCommand = function(doc, start, count, words) {
                        var selStart = doc.selection.start, selEnd = doc.selection.end;
                        return function(log) {
                            doc._wordOrdinals = [];
                            var oldWords = Array.prototype.splice.apply(doc.words, [start, count].concat(words));
                            log(makeEditCommand(doc, start, words.length, oldWords));
                            doc._nextSelection = { start: selStart, end: selEnd };
                        };
                    };

                    var makeTransaction = function(perform) {
                        var commands = [];
                        var log = function(command) {
                            commands.push(command);
                            log.length = commands.length;
                        };
                        perform(log);

                        return function(outerLog) {
                            outerLog(makeTransaction(function(innerLog) {
                                while (commands.length) {
                                    commands.pop()(innerLog);
                                }
                            }));
                        };
                    };

                    var isBreaker = function(word) {
                        if (word.isNewLine()) {
                            return true;
                        }
                        var code = word.code();
                        return !!(code && (code.block || code.eof));
                    };
                    var prototype = node.derive({
                        invalidateBounds: function(){
                            var bounds = this.frame.bounds();
                            var pos = this.position;
                            var result = [
                                pos.x + bounds.l,
                                pos.y + bounds.t,
                                pos.x + this.frame.actualWidth(),
                                pos.y + bounds.h];
                            this.bounds = result;
                            this.stanza.bounds = this.bounds;
                            Progress.call("textBoundsChanged",[this.identity,this.bounds]);
                        },
                        load: function(runs) {
                            var self = this;
                            this.undo = [];
                            this.redo = [];
                            this._wordOrdinals = [];
                            this.words = per(characters(runs))
                                .per(split(self.codes))
                                .map(function(w) {
                                    return word(w, self.codes);
                                })
                                .all();
                            this.words.push(word());/*EOF*/
                            this.layout();
                        },
                        layout: function() {
                            this.frame = null;
                            try {
                                this.frame = per(this.words)
                                    .per(frame(0, 0, this._width, 0, this))
                                    .first();
                                this.invalidateBounds();
                            } catch (x) {
                                console.error("layout exception",x);
                            }
                            if (!this.frame) {
                                console.error('A bug somewhere has produced an invalid state - rolling back');
                                this.performUndo();
                            } else if (this._nextSelection) {
                                var next = this._nextSelection;
                                delete this._nextSelection;
                                this.select(next.start, next.end,true);
                            }
                        },
                        range: function(start, end) {
                            return range(this, start, end);
                        },
                        documentRange: function() {
                            return this.range(0, this.frame.length - 1);
                        },
                        selectedRange: function() {
                            return this.range(this.selection.start, this.selection.end);
                        },
                        save: function() {
                            return this.documentRange().save();
                        },
                        paragraphRange: function(start, end) {
                            var i;

                            // find the character after the nearest breaker before start
                            var startInfo = this.wordContainingOrdinal(start);
                            start = 0;
                            if (startInfo && !isBreaker(startInfo.word)) {
                                for (i = startInfo.index; i > 0; i--) {
                                    if (isBreaker(this.words[i - 1])) {
                                        start = this.wordOrdinal(i);
                                        break;
                                    }
                                }
                            }

                            // find the nearest breaker after end
                            var endInfo = this.wordContainingOrdinal(end);
                            end = this.frame.length - 1;
                            if (endInfo && !isBreaker(endInfo.word)) {
                                for (i = endInfo.index; i < this.words.length; i++) {
                                    if (isBreaker(this.words[i])) {
                                        end = this.wordOrdinal(i);
                                        break;
                                    }
                                }
                            }

                            return this.range(start, end);
                        },
                        insert: function(text, canMoveViewport) {
                            var insertLength = this.selectedRange().setText(text);
                            this.select(this.selection.end + insertLength, null, canMoveViewport);
                        },
                        modifyInsertFormatting: function(attribute, value) {
                            carota.runs.nextInsertFormatting = carota.runs.nextInsertFormatting || {};
                            carota.runs.nextInsertFormatting[attribute] = value;
                        },
                        applyInsertFormatting: function(text) {
                            var formatting = carota.runs.nextInsertFormatting;
                            var insertFormattingProperties = Object.keys(formatting);
                            if (insertFormattingProperties.length) {
                                text.forEach(function(run) {
                                    insertFormattingProperties.forEach(function(property) {
                                        run[property] = formatting[property];
                                    });
                                });
                            }
                        },
                        wordOrdinal: function(index) {
                            if (index < this.words.length) {
                                var cached = this._wordOrdinals.length;
                                if (cached < (index + 1)) {
                                    var o = cached > 0 ? this._wordOrdinals[cached - 1] : 0;
                                    for (var n = cached; n <= index; n++) {
                                        this._wordOrdinals[n] = o;
                                        o += this.words[n].length;
                                    }
                                }
                                return this._wordOrdinals[index];
                            }
                        },
                        wordContainingOrdinal: function(ordinal) {
                            // could rewrite to be faster using binary search over this.wordOrdinal
                            var result;
                            var pos = 0;
                            this.words.some(function(word, i) {
                                if (ordinal >= pos && ordinal < (pos + word.length)) {
                                    result = {
                                        word: word,
                                        ordinal: pos,
                                        index: i,
                                        offset: ordinal - pos
                                    };
                                    return true;
                                }
                                pos += word.length;
                            });
                            return result;
                        },
                        runs: function(emit, range) {
                            var startDetails = this.wordContainingOrdinal(Math.max(0, range.start)),
                                endDetails = this.wordContainingOrdinal(Math.min(range.end, this.frame.length - 1)) || startDetails;
                            if(!(startDetails && endDetails)){
                                /*The words aren't constructed yet*/
                                throw new Exception("range miss");
                            }
                            if (startDetails.index === endDetails.index) {
                                startDetails.word.runs(emit, {
                                    start: startDetails.offset,
                                    end: endDetails.offset
                                });
                            } else {
                                startDetails.word.runs(emit, { start: startDetails.offset });
                                for (var n = startDetails.index + 1; n < endDetails.index; n++) {
                                    this.words[n].runs(emit);
                                }
                                endDetails.word.runs(emit, { end: endDetails.offset });
                            }
                        },
                        spliceWordsWithRuns: function(wordIndex, count, runs) {
                            var self = this;

                            var newWords = per(characters(runs))
                                    .per(split(self.codes))
                                    .truthy()
                                    .map(function(w) {
                                        return word(w, self.codes);
                                    })
                                    .all();

                            // Check if old or new content contains any fancy control codes:
                            var runFilters = false;

                            if ('_filtersRunning' in self) {
                                self._filtersRunning++;
                            } else {
                                for (var n = 0; n < count; n++) {
                                    if (this.words[wordIndex + n].code()) {
                                        runFilters = true;
                                    }
                                }
                                if (!runFilters) {
                                    runFilters = newWords.some(function(word) {
                                        return !!word.code();
                                    });
                                }
                            }

                            this.transaction(function(log) {
                                makeEditCommand(self, wordIndex, count, newWords)(log);
                                if (runFilters) {
                                    self._filtersRunning = 0;
                                    try {
                                        for (;;) {
                                            var spliceCount = self._filtersRunning;
                                            if (!self.editFilters.some(function(filter) {
                                                filter(self);
                                                return spliceCount !== self._filtersRunning;
                                            })) {
                                                break; // No further changes were made
                                            }
                                        }
                                    } finally {
                                        delete self._filtersRunning;
                                    }
                                }
                            });
                        },
                        splice: function(start, end, text) {
                            if (typeof text === 'string') {
                                var sample = Math.max(0, start - 1);
                                var sampleRun = per({ start: sample, end: sample + 1 })
                                        .per(this.runs, this)
                                        .first();
                                text = [
                                    sampleRun ? Object.create(sampleRun, { text: { value: text } }) : { text: text }
                                ];
                            } else if (!Array.isArray(text)) {
                                text = [{ text: text }];
                            }

                            this.applyInsertFormatting(text);

                            var startWord = this.wordContainingOrdinal(start),
                                endWord = this.wordContainingOrdinal(end);
                            /*Toggling formatting on an empty box*/
                            if(!endWord) endWord = startWord;

                            var prefix;
                            if (start === startWord.ordinal) {
                                if (startWord.index > 0 && !isBreaker(this.words[startWord.index - 1])) {
                                    startWord.index--;
                                    var previousWord = this.words[startWord.index];
                                    prefix = per({}).per(previousWord.runs, previousWord).all();
                                } else {
                                    prefix = [];
                                }
                            } else {
                                prefix = per({ end: startWord.offset })
                                    .per(startWord.word.runs, startWord.word)
                                    .all();
                            }

                            var suffix;
                            if (end === endWord.ordinal) {
                                if ((end === this.frame.length - 1) || isBreaker(endWord.word)) {
                                    suffix = [];
                                    endWord.index--;
                                } else {
                                    suffix = per({}).per(endWord.word.runs, endWord.word).all();
                                }
                            } else {
                                suffix = per({ start: endWord.offset })
                                    .per(endWord.word.runs, endWord.word)
                                    .all();
                            }

                            var oldLength = this.frame.length;

                            this.spliceWordsWithRuns(startWord.index, (endWord.index - startWord.index) + 1,
                                                     per(prefix).concat(text).concat(suffix).per(runs.consolidate()).all());

                            return this.frame ? (this.frame.length - oldLength) : 0;
                        },
                        registerEditFilter: function(filter) {
                            this.editFilters.push(filter);
                        },
                        width: function(width) {
                            if (arguments.length === 0) {
                                return this._width;
                            }
                            this._width = width;
                            this.layout();
                            return width;
                        },
                        children: function() {
                            return [this.frame];
                        },
                        toggleCaret: function() {
                            var old = this.caretVisible;
                            if (this.selection.start === this.selection.end) {
                                if (this.selectionJustChanged) {
                                    this.selectionJustChanged = false;
                                    this.caretVisible = true;
                                    return true;
                                } else {
                                    this.caretVisible = !this.caretVisible;
                                }
                            }
                            return this.caretVisible !== old;
                        },
                        getCaretCoords: function(ordinal) {
                            var node = this.byOrdinal(ordinal), b;
                            if (node) {
                                if (node.block && ordinal > 0) {
                                    var nodeBefore = this.byOrdinal(ordinal - 1);
                                    if (nodeBefore.newLine) {
                                        var newLineBounds = nodeBefore.bounds;
                                        var lineBounds = nodeBefore.parent().parent().bounds;
                                        b = rect(lineBounds.l, lineBounds.b, 1, newLineBounds.h);
                                    } else {
                                        b = nodeBefore.bounds;
                                        b = rect(b.r, b.t, 1, b.h);
                                    }
                                } else {
                                    b = node.bounds();
                                    if (b.h) {
                                        b = rect(b.l, b.t, 1, b.h);
                                    } else {
                                        b = rect(b.l, b.t, b.w, 1);
                                    }
                                }
                                return b;
                            }
                        },
                        byCoordinate: function(x, y) {
                            var ordinal = this.frame.byCoordinate(x, y).ordinal;
                            var caret = this.getCaretCoords(ordinal);
                            while (caret.b <= y && ordinal < (this.frame.length - 1)) {
                                ordinal++;
                                caret = this.getCaretCoords(ordinal);
                            }
                            while (caret.t >= y && ordinal > 0) {
                                ordinal--;
                                caret = this.getCaretCoords(ordinal);
                            }
                            return this.byOrdinal(ordinal);
                        },
                        drawSelection: function(ctx, hasFocus) {
                            if (this.selection.end === this.selection.start) {
                            } else {
                                ctx.save();
                                ctx.fillStyle = hasFocus ? 'rgba(0, 100, 200, 0.3)' : 'rgba(160, 160, 160, 0.3)';
                                this.selectedRange().parts(function(part) {
                                    part.bounds(true).fill(ctx);
                                });
                                ctx.restore();
                            }
                        },
                        notifySelectionChanged: function(canMoveViewport) {
                            var self = this;
                            var getFormatting = function() {
                                return self.selectedRange().getFormatting();
                            };
                            this.selectionChanged.fire(getFormatting, canMoveViewport);
                        },
                        select: function(ordinal, ordinalEnd, canMoveViewport) {
                            if (!this.frame) {
                                console.log("Something has gone terribly wrong - doc.transaction will rollback soon");
                                return;
                            }
                            this.selection.start = Math.max(0, ordinal);
                            this.selection.end = Math.min(
                                typeof ordinalEnd === 'number' ? ordinalEnd : this.selection.start,
                                this.frame.length - 1
                            );
                            carota.runs.nextInsertFormatting = {};
                            this.selectionJustChanged = true;
                        },
                        performUndo: function(redo) {
                            var fromStack = redo ? this.redo : this.undo,
                                toStack = redo ? this.undo : this.redo,
                                oldCommand = fromStack.pop();

                            if (oldCommand) {
                                oldCommand(function(newCommand) {
                                    toStack.push(newCommand);
                                });
                                this.layout();
                                this.updateCanvas();
                                this.contentChanged.fire();
                            }
                        },
                        canUndo: function(redo) {
                            return redo ? !!this.redo.length : !!this.undo.length;
                        },
                        transaction: function(perform) {
                            if (this._currentTransaction) {
                                perform(this._currentTransaction);
                            } else {
                                var self = this;
                                while (this.undo.length > 50) {
                                    self.undo.shift();
                                }
                                this.redo.length = 0;
                                var changed = false;
                                this.undo.push(makeTransaction(function(log) {
                                    self._currentTransaction = log;
                                    try {
                                        perform(log);
                                    } catch(e){
                                        console.log("Transaction e",e);
                                    } finally {
                                        changed = log.length > 0;
                                        self._currentTransaction = null;
                                    }
                                }));
                                if (changed) {
                                    self.layout();
                                    self.updateCanvas();
                                    self.contentChanged.fire();
                                }
                            }
                        },
                        type: 'document'
                    });

                    exports = module.exports = function(stanza) {
                        var doc = Object.create(prototype);
                        doc.stanza = stanza;
                        doc.position = {x:stanza.x,y:stanza.y};
                        doc.privacy = stanza.privacy;
                        doc.identity = stanza.identity;
                        doc.slide = Conversations.getCurrentSlideJid();
                        doc._width = 0;
                        doc.selection = { start: 0, end: 0 };
                        doc.caretVisible = true;
                        doc.customCodes = function(code, data, allCodes) {};
                        doc.codes = function(code, data) {
                            var instance = codes(code, data, doc.codes);
                            return instance || doc.customCodes(code, data, doc.codes);
                        };
                        doc.selectionChanged = util.event();
                        doc.contentChanged = util.event();
                        doc.editFilters = [codes.editFilter];
                        doc.load([]);
                        return doc;
                    };
                },
                "dom.js": function (exports, module, require) {
                    exports.clear = function(element) {
                        while (element.firstChild) {
                            element.removeChild(element.firstChild);
                        }
                    };
                    exports.setText = function(element, text) {
                        exports.clear(element);
                        element.appendChild(document.createTextNode(text));
                    };

                    exports.handleEvent = function(element, name, handler) {
                        element.addEventListener(name, function(ev) {
                            if (handler(ev) === false) {
                                ev.preventDefault();
                            }
                        });
                    };
                },
                "editor.js": function (exports, module, require) {
                    var per = require('per');
                    var carotaDoc = require('./doc');
                    var dom = require('./dom');
                    var rect = require('./rect');
                    var selectDragStart = null;

                    var currentTo = Date.now();
                    var paint = exports.paint = function(canvas,doc,hasFocus){
                        var ctx = canvas.getContext('2d');
                        var b = doc.bounds;
                        var output =  rect(0, 0, b[2] - b[0], b[3] - b[1]);
                        if(doc.privacy == "PRIVATE"){
                            ctx.fillStyle = "red";
                            ctx.globalAlpha = 0.1;
                            ctx.fillRect(0,0,doc.frame.actualWidth(),doc.frame.height);
                            ctx.globalAlpha = 1.0;
                        }
                        doc.draw(ctx, output);
                        if(doc.isActive && Modes.currentMode == Modes.text){
                            doc.drawSelection(ctx, selectDragStart || hasFocus);
                        }
                    };

                    var repaintCursor = function(doc){
                        var drawCaret = doc.selectionJustChanged || doc.caretVisible;
                        var ctx = boardContext;
                        var caret = doc.getCaretCoords(doc.selection.start);
                        if (caret) {
                            ctx.save();
                            var screenPos = worldToScreen(doc.position.x,doc.position.y);
                            var s = scale();
                            ctx.translate(screenPos.x,screenPos.y);
                            ctx.scale(s,s);
                            ctx.globalAlpha = 1;
                            ctx.fillStyle = drawCaret ? 'black' : 'white';
                            caret.fill(ctx);
                            ctx.restore();
                        }
                    }

                    exports.create = function(host,externalCanvas,stanza) {
                        host.innerHTML =
                            '<div class="carotaTextArea" style="overflow: hidden; position: absolute; height: 0;">' +
                            '<textarea autocorrect="off" autocapitalize="off" spellcheck="false" tabindex="0" ' +
                            'style="position: absolute; padding: 0px; width: 1000px; height: 1em; top: -10000px; left:-10000px; ' +
                            'outline: none; font-size: 4px;"></textarea>' +
                            '</div>';

                        var textAreaDiv = host.querySelector('.carotaTextArea'),
                            textArea = host.querySelector('textarea'),
                            doc = carotaDoc(stanza),
                            keyboardSelect = 0,
                            keyboardX = null, nextKeyboardX = null,
                            focusChar = null,
                            textAreaContent = '',
                            richClipboard = null,
                            plainClipboard = null;

                        doc.claimFocus = function(){
                            $(textArea).focus();
                        }

                        var hasFocus = function(){
                            return document.focussedElement == textArea;
                        }

                        var maxDimension = determineCanvasConstants().y;
                        doc.updateCanvas = function(){
                            delete doc.stanza.mipMap;
                            if(!doc.canvas){
                                doc.canvas = $("<canvas/>")[0];
                            }
                            var c = doc.canvas;
                            var w = doc.bounds[2] - doc.bounds[0];
                            var h = doc.bounds[3] - doc.bounds[1];
                            var scaled = determineScaling(w,h);
                            var context = c.getContext("2d");
                            c.width = scaled.width;
                            c.height = scaled.height;
                            context.setTransform(scaled.scaleX,0,0,scaled.scaleY,0,0);
                            paint(c,doc,hasFocus());
                        }

                        var toggles = {
                            66: 'bold',
                            73: 'italic',
                            85: 'underline',
                            83: 'strikeout'
                        };

                        var exhausted = function(ordinal, direction) {
                            return direction < 0 ? ordinal <= 0 : ordinal >= doc.frame.length - 1;
                        };

                        var differentLine = function(caret1, caret2) {
                            return (caret1.b <= caret2.t) ||
                                (caret2.b <= caret1.t);
                        };

                        var changeLine = function(ordinal, direction) {
                            var originalCaret = doc.getCaretCoords(ordinal), newCaret;
                            nextKeyboardX = (keyboardX !== null) ? keyboardX : originalCaret.l;

                            while (!exhausted(ordinal, direction)) {
                                ordinal += direction;
                                newCaret = doc.getCaretCoords(ordinal);
                                if (differentLine(newCaret, originalCaret)) {
                                    break;
                                }
                            }

                            originalCaret = newCaret;
                            while (!exhausted(ordinal, direction)) {
                                if ((direction > 0 && newCaret.l >= nextKeyboardX) ||
                                    (direction < 0 && newCaret.l <= nextKeyboardX)) {
                                    break;
                                }

                                ordinal += direction;
                                newCaret = doc.getCaretCoords(ordinal);
                                if (differentLine(newCaret, originalCaret)) {
                                    ordinal -= direction;
                                    break;
                                }
                            }

                            return ordinal;
                        };

                        var endOfline = function(ordinal, direction) {
                            var originalCaret = doc.getCaretCoords(ordinal), newCaret;
                            while (!exhausted(ordinal, direction)) {
                                ordinal += direction;
                                newCaret = doc.getCaretCoords(ordinal);
                                if (differentLine(newCaret, originalCaret)) {
                                    ordinal -= direction;
                                    break;
                                }
                            }
                            return ordinal;
                        };

                        var handleKey = function(key, selecting, ctrlKey) {
                            var start = doc.selection.start,
                                end = doc.selection.end,
                                length = doc.frame.length - 1,
                                handled = false;

                            nextKeyboardX = null;

                            if (!selecting) {
                                keyboardSelect = 0;
                            } else if (!keyboardSelect) {
                                switch (key) {
                                case 37: // left arrow
                                case 38: // up - find character above
                                case 36: // start of line
                                case 33: // page up
                                    keyboardSelect = -1;
                                    break;
                                case 39: // right arrow
                                case 40: // down arrow - find character below
                                case 35: // end of line
                                case 34: // page down
                                    keyboardSelect = 1;
                                    break;
                                }
                            }

                            var ordinal = keyboardSelect === 1 ? end : start;

                            var changingCaret = false;
                            switch (key) {
                            case 37: // left arrow
                                if (!selecting && start != end) {
                                    ordinal = start;
                                } else {
                                    if (ordinal > 0) {
                                        if (ctrlKey) {
                                            var wordInfo = doc.wordContainingOrdinal(ordinal);
                                            if (wordInfo.ordinal === ordinal) {
                                                ordinal = wordInfo.index > 0 ? doc.wordOrdinal(wordInfo.index - 1) : 0;
                                            } else {
                                                ordinal = wordInfo.ordinal;
                                            }
                                        } else {
                                            ordinal--;
                                        }
                                    }
                                }
                                changingCaret = true;
                                break;
                            case 39: // right arrow
                                if (!selecting && start != end) {
                                    ordinal = end;
                                } else {
                                    if (ordinal < length) {
                                        if (ctrlKey) {
                                            var wordInfo = doc.wordContainingOrdinal(ordinal);
                                            ordinal = wordInfo.ordinal + wordInfo.word.length;
                                        } else {
                                            ordinal++;
                                        }
                                    }
                                }
                                changingCaret = true;
                                break;
                            case 40: // down arrow - find character below
                                ordinal = changeLine(ordinal, 1);
                                changingCaret = true;
                                break;
                            case 38: // up - find character above
                                ordinal = changeLine(ordinal, -1);
                                changingCaret = true;
                                break;
                            case 36: // start of line
                                ordinal = endOfline(ordinal, -1);
                                changingCaret = true;
                                break;
                            case 35: // end of line
                                ordinal = endOfline(ordinal, 1);
                                changingCaret = true;
                                break;
                            case 33: // page up
                                ordinal = 0;
                                changingCaret = true;
                                break;
                            case 34: // page down
                                ordinal = length;
                                changingCaret = true;
                                break;
                            case 8: // backspace
                                if (start === end && start > 0) {
                                    doc.range(start - 1, start).clear();
                                    focusChar = start - 1;
                                    doc.select(focusChar, focusChar,true);
                                    doc.notifySelectionChanged(true);
                                    handled = true;
                                }
                                break;
                            case 46: // del
                                if (start === end && start < length) {
                                    doc.range(start, start + 1).clear();
                                    handled = true;
                                }
                                break;
                            case 90: // Z undo
                                if (ctrlKey) {
                                    handled = true;
                                    doc.performUndo();
                                }
                                break;
                            case 89: // Y undo
                                if (ctrlKey) {
                                    handled = true;
                                    doc.performUndo(true);
                                }
                                break;
                            case 65: // A select all
                                if (ctrlKey) {
                                    handled = true;
                                    doc.select(0, length,true);
                                }
                                break;
                            case 67: // C - copy to clipboard
                            case 88: // X - cut to clipboard
                                if (ctrlKey) {
                                    // Allow standard handling to take place as well
                                    richClipboard = doc.selectedRange().save();
                                    plainClipboard = doc.selectedRange().plainText();
                                }
                                break;
                            }

                            var toggle = toggles[key];
                            if (ctrlKey && toggle) {
                                var selRange = doc.selectedRange();
                                selRange.setFormatting(toggle, selRange.getFormatting()[toggle] !== true);
                                handled = true;
                            }

                            if (changingCaret) {
                                switch (keyboardSelect) {
                                case 0:
                                    start = end = ordinal;
                                    break;
                                case -1:
                                    start = ordinal;
                                    break;
                                case 1:
                                    end = ordinal;
                                    break;
                                }

                                if (start === end) {
                                    keyboardSelect = 0;
                                } else {
                                    if (start > end) {
                                        keyboardSelect = -keyboardSelect;
                                        var t = end;
                                        end = start;
                                        start = t;
                                    }
                                }
                                focusChar = ordinal;
                                doc.select(start, end,true);
                                doc.notifySelectionChanged(true);
                                handled = true;
                            }

                            keyboardX = nextKeyboardX;
                            return handled;
                        };

                        dom.handleEvent(textArea, 'keydown', function(ev) {
                            if (handleKey(ev.keyCode, ev.shiftKey, ev.ctrlKey)) {
                                return false;
                            }
                        });

                        dom.handleEvent(textArea, 'input', function() {
                            var newText = textArea.value;
                            if (textAreaContent != newText) {
                                textAreaContent = '';
                                textArea.value = '';
                                if (newText === plainClipboard) {
                                    newText = richClipboard;
                                }
                                doc.insert(newText,true);
                            }
                        });

                        var updateTextArea = function() {
                            focusChar = focusChar === null ? doc.selection.end : focusChar;
                            textAreaContent = doc.selectedRange().plainText();
                            textArea.value = textAreaContent;
                            textArea.select();
                            textArea.focus();
                        };

                        doc.dblclickHandler = function(node) {
                            keyboardX = null;
                            doc.isActive = true;
                            node = node.parent();
                            if (node) {
                                doc.select(node.ordinal, node.ordinal + (node.word ? node.word.text.length : node.length));
                            }
                            selectDragStart = null;
                            updateTextArea();
                            doc.updateCanvas();
                            blit();
                        };
                        doc.mousedownHandler = function(node) {
                            selectDragStart = node.ordinal;
                            doc.select(node.ordinal, node.ordinal,false);
                            keyboardX = null;
                        }
                        doc.mousemoveHandler = function(node) {
                            if (selectDragStart !== null) {
                                if (node) {
                                    focusChar = node.ordinal;
                                    if (selectDragStart > node.ordinal) {
                                        doc.select(node.ordinal, selectDragStart,false);
                                    } else {
                                        doc.select(selectDragStart, node.ordinal,false);
                                    }
                                }
                            }
                        };
                        doc.mouseupHandler = function(node) {
                            try{
                                keyboardX = null;
                                doc.isActive = true;
                                updateTextArea();
                                selectDragStart = null;
                                doc.selectionJustChanged = true;
                                nextCaretToggle = 0;
                                doc.updateCanvas();
                                doc.update();
				doc.notifySelectionChanged();
                                repaintCursor(doc);
                                blit();
                            }
                            catch(e){
                                console.log("mouseUp e",e);
                            }
                        };

                        var nextCaretToggle = new Date().getTime(),
                            focused = false,
                            cachedWidth = host.clientWidth,
                            cachedHeight = host.clientHeight;

                        doc.update = function() {
                            if(Conversations.getCurrentSlideJid() == doc.slide){
                                /*Expire the timeouts once we're in a different spot*/
                                if(doc.isActive){
                                    var now = new Date().getTime();
                                    if (now > nextCaretToggle) {
                                        nextCaretToggle = now + 500;
                                        if (doc.toggleCaret()) {
                                            repaintCursor(doc);
                                        }
                                    }
                                    setTimeout(doc.update,500);
                                }
                            }
                        };

                        doc.sendKey = handleKey;
                        return doc;
                    };
                },
                "frame.js": function (exports, module, require) {
                    var node = require('./node');
                    var wrap = require('./wrap');
                    var rect = require('./rect');

                    var scaledWidth = function(){
                        return scaleWorldToScreen(Modes.text.minimumWidth);
                    };
                    var scaledHeight = function(){
                        return scaleWorldToScreen(Modes.text.minimumHeight());
                    };
                    var prototype = node.derive({
                        bounds: function() {
                            var b = this._bounds;
                            var valid = b && _.every(["l","t","w","h"],function(key){
                                return key in b;
                            });
                            if(!valid) {
                                var left = 0, top = 0, right = 0, bottom = 0;
                                if (this.lines && this.lines.length) {
                                    var first = this.lines[0].bounds();
                                    left = first.l;
                                    top = first.t;
                                    this.lines.forEach(function(line,i) {
                                        var b = line.bounds();
                                        right = Math.max(right, b.l + b.w);
                                        bottom = Math.max(bottom, b.t + b.h);
                                    });
                                }
                                this._bounds = rect(left, top, Math.max(right - left,scaledWidth()), Math.max(this.height ? this.height : bottom - top,scaledHeight()));
                            }
                            return this._bounds;
                        },
                        actualWidth: function() {
                            if (!this._actualWidth) {
                                var result = 0;
                                if(this.lines){
                                    this.lines.forEach(function(line) {
                                        if (typeof line.actualWidth === 'number') {
                                            result = Math.max(result, line.actualWidth);
                                        }
                                    });
                                }
                                this._actualWidth = Math.max(scaledWidth(),result);
                            }
                            return this._actualWidth;
                        },
                        children: function() {
                            return this.lines;
                        },
                        parent: function() {
                            return this._parent;
                        },
                        draw: function(ctx, viewPort) {
                            var top = viewPort ? viewPort.t : 0;
                            var bottom = viewPort ? (viewPort.t + viewPort.h) : Number.MAX_VALUE;
                            if(this.lines){
                                this.lines.some(function(line) {
                                    line.draw(ctx, viewPort);
                                });
                            }
                        },
                        type: 'frame'
                    });

                    exports = module.exports = function(left, top, width, ordinal, parent,
                                                        includeTerminator, initialAscent, initialDescent) {
                        var lines = [];
                        var frame = Object.create(prototype, {
                            lines: { value: lines },
                            _parent: { value: parent },
                            ordinal: { value: ordinal }
                        });
                        var wrapper = wrap(left, top, width, ordinal, frame, includeTerminator, initialAscent, initialDescent);
                        var length = 0, height = 0;
                        return function(emit, word) {
                            if (wrapper(function(line) {
                                if (typeof line === 'number') {
                                    height = line;
                                } else {
                                    length = (line.ordinal + line.length) - ordinal;
                                    lines.push(line);
                                }
                            }, word)) {
                                Object.defineProperty(frame, 'length', { value: length });
                                Object.defineProperty(frame, 'height', { value: height });
                                emit(frame);
                                return true;
                            }
                        };
                    };
                },
                "html.js": function (exports, module, require) {
                    var runs = require('./runs');
                    var per = require('per');

                    var tag = function(name, formattingProperty) {
                        return function(node, formatting) {
                            if (node.nodeName === name) {
                                formatting[formattingProperty] = true;
                            }
                        };
                    };

                    var value = function(type, styleProperty, formattingProperty, transformValue) {
                        return function(node, formatting) {
                            var val = node[type] && node[type][styleProperty];
                            if (val) {
                                if (transformValue) {
                                    val = transformValue(val);
                                }
                                formatting[formattingProperty] = val;
                            }
                        };
                    };

                    var attrValue = function(styleProperty, formattingProperty, transformValue) {
                        return value('attributes', styleProperty, formattingProperty, transformValue);
                    };

                    var styleValue = function(styleProperty, formattingProperty, transformValue) {
                        return value('style', styleProperty, formattingProperty, transformValue);
                    };

                    var styleFlag = function(styleProperty, styleValue, formattingProperty) {
                        return function(node, formatting) {
                            if (node.style && node.style[styleProperty] === styleValue) {
                                formatting[formattingProperty] = true;
                            }
                        };
                    };

                    var obsoleteFontSizes = [ 6, 7, 9, 10, 12, 16, 20, 30 ];

                    var aligns = { left: true, center: true, right: true, justify: true };

                    var checkAlign = function(value) {
                        return aligns[value] ? value : 'left';
                    };

                    var fontName = function(name) {
                        var s = name.split(/\s*,\s*/g);
                        if (s.length == 0) {
                            return name;
                        }
                        name = s[0]
                        var raw = name.match(/^"(.*)"$/);
                        if (raw) {
                            return raw[1].trim();
                        }
                        raw = name.match(/^'(.*)'$/);
                        if (raw) {
                            return raw[1].trim();
                        }
                        return name;
                    };

                    var headings = {
                        H1: 30,
                        H2: 20,
                        H3: 16,
                        H4: 14,
                        H5: 12
                    };

                    var handlers = [
                        tag('B', 'bold'),
                        tag('STRONG', 'bold'),
                        tag('I', 'italic'),
                        tag('EM', 'italic'),
                        tag('U', 'underline'),
                        tag('S', 'strikeout'),
                        tag('STRIKE', 'strikeout'),
                        tag('DEL', 'strikeout'),
                        styleFlag('fontWeight', 'bold', 'bold'),
                        styleFlag('fontStyle', 'italic', 'italic'),
                        styleFlag('textDecoration', 'underline', 'underline'),
                        styleFlag('textDecoration', 'line-through', 'strikeout'),
                        styleValue('color', 'color'),
                        styleValue('fontFamily', 'font', fontName),
                        styleValue('fontSize', 'size', function(size) {
                            var m = size.match(/^([\d\.]+)pt$/);
                            return m ? parseFloat(m[1]) : 10
                        }),
                        styleValue('textAlign', 'align', checkAlign),
                        function(node, formatting) {
                            if (node.nodeName === 'SUB') {
                                formatting.script = 'sub';
                            }
                        },
                        function(node, formatting) {
                            if (node.nodeName === 'SUPER') {
                                formatting.script = 'super';
                            }
                        },
                        function(node, formatting) {
                            if (node.nodeName === 'CODE') {
                                formatting.font = 'monospace';
                            }
                        },
                        function(node, formatting) {
                            var size = headings[node.nodeName];
                            if (size) {
                                formatting.size = size;
                            }
                        },
                        attrValue('color', 'color'),
                        attrValue('face', 'font', fontName),
                        attrValue('align', 'align', checkAlign),
                        attrValue('size', 'size', function(size) {
                            return obsoleteFontSizes[size] || 10;
                        })
                    ];

                    var newLines = [ 'BR', 'P', 'H1', 'H2', 'H3', 'H4', 'H5' ];
                    var isNewLine = {};
                    newLines.forEach(function(name) {
                        isNewLine[name] = true;
                    });

                    exports.parse = function(html, classes) {
                        var root = html;
                        if (typeof root === 'string') {
                            root = document.createElement('div');
                            root.innerHTML = html;
                        }

                        var result = [], inSpace = true;
                        var cons = per(runs.consolidate()).into(result);
                        var emit = function(text, formatting) {
                            cons.submit(Object.create(formatting, {
                                text: { value: text }
                            }));
                        };
                        var dealWithSpaces = function(text, formatting) {
                            text = text.replace(/\n+\s*/g, ' ');
                            var fullLength = text.length;
                            text = text.replace(/^\s+/, '');
                            if (inSpace) {
                                inSpace = false;
                            } else if (fullLength !== text.length) {
                                text = ' ' + text;
                            }
                            fullLength = text.length;
                            text = text.replace(/\s+$/, '');
                            if (fullLength !== text.length) {
                                inSpace = true;
                                text += ' ';
                            }
                            emit(text, formatting);
                        };

                        function recurse(node, formatting) {
                            if (node.nodeType == 3) {
                                dealWithSpaces(node.nodeValue, formatting);
                            } else {
                                if (node == undefined){
                                    return;
                                } else {
                                    formatting = Object.create(formatting);

                                    var classNames = node.attributes['class'];
                                    if (classNames) {
                                        classNames.value.split(' ').forEach(function(cls) {
                                            cls = classes[cls];
                                            if (cls) {
                                                Object.keys(cls).forEach(function(key) {
                                                    formatting[key] = cls[key];
                                                });
                                            }
                                        })
                                    }

                                    handlers.forEach(function(handler) {
                                        handler(node, formatting);
                                    });
                                    if (node.childNodes) {
                                        for (var n = 0; n < node.childNodes.length; n++) {
                                            recurse(node.childNodes[n], formatting);
                                        }
                                    }
                                    if (isNewLine[node.nodeName]) {
                                        emit('\n', formatting);
                                        inSpace = true;
                                    }
                                }
                            }
                        }
                        recurse(root, {});
                        return result;
                    };

                },
                "line.js": function (exports, module, require) {
                    var positionedWord = require('./positionedword');
                    var rect = require('./rect');
                    var node = require('./node');
                    var runs = require('./runs');

                    /*  A Line is returned by the wrap function. It contains an array of PositionedWord objects that are
                     all on the same physical line in the wrapped text.

                     It has a width (which is actually the same for all lines returned by the same wrap). It also has
                     coordinates for baseline, ascent and descent. The ascent and descent have the maximum values of
                     the individual words' ascent and descent coordinates.

                     It has methods:

                     draw(ctx, x, y)
                     - Draw all the words in the line applying the specified (x, y) offset.
                     bounds()
                     - Returns a Rect for the bounding box.
                     */

                    var prototype = node.derive({
                        bounds: function(minimal) {
                            if (minimal) {
                                var firstWord = this.first().bounds(),
                                    lastWord = this.last().bounds();
                                return rect(
                                    firstWord.l,
                                    this.baseline - this.ascent,
                                    (lastWord.l + lastWord.w) - firstWord.l,
                                    this.ascent + this.descent);
                            }
                            return rect(this.left, this.baseline - this.ascent,
                                        this.width, this.ascent + this.descent);
                        },
                        parent: function() {
                            return this.doc;
                        },
                        children: function() {
                            return this.positionedWords;
                        },
                        type: 'line'
                    });

                    module.exports = function(doc, left, width, baseline, ascent, descent, words, ordinal) {

                        var align = words[0].align();

                        var line = Object.create(prototype, {
                            doc: { value: doc }, // should be called frame, or else switch to using parent on all nodes
                            left: { value: left },
                            width: { value: width },
                            baseline: { value: baseline },
                            ascent: { value: ascent },
                            descent: { value: descent },
                            ordinal: { value: ordinal },
                            align: { value: align }
                        });

                        var actualWidth = 0;
                        words.forEach(function(word) {
                            actualWidth += word.width;
                        });
                        actualWidth -= words[words.length - 1].space.width;

                        var x = 0, spacing = 0;
                        if (actualWidth < width) {
                            switch (align) {
                            case 'right':
                                x = width - actualWidth;
                                break;
                            case 'center':
                                x = (width - actualWidth) / 2;
                                break;
                            case 'justify':
                                if (words.length > 1 && !words[words.length - 1].isNewLine()) {
                                    spacing = (width - actualWidth) / (words.length - 1);
                                }
                                break;
                            }
                        }

                        Object.defineProperty(line, 'positionedWords', {
                            value: words.map(function(word) {
                                var wordLeft = x;
                                x += (word.width + spacing);
                                var wordOrdinal = ordinal;
                                ordinal += (word.text.length + word.space.length);
                                return positionedWord(word, line, wordLeft, wordOrdinal, word.width + spacing);
                            })
                        });

                        Object.defineProperty(line, 'actualWidth', { value: actualWidth });
                        Object.defineProperty(line, 'length', { value: ordinal - line.ordinal });
                        return line;
                    };
                },
                "node.js": function (exports, module, require) {
                    var per = require('per');
                    var runs = require('./runs');
                    var rect = require('./rect');
                    var util = require('./util');

                    exports.prototype = {
                        children: function() {
                            return [];
                        },
                        parent: function() {
                            return null;
                        },
                        first: function() {
                            return this.children()[0];
                        },
                        last: function() {
                            return this.children()[this.children().length - 1];
                        },
                        next: function() {
                            var self = this;
                            for (;;) {
                                var parent = self.parent();
                                if (!parent) {
                                    return null;
                                }
                                var siblings = parent.children();
                                var next = siblings[siblings.indexOf(self) + 1];
                                if (next) {
                                    for (;;)  {
                                        var first = next.first();
                                        if (!first) {
                                            break;
                                        }
                                        next = first;
                                    }
                                    return next;
                                }
                                self = parent;
                            }
                        },
                        previous: function() {
                            var parent = this.parent();
                            if (!parent) {
                                return null;
                            }
                            var siblings = parent.children();
                            var prev = siblings[siblings.indexOf(this) - 1];
                            if (prev) {
                                return prev;
                            }
                            var prevParent = parent.previous();
                            return !prevParent ? null : prevParent.last();
                        },
                        byOrdinal: function(index) {
                            var found = null;
                            if (this.children().some(function(child) {
                                if (index >= child.ordinal && index < child.ordinal + child.length) {
                                    found = child.byOrdinal(index);
                                    if (found) {
                                        return true;
                                    }
                                }
                            })) {
                                return found;
                            }
                            return this;
                        },
                        byCoordinate: function(x, y) {
                            var found;
                            this.children().some(function(child) {
                                var b = child.bounds();
                                if (b.contains(x, y)) {
                                    found = child.byCoordinate(x, y);
                                    if (found) {
                                        return true;
                                    }
                                }
                            });
                            if (!found) {
                                found = this.last();
                                while (found) {
                                    var next = found.last();
                                    if (!next) {
                                        break;
                                    }
                                    found = next;
                                }
                                var foundNext = found.next();
                                if (foundNext && foundNext.block) {
                                    found = foundNext;
                                }
                            }
                            return found;
                        },
                        draw: function(ctx, viewPort) {
                            this.children().forEach(function(child) {
                                child.draw(ctx, viewPort);
                            });
                        },
                        parentOfType: function(type) {
                            var p = this.parent();
                            return p && (p.type === type ? p : p.parentOfType(type));
                        },
                        bounds: function() {
                            var l = this._left, t = this._top, r = 0, b = 0;
                            var min = function(x,y){
                                if(isNaN(x)){ return y; }
                                return Math.min(x,y);
                            };
                            var max = function(x,y){
                                if(isNaN(x)){ return y; }
                                return Math.max(x,y);
                            };
                            this.children().forEach(function(child) {
                                var cb = child.bounds();
                                l = min(l, cb.l);
                                t = min(t, cb.t);
                                r = max(r, cb.l + cb.w);
                                b = max(b, cb.t + cb.h);
                            });
                            return rect(l, t, r - l, b - t);
                        }
                    };

                    exports.derive = function(methods) {
                        return util.derive(exports.prototype, methods);
                    };

                    var generic = exports.derive({
                        children: function() {
                            return this._children;
                        },
                        parent: function() {
                            return this._parent;
                        },
                        finalize: function(startDecrement, lengthIncrement) {
                            var start = Number.MAX_VALUE, end = 0;
                            this._children.forEach(function(child) {
                                start = Math.min(start, child.ordinal);
                                end = Math.max(end, child.ordinal + child.length);
                            });
                            Object.defineProperty(this, 'ordinal', { value: start - (startDecrement || 0) });
                            Object.defineProperty(this, 'length', { value: (lengthIncrement || 0) + end - start });
                        }
                    });

                    exports.generic = function(type, parent, left, top) {
                        return Object.create(generic, {
                            type: { value: type },
                            _children: { value: [] },
                            _parent: { value: parent },
                            _left: { value: typeof left === 'number' ? left : Number.MAX_VALUE },
                            _top: { value: typeof top === 'number' ? top : Number.MAX_VALUE }
                        });
                    };
                },
                "part.js": function (exports, module, require) {
                    var text = require('./text');

                    var defaultInline = {
                        measure: function(formatting) {
                            var text = text.measure('?', formatting);
                            return {
                                width: text.width + 4,
                                ascent: text.width + 2,
                                descent: text.width + 2
                            };
                        },
                        draw: function(ctx, x, y, width, ascent, descent) {
                            ctx.fillStyle = 'silver';
                            ctx.fillRect(x, y - ascent, width, ascent + descent);
                            ctx.strokeRect(x, y - ascent, width, ascent + descent);
                            ctx.fillStyle = 'black';
                            ctx.fillText('?', x + 2, y);
                        }
                    };

                    /*  A Part is a section of a word with its own run, because a Word can span the
                     boundaries between runs, so it may have several parts in its text or space
                     arrays.

                     run           - Run being measured.
                     isNewLine     - True if this part only contain a newline (\n). This will be
                     the only Part in the Word, and this is the only way newlines
                     ever occur.
                     width         - Width of the run
                     ascent        - Distance from baseline to top
                     descent       - Distance from baseline to bottom

                     And methods:

                     draw(ctx, x, y)
                     - Draws the Word at x, y on the canvas context ctx. The y
                     coordinate is assumed to be the baseline. The call
                     prepareContext(ctx) will set the canvas up appropriately.
                     */
                    var prototype = {
                        draw: function(ctx, x, y) {
                            if (typeof this.run.text === 'string') {
                                text.draw(ctx, this.run.text, this.run, x, y, this.width, this.ascent, this.descent);
                            } else if (this.code && this.code.draw) {
                                ctx.save();
                                this.code.draw(ctx, x, y, this.width, this.ascent, this.descent, this.run);
                                ctx.restore();
                            }
                        }
                    };

                    module.exports = function(run, codes) {

                        var m, isNewLine, code;
                        if (typeof run.text === 'string') {
                            isNewLine = (run.text.length === 1) && (run.text[0] === '\n');
                            m = text.measure(isNewLine ? text.nbsp : run.text, run);
                        } else {
                            code = codes(run.text) || defaultInline;
                            m = code.measure ? code.measure(run) : {
                                width: 0, ascent: 0, descent: 0
                            };
                        }

                        var part = Object.create(prototype, {
                            run: { value: run },
                            isNewLine: { value: isNewLine },
                            width: { value: isNewLine ? 0 : m.width },
                            ascent: { value: m.ascent },
                            descent: { value: m.descent }
                        });
                        if (code) {
                            Object.defineProperty(part, 'code', { value: code });
                        }
                        return part;
                    };
                },
                "positionedword.js": function (exports, module, require) {
                    var rect = require('./rect');
                    var part = require('./part');
                    var text = require('./text');
                    var node = require('./node');
                    var word = require('./word');
                    var runs = require('./runs');

                    var newLineWidth = function(run) {
                        return text.measure(text.enter, run).width;
                    };

                    var positionedChar = node.derive({
                        bounds: function() {
                            var wb = this.word.bounds();
                            var width = this.word.word.isNewLine()
                                    ? newLineWidth(this.word.word.run)
                                    : this.width || this.part.width;
                            return rect(wb.l + this.left, wb.t, width, wb.h);
                        },
                        parent: function() {
                            return this.word;
                        },
                        byOrdinal: function() {
                            return this;
                        },
                        byCoordinate: function(x, y) {
                            if (x <= this.bounds().center().x) {
                                return this;
                            }
                            return this.next();
                        },
                        type: 'character'
                    });

                    /*  A positionedWord is just a realised Word plus a reference back to the containing Line and
                     the left coordinate (x coordinate of the left edge of the word).

                     It has methods:

                     draw(ctx, x, y)
                     - Draw the word within its containing line, applying the specified (x, y)
                     offset.
                     bounds()
                     - Returns a rect for the bounding box.
                     */
                    var prototype = node.derive({
                        draw: function(ctx) {
                            this.word.draw(ctx, this.line.left + this.left, this.line.baseline);

                            // Handy for showing how word boundaries work
                            /*
                             var b = this.bounds();
                             ctx.strokeRect(b.l, b.t, b.w, b.h);
                             */
                        },
                        bounds: function() {
                            return rect(
                                this.line.left + this.left,
                                this.line.baseline - this.line.ascent,
                                this.word.isNewLine() ? newLineWidth(this.word.run) : this.width,
                                this.line.ascent + this.line.descent);
                        },
                        parts: function(eachPart) {
                            this.word.text.parts.some(eachPart) ||
                                this.word.space.parts.some(eachPart);
                        },
                        realiseCharacters: function() {
                            if (!this._characters) {
                                var cache = [];
                                var x = 0, self = this, ordinal = this.ordinal,
                                    codes = this.parentOfType('document').codes;
                                this.parts(function(wordPart) {
                                    runs.pieceCharacters(function(char) {
                                        var charRun = Object.create(wordPart.run);
                                        charRun.text = char;
                                        var p = part(charRun, codes);
                                        cache.push(Object.create(positionedChar, {
                                            left: { value: x },
                                            part: { value: p },
                                            word: { value: self },
                                            ordinal: { value: ordinal },
                                            length: { value: 1 }
                                        }));
                                        x += p.width;
                                        ordinal++;
                                    }, wordPart.run.text);
                                });
                                // Last character is artificially widened to match the length of the
                                // word taking into account (align === 'justify')
                                var lastChar = cache[cache.length - 1];
                                if (lastChar) {
                                    Object.defineProperty(lastChar, 'width',
                                                          { value: this.width - lastChar.left });
                                    if (this.word.isNewLine() || (this.word.code() && this.word.code().eof)) {
                                        Object.defineProperty(lastChar, 'newLine', { value: true });
                                    }
                                }
                                this._characters = cache;
                            }
                        },
                        children: function() {
                            this.realiseCharacters();
                            return this._characters;
                        },
                        parent: function() {
                            return this.line;
                        },
                        type: 'word'
                    });

                    module.exports = function(word, line, left, ordinal, width) {
                        var pword = Object.create(prototype, {
                            word: { value: word },
                            line: { value: line },
                            left: { value: left },
                            width: { value: width }, // can be different to word.width if (align == 'justify')
                            ordinal: { value: ordinal },
                            length: { value: word.text.length + word.space.length }
                        });
                        return pword;
                    };
                },
                "range.js": function (exports, module, require) {
                    var per = require('per');
                    var runs = require('./runs');

                    function Range(doc, start, end) {
                        this.doc = doc;
                        this.start = start;
                        this.end = end;
                        if (start > end) {
                            this.start = end;
                            this.end = start;
                        }
                    }

                    Range.prototype.parts = function(emit, list) {
                        list = list || this.doc.children();
                        var self = this;

                        list.some(function(item) {
                            if (item.ordinal + item.length <= self.start) {
                                return false;
                            }
                            if (item.ordinal >= self.end) {
                                return true;
                            }
                            if (item.ordinal >= self.start &&
                                item.ordinal + item.length <= self.end) {
                                emit(item);
                            } else {
                                self.parts(emit, item.children());
                            }
                        });
                    };

                    Range.prototype.clear = function() {
                        return this.setText([]);
                    };

                    Range.prototype.setText = function(text) {
                        return this.doc.splice(this.start, this.end, text);
                    };

                    Range.prototype.runs = function(emit) {
                        this.doc.runs(emit, this);
                    };

                    Range.prototype.plainText = function() {
                        return per(this.runs, this).map(runs.getPlainText).all().join('');
                    };

                    Range.prototype.save = function() {
                        return per(this.runs, this).per(runs.consolidate()).all();
                    };

                    Range.prototype.getFormatting = function() {
                        var range = this;
                        if (range.start === range.end) {
                            var pos = range.start;
                            // take formatting of character before, if any, because that's
                            // where plain text picks up formatting when inserted
                            if (pos > 0) {
                                pos--;
                            }
                            range.start = pos;
                            range.end = pos + 1;
                        }
                        return per(range.runs, range).reduce(runs.merge).last() || runs.defaultFormatting;
                    };

                    Range.prototype.setFormatting = function(attribute, value) {
                        var range = this;
                        if (attribute === 'align') {
                            // Special case: expand selection to surrounding paragraphs
                            range = range.doc.paragraphRange(range.start, range.end);
                        }
                        if(attribute === "color"){
                            // The font selector does not provide the right types for this editor
                            if(typeof value == "string"){
                                value = [value,255];
                            }
                        }
                        if (range.start === range.end) {
                            range.doc.modifyInsertFormatting(attribute, value);
                        } else {
                            var saved = range.save();
                            var template = {};
                            template[attribute] = value;
                            runs.format(saved, template);
                            range.setText(saved);
                        }
                    };

                    module.exports = function(doc, start, end) {
                        return new Range(doc, start, end);
                    };
                },
                "rect.js": function (exports, module, require) {

                    var prototype = {
                        contains: function(x, y) {
                            return x >= this.l && x < (this.l + this.w) &&
                                y >= this.t && y < (this.t + this.h);

                        },
                        stroke: function(ctx) {
                            ctx.strokeRect(this.l, this.t, this.w, this.h);
                        },
                        fill: function(ctx) {
                            ctx.fillRect(this.l, this.t, this.w, this.h);
                        },
                        offset: function(x, y) {
                            return rect(this.l + x, this.t + y, this.w, this.h);
                        },
                        equals: function(other) {
                            return this.l === other.l && this.t === other.t &&
                                this.w === other.w && this.h === other.h;
                        },
                        center: function() {
                            return { x: this.l + this.w/2, y: this.t + this.h/2 };
                        }
                    };

                    var rect = module.exports = function(l, t, w, h) {
                        return Object.create(prototype, {
                            l: { value: l },
                            t: { value: t },
                            w: { value: w },
                            h: { value: h },
                            r: { value: l + w },
                            b: { value: t + h }
                        });
                    };
                },
                "runs.js": function (exports, module, require) {
                    exports.formattingKeys = [ 'bold', 'italic', 'underline', 'strikeout', 'color', 'font', 'size', 'align', 'script' ];

                    exports.defaultFormatting = {
                        size: 14,
                        newBoxSize:14,
                        font: 'sans-serif',
                        color: ['#000000',255],
                        bold: false,
                        italic: false,
                        underline: false,
                        strikeout: false,
                        align: 'left',
                        script: 'normal'
                    };

                    exports.resolveKey = function(run,key){
                        return (key in run)? run[key] : exports.defaultFormatting[key];
                    }
                    exports.sameFormatting = function(run1, run2) {
                        return exports.formattingKeys.every(function(key) {
                            var e = _.isEqual(exports.resolveKey(run1,key),
                                              exports.resolveKey(run2,key));
                            return e;
                        });
                    };

                    exports.clone = function(run) {
                        var result = { text: run.text };
                        exports.formattingKeys.forEach(function(key) {
                            var val = run[key];
                            if (val && val != exports.defaultFormatting[key]) {
                                result[key] = val;
                            }
                        });
                        return result;
                    };

                    exports.multipleValues = {};

                    exports.merge = function(run1, run2) {
                        if (arguments.length === 1) {
                            return Array.isArray(run1) ? run1.reduce(exports.merge) : run1;
                        }
                        if (arguments.length > 2) {
                            return exports.merge(Array.prototype.slice.call(arguments, 0));
                        }
                        var merged = {};
                        exports.formattingKeys.forEach(function(key) {
                            if (key in run1 || key in run2) {
                                if (run1[key] === run2[key]) {
                                    merged[key] = run1[key];
                                } else {
                                    merged[key] = exports.multipleValues;
                                }
                            }
                        });
                        return merged;
                    };

                    exports.format = function(run, template) {
                        if (Array.isArray(run)) {
                            run.forEach(function(r) {
                                exports.format(r, template);
                            });
                        } else {
                            Object.keys(template).forEach(function(key) {
                                if (template[key] !== exports.multipleValues) {
                                    run[key] = template[key];
                                }
                            });
                        }
                    };

                    exports.consolidate = function() {
                        var current;
                        return function (emit, run) {
                            if (!current || !exports.sameFormatting(current, run) ||
                                (typeof current.text != 'string') ||
                                (typeof run.text != 'string')) {
                                current = exports.clone(run);
                                emit(current);
                            } else {
                                current.text += run.text;
                            }
                        };
                    };

                    exports.getPlainText = function(run) {
                        if (typeof run.text === 'string') {
                            return run.text;
                        }
                        if (Array.isArray(run.text)) {
                            var str = [];
                            run.text.forEach(function(piece) {
                                str.push(exports.getPiecePlainText(piece));
                            });
                            return str.join('');
                        }
                        return '_';
                    };

                    /*  The text property of a run can be an ordinary string, or a "character object",
                     or it can be an array containing strings and "character objects".

                     A character object is not a string, but is treated as a single character.

                     We abstract over this to provide the same string-like operations regardless.
                     */
                    exports.getPieceLength = function(piece) {
                        return piece.length || 1; // either a string or something like a character
                    };

                    exports.getPiecePlainText = function(piece) {
                        return piece.length ? piece : '_';
                    };

                    exports.getTextLength = function(text) {
                        if (typeof text === 'string') {
                            return text.length;
                        }
                        if (Array.isArray(text)) {
                            var length = 0;
                            text.forEach(function(piece) {
                                length += exports.getPieceLength(piece);
                            });
                            return length;
                        }
                        return 1;
                    };

                    exports.getSubText = function(emit, text, start, count) {
                        if (count === 0) {
                            return;
                        }
                        if (typeof text === 'string') {
                            emit(text.substr(start, count));
                            return;
                        }
                        if (Array.isArray(text)) {
                            var pos = 0;
                            text.some(function(piece) {
                                if (count <= 0) {
                                    return true;
                                }
                                var pieceLength = exports.getPieceLength(piece);
                                if (pos + pieceLength > start) {
                                    if (pieceLength === 1) {
                                        emit(piece);
                                        count -= 1;
                                    } else {
                                        var str = piece.substr(Math.max(0, start - pos), count);
                                        emit(str);
                                        count -= str.length;
                                    }
                                }
                                pos += pieceLength;
                            });
                            return;
                        }
                        emit(text);
                    };

                    exports.getTextChar = function(text, offset) {
                        var result;
                        exports.getSubText(function(c) { result = c }, text, offset, 1);
                        return result;
                    };

                    exports.pieceCharacters = function(each, piece) {
                        if (typeof piece === 'string') {
                            for (var c = 0; c < piece.length; c++) {
                                each(piece[c]);
                            }
                        } else {
                            each(piece);
                        }
                    };
                },
                "split.js": function (exports, module, require) {
                    /*  Creates a stateful transformer function that consumes Characters and produces "word coordinate"
                     objects, which are triplets of Characters representing the first characters of:

                     start   - the word itself
                     end     - any trailing whitespace
                     next    - the subsequent word, or end of document.

                     Newline characters are NOT whitespace. They are always emitted as separate single-character
                     words.

                     If start.equals(end) then the "word" only contains whitespace and so must represent spaces
                     at the start of a line. So even in this case, whitespace is always treated as "trailing
                     after" a word - even if that word happens to be zero characters long!
                     */

                    module.exports = function(codes) {
                        var word = null, trailingSpaces = null, newLine = true;

                        return function(emit, inputChar) {

                            var endOfWord;
                            if (inputChar.char === null) {
                                endOfWord = true;
                            } else {
                                if (newLine) {
                                    endOfWord = true;
                                    newLine = false;
                                }
                                if (typeof inputChar.char === 'string') {
                                    switch (inputChar.char) {
                                    case ' ':
                                        if (!trailingSpaces) {
                                            trailingSpaces = inputChar;
                                        }
                                        break;
                                    case '\n':
                                        endOfWord = true;
                                        newLine = true;
                                        break;
                                    default:
                                        if (trailingSpaces) {
                                            endOfWord = true;
                                        }
                                    }
                                } else {
                                    var code = codes(inputChar.char);
                                    if (code.block || code.eof) {
                                        endOfWord = true;
                                        newLine = true;
                                    }
                                }
                            }
                            if (endOfWord) {
                                if (word && !word.equals(inputChar)) {
                                    if (emit({
                                        text: word,
                                        spaces: trailingSpaces || inputChar,
                                        end: inputChar
                                    }) === false) {
                                        return false;
                                    }
                                    trailingSpaces = null;
                                }
                                if (inputChar.char === null) {
                                    emit(null); // Indicate end of stream
                                }

                                word = inputChar;
                            }
                        };
                    };
                },
                "text.js": function (exports, module, require) {
                    var runs = require('./runs');

                    /*  Returns a font CSS/Canvas string based on the settings in a run
                     */
                    var getFontString = exports.getFontString = function(run) {

                        var size = (run && run.size) || runs.defaultFormatting.size;

                        if (run) {
                            switch (run.script) {
                            case 'super':
                            case 'sub':
                                size *= 0.8;
                                break;
                            }
                        }

                        return (run && run.italic ? 'italic ' : '') +
                            (run && run.bold ? 'bold ' : '') + ' ' +
                            size + 'pt ' +
                            ((run && run.font) || runs.defaultFormatting.font);
                    };

                    /*  Applies the style of a run to the canvas context
                     */
                    exports.applyRunStyle = function(ctx, run) {
                        ctx.fillStyle = (run && run.color && run.color[0]) || runs.defaultFormatting.color[0];
                        ctx.font = getFontString(run);
                    };

                    exports.prepareContext = function(ctx) {
                        ctx.textAlign = 'left';
                        ctx.textBaseline = 'alphabetic';
                    };

                    /* Generates the value for a CSS style attribute
                     */
                    exports.getRunStyle = function(run) {
                        var parts = [
                            'font: ', getFontString(run),
                            '; color: ', ((run && run.color && run.color[0]) || runs.defaultFormatting.color[0])
                        ];

                        if (run) {
                            switch (run.script) {
                            case 'super':
                                parts.push('; vertical-align: super');
                                break;
                            case 'sub':
                                parts.push('; vertical-align: sub');
                                break;
                            }
                        }

                        return parts.join('');
                    };

                    var nbsp = exports.nbsp = String.fromCharCode(160);
                    var enter = exports.enter = nbsp; // String.fromCharCode(9166);

                    /*  Returns width, height, ascent, descent in pixels for the specified text and font.
                     The ascent and descent are measured from the baseline. Note that we add/remove
                     all the DOM elements used for a measurement each time - this is not a significant
                     part of the cost, and if we left the hidden measuring node in the DOM then it
                     would affect the dimensions of the whole page.
                     */
                    var measureText = exports.measureText = function(text, style) {
                        var span, block, div;

                        span = document.createElement('span');
                        block = document.createElement('div');
                        div = document.createElement('div');

                        block.style.display = 'inline-block';
                        block.style.width = '1px';
                        block.style.height = '0';

                        div.style.visibility = 'hidden';
                        div.style.position = 'absolute';
                        div.style.top = '0';
                        div.style.left = '-10000px';
                        div.style.width = '10000px';
                        div.style.height = '200px';

                        div.appendChild(span);
                        div.appendChild(block);
                        document.body.appendChild(div);
                        try {
                            span.setAttribute('style', style);

                            span.innerHTML = '';
                            span.appendChild(document.createTextNode(text.replace(/\s/g, nbsp)));

                            var result = {};
                            block.style.verticalAlign = 'baseline';
                            result.ascent = (block.offsetTop - span.offsetTop);
                            block.style.verticalAlign = 'bottom';
                            result.height = (block.offsetTop - span.offsetTop);
                            result.descent = result.height - result.ascent;
                            result.width = span.offsetWidth;
                        } finally {
                            div.parentNode.removeChild(div);
                            div = null;
                        }
                        return result;
                    };

                    /*  Create a function that works like measureText except it caches every result for every
                     unique combination of (text, style) - that is, it memoizes measureText.

                     So for example:

                     var measure = cachedMeasureText();

                     Then you can repeatedly do lots of separate calls to measure, e.g.:

                     var m = measure('Hello, world', 'font: 12pt Arial');
                     console.log(m.ascent, m.descent, m.width);

                     A cache may grow without limit if the text varies a lot. However, during normal interactive
                     editing the growth rate will be slow. If memory consumption becomes a problem, the cache
                     can be occasionally discarded, although of course this will cause a slow down as the cache
                     has to build up again (text measuring is by far the most costly operation we have to do).
                     */
                    var createCachedMeasureText = exports.createCachedMeasureText = function() {
                        var cache = {};
                        return function(text, style) {
                            var key = style + '<>!&%' + text;
                            var result = cache[key];
                            if (!result) {
                                cache[key] = result = measureText(text, style);
                            }
                            return result;
                        };
                    };

                    exports.cachedMeasureText = createCachedMeasureText();

                    exports.measure = function(str, formatting) {
                        return exports.cachedMeasureText(str, exports.getRunStyle(formatting));
                    };

                    exports.draw = function(ctx, str, formatting, left, baseline, width, ascent, descent) {
                        exports.prepareContext(ctx);
                        exports.applyRunStyle(ctx, formatting);
                        switch (formatting.script) {
                        case 'super':
                            baseline -= (ascent * (1/3));
                            break;
                        case 'sub':
                            baseline += (descent / 2);
                            break;
                        }
                        ctx.fillText(str === '\n' ? exports.enter : str, left, baseline);
                        if (formatting.underline) {
                            ctx.fillRect(left, 1 + baseline, width, 1);
                        }
                        if (formatting.strikeout) {
                            ctx.fillRect(left, 1 + baseline - (ascent/2), width, 1);
                        }
                    };
                },
                "util.js": function (exports, module, require) {
                    exports.event = function() {
                        var handlers = [];

                        var subscribe = function(handler) {
                            handlers.push(handler);
                        };

                        subscribe.fire = function() {
                            var args = Array.prototype.slice.call(arguments, 0);
                            handlers.forEach(function(handler) {
                                handler.apply(null, args);
                            });
                        };

                        return subscribe;
                    };

                    exports.derive = function(prototype, methods) {
                        var properties = {};
                        Object.keys(methods).forEach(function(name) {
                            properties[name] = { value: methods[name] };
                        });
                        return Object.create(prototype, properties);
                    };
                },
                "word.js": function (exports, module, require) {
                    var per = require('per');
                    var part = require('./part');
                    var runs = require('./runs');

                    /*  A Word has the following properties:

                     text      - Section (see below) for non-space portion of word.
                     space     - Section for trailing space portion of word.
                     ascent    - Ascent (distance from baseline to top) for whole word
                     descent   - Descent (distance from baseline to bottom) for whole word
                     width     - Width of the whole word (including trailing space)

                     It has methods:

                     isNewLine()
                     - Returns true if the Word represents a newline. Newlines are
                     always represented by separate words.

                     draw(ctx, x, y)
                     - Draws the Word at x, y on the canvas context ctx.

                     Note: a section (i.e. text and space) is an object containing:

                     parts     - array of Parts
                     ascent    - Ascent (distance from baseline to top) for whole section
                     descent   - Descent (distance from baseline to bottom) for whole section
                     width     - Width of the whole section
                     */

                    var prototype = {
                        isNewLine: function() {
                            return this.text.parts.length == 1 && this.text.parts[0].isNewLine;
                        },
                        code: function() {
                            return this.text.parts.length == 1 && this.text.parts[0].code;
                        },
                        codeFormatting: function() {
                            return this.text.parts.length == 1 && this.text.parts[0].run;
                        },
                        draw: function(ctx, x, y) {
                            per(this.text.parts).concat(this.space.parts).forEach(function(part) {
                                part.draw(ctx, x, y);
                                x += part.width;
                            });
                        },
                        plainText: function() {
                            return this.text.plainText + this.space.plainText;
                        },
                        align: function() {
                            var first = this.text.parts[0];
                            return first ? first.run.align : 'left';
                        },
                        runs: function(emit, range) {
                            var start = range && range.start || 0,
                                end = range && range.end;
                            if (typeof end !== 'number') {
                                end = Number.MAX_VALUE;
                            }
                            [this.text, this.space].forEach(function(section) {
                                section.parts.some(function(part) {
                                    if (start >= end || end <= 0) {
                                        return true;
                                    }
                                    var run = part.run, text = run.text;
                                    if (typeof text === 'string') {
                                        if (start <= 0 && end >= text.length) {
                                            emit(run);
                                        } else if (start < text.length) {
                                            var pieceRun = Object.create(run);
                                            var firstChar = Math.max(0, start);
                                            pieceRun.text = text.substr(
                                                firstChar,
                                                Math.min(text.length, end - firstChar)
                                            );
                                            emit(pieceRun);
                                        }
                                        start -= text.length;
                                        end -= text.length;
                                    } else {
                                        if (start <= 0 && end >= 1) {
                                            emit(run);
                                        }
                                        start--;
                                        end--;
                                    }
                                });
                            });
                        }
                    };

                    var section = function(runEmitter, codes) {
                        var s = {
                            parts: per(runEmitter).map(function(p) {
                                return part(p, codes);
                            }).all(),
                            ascent: 0,
                            descent: 0,
                            width: 0,
                            length: 0,
                            plainText: ''
                        };
                        s.parts.forEach(function(p) {
                            s.ascent = Math.max(s.ascent, p.ascent);
                            s.descent = Math.max(s.descent, p.descent);
                            s.width += p.width;
                            s.length += runs.getPieceLength(p.run.text);
                            s.plainText += runs.getPiecePlainText(p.run.text);
                        });
                        return s;
                    };

                    module.exports = function(coords, codes) {
                        var text, space;
                        if (!coords) {
                            // special end-of-document marker, mostly like a newline with no formatting
                            text = [{ text: '\n' }];
                            space = [];
                        } else {
                            text = coords.text.cut(coords.spaces);
                            space = coords.spaces.cut(coords.end);
                        }
                        text = section(text, codes);
                        space = section(space, codes);
                        var word = Object.create(prototype, {
                            text: { value: text },
                            space: { value: space },
                            ascent: { value: Math.max(text.ascent, space.ascent) },
                            descent: { value: Math.max(text.descent, space.descent) },
                            width: { value: text.width + space.width, configurable: true },
                            length: { value: text.length + space.length }
                        });
                        if (!coords) {
                            Object.defineProperty(word, 'eof', { value: true });
                        }
                        return word;
                    };
                },
                "wrap.js": function (exports, module, require) {
                    var line = require('./line');

                    /*  A stateful transformer function that accepts words and emits lines. If the first word
                     is too wide, it will overhang; if width is zero or negative, there will be one word on
                     each line.

                     The y-coordinate is the top of the first line, not the baseline.

                     Returns a stream of line objects, each containing an array of positionedWord objects.
                     */

                    module.exports = function(left, top, width, ordinal, parent,
                                              includeTerminator, initialAscent, initialDescent) {

                        var lineBuffer = [],
                            lineWidth = 0,
                            maxAscent = initialAscent || 0,
                            maxDescent = initialDescent || 0,
                            quit,
                            lastNewLineHeight = 0,
                            y = top;

                        var store = function(word, emit) {
                            lineBuffer.push(word);
                            lineWidth += word.width;
                            maxAscent = Math.max(maxAscent, word.ascent);
                            maxDescent = Math.max(maxDescent, word.descent);
                            if (word.isNewLine()) {
                                send(emit);
                                lastNewLineHeight = word.ascent + word.descent;
                            }
                        };

                        var send = function(emit) {
                            if (quit || lineBuffer.length === 0) {
                                return;
                            }
                            var l = line(parent, left, width, y + maxAscent, maxAscent, maxDescent, lineBuffer, ordinal);
                            ordinal += l.length;
                            quit = emit(l);
                            y += (maxAscent + maxDescent);
                            lineBuffer.length = 0;
                            lineWidth = maxAscent = maxDescent = 0;
                        };

                        var consumer = null;

                        return function(emit, inputWord) {
                            if (consumer) {
                                lastNewLineHeight = 0;
                                var node = consumer(inputWord);
                                if (node) {
                                    consumer = null;
                                    ordinal += node.length;
                                    y += node.bounds().h;
                                    Object.defineProperty(node, 'block', { value: true });
                                    emit(node);
                                }
                            } else {
                                var code = inputWord.code();
                                if (code && code.block) {
                                    if (lineBuffer.length) {
                                        send(emit);
                                    } else {
                                        y += lastNewLineHeight;
                                    }
                                    consumer = code.block(left, y, width, ordinal, parent, inputWord.codeFormatting());
                                    lastNewLineHeight = 0;
                                }
                                else if (code && code.eof || inputWord.eof) {
                                    if (!code || (includeTerminator && includeTerminator(code))) {
                                        store(inputWord, emit);
                                    }
                                    if (!lineBuffer.length) {
                                        emit(y + lastNewLineHeight - top);
                                    } else {
                                        send(emit);
                                        emit(y - top);
                                    }
                                    quit = true;
                                } else {
                                    lastNewLineHeight = 0;
                                    if (!lineBuffer.length) {
                                        store(inputWord, emit);
                                    } else {
                                        if (lineWidth + inputWord.text.width > width) {
                                            send(emit);
                                        }
                                        store(inputWord, emit);
                                    }
                                }
                            }
                            return quit;
                        };
                    };
                }
            }
        },
        "per": {
            ":mainpath:": "per.js",
            "per.js": function (exports, module, require) {
                (function(exportFunction) {
                    function toFunc(valOrFunc, bindThis) {
                        if (typeof valOrFunc !== 'function') {
                            return Array.isArray(valOrFunc)
                                ? function(emit) {
                                    return valOrFunc.some(emit);
                                } : function(emit) {
                                    return emit(valOrFunc);
                                };
                        }
                        if (bindThis) {
                            return function(emit, value) {
                                valOrFunc.call(bindThis, emit, value);
                            }
                        }
                        return valOrFunc;
                    }

                    function Per(valOrFunc, bindThis) {
                        this.forEach = toFunc(valOrFunc, bindThis);
                    }

                    function blank(emit, value) {
                        emit(value);
                    }

                    function create(valOrFunc, bindThis) {
                        if (arguments.length === 0) {
                            return new Per(blank);
                        }
                        if (valOrFunc && valOrFunc instanceof Per) {
                            return valOrFunc;
                        }
                        return new Per(valOrFunc, bindThis)
                    }

                    Per.prototype.per = function(valOrFunc, bindThis) {
                        var first = this.forEach;
                        var second = toFunc(valOrFunc && valOrFunc.forEach || valOrFunc, bindThis);
                        return create(function(emit, value) {
                            return first(function(firstVal) {
                                return second(emit, firstVal);
                            }, value);
                        });
                    };

                    function lambda(expression) {
                        return typeof expression === 'string'
                            ? new Function('x', 'return ' + expression)
                            : expression;
                    }

                    Per.prototype.map = function(mapFunc) {
                        mapFunc = lambda(mapFunc);
                        return this.per(function(emit, value) {
                            return emit(mapFunc(value));
                        });
                    };

                    Per.prototype.filter = function(predicate) {
                        predicate = lambda(predicate);
                        return this.per(function(emit, value) {
                            if (predicate(value)) {
                                return emit(value);
                            }
                        });
                    };

                    Per.prototype.concat = function(second, secondThis) {
                        if (second instanceof Per) {
                            second = second.forEach;
                        } else {
                            second = toFunc(second, secondThis);
                        }
                        var first = this.forEach;
                        return create(function(emit, value) {
                            first(emit, value);
                            second(emit, value);
                        });
                    };

                    Per.prototype.skip = function(count) {
                        return this.per(function(emit, value) {
                            if (count > 0) {
                                count--;
                                return false;
                            }
                            return emit(value);
                        });
                    };

                    Per.prototype.take = function(count) {
                        return this.per(function(emit, value) {
                            if (count <= 0) {
                                return true;
                            }
                            count--;
                            return emit(value);
                        });
                    };

                    Per.prototype.listen = function(untilFunc) {
                        return this.per(function(emit, value) {
                            if (untilFunc(value)) {
                                return true;
                            }
                            return emit(value);
                        });
                    };

                    Per.prototype.flatten = function() {
                        return this.per(function(emit, array) {
                            return !Array.isArray(array)
                                ? emit(array)
                                : array.some(function(value) {
                                    return emit(value);
                                });
                        });
                    };

                    Per.prototype.reduce = function(reducer, seed) {
                        var result = seed, started = arguments.length == 2;
                        return this.per(function(emit, value) {
                            result = started ? reducer(result, value) : value;
                            emit(result);
                            started = true;
                        });
                    };

                    Per.prototype.multicast = function(destinations) {
                        if (arguments.length !== 1) {
                            destinations = Array.prototype.slice.call(arguments, 0);
                        }
                        destinations = destinations.map(function(destination) {
                            return typeof destination === 'function' ? destination :
                                destination instanceof Per ? destination.forEach :
                                ignore;
                        });
                        return this.listen(function(value) {
                            var quit = true;
                            destinations.forEach(function(destination) {
                                if (!destination(ignore, value)) {
                                    quit = false;
                                }
                            });
                            return quit;
                        });
                    };

                    function optionalLimit(limit) {
                        return typeof limit != 'number' ? Number.MAX_VALUE : limit;
                    }

                    /*  A passive observer - gathers results into the specified array, but
                     otherwise has no effect on the stream of values
                     */
                    Per.prototype.into = function(ar, limit) {
                        if (!Array.isArray(ar)) {
                            throw new Error("into expects an array");
                        }
                        limit = optionalLimit(limit);
                        return this.listen(function(value) {
                            if (limit <= 0) {
                                return true;
                            }
                            ar.push(value);
                            limit--;
                        });
                    };

                    function setOrCall(obj, name) {
                        var prop = obj[name];
                        if (typeof prop === 'function') {
                            return prop;
                        }
                        return function(val) {
                            obj[name] = val;
                        }
                    }

                    /*  Tracks first, last and count for the values as they go past,
                     up to an optional limit (see 'first' and 'last' methods).
                     */
                    Per.prototype.monitor = function(data) {
                        var n = 0;
                        var count = setOrCall(data, 'count'),
                            first = setOrCall(data, 'first'),
                            last = setOrCall(data, 'last'),
                            limit = data.limit;
                        if (typeof limit != 'number') {
                            limit = Number.MAX_VALUE;
                        }
                        if (limit < 1) {
                            return this;
                        }
                        return this.listen(function(value) {
                            if (n === 0) {
                                first(value);
                            }
                            n++;
                            count(n);
                            last(value);
                            if (n >= limit) {
                                return true;
                            }
                        });
                    };

                    /*  Send a value into the pipeline without caring what emerges
                     (only useful if you set up monitors and/or intos, or
                     similar stateful observers).
                     */
                    function ignore() { }
                    Per.prototype.submit = function(value) {
                        return this.forEach(ignore, value);
                    };

                    Per.prototype.all = function() {
                        var results = [];
                        this.into(results).submit();
                        return results;
                    };

                    Per.prototype.first = function() {
                        var results = { limit: 1 };
                        this.monitor(results).submit();
                        return results.count > 0 ? results.first : (void 0);
                    };

                    Per.prototype.last = function() {
                        var results = {};
                        this.monitor(results).submit();
                        return results.count > 0 ? results.last : (void 0);
                    };

                    function truthy(value) { return !!value; }
                    Per.prototype.truthy = function() { return this.filter(truthy); };

                    function min(l, r) { return Math.min(l, r); }
                    Per.prototype.min = function() { return this.reduce(min, Number.MAX_VALUE); };

                    function max(l, r) { return Math.max(l, r); }
                    Per.prototype.max = function() { return this.reduce(max, Number.MIN_VALUE); };

                    function sum(l, r) { return l + r }
                    Per.prototype.sum = function() { return this.reduce(sum, 0); };

                    function and(l, r) { return !!(l && r) }
                    Per.prototype.and = function() { return this.reduce(and, true); };

                    function or(l, r) { return !!(l || r) }
                    Per.prototype.or = function() { return this.reduce(or, false); };

                    function not(v) { return !v }
                    Per.prototype.not = function() { return this.map(not); };

                    create.pulse = function(ms) {
                        var counter = 0;
                        return create(function(emit) {
                            function step() {
                                if (emit(counter++) !== true) {
                                    setTimeout(step, ms);
                                }
                            }
                            step();
                        });
                    };

                    exportFunction(create);

                })(function(per) {
                    if (typeof exports === 'undefined') {
                        this['per'] = per;
                    } else {
                        module.exports = per;
                    }
                });
            }
        }
    })("carota/src/carota");
})();
