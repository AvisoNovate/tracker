(ns user
  (:use io.aviso.repl
        clojure.pprint)
  (:require [clojure.tools.logging :as l]))

(install-pretty-exceptions)

(defmacro other-thread
  [& body]
  `(.start (Thread. (bound-fn [] ~@body))))

