(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer :all]
            [io.aviso.tracker :refer :all]
            [io.aviso.repl :as repl]
            [io.aviso.logging :as logging]))

(repl/install-pretty-exceptions)
(logging/install-pretty-logging)
(logging/install-uncaught-exception-handler)

(defmacro other-thread
  [& body]
  `(.start (Thread. (bound-fn []
                      (checkpoint ~@body)))))

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