(defproject io.aviso/tracker "0.1.1"
  :description "Track per-thread operations when exceptions occur"
  :url "https://github.com/AvisoNovate/tracker"
  :license {:name "Apache Sofware Licencse 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.0"]
                 [io.aviso/pretty "0.1.12"]]
  :codox {:src-dir-uri               "https://github.com/AvisoNovate/tracker/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :profiles {:dev {:dependencies [[log4j "1.2.17"]]}})
