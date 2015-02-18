## 0.1.7 -- UNRELEASED

Changed the timer macro to no longer invoke timer*; timer* is now deprecated.

## 0.1.6 -- 10 Feb 2014

Quick update to convert checkpoint* to a macro, so that it can be used inside a clojure.core.async go
block.

In addition, track* has been converted to a macro. It now accepts a sequence of forms, rather than
a no-arguments function.

## 0.1.5 -- 10 Feb 2015

Added several macros and functions to assist when an operation may span across multiple threads,
such as when using clojure.core.async.

## 0.1.4 -- 27 Jan 2015

Convert a label function to a string early, if it will be logged immediately due to \*log-trace-level\*.

Add a function for retrieving the current list of operations.

## 0.1.3 -- 26 Jan 2015

Operation traces are no longer stored in an atom; they are a persistent vector that is dynamically bound.
This allows an operation to start in one thread and 'continue' in another (if Clojure's bound-fn is used
to communicate the bound Vars into the new thread, as Clojure core.async does).

The \*log-traces\* var has been replaced with \*log-trace-level\*.
This new Var is used to define the level (such as
:debug or :info) to use when logging each operation's label, at the start of each operation.
It defaults to nil, which does not log the operation label on entry (only when an exception has occurred).

When an exception is logged with an operation trace, a single call to the clojure.tools.logging/error
function is made, with a fully assembled string for the messages, operation traces, and formatted
exception.