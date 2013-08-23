/**
 * This benchmark parses and evaluates a fixed string using a simple arithmetic
 * expresssion grammar built from parser combinators. It is an example of a
 * program that is natural to write in an exception-oriented style, or with
 * non-local returns in a language that has them. The natural translation of
 * NLRs to a language without them also uses exceptions in this way.
 *
 * These parser combinators use explicitly initialized forward reference parsers
 * to handle cycles in the productions, rather than using reflection or 
 * #doesNotUnderstand:, to make the benchmark portable to languages lacking
 * these features and to avoid measuring their performance. They also do not use
 * any platform-defined streams to avoid API differences.  Arithmetic operations
 * are masked to keep all intermediate results within Smi range.
 *
 * This benchmark is derived from the Newspeak version of CombinatorialParsers,
 * which is why the Cadence copyrights apply.
 *
 * Copyright 2008 Cadence Design Systems, Inc.
 * Copyright 2012 Cadence Design Systems, Inc.
 * Copyright 2013 Ryan Macnak and Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 */

function main() {
  var parser = new SimpleExpressionGrammar().start.compress();

  var theExpression = randomExpression(20);
  if (theExpression.length != 41137) {
    throw "Generated expression of the wrong size";
  }
  if (parser.parseWithContext(new ParserContext(theExpression)) != 31615) {
    throw "Expression evaluated to wrong value";
  }

  // Warm up.
  for (var i = 0; i < 3; i++) {
    parser.parseWithContext(new ParserContext(theExpression));
  }

  // Measure for at least 10 seconds.
  var startTime = (new Date()).getTime();
  var duration;
  var runs = 0;
  do {
    parser.parseWithContext(new ParserContext(theExpression));
    runs++;
    duration = (new Date()).getTime() - startTime;
  } while(duration < 10000);

  var result = "ParserCombinators: "+(runs*1000.0/duration)+" runs/sec";
  if (typeof console == "object" && typeof console.log == "function") {
    console.log(result);
  } else if (typeof print == "function") {
    print(result);
  }
}


// A fixed sequence of psuedo-random numbers.
var seed = 0xCAFE;
function nextRandom() {
  seed = seed * 0xDEAD + 0xC0DE;
  seed = seed & 0x0FFF;
  return seed;
}
function randomExpression(depth) {
  if (depth<1) {
    return (nextRandom() % 10).toString();
  }
  switch (nextRandom() % 3) {
    case 0:
        return randomExpression(depth-1) + "+" + randomExpression(depth-1);
    case 1:
        return randomExpression(depth-1) + "*" + randomExpression(depth-1);
    case 2:
        return "(" + randomExpression(depth-1) + ")";
  }
  throw "UNREACHABLE";
}


Object.prototype.inheritsFrom = function(shuper) {
  function Inheriter() { }
  Inheriter.prototype = shuper.prototype;
  this.prototype = new Inheriter();
  this.superConstructor = shuper;
}


function ParserContext(content) {
  this._content = content;
  this._pos = 0;
}
ParserContext.prototype.getPosition = function() {
  return this._pos;
}
ParserContext.prototype.setPosition = function(p) {
  this._pos = p;
}
ParserContext.prototype.next = function() {
  return this._content[this._pos++];
}
ParserContext.prototype.atEnd = function() {
  return this._pos >= this._content.length;
}


function ParserError() {
}


function CombinatorialParser() {
  this.compressed = false;
}
CombinatorialParser.prototype.parseWithContext = function(ctxt) {
  throw "Subclass responsibility";
}
CombinatorialParser.prototype.bind = function(p) {
  throw "Subclass responsibility";
}
CombinatorialParser.prototype.compress = function() {
  throw "Subclass responsibility";
}
CombinatorialParser.prototype.then = function(p) {
  var l = new Array();
  l[0] = this;
  l[1] = p;
  return new SequencingParser(l);
}
CombinatorialParser.prototype.character = function(c) {
  return new CharacterRangeParser(c, c);
}
CombinatorialParser.prototype.characterRange = function(p, q) {
  return new CharacterRangeParser(p, q);
}
CombinatorialParser.prototype.eoi = function() {
  return new EOIParser();
}
CombinatorialParser.prototype.star = function() {
  return new StarParser(this);
}
CombinatorialParser.prototype.wrap = function(t) {
  return new WrappingParser(this, t);
}
CombinatorialParser.prototype.or = function(q) {
  return new AlternatingParser(this, q);
}


function CharacterRangeParser(p, q) {
  this.lowerBound = p;
  this.upperBound = q;
  this.compressed = false;
}
CharacterRangeParser.inheritsFrom(CombinatorialParser);
CharacterRangeParser.prototype.parseWithContext = function(ctxt) {
  if (!ctxt.atEnd()) {
    var c = ctxt.next();
    if ((this.lowerBound <= c) && (c <= this.upperBound)) {
      return c;
    }
  }
  throw new ParserError();
}
CombinatorialParser.prototype.compress = function() {
  return this;
}


