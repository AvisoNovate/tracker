(ns io.aviso.tracker
  "Code for tracing behavior so that it can be logged when there's an exception."
  (:require [clojure.tools.logging :as l]
            [clojure.tools.logging.impl :as impl]
            [io.aviso.exception :as exception]))

;; Contains a list of messages (or functions that return messages) used to log the route to the exception.
(def ^:dynamic operation-traces [])

(defn- trace-to-string
  "Converts a trace to a string; a trace may be a function which is invoked."
  [message]
  (str (if (fn? message) (message) message)))
    
(defn- contains-operation-trace
  "Checks to see if an exception (or any of the exception's causes) is an IExceptionInfo containing an :operation-trace."
  [^Throwable e]
  (cond
    (nil? e) false
    (some-> e ex-data :operation-trace) true
    :else (recur (.getCause e))))

(defn- error [logger message]
  (l/log* logger :error nil message))

(defn- log-trace [logger trace-messages message e]
  (error logger "An exception has occurred:")
  (doseq [[trace-message i] (map vector trace-messages (iterate inc 1))]
    (error logger (format "[%3d] - %s" i trace-message)))
  (error logger (format "%s%n%s" message (exception/format-exception e))))

(defn track*
  "Traces the execution of a function of no arguments. The trace macro converts into a call to track-function.
  
  logger - SLF4J Logger where logging should occur
  trace-message - String, object, or function. Function evaulation is deferred until an exception is actually thrown.
  f - function to invoke."
  [logger trace f]
  (binding [operation-traces (conj operation-traces trace)]
    (try
      (f)
      (catch Throwable e
        ;; By nature, the track calls get deeply nested. We want to create an exception only at the initial failure,
        (if (contains-operation-trace e)
          (throw e)
          ;; This is the initial exception thrown so we'll wrap the original exception with the exception info.
          (let [trace-strings (map trace-to-string operation-traces)
                message (or (.getMessage e) (-> e .getClass .getName))]
            (log-trace logger trace-strings message e)
            (throw (ex-info message
                            {:operation-trace trace-strings}
                            e))))))))

(defn get-logger
  "Determines the logger instance for a namespace (by leveraging some clojure.tools.logging internals)."
  [namespace]
  (impl/get-logger l/*logger-factory* namespace))

(defmacro track
  "Tracks the execution of an operation, associating the trace (which describes the opeation) with the execution of the body.

  If an exception occurs inside the body, then all trace messages leading up to the
  point of the exception will be logged (the logger is determined from the current namespace); thus logging only occurs at the
  most deeply nested trace. The exception thrown is formatted and logged as well.

  trace may be a string, or a function that returns a string; execution of the function is deferred until needed. "
  [trace & body]
  `(track* (get-logger ~*ns*) ~trace #(do ~@body)))

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
