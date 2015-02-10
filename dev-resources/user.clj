(ns user
  (:use io.aviso.repl
        io.aviso.logging
        io.aviso.tracker
        clojure.pprint)
  (:require [clojure.tools.logging :as l]))

(install-pretty-exceptions)
(io.aviso.logging/install-pretty-logging)
(io.aviso.logging/install-uncaught-exception-handler)

(defmacro other-thread
  [& body]
  `(.start (Thread. (bound-fn []
                      (checkpoint
                        ~@body)))))


(defn demo-1
  []
  (track "on main thread" (other-thread (throw (RuntimeException. "hello")))))

(defn demo-2
  []
  (track "outer" (track "middle - a" (other-thread (try
                                                     (track "inner - a" (throw (ex-info "blowed up - a" {})))
                                                     (catch Throwable _))))
         (track "middle - b" (other-thread (try
                                             (track "inner - b" (throw (ex-info "blowed up - b" {})))
                                             (catch Throwable _))))))