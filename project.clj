(defproject nu-pedestal-swagger "0.4.6-SNAPSHOT"
  :description "Swagger documentation for Pedestal routes"
  :url "http://github.com/frankiesardo/pedestal-swagger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[codox "0.8.13"]]
  :codox {:src-dir-uri "http://github.com/frankiesardo/pedestal-swagger/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.pedestal/pedestal.service "0.5.2"]
                 [metosin/ring-swagger "0.22.14"]
                 [metosin/ring-swagger-ui "2.2.8"]
                 [frankiesardo/linked "1.2.2"]
                 [s3-wagon-private "1.2.0"]]

  :repositories [["nu-maven" {:url        "s3p://nu-maven/releases/"
                              :username   [:gpg :env/artifacts_aws_access_key_id]
                              :passphrase [:gpg :env/artifacts_aws_secret_access_key]}]
                 ["central" {:url "http://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://clojars.org/repo/"}]]

  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.5.2"]
                                  [metosin/scjsv "0.2.0" :exclusions [org.clojure/core.async]]]}})
