(defproject io.aviso/tracker "0.1.3-SNAPSHOT"
            :description "Track per-thread operations when exceptions occur"
            :url "https://github.com/AvisoNovate/tracker"
            :license {:name "Apache Sofware Licencse 2.0"
                      :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [org.clojure/tools.logging "0.3.0"]
                           [io.aviso/pretty "0.1.12"]]
            :plugins [[lein-shell "0.4.0"]]
            :aliases {"deploy-doc" ["shell"
                                    "scp" "-r" "." "hlship_howardlewisship@ssh.phx.nearlyfreespeech.net:io.aviso/tracker"]
                      "release"    ["do"
                                    "clean,"
                                    "doc,"
                                    "deploy-doc,"
                                    "deploy" "clojars"]}
            :codox {:src-dir-uri               "https://github.com/AvisoNovate/tracker/blob/master/"
                    :src-linenum-anchor-prefix "L"
                    :defaults                  {:doc/format :markdown}}
            :profiles {:dev {:dependencies [[log4j "1.2.17"]]}})
