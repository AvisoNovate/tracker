(ns io.aviso.tracker
  "Code for tracking operations so that a history can be presented when an exception is thrown.

  The main entrypoint is the [[track]] macro."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as l]
            [clojure.tools.logging.impl :as impl]
            [io.aviso.writer :as writer]
            [io.aviso.exception :as exception]
            [io.aviso.toolchest.exceptions :refer [to-message]])
  (:import [java.util WeakHashMap Map]))

;;; Contains a vector of messages (or functions that return messages) used to log the route to the exception.
(def ^:private ^:dynamic *operation-labels*
  "Contains a vector of operation labels (as passed to [[trace]]). These are used to log operations, when exceptions
  occur."
  [])

(def ^:dynamic *log-trace-level*
  "Controls whether operation labels are logged on entry to the [[track]] macro. By default (nil), no
  logging takes places.  Otherwise, this is a logging level (such as :debug or :info) to be used."
  nil)

(defn get-logger
  "Determines the logger instance for a namespace (by leveraging some clojure.tools.logging internals)."
  [namespace]
  (impl/get-logger l/*logger-factory* namespace))

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
  [message]
  (str (if (fn? message) (message) message)))

(defn operation-labels
  "Returns the labels for the current operations as a vector of strings. The outermost operation is first,
  the innermost operation is last."
  {:added "0.1.4"}
  []
  (mapv label-to-string *operation-labels*))

(defn- error [logger message]
  (l/log* logger :error nil message))

(defn- log-trace-label-stack [logger trace-labels e]
  (let [lines (concat ["An exception has occurred:"]
                      (map-indexed (fn [i label]
                                     (format "[%3d] - %s" (inc i) label))
                                   trace-labels)
                      [(exception/format-exception e)])]
    (->> lines
         (str/join writer/eol)
         (error logger))))

(defn checkpoint*
  "The main implementation of [[checkpoint]], using a specific logger.

  :logger
  : logger to use when logging the operation trace.

  :f
  : function to evaluate.

  The result of the function is returned.

  If the function throws an exception, it may be logged (and rethrown).  When the exception is logged,
  it includes the operation trace (the sequence of active operation labels), then the formatted exception.

  In the logging case, the exception is rethrown, wrapped as a new ex-info exception, with :operation-trace
  as the vector of operation label strings."
  {:added "0.1.5"}
  [logger f]
  (try
    (set-should-log-operations! true)
    (f)
    (catch Throwable t
      (if (should-log-operations?)
        (let [trace-label-strings (operation-labels)]
          (log-trace-label-stack logger trace-label-strings t)
          (set-should-log-operations! false)
          (throw (ex-info (to-message t)
                          {:operation-trace trace-label-strings}
                          t)))
        (throw t)))))

(defmacro checkpoint
  "Establishes a checkpoint, a point at which a thrown exception results in
  logging of the operation trace.

  checkpoint is useful when flow of control passes from one thread to another, such as
  when using clojure.core.async. Although the operation trace, being stored in a dynamic var,
  will be conveyed to the new thread, the exception handler necessary to report an exception
  is not. checkpoint provides that exception handler."
  {:added "0.1.5"}
  [& body]
  `(checkpoint* (get-logger ~*ns*) (fn [] ~@body)))

(defn track*
  "Tracks the execution of a function of no arguments. The [[track]] macro converts into a call to track*.
  Uses [[checkpoint]].
  
  logger
  : Logger where logging should occur (as defined by the clojure.tools.logging Logger protocol)

  label
  : String, object, or function. If a function, the function will only be evaluated if the message
    needs to be printed (when an exception occurs, or when logging traces via [[*log-trace-level*]]).

  f
  : function to invoke. track* returns the result from this function."
  [logger label f]
  (let [level' *log-trace-level*
        ;; Convert the label to a string immediately, if it is going to be logged
        ;; immediately. Otherwise, leave the object or function as-is, until it is actually needed.
        label' (if (and level'
                        (fn? label)
                        (impl/enabled? logger level'))
                 (label-to-string label)
                 label)]
    (binding [*operation-labels* (conj *operation-labels* label')]
      (when level'
        (l/log* logger *log-trace-level* nil label'))
      (checkpoint* logger f))))

(defmacro track
  "Tracks the execution of an operation, associating the label (which describes the operation) with the execution of the body.

  track is intended to be nested; each block of work that does something interesting can be wrapped as a track block.

  track is particularly useful when operations span across multiple threads.

  track does *not* do anything magic with respect to lazy evaluation.

  The provided label may be:

  * a simple string
  * a no-arguments function which is invoked, returning a string
  * any other object, which is converted to a string

  If an exception occurs inside the body, then all operation labels leading up to the
  point of the exception will be logged. See [[checkpoint*]] for the details.

  The operation trace is stored in a dynamic var; if you make use of Clojure's bound-fn macro to execute code in a new thread
  with the same bindings, then operations in a new thread will log as successors to operations in the current thread.

  Note that the clojure.core.async thread and go macros *do* propogate bindings to new threads, but it will be necessary
  to use the [[checkpoint]] macro to get the full benefit of operation tracking.

  See also: [[*log-trace-level*]]."
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
