This benchmark parses and evaluates a fixed string using a simple arithmetic
expresssion grammar built from parser combinators. It is an example of a
program that is natural to write in an exception-oriented style, or with
non-local returns in a language that has them. The natural translation of
NLRs to a language without them also uses exceptions in this way.

These parser combinators use explicitly initialized forward reference parsers
to handle cycles in the productions, rather than using reflection or 
doesNotUnderstand:, to make the benchmark portable to languages lacking
these features and to avoid measuring their performance. They also do not use
any platform-defined streams to avoid API differences.  Arithmetic operations
are masked to keep all intermediate results within Smi range.

This benchmark is derived from the Newspeak version of CombinatorialParsers,
which is why the Cadence copyrights apply.

Copyright 2008 Cadence Design Systems, Inc.
Copyright 2012 Cadence Design Systems, Inc.
Copyright 2013 Ryan Macnak and Google Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0
