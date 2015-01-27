## 0.1.4 -- UNRELEASED

## 0.1.3 -- 26 Jan 2015

Operation traces are no longer stored in an atom; they are a persistent vector that is dynamically bound.
This allows an operation to start in one thread and 'continue' in another (if Clojure's bound-fn is used
to communicate the bound Vars into the new thread, as Clojure core.async does).

The \*log-traces\* var has replaces with \*log-trace-level\*.
This new Var is used to define the level (such as
:debug or :info) to use when logging each operation's label, at the start of each operation.
It defaults to nil, which does not log the operation label on entry (only when an exception has occurred).

When an exception is logged with an operation trace, a single call to the clojure.tools.logging/error
function is made, with a fully assembled string for the messages, operation traces, and formatted
exception.