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

import java.util.ArrayList;

public class ParserCombinators {
  public static void main(String[] args) {
    CombinatorialParser parser = new SimpleExpressionGrammar().start.compress();

    String theExpression = randomExpression(20);
    if (theExpression.length() != 41137) {
       throw new RuntimeException("Generated expression of the wrong size");
    }
    Object result = parser.parseWithContext(new ParserContext(theExpression));
    if ((Long)result != 31615) {
      throw new RuntimeException("Expression evaluated to wrong value");
    }

    // Warm up.
    for (int i = 0; i < 3; i++) {
      parser.parseWithContext(new ParserContext(theExpression));
    }

    // Measure for at least 10 seconds.
    long startTime = System.currentTimeMillis();
    long duration;
    int runs = 0;
    do {
      parser.parseWithContext(new ParserContext(theExpression));
      runs++;
      duration = System.currentTimeMillis() - startTime;
    } while(duration < 10000);
    
    System.out.println("ParserCombinators: " +
                       (runs*1000.0/duration) +
                       " runs/sec");
  }

  // A fixed sequence of psuedo-random numbers.
  static int seed = 0xCAFE;
  static int nextRandom() {
    seed = seed * 0xDEAD + 0xC0DE;
    seed = seed & 0x0FFF;
    return seed;
  }
  static String randomExpression(int depth) {
    if (depth<1) {
      return Integer.toString(nextRandom()%10);
    }
    switch ((int)(nextRandom()%3L)) {
      case 0:
          return randomExpression(depth-1) + "+" + randomExpression(depth-1);
      case 1:
          return randomExpression(depth-1) + "*" + randomExpression(depth-1);
      case 2:
          return "(" + randomExpression(depth-1) + ")";
    }
    throw new RuntimeException("UNREACHABLE");
  }
}


class ParserContext {
  private String content;
  private int pos;
  ParserContext(String s) {
    content = s;
    pos = 0;
  }
  int position() {
    return pos;
  }
  void position(int p) {
    pos = p;
  }
  char next() {
    return content.charAt(pos++);
  }
  boolean atEnd() {
    return pos >= content.length();
  }
}


interface Transform {
  Object transform(Object o);
}


class ParserError extends RuntimeException {  
}


abstract class CombinatorialParser {
  boolean compressed = false;
  Object parseWithContext(ParserContext ctxt) {
    throw new RuntimeException("Subclass responsibility");
  }
  void bind(CombinatorialParser p) {
    throw new RuntimeException("Subclass responsibility");
  }
  CombinatorialParser compress() {
    throw new RuntimeException("Subclass responsibility");
  }
  CombinatorialParser then(CombinatorialParser p) {
    CombinatorialParser[] l = new CombinatorialParser[2];
    l[0] = this;
    l[1] = p;
    return new SequencingParser(l);
  }
  CombinatorialParser character(char c) {
    return new CharacterRangeParser(c, c);
  }
  CombinatorialParser characterRange(char p, char q) {
    return new CharacterRangeParser(p, q);
  }
  CombinatorialParser eoi() {
    return new EOIParser();
  }
  CombinatorialParser star() {
    return new StarParser(this);
  }
  CombinatorialParser wrap(Transform t) {
    return new WrappingParser(this, t);
  }
  CombinatorialParser or(CombinatorialParser q) {
    return new AlternatingParser(this, q);
  }
}


class CharacterRangeParser extends CombinatorialParser {
  char lowerBound;
  char upperBound;
  CharacterRangeParser(char p, char q) {
    lowerBound = p;
    upperBound = q;
  }
  Object parseWithContext(ParserContext ctxt) {
    if (!ctxt.atEnd()) {
      char c = ctxt.next();
      if ((lowerBound <= c) && (c <= upperBound)) {
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
  CombinatorialParser[] subparsers;
  SequencingParser(CombinatorialParser[] subparsers) {
    this.subparsers = subparsers;
  }
  CombinatorialParser then(CombinatorialParser p) {
    CombinatorialParser[] l = new CombinatorialParser[subparsers.length + 1];
    for(int i = 0; i < subparsers.length; i++) {
      l[i] = subparsers[i];
    }
    l[subparsers.length] = p;
    return new SequencingParser(l);
  }
  Object parseWithContext(ParserContext ctxt) {
    Object[] results = new Object[subparsers.length];
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
    for (int i = 0; i < subparsers.length; i++) {
      subparsers[i] = subparsers[i].compress();
    }
    return this;
  }
}


class AlternatingParser extends CombinatorialParser {
  CombinatorialParser p, q;
  AlternatingParser(CombinatorialParser p, CombinatorialParser q) {
    this.p = p;
    this.q = q;
  }
  Object parseWithContext(ParserContext ctxt) {
    int pos = ctxt.position();
    try {
      return p.parseWithContext(ctxt);
    } catch (ParserError e) {
      ctxt.position(pos);
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
  StarParser(CombinatorialParser p) {
    this.subparser = p;
  }
  Object parseWithContext(ParserContext ctxt) {
    ArrayList<Object> results = new ArrayList<Object>();
    for (;;) {
      int pos = ctxt.position();
      try {
        results.add( subparser.parseWithContext(ctxt) );
      } catch(ParserError e) {
        ctxt.position(pos);
        return results.toArray();
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
  Transform transform;
  WrappingParser(CombinatorialParser p, Transform t) {
    this.subparser = p;
    this.transform = t;
  }
  Object parseWithContext(ParserContext ctxt) {
    return transform.transform(subparser.parseWithContext(ctxt));
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
      throw new RuntimeException("Forward reference parser already bound");
    }
    forwardee = p;
  }
  CombinatorialParser compress() {
    return forwardee.compress();
  }
  Object parseWithContext(ParserContext ctxt){
    throw new RuntimeException(
        "Forward reference parsers should be compressed away before parsing");
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
    start.bind(exp.then(eoi()).wrap(
        new Transform() { public Object transform(Object o) {
          return ((Object[])o)[0];
        }}
    ));
    
    exp.bind(e1.then(plus.then(e1).star()).wrap(
        new Transform() { public Object transform(Object o) {
          long lhs = (Long)(((Object[])o)[0]);
          Object rhss = ((Object[])o)[1];
          for (Object rhs : (Object[])rhss) {
            lhs = (lhs + ((Long)((Object[])rhs)[1])) % 0xFFFF;
          }
          return lhs;
        }}
    ));
    
    e1.bind(e2.then(times.then(e2).star()).wrap(
        new Transform() { public Object transform(Object o) {
          long lhs = (Long)(((Object[])o)[0]);
          Object rhss = ((Object[])o)[1];
          for (Object rhs : (Object[])rhss) {
            lhs = (lhs * ((Long)((Object[])rhs)[1])) % 0xFFFF;
          }
          return lhs;
        }}
    ));
    
    e2.bind(number.or(parenExp));
    
    parenExp.bind(lparen.then(exp).then(rparen).wrap(
        new Transform() { public Object transform(Object o) {
          return ((Object[])o)[1];
        }}
    ));
    
    number.bind(digit.wrap(
       new Transform() { public Object transform(Object o) {
         return (long)Character.getNumericValue((Character)o);
       }}
    ));
    
    plus.bind(character('+'));
    times.bind(character('*'));
    digit.bind(characterRange('0','9'));
    lparen.bind(character('('));
    rparen.bind(character(')'));
  }
}