function SequencingParser(subparsers) {
  this.subparsers = subparsers;
  this.compressed = false;
}
SequencingParser.inheritsFrom(CombinatorialParser);
SequencingParser.prototype.then = function(p){
  var l = new Array();
  for(var i = 0; i < this.subparsers.length; i++) {
    l[i] = this.subparsers[i];
  }
  l[this.subparsers.length] = p;
  return new SequencingParser(l);
}
SequencingParser.prototype.parseWithContext = function(ctxt) {
  var results = new Array();
  for(var i = 0; i < this.subparsers.length; i++){
    results[i] = this.subparsers[i].parseWithContext(ctxt);
  }
  return results;
}
SequencingParser.prototype.compress = function() {
  if (this.compressed) {
    return this;
  }
  this.compressed = true;
  for(var i = 0; i < this.subparsers.length; i++){
    this.subparsers[i] = this.subparsers[i].compress();
  }
  return this;
}


function AlternatingParser(p,q) {
  this.p = p;
  this.q = q;
  this.compressed = false;
}
AlternatingParser.inheritsFrom(CombinatorialParser);
AlternatingParser.prototype.parseWithContext = function(ctxt) {
  var pos = ctxt.getPosition();
  try {
    return this.p.parseWithContext(ctxt);
  } catch (e) {
    ctxt.setPosition(pos);
    return this.q.parseWithContext(ctxt);
  }
}
AlternatingParser.prototype.compress = function() {
  if (this.compressed) {
    return this;
  }
  this.compressed = true;
  this.p = this.p.compress();
  this.q = this.q.compress();
  return this;
}


function StarParser(p) {
  this.subparser = p;
  this.compressed = false;
}
StarParser.inheritsFrom(CombinatorialParser);
StarParser.prototype.parseWithContext = function(ctxt){
  var results = new Array();
  for (;;) {
    var pos = ctxt.getPosition();
    try {
      results.push(this.subparser.parseWithContext(ctxt));
    } catch(e) {
      ctxt.setPosition(pos);
      return results;
    }
  }
}
StarParser.prototype.compress = function() {
  if (this.compressed) {
    return this;
  }
  this.compressed = true;
  this.subparser = this.subparser.compress();
  return this;
}


function EOIParser() {}
EOIParser.inheritsFrom(CombinatorialParser);
EOIParser.prototype.parseWithContext = function(ctxt) {
  if (ctxt.atEnd()) {
    return null;
  }
  throw new ParserError();
}
EOIParser.prototype.compress = function() {
  return this;
}


function WrappingParser(p,t) {
  this.subparser = p;
  this.transform = t;
  this.compressed = false;
}
WrappingParser.inheritsFrom(CombinatorialParser);
WrappingParser.prototype.parseWithContext = function(ctxt) {
    return this.transform(this.subparser.parseWithContext(ctxt));
}
WrappingParser.prototype.compress = function() {
    if(this.compressed) return this;
    this.compressed = true;
    this.subparser = this.subparser.compress();
    return this;
}



function ForwardReferenceParser() {
  this.forwardee = null;
}
ForwardReferenceParser.inheritsFrom(CombinatorialParser);
ForwardReferenceParser.prototype.bind = function(p) {
  if (this.forwardee != null) {
    throw "Forward reference parser already bound";
  }
  this.forwardee = p;
}
ForwardReferenceParser.prototype.compress = function() {
  return this.forwardee.compress();
}
ForwardReferenceParser.prototype.parseWithContext = function(ctxt) {
  throw "Forward reference parsers should be compressed away before parsing";
}


function SimpleExpressionGrammar() {
  this.start = new ForwardReferenceParser();
  this.exp = new ForwardReferenceParser();
  this.e1 = new ForwardReferenceParser();
  this.e2 = new ForwardReferenceParser();
  
  this.parenExp = new ForwardReferenceParser();
  this.number = new ForwardReferenceParser();
  
  this.plus = new ForwardReferenceParser();
  this.times = new ForwardReferenceParser();
  this.digit = new ForwardReferenceParser();
  this.lparen = new ForwardReferenceParser();
  this.rparen = new ForwardReferenceParser();
  
  this.start.bind(this.exp.then(this.eoi()).wrap(
    function (o) { 
      return o[0];
    }
  ));

  this.exp.bind(this.e1.then(this.plus.then(this.e1).star()).wrap(
      function (o) {
        var lhs = o[0];
        var rhss = o[1];
        for (var i = 0; i < rhss.length; i++) {
          lhs = (lhs + rhss[i][1]) % 0xFFFF;
        }
        return lhs;
      }
  ));

  this.e1.bind(this.e2.then(this.times.then(this.e2).star()).wrap(
      function (o) {
        var lhs = o[0];
        var rhss = o[1];
        for (var i = 0; i < rhss.length; i++) {
          lhs = (lhs * rhss[i][1]) % 0xFFFF;
        }
        return lhs;
      }
  ));

  this.e2.bind(this.number.or(this.parenExp));

  this.parenExp.bind( this.lparen.then(this.exp).then(this.rparen).wrap(
      function (o) {
        return o[1];
      }
  ));

  this.number.bind(this.digit.wrap(function(o) { return parseInt(o); }));
 
  this.plus.bind(this.character('+'));
  this.times.bind(this.character('*'));
  this.digit.bind(this.characterRange('0', '9'));
  this.lparen.bind(this.character('('));
  this.rparen.bind(this.character(')'));
}
SimpleExpressionGrammar.inheritsFrom(CombinatorialParser);

main();
