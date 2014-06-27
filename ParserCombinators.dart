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

main() {
  CombinatorialParser parser = new SimpleExpressionGrammar().start.compress();

  String theExpression = randomExpression(20);
  if (theExpression.length!=41137) {
    throw "Generated expression of the wrong size";
  }
  if (parser.parseWithContext(new ParserContext(theExpression)) != 31615){
    throw "Expression evaluated to wrong value";
  }

  // Warm up.
  for (int i = 0; i < 3; i++) {
    parser.parseWithContext(new ParserContext(theExpression));
  }

  // Measure for at least 10 seconds.
  num startTime = new DateTime.now().millisecondsSinceEpoch;
  num duration;
  int runs = 0;
  do {
    parser.parseWithContext(new ParserContext(theExpression));
    runs++;
    duration = new DateTime.now().millisecondsSinceEpoch - startTime;
  } while(duration < 10000);

  print("ParserCombinators: ${(runs*1000.0/duration).toString()} runs/sec");
}


// A fixed sequence of psuedo-random numbers.
int seed = 0xCAFE;
int nextRandom() {
  seed = seed * 0xDEAD + 0xC0DE;
  seed = seed & 0x0FFF;
  return seed;
}
String randomExpression(int depth) {
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


class ParserContext {
  String _content;
  int _pos;
  ParserContext(String s) {
    _content = s;
    _pos = 0;
  }
  get position {
    return _pos;
  }
  set position(int p){
    _pos = p;
  }
  String next(){
    return _content[_pos++];
  }
  bool atEnd(){
    return _pos >= _content.length;
  }
}


class ParserError {
}


abstract class CombinatorialParser {
  bool compressed = false;
  Object parseWithContext(ParserContext ctxt) {
    throw "Subclass responsibility";
  }
  void bind(CombinatorialParser p) {
    throw "Subclass responsibility";
  }
  CombinatorialParser compress() {
    throw "Subclass responsibility";
  }
  CombinatorialParser then(CombinatorialParser p) {
    return new SequencingParser([this, p]);
  }
  CombinatorialParser character(String c) {
    return new CharacterRangeParser(c,c);
  }
  CombinatorialParser characterRange(String p, String q) {
    return new CharacterRangeParser(p,q);
  }
  CombinatorialParser eoi() {
    return new EOIParser();
  }
  CombinatorialParser star() {
    return new StarParser(this);
  }
  CombinatorialParser wrap(Function t) {
    return new WrappingParser(this, t);
  }
  CombinatorialParser or(CombinatorialParser q) {
    return new AlternatingParser(this, q);
  }
}


class CharacterRangeParser extends CombinatorialParser {
  String lowerBound;
  String upperBound;
  CharacterRangeParser(this.lowerBound, this.upperBound);
  Object parseWithContext(ParserContext ctxt) {
    if (!ctxt.atEnd()) {
      String c = ctxt.next();
      if ((lowerBound.compareTo(c) <= 0) && (c.compareTo(upperBound) <= 0)) {
        return c;
      }
    }
    throw new ParserError();
  }
  CombinatorialParser compress() {
    return this;
  }
}


class SequencingParser extends CombinatorialParser {
  List<CombinatorialParser> subparsers;
  SequencingParser(this.subparsers);
  CombinatorialParser then(CombinatorialParser p) {
    var l = new List<CombinatorialParser>(subparsers.length + 1);
    for(int i = 0; i < subparsers.length; i++) {
      l[i] = subparsers[i];
    }
    l[subparsers.length] = p;
    return new SequencingParser(l);
  }
  Object parseWithContext(ParserContext ctxt) {
  	List results = new List(subparsers.length);
    for (int i=0; i<subparsers.length; i++) {
      results[i] = subparsers[i].parseWithContext(ctxt);
    }
    return results;
  }
  CombinatorialParser compress() {
    if (compressed) {
      return this;
    }
    compressed = true;
    for(int i = 0; i < subparsers.length; i++) {
      subparsers[i] = subparsers[i].compress();
    }
    return this;
  }
}


class AlternatingParser extends CombinatorialParser {
  CombinatorialParser p, q;
  AlternatingParser(this.p, this.q);
  Object parseWithContext(ParserContext ctxt) {
    int pos = ctxt.position;
    try {
      return p.parseWithContext(ctxt);
    } catch (e) {
      ctxt.position = pos;
      return q.parseWithContext(ctxt);
    }
  }
  CombinatorialParser compress() {
    if (compressed) {
      return this;
    }
    compressed = true;
    p = p.compress();
    q = q.compress();
    return this;
  }
}


class StarParser extends CombinatorialParser {
  CombinatorialParser subparser;
  StarParser(this.subparser);
  Object parseWithContext(ParserContext ctxt) {
    List results = new List();
    for (;;) {
      int pos = ctxt.position;
      try {
        results.add(subparser.parseWithContext(ctxt));
      } catch(e) {
        ctxt.position = pos;
        return results;
      }
    }
  }
  CombinatorialParser compress() {
    if (compressed) {
      return this;
    }
    compressed = true;
    subparser = subparser.compress();
    return this;
  }
}


class EOIParser extends CombinatorialParser {
  Object parseWithContext(ParserContext ctxt) {
    if (ctxt.atEnd()) {
      return null;
    }
    throw new ParserError();
  }
  CombinatorialParser compress() {
    return this;
  }
}


class WrappingParser extends CombinatorialParser {
  CombinatorialParser subparser;
  Function transform;
  WrappingParser(this.subparser, this.transform);
  Object parseWithContext(ParserContext ctxt) {
    return transform(subparser.parseWithContext(ctxt));
  }
  CombinatorialParser compress(){
    if (compressed) {
      return this;
    }
    compressed = true;
    subparser = subparser.compress();
    return this;
  }
}


class ForwardReferenceParser extends CombinatorialParser {
  CombinatorialParser forwardee;
  void bind(CombinatorialParser p) {
    if (forwardee != null) {
      throw "Forward reference parser already bound";
    }
    forwardee = p;
  }
  CombinatorialParser compress() {
    return forwardee.compress();
  }
  Object parseWithContext(ParserContext ctxt) {
    throw "Forward reference parsers should be compressed away before parsing";
  }
}


class SimpleExpressionGrammar extends CombinatorialParser {
  CombinatorialParser start = new ForwardReferenceParser();
  CombinatorialParser exp = new ForwardReferenceParser();
  CombinatorialParser e1 = new ForwardReferenceParser();
  CombinatorialParser e2 = new ForwardReferenceParser();
  
  CombinatorialParser parenExp = new ForwardReferenceParser();
  CombinatorialParser number = new ForwardReferenceParser();
  
  CombinatorialParser plus = new ForwardReferenceParser();
  CombinatorialParser times = new ForwardReferenceParser();
  CombinatorialParser digit = new ForwardReferenceParser();
  CombinatorialParser lparen = new ForwardReferenceParser();
  CombinatorialParser rparen = new ForwardReferenceParser();
  
  SimpleExpressionGrammar() {
    start.bind(exp.then(eoi()).wrap((o) {return o[0];}));

    exp.bind(e1.then(plus.then(e1).star()).wrap(
      (o) {
        int lhs = o[0];
        List rhss = o[1];
        for (var i = 0; i < rhss.length; i++) {
          lhs = (lhs + rhss[i][1]) % 0xFFFF;
         }
        return lhs;
      }
    ));
    
    e1.bind(e2.then(times.then(e2).star()).wrap(
      (o) {
        int lhs = o[0];
        List rhss = o[1];
        for (var i = 0; i < rhss.length; i++) {
          lhs = (lhs * rhss[i][1]) % 0xFFFF;
         }
        return lhs;
      }
    ));
    
    e2.bind(number.or(parenExp));

    parenExp.bind(lparen.then(exp).then(rparen).wrap((o) { return o[1]; }));
        
    number.bind(digit.wrap((o) { return int.parse(o); }));
    
    plus.bind(character('+'));
    times.bind(character('*'));
    digit.bind(characterRange('0','9'));
    lparen.bind(character('('));
    rparen.bind(character(')'));
  }
}
