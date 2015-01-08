(defproject sample "0.0.1-SNAPSHOT"
  :description "Sample petstore api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [frankiesardo/pedestal-swagger "0.1.1-SNAPSHOT"]

                 [io.pedestal/pedestal.service "0.3.0"]
                 [io.pedestal/pedestal.jetty "0.3.0"]

                 [ch.qos.logback/logback-classic "1.1.2" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.7"]
                 [org.slf4j/jcl-over-slf4j "1.7.7"]
                 [org.slf4j/log4j-over-slf4j "1.7.7"]]
  :uberjar-name "sample-standalone.jar"
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.5"]]}})
