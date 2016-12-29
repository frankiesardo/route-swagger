(defproject frankiesardo/pedestal-swagger "0.4.4-NUBANK-SNAPSHOT"
  :description "Swagger documentation for Pedestal routes"
  :url "http://github.com/frankiesardo/pedestal-swagger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[codox "0.8.13"]]
  :codox {:src-dir-uri "http://github.com/frankiesardo/pedestal-swagger/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.pedestal/pedestal.service "0.5.1"]
                 [metosin/ring-swagger "0.22.4"]
                 [metosin/ring-swagger-ui "2.1.4-0"]
                 [frankiesardo/linked "1.2.2"]]
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["doc"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v"]
                  ["deploy" "clojars"]
                  ["rsync" "doc/" "gh-pages"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.5.1"]
                                  [metosin/scjsv "0.2.0" :exclusions [org.clojure/core.async]]]}})
