(defproject nu-pedestal-swagger "0.4.7"
  :description "Swagger documentation for Pedestal routes"
  :url "http://github.com/frankiesardo/pedestal-swagger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[codox "0.8.13"]
            [s3-wagon-private "1.3.0"]]
  :codox {:src-dir-uri "http://github.com/frankiesardo/pedestal-swagger/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                 [cheshire "5.8.0"]
                 [io.pedestal/pedestal.service "0.5.3" :exclusions [cheshire]]
                 [metosin/ring-swagger "0.22.14"]
                 [metosin/ring-swagger-ui "2.2.8"]
                 [frankiesardo/linked "1.2.2"]]

  :repositories [["nu-maven" {:url "s3p://nu-maven/releases/" :snapshots false :sign-releases false}]
                 ["central" {:url "http://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://clojars.org/repo/"}]]

  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.5.3"]
                                  [metosin/scjsv "0.4.0" :exclusions [org.clojure/core.async cheshire]]]}})
