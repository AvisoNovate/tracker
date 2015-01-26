(ns io.aviso.tracker
  "Code for tracking operations so that a history can be presented when an exception is thrown."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as l]
            [clojure.tools.logging.impl :as impl]
            [io.aviso.writer :as writer]
            [io.aviso.exception :as exception])
  (:import (java.util WeakHashMap Map)))

;;; Contains a vector of messages (or functions that return messages) used to log the route to the exception.
(def ^:dynamic *operation-traces*
  "Contains a vector of operation traces (as passed to [[trace]]). These are used to log operations, when exceptions
  occur."
  [])

(def ^:dynamic *log-trace-level*
  "Controls whether operation traces are logged on entry to the [[track]] macro. By default (nil), no
  logging takes places.  Otherwise, this is a level (such as :debug or :info) to be used."
  nil)

(def ^:private ^Map log-exceptions?
  (WeakHashMap.))

(defn- set-should-log-operations!
  [flag]
  (locking log-exceptions?
    (if flag
      (.put log-exceptions? (Thread/currentThread) true)
      (.remove log-exceptions? (Thread/currentThread)))))

(defn- should-log-operations?
  []
  (locking log-exceptions?
    (.get log-exceptions? (Thread/currentThread))))

(defn- label-to-string
  "Converts a trace to a string; a trace may be a function which is invoked."
  [message]
  (str (if (fn? message) (message) message)))

(defn- error [logger message]
  (l/log* logger :error nil message))

(defn- log-trace-label-stack [logger trace-labels e]
  (let [lines (concat ["An exception has occurred."]
                      (map-indexed (fn [i label]
                                     (format "[%3d] - %s" (inc i) label))
                                   trace-labels)
                      [(exception/format-exception e)])]
    (->> lines
         (str/join writer/eol)
         (error logger))))

(defn track*
  "Tracks the execution of a function of no arguments. The trace macro converts into a call to track*.
  
  logger
  : Logger where logging should occur (as defined by the clojure.tools.logging Logger protocol)

  label
  : String, object, or function. If a function, the function will only be evaluated if the message
    needs to be printed (when an exception occurs, or when logging traces via [[*log-traces*]]).

  f
  : function to invoke. track* returns the result from this function."
  [logger label f]
  (binding [*operation-traces* (conj *operation-traces* label)]
    (set-should-log-operations! true)
    (try
      (when *log-trace-level*
        (l/log* logger *log-trace-level* nil (label-to-string label)))
      (f)
      (catch Throwable e
        (if (should-log-operations?)
          (let [trace-label-strings (mapv label-to-string *operation-traces*)
                message (or (.getMessage e) (-> e .getClass .getName))]
            (log-trace-label-stack logger trace-label-strings e)
            (set-should-log-operations! false)
            (throw (ex-info message
                            {:operation-trace trace-label-strings}
                            e)))
          (throw e))))))

(defn get-logger
  "Determines the logger instance for a namespace (by leveraging some clojure.tools.logging internals)."
  [namespace]
  (impl/get-logger l/*logger-factory* namespace))

(defmacro track
  "Tracks the execution of an operation, associating the label (which describes the operation) with the execution of the body.

  If an exception occurs inside the body, then all labels leading up to the
  point of the exception will be logged (the logger is determined from the current namespace); thus logging only occurs at the
  most deeply nested label. The exception thrown is formatted and logged as well.  The actual exception will be rethrown, wrapped in a
  new `ex-info` exception, with key `:operation-trace` set to the vector of operation trace strings.

  label may be a string, an object, or a function that returns a string; execution of the function is deferred until
  an operation trace label is needed.

  The operation trace is stored in a dynamic var; if you make use of Clojure's bound-fn macro to execute code in a new thread
  with the same bindings, then operations in a new thread will log as successors to operations in the current thread.

  Note that the clojure.core.async thead and go macros *do* propogate bindings to new threads."
  [label & body]
  `(track* (get-logger ~*ns*) ~label #(do ~@body)))

(defn timer*
  "Executes a function, timing the duration. It then calculates the elapsed time in milliseconds (as a double) and passes that to a second function.
  The second function can log the result. Finally, the result of the main function is returned."
  [main-fn elapsed-fn]
  (let [start-nanos (System/nanoTime)
        result (main-fn)
        elapsed-nanos (- (System/nanoTime) start-nanos)]
    (elapsed-fn (double (/ elapsed-nanos 1e6)))
    result))

(defmacro timer
  "Executes the body, timing the result. The elapsed time in milliseconds (as a double) is passed to the formatter function, which returns
  a string. The resulting string is logged at level INFO."
  [formatter & body]
  `(timer* #(do ~@body) #(l/info (~formatter %))))
